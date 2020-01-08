package webaugustus

/**
 * The class TrainingController controls everything that is related to preparing a job for training AUGUSTUS through the webserver:
 *    - it handles the file upload
 *    - format check
 *    - start a TrainingService thread - to handle:
 *      - the file upload by wget
 *      - format check
 *      - job submission and status checks on Compute Cluster
 *      - rendering of results/job status page
 *      - sending E-Mails concerning the job status (downloaded files, errors, finished) 
 */
class TrainingController {
    
    static allowedMethods = [show: "GET", create: "GET", commit: "POST"] // only POST method invokes commit()
    
    def trainingService // inject the bean
    // human verification:
    def simpleCaptchaService
    
    // max button filesize
    def long maxButtonFileSize = 104857600 // 100 MB = 104857600 bytes getFile etc. gives size in byte
    // max ftp/http filesize
    def long maxFileSizeByWget = 1073741824 // 1 GB = 1073741824 bytes, curl gives size in bytes
    
    
    def show() {
        def instance = Training.get(params.id)
        if (instance == null) {
            render(view: '/jobnotfound')
            return
        }
        respond instance
    }
    
    def create() {
        // check whether the server is busy
        
        def logFile = trainingService.getLogFile()
        // logging verbosity-level
        def logVerb = trainingService.getLogLevel() // 1 only basic log messages, 2 all issued commands, 3 also script content
            
        int jobQueueLength = trainingService.getJobQueueLength()
        def maxJobQueueLength = trainingService.getMaxJobQueueLength()

        if(jobQueueLength >= maxJobQueueLength){
            def logMessage = "Somebody tried to invoke the Training webserver but the job queue was was ${jobQueueLength} longer "
            logMessage += "than ${maxJobQueueLength} and the user was informed that submission is currently not possible"
            Utilities.log(logFile, 1, logVerb, "Training creation", logMessage)

            def m1 = "You tried to access the AUGUSTUS training job submission page."
            def m2 = "Training parameters for gene training can be a process that takes a lot of computation time. "
            m2 += "We estimate that one training process requires approximately up to ten days."
            render(view: "/busy", model: [message1: m1, message2: m2])

            return
        }
        
        Training trainingInstance = new Training(params)
        
        int count = 0
        while (count++ < 100) {
            // try 100 time to get a new trainingInstance with an accession_id not yet used in database
            if (Training.withTransaction { 
                    Training.findAll({ accession_id == trainingInstance.accession_id }) 
                }.isEmpty()) {
                
                break
            }
            Utilities.log(logFile, 1, logVerb, "Training creation", "create a new trainingInstance as currently selected accession_id ${trainingInstance.accession_id} is already used")
            trainingInstance = new Training(params)
        }        
        
        respond trainingInstance
        
        respond new Training(params)
    }

    // fill in sample data
    def fillSample() {
        redirect(action:'create', controller: 'training', params:[genome_ftp_link:"http://bioinf.uni-greifswald.de/webaugustus/examples/chr1to6.fa", protein_ftp_link:"http://bioinf.uni-greifswald.de/webaugustus/examples/rattusProteinsChr1to6.fa", project_name:"Mus_musculus"])
    }

