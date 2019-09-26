package webaugustus

import javax.annotation.PostConstruct

/**
 * The class PredictionController controls everything that is related to preparing a job for predicting genes with pre-trained parameters on a novel genome
 *    - it handles the file upload
 *    - format check
 *    - sending E-Mails concerning the job status (submission)
 *    - start a PredictionService thread - to handle:
 *      - the file upload by wget
 *      - format check
 *      - SGE job submission and status checks
 *      - rendering of results/job status page
 *      - sending E-Mails concerning the job status (downloaded files, errors, finished) 
 */
class PredictionController {
    
    def predictionService // inject the bean
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
        def logVerb = predictionService.getVerboseLevel() // 1 only basic log messages, 2 all issued commands, 3 also script content
            
        def processForLog = "SGE         "
        def cmd = ['qstat -u "*" | grep qw | wc -l']
        Long qstatStatusNumber = Utilities.executeForLong(logFile, logVerb, processForLog, "qstatScript", cmd)
        def sgeLen = PredictionService.getMaxJobsCount()

        if(qstatStatusNumber != null && qstatStatusNumber > sgeLen){
            def logMessage = "Somebody tried to invoke the Prediction webserver but the SGE queue was longer "
            logMessage += "than ${sgeLen} and the user was informed that submission is currently not possible"
            Utilities.log(logFile, 1, logVerb, processForLog, logMessage)

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
        redirect(action:'create', controller: 'prediction', params:[genome_ftp_link:"http://bioinf.uni-greifswald.de/trainaugustus/examples/LG16.fa",project_id:"honeybee1"])
    }

    // the method commit is started if the "Submit Job" button on the website is hit. It is the main method of Prediction Controller and contains a Thread method that will continue running as a background process after the user is redirected to the job status page.
    def commit() {
        
        def logFile = predictionService.getLogFile()
        // logging verbosity-level
        def verb = predictionService.getVerboseLevel() // 1 only basic log messages, 2 all issued commands, 3 also script content
        
        def output_dir = predictionService.getOutputDir()
        def web_output_dir = predictionService.getWebOutputDir()
        def web_output_url = predictionService.getWebOutputURL()
        def war_url = predictionService.getWarURL()
        
        def AUGUSTUS_CONFIG_PATH = PredictionService.getAugustusConfigPath()
        def AUGUSTUS_SPECIES_PATH = PredictionService.getAugustusSpeciesPath()
        def AUGUSTUS_SCRIPTS_PATH = PredictionService.getAugustusScriptPath()
        
        def predictionInstance = new Prediction(params)
        if(!(predictionInstance.id == null)){
            String senderAdress = PredictionService.getWebaugustusEmailAdress()
            flash.error = "Internal error 2. Please contact ${senderAdress} if the problem persists!"
            redirect(action:'create', controller: 'prediction')
            return
        }
        
        // retrieve parameters of form for early save()
        def uploadedGenomeFile = request.getFile('GenomeFile')
        def uploadedParamArch = request.getFile('ArchiveFile')
        def uploadedEstFile = request.getFile('EstFile')
        def uploadedStructFile = request.getFile('HintFile')
        predictionInstance.has_genome_file = !uploadedGenomeFile.empty
        if (predictionInstance.has_genome_file) {
            predictionInstance.genome_file = uploadedGenomeFile.originalFilename
        }
        predictionInstance.has_param_file = !uploadedParamArch.empty
        if (predictionInstance.has_param_file) {
            predictionInstance.archive_file = uploadedParamArch.originalFilename
        }
        predictionInstance.has_est_file = !uploadedEstFile.empty
        if (predictionInstance.has_est_file) {
            predictionInstance.est_file = uploadedEstFile.originalFilename
        }
        predictionInstance.has_hint_file = !uploadedStructFile.empty
        if (predictionInstance.has_hint_file) {
            predictionInstance.hint_file = uploadedStructFile.originalFilename
        }
        predictionInstance.message = ""
        predictionInstance.dateCreated = new Date();
        
        // info string for confirmation E-Mail
        String confirmationString = "Prediction job ID: ${predictionInstance.accession_id}\n"
        predictionInstance.job_id = 0
        // define flags for file format check, file removal in case of failure
        def genomeFastaFlag = 0
        def estFastaFlag = 0
        def estExistsFlag = 0
        def hintExistsFlag = 0
        def overRideUtrFlag = 0
        // species name for AUGUSTUS
        def species
        
        // get date
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "AUGUSTUS prediction webserver starting on ${predictionInstance.dateCreated}")
        
