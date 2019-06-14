package webaugustus

/**
 * The class PredictionController controls everything that is related to submitting a job for predicting genes with pre-trained parameters on a novel genome
 *    - it handles the file upload (or wget)
 *    - format check
 *    - SGE job submission and status checks
 *    - rendering of results/job status page
 *    - sending E-Mails concerning the job status (submission, errors, finished)
 */
class PredictionController {
    
    // need to adjust the output dir to whatever working dir! This is where uploaded files and results will be saved.
    def output_dir = "/data/www/webaugustus/webdata/augpred" // "data/www/augpred/webdata" // should be something in home of webserver user and augustus frontend user.
    //    def output_dir = "/data/www/test"
    // this log File contains the "process log", what was happening with which job when.
    def logFile = new File("${output_dir}/pred.log")
    // this log File contains the "database" (not identical with the grails database and simply for logging purpose)
    def dbFile = new File("${output_dir}/augustus-pred-database.log")
    // web-output, root directory to the results that are shown to end users
    def web_output_dir = "/data/www/webaugustus/prediction-results" // must be writable to webserver application
    def web_output_url = "http://bioinf.uni-greifswald.de/prediction-results/"
    def war_url = "http://bioinf.uni-greifswald.de/webaugustus/"
    def footer = "\n\n------------------------------------------------------------------------------------\nThis is an automatically generated message.\n\nhttp://bioinf.uni-greifswald.de/webaugustus" // footer of e-mail
    // AUGUSTUS_CONFIG_PATH
    def AUGUSTUS_CONFIG_PATH = "/usr/share/augustus/config"
    def AUGUSTUS_SCRIPTS_PATH = "/usr/share/augustus/scripts"
    def BLAT_PATH = "/usr/local/bin/blat"
    // Admin mail for errors
    def admin_email = "xxx@email.com"
    // sgeLen length of SGE queue, when is reached "the server is busy" will be displayed
    def sgeLen = 20;
    // max button filesize
    def long maxButtonFileSize = 104857600 // 100 MB = 104857600 bytes getFile etc. gives size in byte
    // max ftp/http filesize
    def long maxFileSizeByWget = 1073741824 // 1 GB = 1073741824 bytes, curl gives size in bytes
    // EST sequence properties (length)
    def int estMinLen = 250
    def int estMaxLen = 20000
    // logging verbosity-level
    def verb = 3 // 1 only basic log messages, 2 all issued commands, 3 also script content
    def cmd2Script
    def cmdStr
    def logDate
    def maxNSeqs = 250000 // maximal number of scaffolds allowed in genome file
    // other variables
    def prokaryotic = false // flag to determine whether augustus should be run in prokaryotic mode
    // human verification:
    def simpleCaptchaService
    
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
        def processForLog = "SGE         "
        def cmd = ['qstat -u "*" | grep qw | wc -l']
        def qstatStatusNumber = Utilities.executeForLong(logFile, verb, processForLog, "qstatScript", cmd)
        
        if(qstatStatusNumber > sgeLen){
            def logMessage = "Somebody tried to invoke the Prediction webserver but the SGE queue was longer "
            logMessage += "than ${sgeLen} and the user was informed that submission is currently not possible"
            Utilities.log(logFile, 1, verb, processForLog, logMessage)

            def m1 = "You tried to access the AUGUSTUS prediction job submission page."
            def m2 = "Predicting genes with AUGUSTUS is a process that takes a lot of computation time. "
            m2 += "We estimate that one prediction process requires at most approximately 7 days."
            render(view: "/busy", model: [message1: m1, message2: m2])

            return
        }
        