    // the method commit is started if the "Submit Job" button on the website is hit. It is the main method of Training Controller and contains a Thread method that will continue running as a background process after the user is redirected to the job status page.
    def commit() {
        
        def logFile = trainingService.getLogFile()
        // logging verbosity-level
        def verb = trainingService.getLogLevel() // 1 only basic log messages, 2 all issued commands, 3 also script content
        
        def output_dir = trainingService.getOutputDir()
        def web_output_dir = trainingService.getWebOutputDir()
        def http_base_url = trainingService.getHttpBaseURL()
        
        def AUGUSTUS_CONFIG_PATH = TrainingService.getAugustusConfigPath()
        def AUGUSTUS_SCRIPTS_PATH = TrainingService.getAugustusScriptPath()
        
        
        def trainingInstance = new Training(params)
        if(!(trainingInstance.id == null)){
            String senderAdress = TrainingService.getWebaugustusEmailAddress()
            flash.error = "Internal error 5. Please contact ${senderAdress} if the problem persists!"
            redirect(action:'create', controller: 'training')
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
        def uploadedProteinFile = request.getFile('ProteinFile')
        def uploadedEstFile = request.getFile('EstFile')
        def uploadedStructFile = request.getFile('StructFile')
        trainingInstance.has_genome_file = !uploadedGenomeFile.empty
        if (trainingInstance.has_genome_file) {
            trainingInstance.genome_file = uploadedGenomeFile.originalFilename
        }
        trainingInstance.has_protein_file = !uploadedProteinFile.empty
        if (trainingInstance.has_protein_file) {
            trainingInstance.protein_file = uploadedProteinFile.originalFilename
        }
        trainingInstance.has_est_file = !uploadedEstFile.empty
        if (trainingInstance.has_est_file) {
            trainingInstance.est_file = uploadedEstFile.originalFilename
        }
        trainingInstance.has_struct_file = !uploadedStructFile.empty
        if (trainingInstance.has_struct_file) {
            trainingInstance.struct_file = uploadedStructFile.originalFilename
        }
        trainingInstance.results_urls = ""
        trainingInstance.message = ""
        trainingInstance.dateCreated = new Date();
        
        // info string for confirmation E-Mail
        String confirmationString = "Training job ID: ${trainingInstance.accession_id}\n"
        confirmationString += "Species name: ${trainingInstance.project_name}\n"
        trainingInstance.job_id = 0
        trainingInstance.job_error = 0
        
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "AUGUSTUS training webserver starting on ${trainingInstance.dateCreated}")
        
        // redirect function
        def cleanRedirect = {
            if(trainingInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} by anonymous user is aborted!")
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} is aborted!")
            }
            flash.message = "Info: Please check all fields marked in blue for completeness before starting the training job!"
            // flags for redirect to submission form, display warning in appropriate places
            trainingInstance.warn = true
            