        // put redirect procedure into a function
        def cleanRedirect = {
            if(predictionInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous is aborted!")
            }else{
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
        // clean up directory (delete) function
        def String dirName = "${output_dir}/${predictionInstance.accession_id}"
        def projectDir = new File(dirName)
        def deleteDir = {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Project directory ${dirName} is deleted")
            def cmd = ["rm -r ${dirName} &> /dev/null"]
            Utilities.execute(logFile, 2, predictionInstance.accession_id, "removeProjectDir", cmd)
        }
        // log abort function
        def logAbort = {
            if(predictionInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous user is aborted!")
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} is aborted!")
            }
        }
        //verify that the submitter is a person
        boolean captchaValid = simpleCaptchaService.validateCaptcha(params.captcha)
        if(captchaValid == false){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The user is probably not a human person.")
            flash.error = "The verification string at the bottom of the page was not entered correctly!"
            cleanRedirect()
            return
        }
        
        predictionInstance.validate()
        if (predictionInstance.hasErrors()) {
            cleanRedirect()
            return
        }
        
        // utr checkbox
        if(predictionInstance.utr == true){
            overRideUtrFlag = 1
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled UTR prediction.")
        }else{
            overRideUtrFlag = 0
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User did not enable UTR prediction.")
        }
        // get parameter archive file (if available)
        //def uploadedParamArch = request.getFile('ArchiveFile')
        if(!uploadedParamArch.empty){
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
            }else if(archCheckLogSize > 0){
                overRideUtrFlag = 0 // UTR predictions are now permanently disabled
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR predictions have been disabled because UTR parameters are missing!")
            }
        }else{predictionInstance.archive_file = "empty"}
        // check whether parameters are available for project_id (previous prediction run)
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The given parameter ID is ${predictionInstance.project_id}")
        if(!(predictionInstance.project_id == null)){
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
        //def uploadedGenomeFile
        //uploadedGenomeFile = request.getFile('GenomeFile')
        def seqNames = []
        if(!uploadedGenomeFile.empty){
            // check file size
            def long preUploadSize = uploadedGenomeFile.getSize()
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

            if("${uploadedGenomeFile.originalFilename}" =~ /\.gz/){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Genome file is gzipped.")
                def cmd = ["mv ${dirName}/genome.fa ${dirName}/genome.fa.gz; gunzip ${dirName}/genome.fa.gz"]
                Utilities.execute(logFile, verb, predictionInstance.accession_id, "gunzipGenomeScript", cmd)

                if (!new File(projectDir, "genome.fa").exists()) {
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
            def metacharacterFlag = 0
            new File(projectDir, "genome.fa").eachLine{line ->
                if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){ genomeFastaFlag = 1 }
                if(line =~ /\*/ || line =~ /\?/){
                    metacharacterFlag = 1
                }else{
                    if(line =~ /^>/){
                        def len = line.length()
                        seqNames << line[1..(len-1)]
                    }
                }
            }
            if(metacharacterFlag == 1){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                deleteDir()
                flash.error = "Genome file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if(genomeFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file was not fasta.")
                deleteDir()
                flash.error = "Genome file ${uploadedGenomeFile.originalFilename} is not in DNA fasta format."
                cleanRedirect()
                return
            }
            cmd = ["cksum ${dirName}/genome.fa"]
            predictionInstance.genome_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.genome_size = uploadedGenomeFile.size
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome.fa is ${predictionInstance.genome_size} big and has a cksum of ${predictionInstance.genome_cksum}.")
        }
        // retrieve beginning of genome file for format check
        if(!(predictionInstance.genome_ftp_link == null)){
            confirmationString = "${confirmationString}Genome file: ${predictionInstance.genome_ftp_link}\n"
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
            def pattern = ".*Length: (\\d*).* "
            Integer genome_size = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "spiderScript", cmd, pattern)
            if(genome_size == null || genome_size > maxFileSizeByWget){//1 GB
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Genome file size exceeds permitted ${maxFileSizeByWget} bytes by ${genome_size} bytes.")
                deleteDir()
                flash.error = "Genome file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
                cleanRedirect()
                return
            }

            // checking web file for DNA fasta format:
            if(!("${predictionInstance.genome_ftp_link}" =~ /\.gz/)){
                def URL url = new URL("${predictionInstance.genome_ftp_link}");
                def URLConnection uc = url.openConnection()
                def BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
                try{
                    def String inputLine
                    def char inputChar
                    def charCounter = 1
                    while ( ((inputChar = br.read()) != null) && (charCounter <= 1000)) {
                        if(inputChar =~ />/){
                            inputLine = br.readLine();
                        }else if(!(inputChar =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(inputChar =~ /^$/)){
                            genomeFastaFlag = 1
                        }
                        charCounter = charCounter + 1
                    }
                }finally{
                    br.close()
                }
                if(genomeFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The first 20 lines in genome file are not fasta.")
                    deleteDir()
                    flash.error = "Genome file ${predictionInstance.genome_ftp_link} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The linked genome file is gzipped. Format will be checked later after extraction.")
            }
        }
        // upload of est file
        // def uploadedEstFile = request.getFile('EstFile')
        if(!uploadedEstFile.empty){
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
            if("${uploadedEstFile.originalFilename}" =~ /\.gz/){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST file is gzipped.")
                def cmd = ["mv ${dirName}/est.fa ${dirName}/est.fa.gz; gunzip ${dirName}/est.fa.gz"]
                Utilities.execute(logFile, verb, predictionInstance.accession_id, "gunzipEstScript", cmd)

                if (!new File(projectDir, "est.fa").exists()) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The gzipped EST file is corrupt")
                    deleteDir()
                    flash.error = "The gzipped EST file is corrupt."
                    cleanRedirect()
                    return
                }
            }
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Uploaded EST file ${uploadedEstFile.originalFilename} was renamed to est.fa and moved to ${dirName}")
            // check fasta format
            def metacharacterFlag = 0
            new File(projectDir, "est.fa").eachLine{line ->
                if(line =~ /\*/ || line =~ /\?/){
                    metacharacterFlag = 1
                }else{
                    if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]/) && !(line =~ /^$/)){
                        estFastaFlag = 1
                    }
                }
            }
            if(metacharacterFlag == 1){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).")
                deleteDir()
                flash.error = "cDNA file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if(estFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file was not fasta.")
                deleteDir()
                flash.error = "cDNA file ${uploadedEstFile.originalFilename} is not in DNA fasta format."
                cleanRedirect()
                return
            } else { estExistsFlag = 1 }

            def cmd = ["cksum ${dirName}/est.fa"]
            predictionInstance.est_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.est_size = uploadedEstFile.size
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "est.fa is ${predictionInstance.est_size} big and has a cksum of ${predictionInstance.est_cksum}.")
        }
        // retrieve beginning of est file for format check
        if(!(predictionInstance.est_ftp_link == null)){
            confirmationString = "${confirmationString}cDNA file: ${predictionInstance.est_ftp_link}\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "est web-link is ${predictionInstance.est_ftp_link}")
            projectDir.mkdirs()
            estExistsFlag = 1

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
            def pattern = ".*Length: (\\d*).* "
            Long est_size = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "spiderScript", cmd, pattern)
            if(est_size == null || est_size > maxFileSizeByWget){//1 GB
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST file size exceeds permitted ${maxFileSizeByWget} bytes by ${est_size} bytes.")
                deleteDir()
                flash.error = "cDNA file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
                cleanRedirect()
                return
            }

            if(!("${predictionInstance.est_ftp_link}" =~ /\.gz/)){
                // checking web file for DNA fasta format:
                def URL url = new URL("${predictionInstance.est_ftp_link}");
                def URLConnection uc = url.openConnection()                    
                def BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
                try{
                    def String inputLine
                    def char inputChar
                    def charCounter = 1
                    while ( ((inputChar = br.read()) != null) && (charCounter <= 1000)) {
                        if(inputChar =~ />/){
                            inputLine = br.readLine();
                        }else if(!(inputChar =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(inputChar =~ /^$/)){
                            estFastaFlag = 1
                        }
                        charCounter = charCounter + 1
                    }
                }finally{
                    br.close()
                }
                if(estFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file was not fasta.")
                    deleteDir()
                    flash.error = "cDNA file ${predictionInstance.est_ftp_link} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The linked EST file is gzipped. Format will be checked later after extraction.")
            }
        }
        // get hints file, format check
        // def uploadedStructFile = request.getFile('HintFile')
        if(!uploadedStructFile.empty){
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
            def gffColErrorFlag = 0
            def gffNameErrorFlag = 0
            def gffSourceErrorFlag = 0
            def gffFeatureErrorFlag = 0
            if(!uploadedGenomeFile.empty){ // if seqNames already exists
                // gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
                def gffArray
                def isElement
                def metacharacterFlag = 0
                new File(projectDir, "hints.gff").eachLine{line ->
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = 1
                    }else{
                        gffArray = line.split("\t")
                        if(!(gffArray.size() == 9)){
                            gffColErrorFlag = 1
                        }else{
                            isElement = 0
                            seqNames.each{ seq ->
                                if(seq =~ /${gffArray[0]}/){ isElement = 1 }
                                if(isElement == 0){ gffNameErrorFlag = 1 }
                                if(!("${gffArray[8]}" =~ /source=M/)){gffSourceErrorFlag = 1}
                                if(!("${gffArray[2]}" =~ /start$/) && !("${gffArray[2]}" =~ /stop$/) && !("${gffArray[2]}" =~ /tss$/) && !("${gffArray[2]}" =~ /tts$/) && !("${gffArray[2]}" =~ /ass$/) && !("${gffArray[2]}" =~ /dss$/) && !("${gffArray[2]}" =~ /exonpart$/) && !("${gffArray[2]}" =~ /exon$/) && !("${gffArray[2]}" =~ /exon$/) && !("${gffArray[2]}" =~ /intronpart$/) && !("${gffArray[2]}" =~ /intron$/) && !("${gffArray[2]}" =~ /CDSpart$/) && !("${gffArray[2]}" =~ /CDS$/) && !("${gffArray[2]}" =~ /UTRpart$/) && !("${gffArray[2]}" =~ /UTR$/) && !("${gffArray[2]}" =~ /irpart$/) && !("${gffArray[2]}" =~ /nonexonpart$/) && !("${gffArray[2]}" =~ /genicpart$/)){
                                    gffFeatureErrorFlag = 1
                                }
                            }
                        }
                    }
                }
                if(metacharacterFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The hints file contains metacharacters (e.g. * or ?).")
                    deleteDir()
                    flash.error = "Hints file contains metacharacters (*, ?, ...). This is not allowed."
                    cleanRedirect()
                    return
                }
                if(gffSourceErrorFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file's last column is not in correct format")
                    flash.error = "Hints file  ${predictionInstance.hint_file} is not in a compatible gff format (the last column does not contain source=M). Please make sure the gff-format complies with the instructions in our 'Help' section!"
                }
                if(gffColErrorFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file does not always contain 9 columns.")
                    flash.error = "Hints file  ${predictionInstance.hint_file} is not in a compatible gff format (has not 9 columns). Please make sure the gff-format complies with the instructions in our 'Help' section!"
                }
                if(gffNameErrorFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file contains entries that do not comply with genome sequence names.")
                    flash.error = "Entries in the hints file  ${predictionInstance.hint_file} do not match the sequence names of the genome file. Please make sure the gff-format complies with the instructions in our 'Help' section!"
                }
                if(gffFeatureErrorFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hint file contains unsupported features.")
                    flash.error = "Entries in the hints file  ${predictionInstance.hint_file} contain unsupported features. Please make sure the gff-format complies with the instructions in our 'Help' section!"
                }
                if((gffColErrorFlag == 1 || gffNameErrorFlag == 1 || gffSourceErrorFlag == 1 || gffFeatureErrorFlag == 1)){
                    deleteDir()
                    cleanRedirect()
                    return
                }
            }
            hintExistsFlag = 1

            def cmd = ["cksum ${dirName}/hints.gff"]
            predictionInstance.hint_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "structCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.hint_size = uploadedStructFile.size
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "hints.gff is ${predictionInstance.hint_size} big and has a cksum of ${predictionInstance.hint_cksum}.")
        }
        confirmationString = "${confirmationString}User set UTR prediction: ${predictionInstance.utr}\n"
        // utr
        // check whether utr parameters actually exist:
        def utrParamContent = new File("${AUGUSTUS_SPECIES_PATH}/${species}/${species}_utr_probs.pbl")
        if(utrParamContent.exists() == false){
            overRideUtrFlag = 0;
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR prediction was disabled because UTR parameters do not exist for this species!")
        }
        // enable or disable utr prediction in AUGUSTUS command
        if(overRideUtrFlag==1){
            if(predictionInstance.allowed_structures != 1 && predictionInstance.allowed_structures != 2){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR prediction was disabled due to incompatibility with at least one or exactly one gene predcition")
                overRideUtrFlag = 0;
            }
        }else if(overRideUtrFlag==0 && predictionInstance.utr == true){
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
                msgStr = "${msgStr}${war_url}prediction/show/${predictionInstance.id}.\n\n"
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
            logAbort()
            render(view:'create', model:[prediction:predictionInstance])
        }
    }// end of commit
} // end of Controller
