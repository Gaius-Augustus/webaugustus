package webaugustus

import org.springframework.validation.FieldError

/**
 * The class PredictionController controls everything that is related to preparing a job for predicting genes with pre-trained parameters on a novel genome
 *    - it handles the file upload
 *    - format check
 *    - sending E-Mails concerning the job status (submission)
 *    - start a PredictionService thread - to handle:
 *      - the file upload by wget
 *      - format check
 *      - job submission and status checks on Compute Cluster
 *      - rendering of results/job status page
 *      - sending E-Mails concerning the job status (downloaded files, errors, finished) 
 */
class PredictionController {
    
    static allowedMethods = [show: "GET", create: "GET", commit: "POST"] // only POST method invokes commit()
    
    def predictionService // inject the bean
    def messageSource     // inject the messageSource
    // human verification:
    def simpleCaptchaService
    
    // max button filesize
    def long maxButtonFileSize = 104857600 // 100 MB = 104857600 bytes getFile etc. gives size in byte
    // max ftp/http filesize
    def long maxFileSizeByWget = 1073741824 // 1 GB = 1073741824 bytes, curl gives size in bytes
    
    // other variables
    def prokaryotic = false // flag to determine whether augustus should be run in prokaryotic mode

    def show() {
        def instance = Prediction.get(params.id)
        if (instance == null) {
            render(view: '/jobnotfound')
            return
        }
        respond instance
    }
    
    def create() {
        // check whether the server is busy
        
        def logFile = predictionService.getLogFile()
        // logging verbosity-level
        def logVerb = predictionService.getLogLevel() // 1 only basic log messages, 2 all issued commands, 3 also script content
            
        int jobQueueLength = predictionService.getJobQueueLength()
        def maxJobQueueLength = predictionService.getMaxJobQueueLength()
        
        if(jobQueueLength >= maxJobQueueLength){
            def logMessage = "Somebody tried to invoke the Prediction webserver but the job queue length was ${jobQueueLength} and longer "
            logMessage += "than ${maxJobQueueLength} and the user was informed that submission is currently not possible"
            Utilities.log(logFile, 1, logVerb, "Prediction creation", logMessage)

            def m1 = "You tried to access the AUGUSTUS prediction job submission page."
            def m2 = "Predicting genes with AUGUSTUS is a process that takes a lot of computation time. "
            m2 += "We estimate that one prediction process requires at most approximately 7 days."
            render(view: "/busy", model: [message1: m1, message2: m2])

            return
        }
        
        Prediction predictionInstance = new Prediction(params)
        
        int count = 0
        while (count++ < 100) {
            // try 100 time to get a new predictionInstance with an accession_id not yet used in database
            if (Prediction.withTransaction { 
                    Prediction.findAll({ accession_id == predictionInstance.accession_id }) 
                }.isEmpty()) {
                
                break
            }
            Utilities.log(logFile, 1, logVerb, "Prediction creation", "create a new predictionInstance as currently selected accession_id ${predictionInstance.accession_id} is already used")
            predictionInstance = new Prediction(params)
        }        
        
        respond predictionInstance
    }
    
    def fillSample() {
        redirect(action:'create', controller: 'prediction', params:[genome_ftp_link:"http://bioinf.uni-greifswald.de/webaugustus/examples/LG16.fa",project_id:"honeybee1"])
    }