            render(view:'create', model:[training:trainingInstance])

        }
        // directory delete function
        def String dirName = "${output_dir}/${trainingInstance.accession_id}"
        def projectDir = new File(dirName)
        def deleteDir = {
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Project directory is deleted")
            def cmd = ["rm -r ${dirName} &> /dev/null"]
            Utilities.execute(logFile, 2, trainingInstance.accession_id, "removeProjectDir", cmd)
        }
        
        //verify that the submitter is a person
        boolean captchaValid = simpleCaptchaService.validateCaptcha(params.captcha)
        if(captchaValid == false){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The user is probably not a human person.")
            flash.error = "The verification string at the bottom of the page was not entered correctly!"
            cleanRedirect()
            return
        }
        
        trainingInstance.validate()

        if (trainingInstance.hasErrors()) {
            cleanRedirect()
            return
        }

        trainingInstance.project_name = trainingInstance.project_name.trim()
        // check that species name does not contain spaces
        if(trainingInstance.project_name =~ /\s/){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The species name contained whitespaces.")
            flash.error = "Species name ${trainingInstance.project_name} contains white spaces."
            cleanRedirect()
            return
        }

        // upload of genome file
        if(!uploadedGenomeFile.empty){
            // check file size
            long preUploadSize = uploadedGenomeFile.getSize()
            projectDir.mkdirs()

            if(preUploadSize > maxButtonFileSize){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The selected genome file was bigger than ${maxButtonFileSize}.")
                deleteDir()
                flash.error = "Genome file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }

            uploadedGenomeFile.transferTo( new File(projectDir, "genome.fa"))
            confirmationString = "${confirmationString}Genome file: ${trainingInstance.genome_file}\n"

            if (Utilities.isUnSupportedCompressMode(uploadedGenomeFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file is compressed by a not supported algorithm ${uploadedGenomeFile.originalFilename}")
                deleteDir()
                flash.error = "The genome file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            if (Utilities.isSupportedCompressMode(uploadedGenomeFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Genome file ${uploadedGenomeFile.originalFilename} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/genome.fa", "gz", logFile, verb, trainingInstance.accession_id)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gzipped Genome file is corrupt")
                    deleteDir()
                    flash.error = "The gzipped Genome file is corrupt."
                    cleanRedirect()
                    return
                }
            }
            
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "uploaded genome file ${uploadedGenomeFile.originalFilename} was renamed to genome.fa and moved to ${dirName}")
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "trying: grep -c '>' ${dirName}/genome.fa > ${dirName}/genome.nSeq")
            // check number of scaffolds
            def cmd = ["grep -c '>' ${dirName}/genome.fa"]
            Long nSeqNumber = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "nSeqFile", cmd)
            int maxNSeqs = TrainingService.getMaxNSeqs()
            if(nSeqNumber == null || nSeqNumber > maxNSeqs){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.")
                deleteDir()
                flash.error = "Genome file contains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds), which is the maximal number of scaffolds that we permit for submission with WebAUGUSTUS. Please remove all short scaffolds from your genome file."
                cleanRedirect()
                return
            }
            // check for fasta format
            Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(new File(projectDir, "genome.fa"))
            
            if (Utilities.FastaStatus.CONTAINS_METACHARACTERS.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                deleteDir()
                flash.error = "The genome file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file was not fasta.")
                deleteDir()
                flash.error = "Genome file ${uploadedGenomeFile.originalFilename} is not in DNA fasta format."
                cleanRedirect()
                return
            }
            cmd = ["cksum ${dirName}/genome.fa"]
            trainingInstance.genome_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.genome_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ") // just in case the file was gzipped
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome.fa is ${trainingInstance.genome_size} big and has a cksum of ${trainingInstance.genome_cksum}.")
        } // end of genome file upload

        // retrieve beginning of genome file for format check
        if(!(trainingInstance.genome_ftp_link == null)){
            if (Utilities.isUnSupportedCompressMode(trainingInstance.genome_ftp_link)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file is compressed by a not supported algorithm ${trainingInstance.genome_ftp_link}")
                deleteDir()
                flash.error = "The genome file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome web-link is ${trainingInstance.genome_ftp_link}")
            projectDir.mkdirs()

            // check whether URL exists
            def cmd = ['curl', '-IL', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', trainingInstance.genome_ftp_link]
            Integer error_code = Utilities.executeForInteger(logFile, 3, trainingInstance.accession_id, "urlExistsScript", cmd)
            if(error_code == null || (error_code != 200 && error_code != 302)){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome URL is not accessible. Response code: ${error_code}.")
                deleteDir()
                flash.error = "Cannot retrieve genome file from HTTP/FTP link ${trainingInstance.genome_ftp_link}."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome URL is accessible. Response code: ${error_code}.")
            }

            // check whether the genome file is small enough for upload
            cmd = ["wget --spider ${trainingInstance.genome_ftp_link} 2>&1"]
            def pattern = ".*Length: (\\d*).*"
            Long genome_size = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "spiderScript", cmd, pattern)
            if (genome_size == null) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Invalid genome URL.")
                flash.error = "Cannot retrieve genome file from HTTP/FTP link ${trainingInstance.genome_ftp_link}."
            }
            else if (genome_size > maxFileSizeByWget) { // 1 GB
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Genome file size exceeds permitted ${maxFileSizeByWget} bytes by ${genome_size} bytes.")
                flash.error = "Genome file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
            }
            if (genome_size == null || genome_size > maxFileSizeByWget) {
                deleteDir()
                cleanRedirect()
                return
            }

            confirmationString = "${confirmationString}Genome file: ${trainingInstance.genome_ftp_link}\n"
            // checking web file for DNA fasta format:
            if ( !Utilities.isSupportedCompressMode(trainingInstance.genome_ftp_link) ) {
                URL url = new URL("${trainingInstance.genome_ftp_link}")
                Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(url)
                
                if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The first 20 lines in genome file are not fasta.")
                    deleteDir()
                    flash.error = "Genome file ${trainingInstance.genome_ftp_link} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The linked genome file ${trainingInstance.genome_ftp_link} is gzipped. Format will be checked later after extraction.")
            }
        }

        // upload of est file
        if(!uploadedEstFile.empty){
            // check file size
            def long preUploadSize = uploadedEstFile.getSize()
            if(preUploadSize > maxButtonFileSize){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The selected cDNA file was bigger than ${maxButtonFileSize}.")
                deleteDir()
                flash.error = "cDNA file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }
            projectDir.mkdirs()
            uploadedEstFile.transferTo( new File(projectDir, "est.fa"))
            confirmationString = "${confirmationString}cDNA file: ${trainingInstance.est_file}\n"

            if (Utilities.isUnSupportedCompressMode(uploadedEstFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The EST file is compressed by a not supported algorithm ${uploadedEstFile.originalFilename}")
                deleteDir()
                flash.error = "The cDNA file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            if (Utilities.isSupportedCompressMode(uploadedEstFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST file ${uploadedEstFile.originalFilename} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/est.fa", "gz", logFile, verb, trainingInstance.accession_id)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gzipped EST file is corrupt")
                    deleteDir()
                    flash.error = "The gzipped cDNA file is corrupt."
                    cleanRedirect()
                    return
                }
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Uploaded EST file ${uploadedEstFile.originalFilename} was renamed to est.fa and moved to ${dirName}")
            // check fasta format
            Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(new File(projectDir, "est.fa"))
            
            if (Utilities.FastaStatus.CONTAINS_METACHARACTERS.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).");
                deleteDir()
                flash.error = "The cDNA file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file was not fasta.")
                deleteDir()
                flash.error = "cDNA file ${uploadedEstFile.originalFilename} is not in DNA fasta format."
                cleanRedirect()
                return
            }

            def cmd = ["cksum ${dirName}/est.fa"]
            trainingInstance.est_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.est_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ") // just in case the file was gzipped
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "est.fa is ${trainingInstance.est_size} big and has a cksum of ${trainingInstance.est_cksum}.")
        }

        // retrieve beginning of est file for format check
        if(!(trainingInstance.est_ftp_link == null)){
            if (Utilities.isUnSupportedCompressMode(trainingInstance.est_ftp_link)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The EST file is compressed by a not supported algorithm ${trainingInstance.est_ftp_link}")
                deleteDir()
                flash.error = "The cDNA file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "est web-link is ${trainingInstance.est_ftp_link}")
            projectDir.mkdirs()
            confirmationString = "${confirmationString}cDNA file: ${trainingInstance.est_ftp_link}\n"

            // check whether URL exists
            def cmd = ['curl', '-IL', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', trainingInstance.est_ftp_link]
            Integer error_code = Utilities.executeForInteger(logFile, 3, trainingInstance.accession_id, "urlExistsScript", cmd)
            if(error_code == null || (error_code != 200 && error_code != 302)){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The EST URL is not accessible. Response code: ${error_code}.")
                deleteDir()
                flash.error = "Cannot retrieve cDNA file from HTTP/FTP link ${trainingInstance.est_ftp_link}."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The EST URL is accessible. Response code: ${error_code}.")
            }

            // check whether the genome file is small enough for upload
            cmd = ["wget --spider ${trainingInstance.est_ftp_link} 2>&1"]
            def pattern = ".*Length: (\\d*).*"
            Long est_size = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "spiderScript", cmd, pattern)
            if (est_size == null) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Invalid EST URL.")
                flash.error = "Cannot retrieve cDNA file from HTTP/FTP link ${trainingInstance.est_ftp_link}."
            }
            else if (est_size > maxFileSizeByWget) { // 1 GB
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST file size exceeds permitted ${maxFileSizeByWget} bytes by ${est_size} bytes.")
                flash.error = "cDNA file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
            }
            if (est_size == null || est_size > maxFileSizeByWget) {
                deleteDir()
                cleanRedirect()
                return
            }

            // checking web file for DNA fasta format
            if ( !Utilities.isSupportedCompressMode(trainingInstance.est_ftp_link) ) {
                URL url = new URL("${trainingInstance.est_ftp_link}")
                Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(url)
                
                if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file was not fasta.")
                    deleteDir()
                    flash.error = "cDNA file ${trainingInstance.est_ftp_link} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The linked EST file ${trainingInstance.est_ftp_link} is gzipped. Format will be checked later after extraction.")
            }
        }

        // upload of structure file
        if(!uploadedStructFile.empty){
            // check file size
            def long preUploadSize = uploadedStructFile.getSize()
            if(preUploadSize <= maxButtonFileSize * 2){
                projectDir.mkdirs()
                uploadedStructFile.transferTo( new File(projectDir, "training-gene-structure.gff"))
                confirmationString = "${confirmationString}Training gene structure file: ${trainingInstance.struct_file}\n"
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Uploaded training gene structure file ${uploadedStructFile.originalFilename} was renamed to training-gene-structure.gff and moved to ${dirName}")
                def gffColErrorFlag = 0
                def gffNameErrorFlag = 0
                def structureGbkFlag = 0
                if(!uploadedGenomeFile.empty){
                    // gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
                    boolean metacharacterFlag = false
                    Utilities.log(logFile, 2, verb, trainingInstance.accession_id, "Checking training-gene-structure.gff file format")
                    new File(projectDir, "training-gene-structure.gff").eachLine{line ->
                        if (metacharacterFlag) {
                            return
                        }
                        // check whether weird metacharacters are included
                        if(line =~ /\*/ || line =~ /\?/){
                            metacharacterFlag = true
                        }
                        else if (line.startsWith("LOCUS")) {
                            structureGbkFlag = 1
                        }                        
                    }
                    if (metacharacterFlag) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gene structure file contains metacharacters (e.g. * or ?).");
                        deleteDir()
                        flash.error = "Gene Structure file contains metacharacters (*, ?, ...). This is not allowed."
                        cleanRedirect()
                        return
                    }
                    if(structureGbkFlag == 0){
                        def checkGffScript = new File(projectDir, "checkGff.sh")
                        def gffChkOutFile = "${dirName}/gffCheck.out"
                        def gffChkColsFile = "${dirName}/gffCols.out"
                        checkGffScript << "/usr/bin/perl ${AUGUSTUS_SCRIPTS_PATH}/findGffNamesInFasta.pl --gff=${dirName}/training-gene-structure.gff --genome=${dirName}/genome.fa --out=${gffChkColsFile} &> ${gffChkOutFile}"
                        def cmdStr = "bash ${dirName}/checkGff.sh"
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, cmdStr)
                        def ckStr = "${cmdStr}".execute()
                        ckStr.waitFor()
                        def ckContent = new File("${gffChkOutFile}").text
                        def stContent = new Scanner(ckContent)
                        def long gffErrorStatus
                        gffErrorStatus = stContent.nextLong();
                        ckContent = new File("${gffChkColsFile}").text
                        stContent = new Scanner(ckContent)
                        def long gffColStatus
                        gffColStatus = stContent.nextLong()
                        if(gffErrorStatus == 1){
                            gffNameErrorFlag = 1
                        }
                        if(gffColStatus == 1){
                            gffColErrorFlag = 1
                        }
                        cmdStr = "rm ${dirName}/checkGff.sh ${gffChkOutFile} ${gffChkColsFile}"
                        def delProc = "${cmdStr}".execute()
                        delProc.waitFor()
                    }
                    if(gffColErrorFlag == 1 && structureGbkFlag == 0){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Training gene structure file does not always contain 9 columns.")
                        flash.error = "Training gene structure file  ${trainingInstance.struct_file} is not in a compatible gff format (has not 9 columns). Please make sure the gff-format complies with the instructions in our 'Help' section!"
                    }
                    if(gffNameErrorFlag == 1 && structureGbkFlag == 0){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Training gene structure file contains entries that do not comply with genome sequence names.")
                        flash.error = "Entries in the training gene structure file  ${trainingInstance.struct_file} do not match the sequence names of the genome file. Please make sure the gff-format complies with the instructions in our 'Help' section!"
                    }
                    if((gffColErrorFlag == 1 || gffNameErrorFlag == 1) && structureGbkFlag == 0){
                        deleteDir()
                        cleanRedirect()
                        return
                    }
                }

            }else{
                def allowedStructSize = maxButtonFileSize * 2
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The selected training gene structure file was bigger than ${allowedStructSize}.")
                deleteDir()
                flash.error = "Training gene structure file is bigger than ${allowedStructSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }

            def cmd = ["cksum ${dirName}/training-gene-structure.gff"]
            trainingInstance.struct_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "structCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.struct_size = uploadedStructFile.size
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "training-gene-structure.gff is ${trainingInstance.struct_size} big and has a cksum of ${trainingInstance.struct_cksum}.")
        }


        // upload of protein file
        if(!uploadedProteinFile.empty){
            // check file size
            def long preUploadSize = uploadedProteinFile.getSize()
            if(preUploadSize > maxButtonFileSize){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The selected protein file was bigger than ${maxButtonFileSize}.")
                deleteDir()
                flash.error = "Protein file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                cleanRedirect()
                return
            }

            projectDir.mkdirs()
            uploadedProteinFile.transferTo( new File(projectDir, "protein.fa"))
            confirmationString = "${confirmationString}Protein file: ${trainingInstance.protein_file}\n"

            if (Utilities.isUnSupportedCompressMode(uploadedProteinFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The Protein file is compressed by a not supported algorithm ${uploadedProteinFile.originalFilename}")
                deleteDir()
                flash.error = "The Protein file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            if (Utilities.isSupportedCompressMode(uploadedProteinFile.originalFilename)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Protein file ${uploadedProteinFile.originalFilename} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/protein.fa", "gz", logFile, verb, trainingInstance.accession_id)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gzipped Protein file is corrupt")
                    deleteDir()
                    flash.error = "The gzipped Protein file is corrupt."
                    cleanRedirect()
                    return
                }
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Uploaded protein file ${uploadedProteinFile.originalFilename} was renamed to protein.fa and moved to ${dirName}")
            // check fasta format
            Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(new File(projectDir, "protein.fa"), true)
            
            if (Utilities.FastaStatus.CONTAINS_METACHARACTERS.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file contains metacharacters (e.g. * or ?).");
                deleteDir()
                flash.error = "The protein file contains metacharacters (*, ?, ...). This is not allowed."
                cleanRedirect()
                return
            }
            if (Utilities.FastaStatus.NO_PROTEIN_FASTA.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not recognized as protein file (probably DNA sequence).")
                deleteDir()
                flash.error = "Your protein file was not recognized as a protein file. It may be DNA file. The training job was not started. Please contact augustus@uni-greifswald.de if you are completely sure this file is a protein fasta file."
                cleanRedirect()
                return
            }
            if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not protein fasta.")
                deleteDir()
                flash.error = "Protein file ${uploadedProteinFile.originalFilename} is not in protein fasta format."
                cleanRedirect()
                return
            }
            
            def cmd = ["cksum ${dirName}/protein.fa"]
            trainingInstance.protein_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.protein_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "\\d* (\\d*) ") // just in case the file was gzipped
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${trainingInstance.protein_cksum}.")
        }

        // retrieve beginning of protein file for format check
        if(!(trainingInstance.protein_ftp_link == null)){
            if (Utilities.isUnSupportedCompressMode(trainingInstance.protein_ftp_link)) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file is compressed by a not supported algorithm ${trainingInstance.protein_ftp_link}")
                deleteDir()
                flash.error = "The protein file is compressed by a not supported algorithm. Please use gzip to compress your file."
                cleanRedirect()
                return
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein web-link is ${trainingInstance.protein_ftp_link}")
            projectDir.mkdirs()
            confirmationString = "${confirmationString}Protein file: ${trainingInstance.protein_ftp_link}\n"

             // check whether URL exists
            def cmd = ['curl', '-IL', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', trainingInstance.protein_ftp_link]
            Integer error_code = Utilities.executeForInteger(logFile, 3, trainingInstance.accession_id, "urlExistsScript", cmd)
            if(error_code == null || (error_code != 200 && error_code != 302)){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein URL is not accessible. Response code: ${error_code}.")
                deleteDir()
                flash.error = "Cannot retrieve protein file from HTTP/FTP link ${trainingInstance.protein_ftp_link}."
                cleanRedirect()
                return
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein URL is accessible. Response code: ${error_code}.")
            }

            // check whether the protein file is small enough for upload
            cmd = ["wget --spider ${trainingInstance.protein_ftp_link} 2>&1"]
            def pattern = ".*Length: (\\d*).*"
            Long protein_size = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "spiderScript", cmd, pattern)
            if (protein_size == null) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Invalid protein URL.")
                flash.error = "Cannot retrieve protein file from HTTP/FTP link ${trainingInstance.protein_ftp_link}."
            }
            else if (protein_size > maxFileSizeByWget) { // 1 GB
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Protein file size exceeds permitted ${maxFileSizeByWget} bytes by ${protein_size} bytes.")
                flash.error = "protein file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
            }
            if (protein_size == null || protein_size > maxFileSizeByWget) {
                deleteDir()
                cleanRedirect()
                return
            }

            if ( !Utilities.isSupportedCompressMode(trainingInstance.protein_ftp_link) ) {
                // checking web file for protein fasta format:
                def URL url = new URL("${trainingInstance.protein_ftp_link}");
                Utilities.FastaStatus fastaStatus = Utilities.checkFastaFormat(url, true)
                
                if (Utilities.FastaStatus.NO_PROTEIN_FASTA.equals(fastaStatus)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not recognized as protein file (probably DNA sequence).")
                    deleteDir()
                    flash.error = "Protein file ${trainingInstance.protein_ftp_link} does not contain protein sequences."
                    cleanRedirect()
                    return
                }
                if (!Utilities.FastaStatus.VALID_FASTA.equals(fastaStatus)) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not protein fasta.")
                    deleteDir()
                    flash.error = "Protein file ${trainingInstance.protein_ftp_link} is not in fasta format."
                    cleanRedirect()
                    return
                }
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The linked Protein file ${trainingInstance.protein_ftp_link} is gzipped. Format will be checked later after extraction.")
            }

        }
        // send confirmation email and redirect
        trainingInstance.validate();
        if(!trainingInstance.hasErrors() && Utilities.saveDomainWithTransaction(trainingInstance)){
            Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "committed trainingInstance.id ${trainingInstance.id}")

            // generate empty results page
            def cmd = ["${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${trainingInstance.dateCreated}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 0 &> /dev/null"]
            Utilities.execute(logFile, verb, trainingInstance.accession_id, "emptyPageScript", cmd)
            trainingInstance.warn = false
            trainingInstance.job_status = 0
            String mailStr = "Details of your job:\n\n${confirmationString}\n"
            trainingInstance.message = "----------------------------------------\n${trainingInstance.dateCreated} - Message:\n"
            trainingInstance.message = "${trainingInstance.message}----------------------------------------\n\n${mailStr}"
            Utilities.saveDomainWithTransaction(trainingInstance)

            if(trainingInstance.email_adress != null){
                String msgStr = "Thank you for submitting a job to train AUGUSTUS parameters for species ${trainingInstance.project_name}.\n\n"
                msgStr += "${mailStr}The status/results page of your job is ${http_base_url}show/${trainingInstance.id}.\n\n"
                msgStr += "You will be notified by e-mail after computations of your job have finished.\n\n"
                trainingService.sendMailToUser(trainingInstance, "Your AUGUSTUS training job ${trainingInstance.accession_id}", msgStr)
                
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Confirmation e-mail sent.")
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Did not send confirmation e-mail because user stays anonymous, but everything is ok.")
            }
            trainingService.startWorkerThread()
            redirect(action:'show', controller: 'training', id: trainingInstance.id)
        } else {
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "An error occurred in the trainingInstance (e.g. E-Mail missing, see domain restrictions).")
            deleteDir()
            if(trainingInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} by anonymous user is aborted!")
            }else{
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} is aborted!")
            }
            render(view:'create', model:[training:trainingInstance])
        }
    }
}