        respond new Prediction(params)
    }
    
    def fillSample() {
        redirect(action:'create', controller: 'prediction', params:[genome_ftp_link:"http://bioinf.uni-greifswald.de/trainaugustus/examples/LG16.fa",project_id:"honeybee1"])
    }

    // the method commit is started if the "Submit Job" button on the website is hit. It is the main method of Prediction Controller and contains a Thread method that will continue running as a background process after the user is redirected to the job status page.
    def commit() {
        def predictionInstance = new Prediction(params)
        if(!(predictionInstance.id == null)){
            flash.error = "Internal error 2. Please contact augustus-web@uni-greifswald.de if the problem persists!"
            redirect(action:'create', controller: 'prediction')
            return
        }
        
        // retrieve parameters of form for early save()
        def uploadedGenomeFile = request.getFile('GenomeFile')
        def uploadedParamArch = request.getFile('ArchiveFile')
        def uploadedEstFile = request.getFile('EstFile')
        def uploadedStructFile = request.getFile('HintFile')
        if(!(uploadedGenomeFile.empty)){
            predictionInstance.genome_file = uploadedGenomeFile.originalFilename
        }
        if(!(uploadedParamArch.empty)){
            predictionInstance.archive_file = uploadedParamArch.originalFilename
        }
        if(!(uploadedEstFile.empty)){
            predictionInstance.est_file = uploadedEstFile.originalFilename
        }
        if(!(uploadedStructFile.empty)){
            predictionInstance.hint_file = uploadedStructFile.originalFilename
        }
        predictionInstance.message = ""
        predictionInstance.dateCreated = new Date();
        predictionInstance.validate()
        
        if (predictionInstance.hasErrors()) {
            render(view:'create', model:[prediction:predictionInstance])
            return
        }
        
        // info string for confirmation E-Mail
        def confirmationString
        def mailStr
        confirmationString = "Prediction job ID: ${predictionInstance.accession_id}\n"
        predictionInstance.job_id = 0
        // define flags for file format check, file removal in case of failure
        def archiveExistsFlag = 0
        def speciesNameExistsFlag = 0
        def genomeFastaFlag = 0
        def estFastaFlag = 0
        def estExistsFlag = 0
        def hintGffFlag = 0
        def hintExistsFlag = 0
        def overRideUtrFlag = 0
        // species name for AUGUSTUS
        def species
        // delProc is needed at many places
        def delProc
        def st
        def content
        def int error_code
        def urlExistsScript
        def sgeErrSize = 10
        def writeResultsErrSize = 10
        def msgStr
        // get date
        def today = new Date()
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "AUGUSTUS prediction webserver starting on ${today}")
        // get IP-address
        // String userIP = request.remoteAddr
        logDate = new Date()
        //Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user IP: ${userIP}")

        // flag for redirect to submission form, display warning in appropriate places
        predictionInstance.warn = true
        // parameters for redirecting
        def redirParams=[:]
        if(predictionInstance.email_adress != null){
            redirParams["email_adress"]="${predictionInstance.email_adress}"
        }
        if(predictionInstance.genome_ftp_link != null){
            redirParams["genome_ftp_link"]="${predictionInstance.genome_ftp_link}"
        }
        if(predictionInstance.est_ftp_link != null){
            redirParams["est_ftp_link"]="${predictionInstance.est_ftp_link}"
        }
        if(predictionInstance.project_id != null){
            redirParams["project_id"]="${predictionInstance.project_id}"
        }
        if(predictionInstance.genome_file != null){
            redirParams["has_genome_file"]="${predictionInstance.warn}"
        }
        if(predictionInstance.est_file != null){
            redirParams["has_est_file"]="${predictionInstance.warn}"
        }
        if(predictionInstance.archive_file != null){
            redirParams["has_param_file"]="${predictionInstance.warn}"
        }
        if(predictionInstance.hint_file != null){
            redirParams["has_hint_file"]="${predictionInstance.warn}"
        }
        if(predictionInstance.species_select != "null"){
            redirParams["has_select"]="${predictionInstance.warn}"
        }
        if(predictionInstance.utr == true){
            redirParams["has_utr"]="${predictionInstance.warn}"
        }
        if(predictionInstance.pred_strand != 1){
            redirParams["has_strand"]="${predictionInstance.warn}"
        }
        if(predictionInstance.alt_transcripts != 1){
            redirParams["has_transcripts"]="${predictionInstance.warn}"
        }
        if(predictionInstance.allowed_structures != 1){
            redirParams["has_structures"]="${predictionInstance.warn}"
        }
        if(predictionInstance.ignore_conflicts == true){
            redirParams["has_conflicts"]="${predictionInstance.warn}"
        }
        if(predictionInstance.agree_email == true){
            redirParams["agree_email"] = true
        }
        if(predictionInstance.agree_nonhuman == true){
            redirParams["agree_nonhuman"] = true
        }

        redirParams["warn"]="${predictionInstance.warn}"
        // put redirect procedure into a function
        def cleanRedirect = {
            if(predictionInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous is aborted!")
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} is aborted!")
            }
            flash.message = "Info: Please check all fields marked in blue for completeness before starting the prediction job!"
            redirect(action:'create', controller: 'prediction', params:redirParams)
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
            cmd2Script = "${AUGUSTUS_SCRIPTS_PATH}/checkParamArchive.pl ${dirName}/parameters.tar.gz ${paramDirName} > ${dirName}/archCheck.log 2> ${dirName}/archCheck.err"
            cmd = ["${cmd2Script}"]
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
            archiveExistsFlag = 1
        }else{predictionInstance.archive_file = "empty"}
        // check whether parameters are available for project_id (previous prediction run)
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The given parameter ID is ${predictionInstance.project_id}")
        if(!(predictionInstance.project_id == null)){
            def spec_conf_dir = new File("${AUGUSTUS_CONFIG_PATH}/species/${predictionInstance.project_id}")
            if(!spec_conf_dir.exists()){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The given parameter-string \"${predictionInstance.project_id}\" does not exist on our system.")
                deleteDir()
                flash.error = "The specified parameter ID ${predictionInstance.project_id} does not exist on our system."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Requested ${spec_conf_dir} exists on our system.")
                speciesNameExistsFlag = 1
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
            flash.error = "You specified parameters in three different ways. Please decide for on way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.archive_file != "empty" && predictionInstance.project_id != null){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters as archive file and as project ID. Please decide for on way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.project_id != null && predictionInstance.species_select != "null"){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters as project ID and by selecting an organism from the dropdown menu. Please decide for on way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
            deleteDir()
            cleanRedirect()
            return
        }else if(predictionInstance.archive_file != "empty" && predictionInstance.species_select != "null"){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "user specified more than one option for AUGUSTUS parameters")
            flash.error = "You specified parameters as parameter archive and by selecting an organism from the dropdown menu. Please decide for on way! You need to specify a parameter archive for upload OR enter a project identifier OR select an organism!"
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
            def nSeqNumber = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "nSeqFile", cmd)
            if(nSeqNumber > maxNSeqs){
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
            def cmd = ['curl', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', predictionInstance.genome_ftp_link]
            error_code = Utilities.executeForInteger(logFile, 3, predictionInstance.accession_id, "urlExistsScript", cmd)
            if(!(error_code == 200) && !(error_code == 302)){
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
            def genome_size = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "spiderScript", cmd, pattern)
            if(genome_size > maxFileSizeByWget){//1 GB
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
            def cmd = ['curl', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', predictionInstance.est_ftp_link]
            error_code = Utilities.executeForInteger(logFile, 3, predictionInstance.accession_id, "urlExistsScript", cmd)
            if(!(error_code == 200) && !(error_code == 302)){
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
            def est_size = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "spiderScript", cmd, pattern)
            if(est_size > maxFileSizeByWget){//1 GB
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
            def long preUploadSize = uploadedStructFile.getSize()
            def long allowedHintsSize = maxButtonFileSize * 2
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
        def radioParameterString
        confirmationString = "${confirmationString}User set UTR prediction: ${predictionInstance.utr}\n"
        // utr
        // check whether utr parameters actually exist:
        def utrParamContent = new File("${AUGUSTUS_CONFIG_PATH}/species/${species}/${species}_utr_probs.pbl")
        if(utrParamContent.exists() == false){
            overRideUtrFlag = 0;
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR prediction was disabled because UTR parameters do not exist for this species!")
        }
        // enable or disable utr prediction in AUGUSTUS command
        if(overRideUtrFlag==1){
            if(predictionInstance.allowed_structures == 1 || predictionInstance.allowed_structures == 2){
                radioParameterString = " --UTR=on"
            }else{
                radioParameterString = " --UTR=off"
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "UTR prediction was disabled due to incompatibility with at least one or exactly one gene predcition")
                overRideUtrFlag = 0;
            }
        }else if(overRideUtrFlag==0 && predictionInstance.utr == true){
            confirmationString = "${confirmationString}Server set UTR prediction: false [UTR parameters missing or conflict with allowed gene structure!]\n"
            radioParameterString = " --UTR=off"
        }else{
            radioParameterString = " --UTR=off"
        }
        // strand prediction radio buttons
        if(predictionInstance.pred_strand == 1){
            radioParameterString = "${radioParameterString} --strand=both"
            confirmationString = "${confirmationString}Report genes on: both strands\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction on both strands.")
        }else if(predictionInstance.pred_strand == 2){
            confirmationString = "${confirmationString}Report genes on: forward strand only\n"
            radioParameterString = "${radioParameterString} --strand=forward"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction on forward strand, only.")
        }else{
            confirmationString = "${confirmationString}Report genes on: reverse strand only\n"
            radioParameterString = "${radioParameterString} --strand=backward"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction on reverse strand, only.")
        }
        // alternative transcript radio buttons
        if(predictionInstance.alt_transcripts == 1){
            radioParameterString = "${radioParameterString} --sample=100 --keep_viterbi=true --alternatives-from-sampling=false"
            confirmationString = "${confirmationString}Alternative transcripts: none\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User disabled prediction of alternative transcripts.")
        }else if(predictionInstance.alt_transcripts == 2){
            radioParameterString = "${radioParameterString} --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.2 --minmeanexonintronprob=0.5 --maxtracks=2"
            confirmationString = "${confirmationString}Alternative transcripts: few\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction of few alternative transcripts.")
        }else if(predictionInstance.alt_transcripts == 3){
            radioParameterString = "${radioParameterString} --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.08 --minmeanexonintronprob=0.4 --maxtracks=3"
            confirmationString = "${confirmationString}Alternative transcripts: medium\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction of medium alternative transcripts.")
        }else{
            radioParameterString = "${radioParameterString} --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.08 --minmeanexonintronprob=0.3 --maxtracks=20"
            confirmationString = "${confirmationString}Alternative transcripts: many\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled prediction of many alternative transcripts.")
        }
        // gene structure radio buttons
        if(predictionInstance.allowed_structures == 1){
            radioParameterString = "${radioParameterString} --genemodel=partial"
            confirmationString = "${confirmationString}Allowed gene structure: predict any number of (possibly partial) genes\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled the prediction of any number of genes.")
        }else if(predictionInstance.allowed_structures == 2){
            radioParameterString = "${radioParameterString} --genemodel=complete"
            confirmationString = "${confirmationString}Allowed gene structure: only predict complete genes\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User disabled the prediction of incomplete genes.")
        }else if(predictionInstance.allowed_structures == 3){
            radioParameterString = "${radioParameterString} --genemodel=atleastone"
            confirmationString = "${confirmationString}Allowed gene structure: only predict complete genes - at least one\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User disabled the prediction of incomplete genes and insists on at least one predicted gene.")
        }else{
            radioParameterString = "${radioParameterString} --genemodel=exactlyone"
            confirmationString = "${confirmationString}Allowed gene structure: predict exactly one gene\n"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User enabled the prediction of exactly one gene.")
        }
        // ignore gene structure conflicts with other strand checkbox
        if(predictionInstance.ignore_conflicts == false){
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "User did not enable to ignore strand conflicts.")
        }else{
            radioParameterString = "${radioParameterString} --strand=both"
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
            def cmd = ["${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${predictionInstance.accession_id} null ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 0 &> /dev/null"]
            Utilities.execute(logFile, verb, predictionInstance.accession_id, "emptyPageScript", cmd)
            predictionInstance.job_status = 0
            logDate = new Date()
            mailStr = "Details of your job:\n\n${confirmationString}\n"
            predictionInstance.message = "----------------------------------------\n${logDate} - Message:\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------\n\n${mailStr}"
            Utilities.saveDomainWithTransaction(predictionInstance)
            
            if(predictionInstance.email_adress != null){
                msgStr = "Hello!\n\n"
                msgStr = "${msgStr}Thank you for submitting the AUGUSTUS gene prediction "
                msgStr = "${msgStr}job ${predictionInstance.accession_id}.\n\n"
                msgStr = "${msgStr}${mailStr}The status/results page of your job is "
                msgStr = "${msgStr}${war_url}prediction/show/${predictionInstance.id}.\n\n"
                msgStr = "${msgStr}You will be notified via email when the job has finished.\n\nBest regards,\n\n"
                msgStr = "${msgStr}the AUGUSTUS web server team"
                sendMail {
                    to "${predictionInstance.email_adress}"
                    subject "AUGUSTUS prediction job ${predictionInstance.accession_id}"
                    text """${msgStr}${footer}"""
                }
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Confirmation e-mail sent.")
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Did not send confirmation e-mail because user stays anonymous, but everything is ok.")
            }
            redirect(action:'show', controller: 'prediction', id: predictionInstance.id)
        } else {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "An error occurred in the predictionInstance (e.g. E-Mail missing, see domain restrictions).")
            deleteDir()
            logAbort()
            render(view:'create', model:[prediction:predictionInstance])
            return
        }

        //---------------------  BACKGROUND PROCESS ----------------------------
        Thread.start{
            // retrieve genome file
            if(!(predictionInstance.genome_ftp_link == null)){
                projectDir.mkdirs()

                def cmd = ["wget -O ${dirName}/genome.fa ${predictionInstance.genome_ftp_link}  &> /dev/null"]
                Utilities.execute(logFile, verb, predictionInstance.accession_id, "getGenomeScript", cmd)

                if("${predictionInstance.genome_ftp_link}" =~ /\.gz/){
                    cmd = ["mv ${dirName}/genome.fa ${dirName}/genome.fa.gz; gunzip ${dirName}/genome.fa.gz"]
                    Utilities.execute(logFile, verb, predictionInstance.accession_id, "gunzipGenomeScript", cmd)
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Unpacked genome file.")
                }
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome file upload finished, file stored as genome.fa at ${dirName}")
                // check number of scaffolds (to avoid Java heapspace error in the next step)
                cmd = ["grep -c '>' ${dirName}/genome.fa"]
                def nSeqNumber = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "nSeqFile", cmd)
                if(nSeqNumber > maxNSeqs){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.");
                    deleteDir()
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided genome file\n${predictionInstance.genome_ftp_link}\ncontains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds). This is not allowed!\n\n"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}-----------------------------"
                    predictionInstance.message = "${predictionInstance.message}-----------------\n${logDate}"
                    predictionInstance.message = "${predictionInstance.message} - Error Message:\n-----------"
                    predictionInstance.message = "${predictionInstance.message}-----------------------------"
                    predictionInstance.message = "${predictionInstance.message}------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }

                // check for fasta format & get seq names for gff validation:
                def metacharacterFlag = 0
                new File(projectDir, "genome.fa").eachLine{line ->
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = 1
                    }else{
                        if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){ genomeFastaFlag = 1 }
                        if(line =~ /^>/){
                            def len = line.length()
                            seqNames << line[1..(len-1)]
                        }
                    }
                }
                if(metacharacterFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                    deleteDir()
                    logDate = new Date()
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided genome file\n${predictionInstance.genome_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}----------------------------"
                    predictionInstance.message = "${predictionInstance.message}------------------\n${logDate}"
                    predictionInstance.message = "${predictionInstance.message} - Error Message:\n----------"
                    predictionInstance.message = "${predictionInstance.message}------------------------------"
                    predictionInstance.message = "------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }
                if(genomeFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file was not fasta.")
                    deleteDir()
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided genome file ${predictionInstance.genome_ftp_link} was not in DNA fasta format.\n\n"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress == null){
                        msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }
                // check gff format
                def gffColErrorFlag = 0
                def gffNameErrorFlag = 0
                def gffSourceErrorFlag = 0
                if((!uploadedStructFile.empty) &&(!(predictionInstance.genome_ftp_link == null))){ // if seqNames already exists
                    // gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
                    def gffArray
                    def isElement
                    metacharacterFlag = 0
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
                                }
                            }
                        }
                    }
                    if(metacharacterFlag == 1){
                        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The hints file contains metacharacters (e.g. * or ?).");
                        deleteDir()
                        logDate = new Date()
                        mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided hints file\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                        logDate = new Date()
                        predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(predictionInstance)
                        if(predictionInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                            sendMail {
                                to "${predictionInstance.email_adress}"
                                subject "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                                text """${msgStr}${footer}"""
                            }
                        }
                        predictionInstance.results_urls = null
                        predictionInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(predictionInstance)
                        return
                    }
                    if(gffColErrorFlag == 1){
                        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hints file does not always contain 9 columns.")
                        mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided hints file\n${predictionInstance.hint_file}\ndid not contain 9 columns in each line. Please make sure the gff-format complies\nwith the instructions in our 'Help' section before submitting another job!\n\n"
                        logDate = new Date()
                        predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(predictionInstance)
                        if(predictionInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                            sendMail {
                                to "${predictionInstance.email_adress}"
                                subject "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                                text """${msgStr}${footer}"""
                            }
                        }
                    }
                    if(gffNameErrorFlag == 1){
                        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hints file contains entries that do not comply with genome sequence names.")
                        mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequence names in\nthe provided hints file\n${predictionInstance.hint_file}\ndid not comply with the sequence names in the supplied genome file\n${predictionInstance.genome_ftp_link}.\nPlease make sure the gff-format complies with the instructions in our 'Help' section\nbefore submitting another job!\n\n"
                        logDate = new Date()
                        predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(predictionInstance)
                        if(predictionInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                            sendMail {
                                to "${predictionInstance.email_adress}"
                                subject "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                                text """${msgStr}${footer}"""
                            }
                        }
                    }
                    if(gffSourceErrorFlag ==1){
                        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hints file contains entries that do not have source=M in the last column.")
                        mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the last column of your\nhints file\n${predictionInstance.hint_file}\ndoes not contain the content source=M. Please make sure the gff-format complies with\nthe instructions in our 'Help' section before submitting another job!\n\n"
                        logDate = new Date()
                        predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(predictionInstance)
                        if(predictionInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                            sendMail {
                                to "${predictionInstance.email_adress}"
                                subject "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                                text """${msgStr}${footer}"""
                            }
                        }
                    }
                    if((gffColErrorFlag == 1 || gffNameErrorFlag == 1 || gffSourceErrorFlag ==1)){
                        deleteDir()
                        logAbort()
                        predictionInstance.results_urls = null
                        predictionInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(predictionInstance)
                        return
                    }
                }

                cmd = ["cksum ${dirName}/genome.fa"]
                predictionInstance.genome_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
                predictionInstance.genome_size =  Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ")
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome.fa is ${predictionInstance.genome_size} big and has a cksum of ${predictionInstance.genome_cksum}.")
            } // end of if(!(predictionInstance.genome_ftp_link == null))

            // retrieve EST file
            if(!(predictionInstance.est_ftp_link == null)){

                def cmd = ["wget -O ${dirName}/est.fa ${predictionInstance.est_ftp_link}  &> /dev/null"]
                Utilities.execute(logFile, verb, predictionInstance.accession_id, "getEstScript", cmd)

                if("${predictionInstance.est_ftp_link}" =~ /\.gz/){
                    cmd = ["mv ${dirName}/est.fa ${dirName}/est.fa.gz; gunzip ${dirName}/est.fa.gz"]
                    Utilities.execute(logFile, verb, predictionInstance.accession_id, "gunzipEstScript", cmd)
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Unpacked EST file.")
                }
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST/cDNA file upload finished, file stored as est.fa at ${dirName}")
                // check for fasta format:
                def metacharacterFlag = 0
                new File(projectDir, "est.fa").eachLine{line ->
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = 1
                    }else{
                        if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){
                            estFastaFlag = 1
                        }
                    }
                }
                if(metacharacterFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).");
                    deleteDir()
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided cDNA file\n${predictionInstance.est_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }
                if(estFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The EST/cDNA file was not fasta.")
                    deleteDir()
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided cDNA file\n${predictionInstance.est_ftp_link}\nwas not in DNA fasta format.\n\n"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }

                cmd = ["cksum ${dirName}/est.fa"]
                predictionInstance.est_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
                predictionInstance.est_size =  Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ")
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "est.fa is ${predictionInstance.est_size} big and has a cksum of ${predictionInstance.est_cksum}.")
            } // end of if(!(predictionInstance.est_ftp_link == null))

            // check whether EST file is NOT RNAseq, i.e. does not contain on average very short entries
            def int nEntries = 0
            def int totalLen = 0
            if(estExistsFlag == 1){
                new File(projectDir, "est.fa").eachLine{line ->
                    if(line =~ /^>/){
                        nEntries = nEntries + 1
                    }else{
                        totalLen = totalLen + line.size()
                    }
                }
                def avEstLen = totalLen/nEntries
                if(avEstLen < estMinLen){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data.")
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequences files\nwith an average sequence length shorter than ${estMinLen} might contain RNAseq\nraw sequences. Currently, our web server application does not support the integration\nof RNAseq raw sequences. Please either assemble your sequences into longer contigs,\nor remove short sequences from your current file, or submit a new job without\nspecifying a cDNA file.\n\n"
                    def errorStrMsg = "Hello!\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress != null){

                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${errorStrMsg}${footer}"""
                        }
                    }
                    deleteDir()
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }else if(avEstLen > estMaxLen){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data.")
                    logAbort()
                    mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequence\nfiles with an average sequence length longer than ${estMaxLen} might not contain\nESTs or cDNAs. Please either remove long sequences from your current file, or\nsubmit a new job without specifying a cDNA file.\n\n"
                    def errorStrMsg = "Hello!\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                    logDate = new Date()
                    predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    if(predictionInstance.email_adress != null){
                        sendMail {
                            to "${predictionInstance.email_adress}"
                            subject "AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted"
                            text """${errorStrMsg}${footer}"""
                        }
                    }
                    deleteDir()
                    predictionInstance.results_urls = null
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                    return
                }
            }

            // confirm file upload via e-mail
            if((!(predictionInstance.genome_ftp_link == null)) || (!(predictionInstance.est_ftp_link == null))){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Retrieved all ftp files successfully.")
                mailStr = "We have retrieved all files that you specified, successfully. You may delete them\nfrom the public server, now, without affecting the AUGUSTUS prediction job.\n\n"
                logDate = new Date()
                predictionInstance.message = "${predictionInstance.message}----------------------------------------\n${logDate} - Message:\n----------------------------------------\n\n${mailStr}"
                Utilities.saveDomainWithTransaction(predictionInstance)
                if(predictionInstance.email_adress != null){
                    msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS web server team"
                    sendMail {
                        to "${predictionInstance.email_adress}"
                        subject "File upload has been completed for AUGUSTUS prediction job ${predictionInstance.accession_id}"
                        text """${msgStr}${footer}"""
                    }
                }
            }

            // File formats appear to be ok.
            // check whether this job was submitted before:
            def grepScript = new File(projectDir, "grepScript.sh")
            def grepResult = "${dirName}/grep.result"
            cmd2Script = "grep \"\\(Genome-Cksum: \\[${predictionInstance.genome_cksum}\\] Genome-Filesize: \\[${predictionInstance.genome_size}\\]\\)\" ${dbFile} | grep \"\\(EST-Cksum: \\[${predictionInstance.est_cksum}\\] EST-Filesize: \\[${predictionInstance.est_size}\\]\\)\" | grep \"\\(Hint-Cksum: \\[${predictionInstance.hint_cksum}\\] Hint-Filesize: \\[${predictionInstance.hint_size}\\] Parameter-String: \\[${predictionInstance.project_id}\\]\\)\" | grep \"\\(Parameter-Cksum: \\[${predictionInstance.archive_cksum}\\] Parameter-Size: \\[${predictionInstance.archive_size}\\] Server-Set-UTR-Flag: \\[${overRideUtrFlag}\\]\\)\" | grep \"\\(Report-Genes: \\[${predictionInstance.pred_strand}\\] Alternative-Transcripts: \\[${predictionInstance.alt_transcripts}\\] Gene-Structures: \\[${predictionInstance.allowed_structures}\\] Ignore-Conflicts: \\[${predictionInstance.ignore_conflicts}\\]\\)\"  > ${grepResult} 2> /dev/null"
            cmdStr = "bash ${dirName}/grepScript.sh"
            grepScript << "${cmd2Script}"
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "grepScript << \"${cmd2Script}\"")
            def grepJob = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
            grepJob.waitFor()
            def grepContent = new File("${grepResult}").text
            if(grepContent =~ /Genome-Cksum/){
                //job was submitted before. Send E-Mail to user with a link to the results.
                def id_array = grepContent =~ /Grails-ID: \[(\w*)\] /
                // oldID is a parameter that is used for showing redirects (see bottom)
                def oldID
                (0..id_array.groupCount()).each{oldID = "${id_array[0][it]}"}
                def oldAccScript = new File(projectDir, "oldAcc.sh")
                def oldAccResult = "${dirName}/oldAcc.result"
                cmd2Script = "grep \"Grails-ID: \\[${oldID}\\]\" ${dbFile} | perl -ne \"@t = split(/\\[/); @t2 = split(/\\]/, \\\$t[4]); print \\\$t2[0];\" > ${oldAccResult} 2> /dev/null"
                oldAccScript << "${cmd2Script}"
                Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "oldAccScript << \"${cmd2Script}\"")
                cmdStr = "bash ${dirName}/oldAcc.sh"
                def oldAccScriptProc = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
                oldAccScriptProc.waitFor()
                def oldAccContent = new File("${oldAccResult}").text
                mailStr = "You submitted job ${predictionInstance.accession_id}.\nThe job was aborted because the files that you submitted were submitted, before.\n\n"
                predictionInstance.old_url = "${war_url}prediction/show/${oldID}"
                logDate = new Date()
                predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                Utilities.saveDomainWithTransaction(predictionInstance)
                if(predictionInstance.email_adress != null){
                    msgStr = "Hello!\n\n${mailStr}The old job with identical input files and identical parameters"
                    msgStr = "${msgStr} is available at\n${war_url}prediction/show/${oldID}.\n\nBest regards,\n\n"
                    msgStr = "${msgStr}the AUGUSTUS web server team"
                    sendMail {
                        to "${predictionInstance.email_adress}"
                        subject "AUGUSTUS prediction job ${predictionInstance.accession_id} was submitted before as job ${oldAccContent}"
                        text """${msgStr}${footer}"""
                    }
                }
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Data are identical to old job ${oldAccContent} with Accession-ID ${oldAccContent}.")
                deleteDir()
                logAbort()
                predictionInstance.results_urls = null
                predictionInstance.job_status = 5
                Utilities.saveDomainWithTransaction(predictionInstance)
                return
            } // end of job was submitted before check

            //Write DB file:
            dbFile << "Date: [${today}] Grails-ID: [${predictionInstance.id}] Accession-ID: [${predictionInstance.accession_id}] Genome-File: [${predictionInstance.genome_file}] Genome-FTP-Link: [${predictionInstance.genome_ftp_link}] Genome-Cksum: [${predictionInstance.genome_cksum}] Genome-Filesize: [${predictionInstance.genome_size}] EST-File: [${predictionInstance.est_file}] EST-FTP-Link: [${predictionInstance.est_ftp_link}] EST-Cksum: [${predictionInstance.est_cksum}] EST-Filesize: [${predictionInstance.est_size}] Hint-File: [${predictionInstance.hint_file}] Hint-Cksum: [${predictionInstance.hint_cksum}] Hint-Filesize: [${predictionInstance.hint_size}] Parameter-String: [${predictionInstance.project_id}] Parameter-File: [${predictionInstance.archive_file}] Parameter-Cksum: [${predictionInstance.archive_cksum}] Parameter-Size: [${predictionInstance.archive_size}] Server-Set-UTR-Flag: [${overRideUtrFlag}] User-Set-UTR-Flag: [${predictionInstance.utr}] Report-Genes: [${predictionInstance.pred_strand}] Alternative-Transcripts: [${predictionInstance.alt_transcripts}] Gene-Structures: [${predictionInstance.allowed_structures}] Ignore-Conflicts: [${predictionInstance.ignore_conflicts}]\n"

            //rename and move parameters
            if(!uploadedParamArch.empty){
                def mvParamsScript = new File(projectDir, "mvParams.sh")
                cmd2Script = "${AUGUSTUS_SCRIPTS_PATH}/moveParameters.pl ${dirName}/params ${predictionInstance.accession_id} ${AUGUSTUS_CONFIG_PATH}/species 2> /dev/null\n"
                mvParamsScript << "${cmd2Script}"
                Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "mvParamsScript << \"${cmd2Script}\"")
                cmdStr = "bash ${mvParamsScript}"
                def mvParamsRunning = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
                mvParamsRunning.waitFor()
                species = "${predictionInstance.accession_id}"
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Moved uploaded parameters and renamed species to ${predictionInstance.accession_id}")
            }
            //Create sge script:
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Writing SGE submission script.")
            def sgeFile = new File(projectDir, "aug-pred.sh")
            // write command in script (according to uploaded files)
            sgeFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
            def cmdStr = "mkdir ${dirName}/augustus\n"
            if(estExistsFlag == 1){
                cmdStr = "${cmdStr}${BLAT_PATH} -noHead ${dirName}/genome.fa ${dirName}/est.fa ${dirName}/est.psl\n"
                cmdStr = "${cmdStr}cat ${dirName}/est.psl | sort -n -k 16,16 | sort -s -k 14,14 > ${dirName}/est.s.psl\n"
                cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/blat2hints.pl --in=${dirName}/est.s.psl --out=${dirName}/est.hints --source=E\n"
                cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/blat2gbrowse.pl ${dirName}/est.s.psl ${dirName}/est.gbrowse\n"
            }
            if(hintExistsFlag == 1){
                cmdStr = "${cmdStr}cat ${dirName}/hints.gff >> ${dirName}/est.hints\n"
            }
            if((hintExistsFlag == 1) || (estExistsFlag == 1)){
                radioParameterString = "${radioParameterString} --hintsfile=${dirName}/est.hints --extrinsicCfgFile=${AUGUSTUS_CONFIG_PATH}/extrinsic/extrinsic.ME.cfg"
            }
            cmdStr = "${cmdStr}cd ${dirName}/augustus\naugustus --species=${species} ${radioParameterString} ${dirName}/genome.fa --codingseq=on --exonnames=on > ${dirName}/augustus/augustus.gff\n"
            cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/getAnnoFasta.pl --seqfile=${dirName}/genome.fa ${dirName}/augustus/augustus.gff\n"
            cmdStr = "${cmdStr}cat ${dirName}/augustus/augustus.gff | perl -ne 'if(m/\\tAUGUSTUS\\t/){print;}' > ${dirName}/augustus/augustus.gtf\n"
            cmdStr = "${cmdStr}cat ${dirName}/augustus/augustus.gff | ${AUGUSTUS_SCRIPTS_PATH}/augustus2gbrowse.pl > ${dirName}/augustus/augustus.gbrowse\n"
            cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${predictionInstance.accession_id} null ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 2> ${dirName}/writeResults.err"
            sgeFile << "${cmdStr}"
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "sgeFile=${cmdStr}")
            // write submission script
            def submissionScript = new File(projectDir, "submit.sh")
            def fileID = "${dirName}/jobID"
            cmd2Script = "cd ${dirName}; qsub aug-pred.sh > ${fileID} 2> /dev/null"
            submissionScript << "${cmd2Script}"
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "submissionScript << \"${cmd2Script}\"")
            // submit job
            cmdStr = "bash ${dirName}/submit.sh"
            def jobSubmission = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
            jobSubmission.waitFor()
            // get job ID
            content = new File(fileID).text
            if (content.isEmpty()) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The augustus job wasn't started")
                predictionInstance.results_urls = null
                predictionInstance.job_status = 5
                Utilities.saveDomainWithTransaction(predictionInstance)
                return
            }

            def jobID_array = content =~/Your job (\d*)/
            def jobID
            (1..jobID_array.groupCount()).each{jobID = "${jobID_array[0][it]}"}
            predictionInstance.job_id = jobID
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${jobID} submitted.")
            // check for job status
            predictionInstance.job_status = 1 // submitted
            Utilities.saveDomainWithTransaction(predictionInstance)
            def statusScript = new File(projectDir, "status.sh")
            def statusFile = "${dirName}/job.status"
            cmd2Script = "cd ${dirName}; qstat -u \"*\" | grep aug-pred | grep ${jobID} > ${statusFile} 2> /dev/null"
            statusScript << "${cmd2Script}"
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "statusScript << \"${cmd2Script}\"")
            def statusContent
            def statusCheck
            def qstat = 1
            def runFlag = 0;

            while(qstat == 1){
                sleep(300000) // 300000 = 5 minutes
                cmdStr = "bash ${dirName}/status.sh"
                statusCheck = "${cmdStr}".execute()
                statusCheck.waitFor()
                sleep(100)
                statusContent = new File("${statusFile}").text
                if(statusContent =~ /qw/){
                    predictionInstance.job_status = 2
                }else if( statusContent =~ /  r  / ){
                    predictionInstance.job_status = 3
                    if(runFlag == 0){
                        today = new Date()
                        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${jobID} begins running at ${today}.")
                    }
                    runFlag = 1
                }else if(!statusContent.empty){
                    predictionInstance.job_status = 3
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${jobID} is neither in qw nor in r status but is still on the grid!")
                }else{
                    predictionInstance.job_status = 4
                    qstat = 0
                    today = new Date()
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${jobID} left SGE at ${today}.")
                }
                Utilities.saveDomainWithTransaction(predictionInstance)
            }
            // set file rigths to readable by others
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "set file permissions on ${web_output_dir}/${predictionInstance.accession_id}")
            def webOutputDir = new File(web_output_dir, predictionInstance.accession_id)
            if (webOutputDir.exists()) {
                webOutputDir.setReadable(true, false)
                webOutputDir.setExecutable(true, false);
                webOutputDir.eachFile { file -> file.setReadable(true, false) } // actually just predictions.tar.gz
            }
            // collect results link information
            if(new File("${web_output_dir}/${predictionInstance.accession_id}/predictions.tar.gz").exists()){
                predictionInstance.results_urls = "<p><b>Prediction archive</b>&nbsp;&nbsp;<a href=\"${web_output_url}${predictionInstance.accession_id}/predictions.tar.gz\">predictions.tar.gz</a><br></p>"
                Utilities.saveDomainWithTransaction(predictionInstance)
            }
            // check whether errors occured by log-file-sizes
            if(new File(projectDir, "aug-pred.sh.e${jobID}").exists()){
                sgeErrSize = new File(projectDir, "aug-pred.sh.e${jobID}").size()
            }else{
                sgeErrSize = 10
                Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "segErrFile was not created. Setting size to default value 10.")
            }
            if(new File(projectDir, "writeResults.err").exists()){
                writeResultsErrSize = new File(projectDir, "writeResults.err").size()
            }else{
                writeResultsErrSize = 10
                Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "writeResultsErr was not created. Setting size to default value 10.")
            }
            if(sgeErrSize==0 && writeResultsErrSize==0){
                mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} finished.\n\n"
                logDate = new Date()
                predictionInstance.message = "${predictionInstance.message}----------------------------------------\n${logDate} - Message:\n----------------------------------------\n\n${mailStr}"
                Utilities.saveDomainWithTransaction(predictionInstance)
                if(predictionInstance.email_adress == null){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Computation was successful. Did not send e-mail to user because no e-mail adress was supplied.")
                }
                if(predictionInstance.email_adress != null){
                    msgStr = "Hello!\n\n${mailStr}You find the results at "
                    msgStr = "${msgStr}${war_url}prediction/show/${predictionInstance.id}.\n\nBest regards,\n\n"
                    msgStr = "${msgStr}the AUGUSTUS web server team"
                    sendMail {
                        to "${predictionInstance.email_adress}"
                        subject "AUGUSTUS prediction job ${predictionInstance.accession_id} is complete"
                        text """${msgStr}${footer}"""
                    }
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
                }
                // unpack with 7z x XA2Y5VMJ.tar.7z
                // tar xvf XA2Y5VMJ.tar
                def packResults = new File("${output_dir}/pack${predictionInstance.accession_id}.sh")
                cmd2Script = "cd ${output_dir}; tar -czvf ${predictionInstance.accession_id}.tar.gz ${predictionInstance.accession_id} &> /dev/null"
                packResults << "${cmd2Script}"
                Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "packResults << \"${cmd2Script}\"")
                //packResults << "cd ${output_dir}; tar cf - ${predictionInstance.accession_id} | 7z a -si ${predictionInstance.accession_id}.tar.7z; rm -r ${predictionInstance.accession_id};"
                cmdStr = "bash ${output_dir}/pack${predictionInstance.accession_id}.sh"
                def cleanUp = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
                cleanUp.waitFor()
                cmdStr = "rm ${output_dir}/pack${predictionInstance.accession_id}.sh &> /dev/null"
                cleanUp = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
                deleteDir()
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "job directory was packed with tar/gz.")
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job completed. Result: ok.")
            }else{
                if(sgeErrSize > 0){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "a SGE error occured!");
                    msgStr = "Hi ${admin_email}!\n\nJob: ${predictionInstance.accession_id}\n"
                    //msgStr = "${msgStr}IP: ${userIP}\n"
                    //msgStr = "${msgStr}E-Mail: ${predictionInstance.email_adress}\n"
                    msgStr = "${msgStr}Link: ${war_url}prediction/show/${predictionInstance.id}\n\n"
                    msgStr = "${msgStr}An SGE error occured. Please check manually what's wrong. "
                    if(predictionInstance.email_adress == null){
                        msgStr = "${msgStr}The user has not been informed."
                        sendMail {
                            to "${admin_email}"
                            subject "Error in AUGUSTUS prediction job ${predictionInstance.accession_id}"
                            text """${msgStr}${footer}"""
                        }
                    }else{
                        msgStr = "${msgStr}The user has been informed."
                        sendMail {
                            to "${admin_email}"
                            subject "Error in AUGUSTUS prediction job ${predictionInstance.accession_id}"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                }else{
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "an error occured during writing results!");
                    msgStr = "Hi ${admin_email}!\n\nJob: ${predictionInstance.accession_id}\n"
                    //msgStr = "${msgStr}IP: ${userIP}\n"
                    //msgStr = "${msgStr}E-Mail: ${predictionInstance.email_adress}\n"
                    msgStr = "${msgStr}Link: ${war_url}prediction/show/${predictionInstance.id}\n\n"
                    msgStr = "${msgStr}An error occured during writing results.. Please check manually what's wrong. "
                    if(predictionInstance.email_adress == null){
                        msgStr = "${msgStr} The user has not been informed."
                        sendMail {
                            to "${admin_email}"
                            subject "Error in AUGUSTUS prediction job ${predictionInstance.accession_id}"
                            text """${msgStr}${footer}"""
                        }
                    }else{
                        msgStr = "${msgStr} The user has been informed."
                        sendMail {
                            to "${admin_email}"
                            subject "Error in AUGUSTUS prediction job ${predictionInstance.accession_id}"
                            text """${msgStr}${footer}"""
                        }
                    }
                    predictionInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(predictionInstance)
                }
                mailStr = "An error occured while running the AUGUSTUS prediction job ${predictionInstance.accession_id}.\n\n"
                logDate = new Date()
                predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}Please contact augustus-web@uni-greifswald.de if you want to find out what went wrong.\n\n"
                Utilities.saveDomainWithTransaction(predictionInstance)
                if(predictionInstance.email_adress == null){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The job is in an error state. Cound not send e-mail to anonymous user because no email adress was supplied.")
                }else{
                    msgStr = "Hello!\n\n${mailStr}The administrator of the AUGUSTUS web server has been informed and"
                    msgStr = "${msgStr} will get back to you as soon as the problem is solved.\n\nBest regards,\n\n"
                    msgStr = "${msgStr}the AUGUSTUS web server team"
                    sendMail {
                        to "${predictionInstance.email_adress}"
                        subject "An error occured while executing AUGUSTUS prediction job ${predictionInstance.accession_id}"
                        text """${msgStr}${footer}"""
                    }
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
                }
            }
        }
        //------------ END BACKGROUND PROCESS ----------------------------------
    }// end of commit
} // end of Controller