    // the method commit is started if the "Submit Job" button on the website is hit. It is the main method of Prediction Controller and contains a Thread method that will continue running as a background process after the user is redirected to the job status page.
    def commit() {
        
        def logFile = predictionService.getLogFile()
        // logging verbosity-level
        def verb = predictionService.getLogLevel() // 1 only basic log messages, 2 all issued commands, 3 also script content
        
        def output_dir = predictionService.getOutputDir()
        def web_output_dir = predictionService.getWebOutputDir()
        def http_base_url = predictionService.getHttpBaseURL()
        
        def AUGUSTUS_CONFIG_PATH = PredictionService.getAugustusConfigPath()
        def AUGUSTUS_SPECIES_PATH = PredictionService.getAugustusSpeciesPath()
        def AUGUSTUS_SCRIPTS_PATH = PredictionService.getAugustusScriptPath()
        
        def predictionInstance = new Prediction(params)
        if (predictionInstance.id != null) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Internal error 2.")
            String senderAdress = PredictionService.getWebaugustusEmailAddress()
            flash.error = "Internal error 2. Please contact ${senderAdress} if the problem persists!"
            redirect(action:'create', controller: 'prediction')
            return
        }
        if (request == null) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Internal error 3.")
            String senderAdress = PredictionService.getWebaugustusEmailAddress()
            flash.error = "Internal error 3. Please contact ${senderAdress} if the problem persists!"
            redirect(action:'create', controller: 'prediction')
            return
        }
        
         // clean up directory (delete) function
        String dirName = "${output_dir}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
        def deleteDir = {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Project directory ${dirName} is deleted")
            def cmd = ["rm -r ${dirName} &> /dev/null"]
            Utilities.execute(logFile, 2, predictionInstance.accession_id, "removeProjectDir", cmd)
        }
        
         // put redirect procedure into a function
        def cleanRedirect = {
            if (predictionInstance.email_adress == null) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous is aborted!")
            }
            else {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} is aborted!")
            }
            flash.message = "Info: Please check all fields marked in blue for completeness before starting the prediction job!"
            if (predictionInstance.species_select != null && !predictionInstance.species_select.equals("null") ) {
                predictionInstance.project_id = null
            }
            // flag for redirect to submission form, display warning in appropriate places
            predictionInstance.warn = true
       
            render(view:'create', model:[prediction:predictionInstance])
        }
        
        //verify that the submitter is a person
        boolean captchaValid = simpleCaptchaService.validateCaptcha(params.captcha)
        if (captchaValid == false) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The user is probably not a human person.")
            flash.error = "The verification string at the bottom of the page was not entered correctly!"
            cleanRedirect()
            return
        }
        
        try {
            request.getFile('GenomeFile')
        }
        catch (groovy.lang.MissingMethodException e) {
            Utilities.log(logFile, 1, 1, "COMMIT", "catched MissingMethodException ${e}")
            response.sendError(405)
            return
        }
        
        // retrieve parameters of form for early save()
        def uploadedGenomeFile = request.getFile('GenomeFile')
        def uploadedParamArch = request.getFile('ArchiveFile')
        def uploadedEstFile = request.getFile('EstFile')
        def uploadedStructFile = request.getFile('HintFile')
        predictionInstance.has_genome_file = uploadedGenomeFile != null && !uploadedGenomeFile.empty
        if (predictionInstance.has_genome_file) {
            predictionInstance.genome_file = uploadedGenomeFile.originalFilename
        }
        predictionInstance.has_param_file = uploadedParamArch != null && !uploadedParamArch.empty
        if (predictionInstance.has_param_file) {
            predictionInstance.archive_file = uploadedParamArch.originalFilename
        }
        predictionInstance.has_est_file = uploadedEstFile != null && !uploadedEstFile.empty
        if (predictionInstance.has_est_file) {
            predictionInstance.est_file = uploadedEstFile.originalFilename
        }
        predictionInstance.has_hint_file = uploadedStructFile != null && !uploadedStructFile.empty
        if (predictionInstance.has_hint_file) {
            predictionInstance.hint_file = uploadedStructFile.originalFilename
        }
        predictionInstance.message = ""
        predictionInstance.dateCreated = new Date();
        
        // info string for confirmation E-Mail
        String confirmationString = "Prediction job ID: ${predictionInstance.accession_id}\n"
        predictionInstance.job_id = 0
        // define flags for file format check, file removal in case of failure
        def overRideUtrFlag = 0
        // species name for AUGUSTUS
        def species
        
        // get date
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "AUGUSTUS prediction webserver starting on ${predictionInstance.dateCreated}")
        
        predictionInstance.validate()
        if (predictionInstance.hasErrors()) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "job request has errors: wrong or incomplete data in form")
            
            try {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id,  'prediction request errors ' + predictionInstance.errors.allErrors.size())
                predictionInstance.errors.allErrors.each {FieldError error ->
//                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "error: " + error)
//                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "default message: " + error.getDefaultMessage())
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "message source : " + messageSource.getMessage(error, null))
                }
            }
            catch (Throwable t) {
                Utilities.log(logFile, 1, 1, predictionInstance.accession_id, "catched Throwable ${t}")
            }
            
            cleanRedirect()
            return
        }
        
        // utr checkbox
        if(predictionInstance.utr == true){
            overRideUtrFlag = 1
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled UTR prediction.")
            
            // check valid UTR parameter combinations
            if (predictionInstance.ignore_conflicts) { // User enabled ignore strand conflicts.
                predictionInstance.errors.rejectValue("utr", "", "UTR prediction and the option \"Ignore conflicts with other strand\" are mutually exclusive. Please read the instructions in the UTR's 'Help' section!")
                cleanRedirect()
                return
            }
        }else{
            overRideUtrFlag = 0
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User did not enable UTR prediction.")
        }
        // get parameter archive file (if available)
        if (uploadedParamArch != null && !uploadedParamArch.empty) {
            // check file size
            def long preUploadSize = uploadedParamArch.getSize()
            if(preUploadSize > maxButtonFileSize){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The selected parameter archive file was bigger than ${maxButtonFileSize}.")
                flash.error = "Parameter archive file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }
            // actually upload the file
            projectDir.mkdirs()
            uploadedParamArch.transferTo( new File(projectDir, "parameters.tar.gz"))
            //predictionInstance.archive_file = uploadedParamArch.originalFilename
            confirmationString = "${confirmationString}Parameter archive: ${predictionInstance.archive_file}\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "uploaded parameter archive ${predictionInstance.archive_file} was renamed to parameters.tar.gz and moved to ${dirName}")
            def cmd = ["cksum ${dirName}/parameters.tar.gz"]
            predictionInstance.archive_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "archCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.archive_size = uploadedParamArch.size
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "parameters.tar.gz is ${predictionInstance.archive_size} big and has a cksum of ${predictionInstance.archive_cksum}.")

            // check whether the archive contains all relevant files
            def String paramDirName = "${dirName}/params"
            def paramDir = new File(paramDirName)
            paramDir.mkdirs()
            def cmdStr = "${AUGUSTUS_SCRIPTS_PATH}/checkParamArchive.pl ${dirName}/parameters.tar.gz ${paramDirName} > ${dirName}/archCheck.log 2> ${dirName}/archCheck.err"
            cmd = ["${cmdStr}"]
            Utilities.execute(logFile, 3, predictionInstance.accession_id, "checkParamArch", cmd)
            def archCheckLog = new File(projectDir, "archCheck.log")
            def archCheckErr = new File(projectDir, "archCheck.err")
            def archCheckLogSize = archCheckLog.text.size()
            def archCheckErrSize = archCheckErr.text.size()
            // if essential file are missing, redirect to input interface and inform user that the archive was not compatible
            if(archCheckErrSize > 0){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The parameter archive was not compatible.")
                deleteDir()
                flash.error = "Parameter archive ${uploadedParamArch.originalFilename} is not compatible with the AUGUSTUS prediction web server application."
                cleanRedirect()
                return
                // if only UTR params are missing, set flag to override any user-defined UTR settings
            }
            else if (archCheckLogSize > 0 && overRideUtrFlag == 1) {
                overRideUtrFlag = 0 // UTR predictions are now permanently disabled
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR predictions have been disabled because UTR parameters are missing!")
            }
        }else{predictionInstance.archive_file = "empty"}
        // check whether parameters are available for project_id (previous prediction run)
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The given parameter ID is ${predictionInstance.project_id}")
        if(!(predictionInstance.project_id == null)){
            predictionInstance.project_id = predictionInstance.project_id.trim();
            def spec_conf_dir = new File("${AUGUSTUS_SPECIES_PATH}/${predictionInstance.project_id}")
            if(!spec_conf_dir.exists()){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The given parameter-string \"${predictionInstance.project_id}\" does not exist on our system.")
                deleteDir()
                flash.error = "The specified parameter ID ${predictionInstance.project_id} does not exist on our system."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Requested ${spec_conf_dir} exists on our system.")
                species = predictionInstance.project_id
            }
            confirmationString = "${confirmationString}AUGUSTUS parameter project identifier: ${predictionInstance.project_id}\n"
        }
        // check whether parameters were supplied in double or triple
        if(predictionInstance.archive_file == "empty" && predictionInstance.project_id == null && predictionInstance.species_select == "null"){
            flash.error = "You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified no AUGUSTUS parameters")
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.archive_file != "empty" && predictionInstance.project_id != null && predictionInstance.species_select != "null"){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters in three different ways. Please decide for one way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.archive_file != "empty" && predictionInstance.project_id != null){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters as archive file and as project ID. Please decide for one way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.project_id != null && predictionInstance.species_select != "null"){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters as project ID and by selecting an organism from the dropdown menu. Please decide for one way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.archive_file != "empty" && predictionInstance.species_select != "null"){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters as parameter archive and by selecting an organism from the dropdown menu. Please decide for one way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }
        // assign parameter set from dropdown menu
        if(predictionInstance.species_select == "Acyrthosiphon pisum (animal)"){
            predictionInstance.project_id = "pea_aphid"
        }else if(predictionInstance.species_select == "Aedes aegypti (animal)"){
            predictionInstance.project_id = "aedes"
        }else if(predictionInstance.species_select == "Amphimedon queenslandica (animal)"){
            predictionInstance.project_id = "amphimedon"
        }else if(predictionInstance.species_select == "Apis mellifera (animal)"){
            predictionInstance.project_id = "honeybee1"
        }else if(predictionInstance.species_select == "Brugia malayi (animal)"){
            predictionInstance.project_id = "brugia"
        }else if(predictionInstance.species_select == "Caenorhabditis elegans (animal)"){
            predictionInstance.project_id = "caenorhabditis"
        }else if(predictionInstance.species_select == "Callorhinchus milii (animal)"){
            predictionInstance.project_id = "elephant_shark"
        }else if(predictionInstance.species_select == "Drosophila melanogaster (animal)"){
            predictionInstance.project_id = "fly"
        }else if(predictionInstance.species_select == "Gallus gallus domesticus (animal)"){
            predictionInstance.project_id = "chicken"
        }else if(predictionInstance.species_select == "Homo sapiens (animal)"){
            predictionInstance.project_id = "human"
        }else if(predictionInstance.species_select == "Petromyzon marinus (animal)"){
            predictionInstance.project_id = "lamprey"
        }else if(predictionInstance.species_select == "Nasonia vitripennis (animal)"){
            predictionInstance.project_id = "nasonia"
        }else if(predictionInstance.species_select == "Schistosoma mansoni (animal)"){
            predictionInstance.project_id = "schistosoma"
        }else if(predictionInstance.species_select == "Tribolium castaneum (animal)"){
            predictionInstance.project_id = "tribolium2012"
        }else if(predictionInstance.species_select == "Trichinella spiralis (animal)"){
            predictionInstance.project_id = "trichinella"
        }else if(predictionInstance.species_select == "Tetrahymena thermophila (alveolata)"){
            predictionInstance.project_id = "tetrahymena"
        }else if(predictionInstance.species_select == "Toxoplasma gondii (alveolata)"){
            predictionInstance.project_id = "toxoplasma"
        }else if(predictionInstance.species_select == "Leishmania tarantolae (protozoa)"){
            predictionInstance.project_id = "leishmania_tarentolae"
        }else if(predictionInstance.species_select == "Arabidopsis thaliana (plant)"){
            predictionInstance.project_id = "arabidopsis"
        }else if(predictionInstance.species_select == "Chlamydomonas reinhardtii (alga)"){
            predictionInstance.project_id = "chlamy2011"
        }else if(predictionInstance.species_select == "Galdieria sulphuraria (alga)"){
            predictionInstance.project_id = "galdieria"
        }else if(predictionInstance.species_select == "Solaneum lycopersicum (plant)"){
            predictionInstance.project_id = "tomato"
        }else if(predictionInstance.species_select == "Triticum/wheat (plant)"){
            predictionInstance.project_id = "wheat"
        }else if(predictionInstance.species_select == "Zea mays (plant)"){
            predictionInstance.project_id = "maize"
        }else if(predictionInstance.species_select == "Aspergillus fumigatus (fungus)"){
            predictionInstance.project_id = "aspergillus_fumigatus"
        }else if(predictionInstance.species_select == "Aspergillus nidulans (fungus)"){
            predictionInstance.project_id = "aspergillus_nidulans"
        }else if(predictionInstance.species_select == "Aspergillus oryzae (fungus)"){
            predictionInstance.project_id = "aspergillus_oryzae"
        }else if(predictionInstance.species_select == "Aspergillus terreus (fungus)"){
            predictionInstance.project_id = "aspergillus_terreus"
        }else if(predictionInstance.species_select == "Botrytis cinerea (fungus)"){
            predictionInstance.project_id = "botrytis_cinerea"
        }else if(predictionInstance.species_select == "Candida albicans (fungus)"){
            predictionInstance.project_id = "candida_albicans"
        }else if(predictionInstance.species_select == "Candida guilliermondii (fungus)"){
            predictionInstance.project_id = "candida_guilliermondii"
        }else if(predictionInstance.species_select == "Candida tropicalis (fungus)"){
            predictionInstance.project_id = "candida_tropicalis"
        }else if(predictionInstance.species_select == "Chaetomium globosum (fungus)"){
            predictionInstance.project_id = "chaetomium_globosum"
        }else if(predictionInstance.species_select == "Coccidioides immitis (fungus)"){
            predictionInstance.project_id = "coccidioides_immitis"
        }else if(predictionInstance.species_select == "Coprinus cinereus (fungus)"){
            predictionInstance.project_id = "coprinus"
        }else if(predictionInstance.species_select == "Cryptococcus neoformans (fungus)"){
            predictionInstance.project_id = "cryptococcus_neoformans_neoformans_B"
        }else if(predictionInstance.species_select == "Debarymomyces hansenii (fungus)"){
            predictionInstance.project_id = "debaryomyces_hansenii"
        }else if(predictionInstance.species_select == "Encephalitozoon cuniculi (fungus)"){
            predictionInstance.project_id = "encephalitozoon_cuniculi_GB"
        }else if(predictionInstance.species_select == "Eremothecium gossypii (fungus)"){
            predictionInstance.project_id = "eremothecium_gossypii"
        }else if(predictionInstance.species_select == "Fusarium graminearum (fungus)"){
            predictionInstance.project_id = "fusarium_graminearum"
        }else if(predictionInstance.species_select == "Histoplasma capsulatum (fungus)"){
            predictionInstance.project_id = "histoplasma_capsulatum"
        }else if(predictionInstance.species_select == "Kluyveromyces lactis (fungus)"){
            predictionInstance.project_id = "kluyveromyces_lactis"
        }else if(predictionInstance.species_select == "Laccaria bicolor (fungus)"){
            predictionInstance.project_id = "laccaria_bicolor"
        }else if(predictionInstance.species_select == "Lodderomyces elongisporus (fungus)"){
            predictionInstance.project_id = "lodderomyces_elongisporus"
        }else if(predictionInstance.species_select == "Magnaporthe grisea (fungus)"){
            predictionInstance.project_id = "magnaporthe_grisea"
        }else if(predictionInstance.species_select == "Neurospora crassa (fungus)"){
            predictionInstance.project_id = "neurospora_crassa"
        }else if(predictionInstance.species_select == "Phanerochaete chrysosporium (fungus)"){
            predictionInstance.project_id = "phanerochaete_chrysosporium"
        }else if(predictionInstance.species_select == "Pichia stipitis (fungus)"){
            predictionInstance.project_id = "pichia_stipitis"
        }else if(predictionInstance.species_select == "Rhizopus oryzae (fungus)"){
            predictionInstance.project_id = "rhizopus_oryzae"
        }else if(predictionInstance.species_select == "Saccharomyces cerevisiae (fungus)"){
            predictionInstance.project_id = "saccharomyces_cerevisiae_S288C"
        }else if(predictionInstance.species_select == "Camponotus floridanus (animal)"){
            predictionInstance.project_id = "camponotus_floridanus"
        }else if(predictionInstance.species_select == "Danio rerio (animal)"){
            predictionInstance.project_id = "zebrafish"
        }else if(predictionInstance.species_select == "Schizosaccharomyces pombe (fungus)"){
            predictionInstance.project_id = "schizosaccharomyces_pombe"
        }else if(predictionInstance.species_select == "Ustilago maydis (fungus)"){
            predictionInstance.project_id = "ustilago_maydis"
        }else if(predictionInstance.species_select == "Verticillium longisporum (fungus)"){
            predictionInstance.project_id = "verticillium_longisporum1"
        }else if(predictionInstance.species_select == "Yarrowia lipolytica (fungus)"){
            predictionInstance.project_id = "yarrowia_lipolytica"
        }else if(predictionInstance.species_select == "Heliconius melpomene (animal)"){
            predictionInstance.project_id = "heliconius_melpomene1"
        }else if(predictionInstance.species_select == "Bombus terrestris (animal)"){
            predictionInstance.project_id = "bombus_terrestris2"
        }else if(predictionInstance.species_select == "Rhodnius prolixus (animal)"){
            predictionInstance.project_id = "rhodnium"
        }else if(predictionInstance.species_select == "Conidiobolus coronatus (fungus)"){
            predictionInstance.project_id = "Conidiobolus_coronatus"
        }else if(predictionInstance.species_select == "Sulfolobus solfataricus (archaeon)"){
            predictionInstance.project_id = "sulfolobus_solfataricus"
            prokaryotic = true
        }else if(predictionInstance.species_select == "Escherichia coli (bacterium)"){
            predictionInstance.project_id = "E_coli_K12"
            prokaryotic = true
        }else if(predictionInstance.species_select == "Thermoanaerobacter tengcongensis (bacterium)"){
            predictionInstance.project_id = "thermoanaerobacter_tengcongensis"
            prokaryotic = true
        }
        if(predictionInstance.project_id != null && predictionInstance.species_select != "null"){
            species = predictionInstance.project_id
            confirmationString = "${confirmationString}AUGUSTUS parameter project identifier: ${predictionInstance.project_id}\n"
        }
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Parameter set ${predictionInstance.project_id} was assigned through dropdown selection ${predictionInstance.species_select}")
        if(predictionInstance.project_id == null && predictionInstance.archive_file == "empty"){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "project_id is empty.")
            deleteDir()
            flash.error = "No parameters given!"
            cleanRedirect()
            return

        }

        // upload of genome file
        List seqNames = []
        if (uploadedGenomeFile != null && !uploadedGenomeFile.empty) {
            // check file size
            long preUploadSize = uploadedGenomeFile.getSize()
            if(preUploadSize > maxButtonFileSize){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The selected genome file was bigger than ${maxButtonFileSize}. Submission rejected.")
                deleteDir()
                flash.error = "Genome file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }
            projectDir.mkdirs()
            uploadedGenomeFile.transferTo( new File(projectDir, "genome.fa"))
            //predictionInstance.genome_file = uploadedGenomeFile.originalFilename
            confirmationString = "${confirmationString}Genome file: ${predictionInstance.genome_file}\n"
            
            if (Utilities.isUnSupportedCompressMode(uploadedGenomeFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file is compressed by a not supported algorithm ${uploadedGenomeFile.originalFilename}")
                deleteDir()
                flash.error = "The genome file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            if (Utilities.isSupportedCompressMode(uploadedGenomeFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Genome file ${uploadedGenomeFile.originalFilename} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/genome.fa", "gz", logFile, verb, predictionInstance.accession_id)) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The gzipped Genome file is corrupt")
                    deleteDir()
                    flash.error = "The gzipped Genome file is corrupt."
                    cleanRedirect()
                    return
                }
            }
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "uploaded genome file ${uploadedGenomeFile.originalFilename} was renamed to genome.fa and moved to ${dirName}")
            // check number of scaffolds
            def cmd = ["grep -c '>' ${dirName}/genome.fa"]
            Long nSeqNumber = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "nSeqFile", cmd)
            int maxNSeqs = PredictionService.getMaxNSeqs()
            if(nSeqNumber == null || nSeqNumber > maxNSeqs){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.")
                deleteDir()
                flash.error = "Genome file contains more than ${maxNSeqs} scaffolds (${nSeqNumber}), which is the maximal number of scaffolds that we permit for submission with WebAUGUSTUS. Please remove all short scaffolds from your genome file!"
                cleanRedirect()
                return
            }

            // check for fasta format & extract fasta headers for gff validation:
            Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(new File(projectDir, "genome.fa"), seqNames)
            
            if (Utilities.FastaStatus.CONTAINS_METACHARACTERS.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                deleteDir()
                flash.error = "Genome file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file was not fasta.")
                deleteDir()
                flash.error = "Genome file ${uploadedGenomeFile.originalFilename} is not in DNA fasta format."
                cleanRedirect()
                return
            }
            cmd = ["cksum ${dirName}/genome.fa"]
            predictionInstance.genome_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.genome_size =  Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ") // just in case the file was gzipped
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome.fa is ${predictionInstance.genome_size} big and has a cksum of ${predictionInstance.genome_cksum}.")
        }
        // retrieve beginning of genome file for format check
        if(!(predictionInstance.genome_ftp_link == null)){
            confirmationString = "${confirmationString}Genome file: ${predictionInstance.genome_ftp_link}\n"
            if (Utilities.isUnSupportedCompressMode(predictionInstance.genome_ftp_link)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file is compressed by a not supported algorithm ${predictionInstance.genome_ftp_link}")
                deleteDir()
                flash.error = "The genome file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }            
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome web-link is ${predictionInstance.genome_ftp_link}")
            projectDir.mkdirs()

            // check whether URL exists
            def cmd = ['curl', '-IL', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', predictionInstance.genome_ftp_link]
            Integer error_code = Utilities.executeForInteger(logFile, 3, predictionInstance.accession_id, "urlExistsScript", cmd)
            if(error_code == null || (error_code != 200 && error_code != 302)){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome URL is not accessible. Response code: ${error_code}.")
                deleteDir()
                flash.error = "Cannot retrieve genome file from HTTP/FTP link ${predictionInstance.genome_ftp_link}."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome URL is accessible. Response code: ${error_code}.")
            }

            // check whether the genome file is small enough for upload
            cmd = ["wget --spider ${predictionInstance.genome_ftp_link} 2>&1"]
            def pattern = ".*Length: (\\d*).*"
            Long genome_size = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "spiderScript", cmd, pattern)
            if (genome_size == null) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Invalid genome URL.")
                flash.error = "Cannot retrieve genome file from HTTP/FTP link ${predictionInstance.genome_ftp_link}."
            }
            else if (genome_size > maxFileSizeByWget){ // 1 GB
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Genome file size exceeds permitted ${maxFileSizeByWget} bytes by ${genome_size} bytes.")
                flash.error = "Genome file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
            }
            if (genome_size == null || genome_size > maxFileSizeByWget) {
                deleteDir()
                cleanRedirect()
                return
            }

            // checking web file for DNA fasta format:
            if ( !Utilities.isSupportedCompressMode(predictionInstance.genome_ftp_link) ) {
                URL url = new URL("${predictionInstance.genome_ftp_link}");
                Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(url)
                
                if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The first 20 lines in genome file are not fasta.")
                    deleteDir()
                    flash.error = "Genome file ${predictionInstance.genome_ftp_link} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The linked genome file ${predictionInstance.genome_ftp_link} is gzipped. Format will be checked later after extraction.")
            }
        }
        
        // upload of est file
        if (uploadedEstFile != null && !uploadedEstFile.empty) {
            // check file size
            def long preUploadSize = uploadedEstFile.getSize()
            if(preUploadSize > maxButtonFileSize){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The selected cDNA file was bigger than ${maxButtonFileSize}.")
                flash.error = "cDNA file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                deleteDir()
                cleanRedirect()
                return
            }
            projectDir.mkdirs()
            uploadedEstFile.transferTo( new File(projectDir, "est.fa"))
            //predictionInstance.est_file = uploadedEstFile.originalFilename
            confirmationString = "${confirmationString}cDNA file: ${predictionInstance.est_file}\n"
            
            if (Utilities.isUnSupportedCompressMode(uploadedEstFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The EST file is compressed by a not supported algorithm ${uploadedEstFile.originalFilename}")
                deleteDir()
                flash.error = "The cDNA file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            if (Utilities.isSupportedCompressMode(uploadedEstFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST file ${uploadedEstFile.originalFilename} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/est.fa", "gz", logFile, verb, predictionInstance.accession_id)) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The gzipped EST file is corrupt")
                    deleteDir()
                    flash.error = "The gzipped cDNA file is corrupt."
                    cleanRedirect()
                    return
                }
            }
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Uploaded EST file ${uploadedEstFile.originalFilename} was renamed to est.fa and moved to ${dirName}")
            // check fasta format
            Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(new File(projectDir, "est.fa"))
           
            if (Utilities.FastaStatus.CONTAINS_METACHARACTERS.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).")
                deleteDir()
                flash.error = "cDNA file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file was not fasta.")
                deleteDir()
                flash.error = "cDNA file ${uploadedEstFile.originalFilename} is not in DNA fasta format."
                cleanRedirect()
                return
            }

            def cmd = ["cksum ${dirName}/est.fa"]
            predictionInstance.est_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.est_size =  Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ") // just in case the file was gzipped
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "est.fa is ${predictionInstance.est_size} big and has a cksum of ${predictionInstance.est_cksum}.")
        }
        // retrieve beginning of est file for format check
        if(!(predictionInstance.est_ftp_link == null)){
            confirmationString = "${confirmationString}cDNA file: ${predictionInstance.est_ftp_link}\n"
            if (Utilities.isUnSupportedCompressMode(predictionInstance.est_ftp_link)) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The EST file is compressed by a not supported algorithm ${predictionInstance.est_ftp_link}")
                deleteDir()
                flash.error = "The cDNA file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "est web-link is ${predictionInstance.est_ftp_link}")
            projectDir.mkdirs()

            // check whether URL exists
            def cmd = ['curl', '-IL', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', predictionInstance.est_ftp_link]
            Integer error_code = Utilities.executeForInteger(logFile, 3, predictionInstance.accession_id, "urlExistsScript", cmd)
            if(error_code == null || (error_code != 200 && error_code != 302)){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The EST URL is not accessible. Response code: ${error_code}.")
                deleteDir()
                flash.error = "Cannot retrieve cDNA file from HTTP/FTP link ${predictionInstance.est_ftp_link}."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The EST URL is accessible. Response code: ${error_code}.")
            }

            // check whether the genome file is small enough for upload
            cmd = ["wget --spider ${predictionInstance.est_ftp_link} 2>&1"]
            def pattern = ".*Length: (\\d*).*"
            Long est_size = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "spiderScript", cmd, pattern)
            if (est_size == null) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Invalid EST URL.")
                flash.error = "Cannot retrieve cDNA file from HTTP/FTP link ${predictionInstance.est_ftp_link}."
            }
            else if (est_size > maxFileSizeByWget) { // 1 GB
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST file size exceeds permitted ${maxFileSizeByWget} bytes by ${est_size} bytes.")
                flash.error = "cDNA file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
            }
            if (est_size == null || est_size > maxFileSizeByWget){
                deleteDir()
                cleanRedirect()
                return
            }

            // checking web file for DNA fasta format
            if ( !Utilities.isSupportedCompressMode(predictionInstance.est_ftp_link) ) {
                URL url = new URL("${predictionInstance.est_ftp_link}")
                Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(url)
                
                if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file was not fasta.")
                    deleteDir()
                    flash.error = "cDNA file ${predictionInstance.est_ftp_link} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The linked EST file ${predictionInstance.est_ftp_link} is gzipped. Format will be checked later after extraction.")
            }
        }
        // get hints file, format check
        // def uploadedStructFile = request.getFile('HintFile')
        if (uploadedStructFile != null && !uploadedStructFile.empty) {
            // check file size
            long preUploadSize = uploadedStructFile.getSize()
            long allowedHintsSize = maxButtonFileSize * 2
            if(preUploadSize > allowedHintsSize){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The selected Hints file was bigger than ${allowedHintsSize}.")
                deleteDir()
                flash.error = "Hints file is bigger than ${allowedHintsSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }
            projectDir.mkdirs()
            uploadedStructFile.transferTo( new File(projectDir, "hints.gff"))
            //predictionInstance.hint_file = uploadedStructFile.originalFilename
            confirmationString = "${confirmationString}Hints file: ${predictionInstance.hint_file}\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Uploaded hints file ${uploadedStructFile.originalFilename} was renamed to hints.gff and moved to ${dirName}")
            if (uploadedGenomeFile != null && !uploadedGenomeFile.empty) { // if seqNames already exists
                // gff format validation: number of columns 9, + or - in column 7, column 1 must be  member of seqNames
                Set seqNamesSet = seqNames.toSet()
                Set allowedFeatures = ["start", "stop", "tss", "tts", "ass", "dss", "exonpart", "exon", "intronpart", "intron",
                    "CDSpart", "CDS", "UTRpart", "UTR", "irpart", "nonexonpart", "genicpart"] as HashSet
                boolean emptyFlag = false
                boolean commentFlag = false
                boolean metacharacterFlag = false
                boolean gffColErrorFlag = false
                boolean gffNameErrorFlag = false
                boolean gffSourceErrorFlag = false
                boolean gffFeatureErrorFlag = false
                String unsupportedSource = ""
                String unsupportedSeqName = ""
                String unsupportedFeature = ""
                new File(projectDir, "hints.gff").eachLine{line ->
                    line = line.trim()
                    if (line.size() == 0) {
                        emptyFlag = true
                        return
                    }
                    if (line.startsWith("#")) {
                        commentFlag = true
                        return
                    }                    
                    if (!metacharacterFlag && (line.contains("*") || line.contains("?"))) {
                        metacharacterFlag = true
                    }
                    
                    if (gffColErrorFlag && gffNameErrorFlag && gffSourceErrorFlag && gffFeatureErrorFlag) {
                        return
                    }
                    
                    def gffArray = line.split("\t")
                    if (gffArray.size() != 9) {
                        gffColErrorFlag = true
                    }
                    else {
                        if (!gffSourceErrorFlag && (!gffArray[8].contains("source=M") && !gffArray[8].contains("src=M"))) {
                            unsupportedSource = gffArray[8]
                            gffSourceErrorFlag = true
                        }
                        if (!gffNameErrorFlag && !seqNamesSet.contains(gffArray[0])) {
                            unsupportedSeqName = gffArray[0]
                            gffNameErrorFlag = true
                        }
                        if (!gffFeatureErrorFlag && !allowedFeatures.contains(gffArray[2])) {
                            unsupportedFeature = gffArray[2]
                            gffFeatureErrorFlag = true
                        }                        
                    }
                }
                
                if (emptyFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The hints file contains empty lines.")
                    predictionInstance.errors.rejectValue("hint_file", "", "Hints file ${predictionInstance.hint_file} contains empty lines. This is not allowed.")
                }
                if (commentFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The hints file contains comments.")
                    predictionInstance.errors.rejectValue("hint_file", "", "Hints file ${predictionInstance.hint_file} contains comments. This is not allowed.")
                }
                if (metacharacterFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The hints file contains metacharacters (e.g. * or ?).")
                    predictionInstance.errors.rejectValue("hint_file", "", "Hints file ${predictionInstance.hint_file} contains metacharacters (*, ?, ...). This is not allowed.")
                }
                if (gffSourceErrorFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint files last column is not in correct format (e.g. \"${unsupportedSource}\")")
                    predictionInstance.errors.rejectValue("hint_file", "", "Hints file ${predictionInstance.hint_file} is not in a compatible gff format (the last column does not contain \"source=M\" but \"${unsupportedSource}\"). Please make sure the gff-format complies with the instructions in our 'Help' section!")
                }
                if (gffColErrorFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file does not always contain 9 columns.")
                    predictionInstance.errors.rejectValue("hint_file", "", "Hints file ${predictionInstance.hint_file} is not in a compatible gff format (has not 9 columns). Please make sure the gff-format complies with the instructions in our 'Help' section!")
                }
                if (gffNameErrorFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file contains entries that do not comply with genome sequence names. (e.g. \"${unsupportedSeqName}\")")
                    predictionInstance.errors.rejectValue("hint_file", "", "Entries in the hints file ${predictionInstance.hint_file} do not match the sequence names of the genome file (e.g. \"${unsupportedSeqName}\"). Please make sure the gff-format complies with the instructions in our 'Help' section!")
                }
                if (gffFeatureErrorFlag) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file contains unsupported features (e.g. \"${unsupportedFeature}\").")
                    predictionInstance.errors.rejectValue("hint_file", "", "Entries in the hints file ${predictionInstance.hint_file} contains unsupported features (e.g. \"${unsupportedFeature}\"). Please make sure the gff-format complies with the instructions in our 'Help' section!")
                }
                if (emptyFlag || commentFlag || metacharacterFlag || gffColErrorFlag || gffNameErrorFlag || gffSourceErrorFlag || gffFeatureErrorFlag) {
                    deleteDir()
                    cleanRedirect()
                    return
                }
            }

            def cmd = ["cksum ${dirName}/hints.gff"]
            predictionInstance.hint_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "structCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.hint_size = uploadedStructFile.size
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "hints.gff is ${predictionInstance.hint_size} big and has a cksum of ${predictionInstance.hint_cksum}.")
        }
        confirmationString = "${confirmationString}User set UTR prediction: ${predictionInstance.utr}\n"
        // utr
        // check whether utr parameters actually exist:
        if (species != null && new File("${AUGUSTUS_SPECIES_PATH}/${species}").exists()) {
            def utrParamContent = new File("${AUGUSTUS_SPECIES_PATH}/${species}/${species}_utr_probs.pbl")
            if (!utrParamContent.exists()) {
                overRideUtrFlag = 0;
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR prediction was disabled because UTR parameters do not exist for this species!")
            }
        }
        // enable or disable utr prediction in AUGUSTUS command
        if(overRideUtrFlag==1){
            if(predictionInstance.allowed_structures != 1 && predictionInstance.allowed_structures != 2){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR prediction was disabled due to incompatibility with at least one or exactly one gene predcition")
                overRideUtrFlag = 0;
            }
        }
        if (overRideUtrFlag==0 && predictionInstance.utr) {
            confirmationString = "${confirmationString}Server set UTR prediction: false [UTR parameters missing or conflict with allowed gene structure!]\n"
        }
        // strand prediction radio buttons
        if(predictionInstance.pred_strand == 1){
            confirmationString = "${confirmationString}Report genes on: both strands\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction on both strands.")
        }else if(predictionInstance.pred_strand == 2){
            confirmationString = "${confirmationString}Report genes on: forward strand only\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction on forward strand, only.")
        }else{
            confirmationString = "${confirmationString}Report genes on: reverse strand only\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction on reverse strand, only.")
        }
        // alternative transcript radio buttons
        if(predictionInstance.alt_transcripts == 1){
            confirmationString = "${confirmationString}Alternative transcripts: none\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User disabled prediction of alternative transcripts.")
        }else if(predictionInstance.alt_transcripts == 2){
            confirmationString = "${confirmationString}Alternative transcripts: few\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction of few alternative transcripts.")
        }else if(predictionInstance.alt_transcripts == 3){
            confirmationString = "${confirmationString}Alternative transcripts: medium\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction of medium alternative transcripts.")
        }else{
            confirmationString = "${confirmationString}Alternative transcripts: many\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction of many alternative transcripts.")
        }
        // gene structure radio buttons
        if(predictionInstance.allowed_structures == 1){
            confirmationString = "${confirmationString}Allowed gene structure: predict any number of (possibly partial) genes\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled the prediction of any number of genes.")
        }else if(predictionInstance.allowed_structures == 2){
            confirmationString = "${confirmationString}Allowed gene structure: only predict complete genes\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User disabled the prediction of incomplete genes.")
        }else if(predictionInstance.allowed_structures == 3){
            confirmationString = "${confirmationString}Allowed gene structure: only predict complete genes - at least one\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User disabled the prediction of incomplete genes and insists on at least one predicted gene.")
        }else{
            confirmationString = "${confirmationString}Allowed gene structure: predict exactly one gene\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled the prediction of exactly one gene.")
        }
        // ignore gene structure conflicts with other strand checkbox
        if(predictionInstance.ignore_conflicts == false){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User did not enable to ignore strand conflicts.")
        }else{
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled to ignore strand conflicts.")
        }
        confirmationString = "${confirmationString}Ignore conflictes with other strand: ${predictionInstance.ignore_conflicts}\n"
        // prokaryotic predictions (log information only)
        if(prokaryotic == false){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User selected a eukaryotic parameter set.")
        }else{
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User selected an experimental prokaryotic parameter set.");
        }
        // send confirmation email and redirect
        predictionInstance.validate();
        if(!predictionInstance.hasErrors() && Utilities.saveDomainWithTransaction(predictionInstance)){
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "committed predictionInstance.id ${predictionInstance.id}")
            
            // generate empty results page
            def cmd = ["${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${predictionInstance.accession_id} null '${predictionInstance.dateCreated}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 0 &> /dev/null"]
            Utilities.execute(logFile, verb, predictionInstance.accession_id, "emptyPageScript", cmd)
            predictionInstance.warn = false
            predictionInstance.job_status = 0
            String mailStr = "Details of your job:\n\n${confirmationString}\n"
            predictionInstance.message = "----------------------------------------\n${predictionInstance.dateCreated} - Message:\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------\n\n${mailStr}"
            Utilities.saveDomainWithTransaction(predictionInstance)
            
            if(predictionInstance.email_adress != null){
                String msgStr = "Thank you for submitting the AUGUSTUS gene prediction "
                msgStr = "${msgStr}job ${predictionInstance.accession_id}.\n\n"
                msgStr = "${msgStr}${mailStr}The status/results page of your job is "
                msgStr = "${msgStr}${http_base_url}show/${predictionInstance.id}.\n\n"
                msgStr = "${msgStr}You will be notified via email when the job has finished.\n\n"
                predictionService.sendMailToUser(predictionInstance, "AUGUSTUS prediction job ${predictionInstance.accession_id}", msgStr)
                
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Confirmation e-mail sent.")
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Did not send confirmation e-mail because user stays anonymous, but everything is ok.")
            }
            predictionService.startWorkerThread()
            redirect(action:'show', controller: 'prediction', id: predictionInstance.id)
        } else {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "An error occurred in the predictionInstance (e.g. E-Mail missing, see domain restrictions).")
            deleteDir()
            if(predictionInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous user is aborted!")
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} is aborted!")
            }
            render(view:'create', model:[prediction:predictionInstance])
        }
    }// end of commit
} // end of Controller
