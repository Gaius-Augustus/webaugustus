package webaugustus

/**
 * The class TrainingController controls everything that is related to submitting a job for training AUGUSTUS through the webserver:
 *    - it handles the file upload (or wget)
 *    - format check
 *    - SGE job submission and status checks
 *    - rendering of results/job status page
 *    - sending E-Mails concerning the job status (submission, errors, finished)
 */
class TrainingController {
    
    // need to adjust the output dir to whatever working dir! This is where uploaded files and results will be saved.
    def output_dir = "/data/www/webaugustus/webdata/augtrain" // "/data/www/augtrain/webdata" // should be something in home of webserver user and augustus frontend user.
    //def output_dir = "/data/www/test"
    // this log File contains the "process log", what was happening with which job when.
    def logFile = new File("${output_dir}/train.log")
    // web-output, root directory to the results that are shown to end users
    def web_output_dir = "/data/www/webaugustus/training-results" // must be writable to webserver application 
    def web_output_url = "http://bioinf.uni-greifswald.de/training-results/"
    def war_url = "http://bioinf.uni-greifswald.de/webaugustus/"
    def footer = "\n\n------------------------------------------------------------------------------------\nThis is an automatically generated message.\n\nhttp://bioinf.uni-greifswald.de/webaugustus" // footer of e-mail
    // AUGUSTUS_CONFIG_PATH
    def AUGUSTUS_CONFIG_PATH = "/usr/share/augustus/config"
    def AUGUSTUS_SCRIPTS_PATH = "/usr/share/augustus/scripts"
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
    def msgStr
    def logDate
    def maxNSeqs = 250000 // maximal number of scaffolds allowed in genome file
    // human verification:
    def simpleCaptchaService

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
        def processForLog = "SGE         "
        def cmd = ['qstat -u "*" | grep qw | wc -l']
        def qstatStatusNumber = Utilities.executeForLong(logFile, verb, processForLog, "qstatScript", cmd)

        if(qstatStatusNumber > sgeLen){
            Utilities.log(logFile, 1, verb, processForLog, "Somebody tried to invoke the Training webserver but the SGE queue was longer than ${sgeLen} and the user was informed that submission is currently not possible")

            def m1 = "You tried to access the AUGUSTUS training job submission page."
            def m2 = "Training parameters for gene training can be a process that takes a lot of computation time. "
            m2 += "We estimate that one training process requires approximately up to ten days."
            render(view: "/busy", model: [message1: m1, message2: m2])

            return
        }
        
        respond new Training(params)
    }

    // fill in sample data
    def fillSample() {
        redirect(action:'create', controller: 'training', params:[genome_ftp_link:"http://bioinf.uni-greifswald.de/trainaugustus/examples/chr1to6.fa", protein_ftp_link:"http://bioinf.uni-greifswald.de/trainaugustus/examples/rattusProteinsChr1to6.fa", project_name:"Mus_musculus"])
    }

    // the method commit is started if the "Submit Job" button on the website is hit. It is the main method of Training Controller and contains a Thread method that will continue running as a background process after the user is redirected to the job status page.
    def commit() {
        def trainingInstance = new Training(params)
        if(!(trainingInstance.id == null)){
            flash.error = "Internal error 2. Please contact augustus-web@uni-greifswald.de if the problem persists!"
            redirect(action:'create', controller: 'training')
            return
        }else{
            // retrieve parameters of form for early save()
            def uploadedGenomeFile = request.getFile('GenomeFile')
            def uploadedProteinFile = request.getFile('ProteinFile')
            def uploadedEstFile = request.getFile('EstFile')
            def uploadedStructFile = request.getFile('StructFile')
            if(!(uploadedGenomeFile.empty)){
                trainingInstance.genome_file = uploadedGenomeFile.originalFilename
            }
            if(!(uploadedProteinFile.empty)){
                trainingInstance.protein_file = uploadedProteinFile.originalFilename
            }
            if(!(uploadedEstFile.empty)){
                trainingInstance.est_file = uploadedEstFile.originalFilename
            }
            if(!(uploadedStructFile.empty)){
                trainingInstance.struct_file = uploadedStructFile.originalFilename
            }
            trainingInstance.results_urls = ""
            trainingInstance.message = ""
            trainingInstance.dateCreated = new Date();
            trainingInstance.validate()

            if (trainingInstance.hasErrors()) {
                render(view:'create', model:[training:trainingInstance])
                return
            }
            
            // info string for confirmation E-Mail
            def confirmationString
            confirmationString = "Training job ID: ${trainingInstance.accession_id}\n"
            confirmationString = "${confirmationString}Species name: ${trainingInstance.project_name}\n"
            def mailStr
            trainingInstance.job_id = 0
            trainingInstance.job_error = 0
            // define flags for file format check, file removal in case of failure
            def genomeFastaFlag = 0
            def estFastaFlag = 0
            def estExistsFlag = 0
            def structureGffFlag = 0
            def structureGbkFlag = 0
            def structureExistsFlag = 0
            def proteinFastaFlag = 0
            def proteinExistsFlag = 0
            // delProc is needed at many places
            def delProc
            def st
			def content
            def int error_code
            def urlExistsScript
            def autoAugErrSize = 10 // default: error
            def sgeErrSize = 10 // default: error
            def writeResultsErrSize = 10 // default: error
            // get date
            def today = new Date()
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "AUGUSTUS training webserver starting on ${today}")
            // get IP-address
            //String userIP = request.remoteAddr
            //Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "user IP: ${userIP}")

            // flags for redirect to submission form, display warning in appropriate places
            trainingInstance.warn = true
            // parameters for redirecting
            def redirParams = [:]
            redirParams["warn"] = "${trainingInstance.warn}"
            if(trainingInstance.email_adress != null){
                redirParams["email_adress"] = "${trainingInstance.email_adress}"
            }
            if(trainingInstance.project_name != null){
                redirParams["project_name"] = "${trainingInstance.project_name}"
            }
            if(trainingInstance.genome_ftp_link != null){
                redirParams["genome_ftp_link"] = "${trainingInstance.genome_ftp_link}"
            }
            if(trainingInstance.est_ftp_link != null){
                redirParams["est_ftp_link"] = "${trainingInstance.est_ftp_link}"
            }
            if(trainingInstance.protein_ftp_link != null){
                redirParams["protein_ftp_link"] = "${trainingInstance.protein_ftp_link}"
            }
            if(trainingInstance.genome_file != null){
                redirParams["has_genome_file"] = "${trainingInstance.warn}"
            }
            if(trainingInstance.est_file != null){
                redirParams["has_est_file"] = "${trainingInstance.warn}"
            }
            if(trainingInstance.protein_file != null){
                redirParams["has_protein_file"] = "${trainingInstance.warn}"
            }
            if(trainingInstance.struct_file != null){
                redirParams["has_struct_file"] = "${trainingInstance.warn}"
            }
            if(trainingInstance.agree_email == true){
                redirParams["agree_email"] = true
            }
            if(trainingInstance.agree_nonhuman == true){
                redirParams["agree_nonhuman"] = true
            }


            // redirect function
            def cleanRedirect = {
                logDate = new Date()
                if(trainingInstance.email_adress == null){
                    //logFile << "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by anonymous user with IP ${userIP} is aborted!\n"
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} by anonymous user is aborted!")
                }else{
                    // logFile << "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} is aborted!")
                }
                flash.message = "Info: Please check all fields marked in blue for completeness before starting the training job!"
                redirect(action:'create', controller: 'training', params:redirParams)

            }
            // directory delete function
            def String dirName = "${output_dir}/${trainingInstance.accession_id}"
            def projectDir = new File(dirName)
            def deleteDir = {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Project directory is deleted")
                def cmd = ["rm -r ${dirName} &> /dev/null"]
                Utilities.execute(logFile, 2, trainingInstance.accession_id, "removeProjectDir", cmd)
            }
            // log abort function
            def logAbort = {
                logDate = new Date()
                if(trainingInstance.email_adress == null){
                    //logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by anonymous user with IP ${userIP} is aborted!\n"
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} by anonymous user is aborted!")
                }else{
                    //logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"

                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} is aborted!")
                }
            }

            //verify that the submitter is a person
            boolean captchaValid = simpleCaptchaService.validateCaptcha(params.captcha)
            if(captchaValid == false){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The user is probably not a human person.")
                flash.error = "The verification string at the bottom of the page was not entered correctly!"
                cleanRedirect()
                return
            }

            // check that species name does not contain spaces
            if(trainingInstance.project_name =~ /\s/){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The species name contained whitespaces.")
                flash.error = "Species name  ${trainingInstance.project_name} contains white spaces."
                cleanRedirect()
                return
            }

            // upload of genome file
            if(!uploadedGenomeFile.empty){
                // check file size
                def long preUploadSize = uploadedGenomeFile.getSize()
                projectDir.mkdirs()
                
                if(preUploadSize > maxButtonFileSize){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The selected genome file was bigger than ${maxButtonFileSize}.")
                    flash.error = "Genome file is bigger than ${maxButtonFileSize/1024/1024} MB, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
                    cleanRedirect()
                    return
                }
                
                uploadedGenomeFile.transferTo( new File(projectDir, "genome.fa"))
                confirmationString = "${confirmationString}Genome file: ${trainingInstance.genome_file}\n"
                    
                if("${uploadedGenomeFile.originalFilename}" =~ /\.gz/){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Genome file is gzipped.")
                    def cmd = ["mv ${dirName}/genome.fa ${dirName}/genome.fa.gz; gunzip ${dirName}/genome.fa.gz"]
                    Utilities.execute(logFile, verb, trainingInstance.accession_id, "gunzipGenomeScript", cmd)

                    if (!new File(projectDir, "genome.fa").exists()) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gzipped Genome file is corrupt")
                        flash.error = "The gzipped Genome file is corrupt."
                        cleanRedirect()
                        return
                    }
                }

                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "uploaded genome file ${uploadedGenomeFile.originalFilename} was renamed to genome.fa and moved to ${dirName}")
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "trying: grep -c '>' ${dirName}/genome.fa > ${dirName}/genome.nSeq")
                // check number of scaffolds
                def cmd = ["grep -c '>' ${dirName}/genome.fa"]
                def long nSeqNumber = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "nSeqFile", cmd)
                if(nSeqNumber > maxNSeqs){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.")
                    deleteDir()
                    flash.error = "Genome file contains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds), which is the maximal number of scaffolds that we permit for submission with WebAUGUSTUS. Please remove all short scaffolds from your genome file."
                    cleanRedirect()
                    return
                }
                // check for fasta format
                def metacharacterFlag = 0
                new File(projectDir, "genome.fa").eachLine{line ->
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = 1
                    }else{
                        if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){ genomeFastaFlag = 1 }

                    }
                }
                if(metacharacterFlag == 1){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                    deleteDir()
                    flash.error = "The genome file contains metacharacters (*, ?, ...). This is not allowed."
                    cleanRedirect()
                    return
                }


                if(genomeFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file was not fasta.")
                    deleteDir()
                    flash.error = "Genome file ${uploadedGenomeFile.originalFilename} is not in DNA fasta format."
                    cleanRedirect()
                    return
                }
                cmd = ["cksum ${dirName}/genome.fa"]
                trainingInstance.genome_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
                trainingInstance.genome_size = uploadedGenomeFile.size
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome.fa is ${trainingInstance.genome_size} big and has a cksum of ${trainingInstance.genome_cksum}.")
            } // end of genome file upload

            // retrieve beginning of genome file for format check
            if(!(trainingInstance.genome_ftp_link == null)){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome web-link is ${trainingInstance.genome_ftp_link}")
                projectDir.mkdirs()
                
                // check whether URL exists
                def cmd = ['curl', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', trainingInstance.genome_ftp_link]
                error_code = Utilities.executeForInteger(logFile, 3, trainingInstance.accession_id, "urlExistsScript", cmd)
                if(!(error_code == 200) && !(error_code == 302)){
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
                def pattern = ".*Length: (\\d*).* "
                def genome_size = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "spiderScript", cmd, pattern)
                if(genome_size > maxFileSizeByWget){//1 GB
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Genome file size exceeds permitted ${maxFileSizeByWget} bytes by ${genome_size} bytes.")
                    deleteDir()
                    flash.error = "Genome file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
                    cleanRedirect()
                    return
                }
                
                confirmationString = "${confirmationString}Genome file: ${trainingInstance.genome_ftp_link}\n"
                // checking web file for DNA fasta format:
                if(!("${trainingInstance.genome_ftp_link}" =~ /\.gz/)){
                    def URL url = new URL("${trainingInstance.genome_ftp_link}");
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
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The first 20 lines in genome file are not fasta.")
                        deleteDir()
                        flash.error = "Genome file ${trainingInstance.genome_ftp_link} is not in DNA fasta format."
                        cleanRedirect()
                        return
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The linked genome file is gzipped. Format will be checked later after extraction.")
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
                
                if("${uploadedEstFile.originalFilename}" =~ /\.gz/){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST file is gzipped.")
                    def cmd = ["mv ${dirName}/est.fa ${dirName}/est.fa.gz; gunzip ${dirName}/est.fa.gz"]
                    Utilities.execute(logFile, verb, trainingInstance.accession_id, "gunzipEstScript", cmd)

                    if (!new File(projectDir, "est.fa").exists()) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gzipped EST file is corrupt")
                        deleteDir()
                        flash.error = "The gzipped EST file is corrupt."
                        cleanRedirect()
                        return
                    }
                }
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Uploaded EST file ${uploadedEstFile.originalFilename} was renamed to est.fa and moved to ${dirName}")
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
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).");
                    deleteDir()
                    flash.error = "The cDNA file contains metacharacters (*, ?, ...). This is not allowed."
                    cleanRedirect()
                    return
                }
                if(estFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file was not fasta.")
                    deleteDir()
                    flash.error = "cDNA file ${uploadedEstFile.originalFilename} is not in DNA fasta format."
                    cleanRedirect()
                    return
                } else { estExistsFlag = 1 }
                
                def cmd = ["cksum ${dirName}/est.fa"]
                trainingInstance.est_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
                trainingInstance.est_size = uploadedEstFile.size
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "est.fa is ${trainingInstance.est_size} big and has a cksum of ${trainingInstance.est_cksum}.")
            }

            // retrieve beginning of est file for format check
            if(!(trainingInstance.est_ftp_link == null)){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "est web-link is ${trainingInstance.est_ftp_link}")
                projectDir.mkdirs()
                confirmationString = "${confirmationString}cDNA file: ${trainingInstance.est_ftp_link}\n"
                
                // check whether URL exists
                def cmd = ['curl', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', trainingInstance.est_ftp_link]
                error_code = Utilities.executeForInteger(logFile, 3, trainingInstance.accession_id, "urlExistsScript", cmd)
                if(!(error_code == 200) && !(error_code == 302)){
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
                def pattern = ".*Length: (\\d*).* "
                def est_size = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "spiderScript", cmd, pattern)
                if(est_size > maxFileSizeByWget){//1 GB
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST file size exceeds permitted ${maxFileSizeByWget} bytes by ${est_size} bytes.")
                    deleteDir()
                    flash.error = "cDNA file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
                    cleanRedirect()
                    return
                }
                
                if(!("${trainingInstance.est_ftp_link}" =~ /\.gz/)){
                    // checking web file for DNA fasta format:
                    def URL url = new URL("${trainingInstance.est_ftp_link}");
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
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file was not fasta.")
                        deleteDir()
                        flash.error = "cDNA file ${trainingInstance.est_ftp_link} is not in DNA fasta format."
                        cleanRedirect()
                        return
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The linked EST file is gzipped. Format will be checked later after extraction.")
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
                    if(!uploadedGenomeFile.empty){
                        // gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
                        def metacharacterFlag = 0
                        Utilities.log(logFile, 2, verb, trainingInstance.accession_id, "Checking training-gene-structure.gff file format")
                        new File(projectDir, "training-gene-structure.gff").eachLine{line ->
                            // check whether weird metacharacters are included
                            if(line =~ /\*/ || line =~ /\?/){
                                metacharacterFlag = 1
                            }else{
                                if(line =~ /^LOCUS/){
                                    structureGbkFlag = 1
                                }
                            }
                        }
                        if(structureGbkFlag == 0){
                            def checkGffScript = new File(projectDir, "checkGff.sh")
                            def gffChkOutFile = "${dirName}/gffCheck.out"
                            def gffChkColsFile = "${dirName}/gffCols.out"
                            checkGffScript << "/usr/bin/perl ${AUGUSTUS_SCRIPTS_PATH}/findGffNamesInFasta.pl --gff=${dirName}/training-gene-structure.gff --genome=${dirName}/genome.fa --out=${gffChkColsFile} &> ${gffChkOutFile}"
                            cmdStr = "bash ${dirName}/checkGff.sh"
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
                            delProc = "${cmdStr}".execute()
                            delProc.waitFor()
                        }
                        if(metacharacterFlag == 1){
                            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gene structure file contains metacharacters (e.g. * or ?).");
                            deleteDir()
                            flash.error = "Gene Structure file contains metacharacters (*, ?, ...). This is not allowed."
                            cleanRedirect()
                            return
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
                structureExistsFlag = 1
                
                def cmd = ["cksum ${dirName}/training-gene-structure.gff"]
                trainingInstance.struct_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "structCksumScript", cmd, "(\\d*) \\d* ")
                trainingInstance.struct_size = uploadedStructFile.size
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "training-gene-structure.gff is ${trainingInstance.struct_size} big and has a cksum of ${trainingInstance.struct_cksum}.")
            }


            // upload of protein file
            def cRatio = 0
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
                
                if("${uploadedProteinFile.originalFilename}" =~ /\.gz/){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Protein file is gzipped.")
                    def cmd = ["mv ${dirName}/protein.fa ${dirName}/protein.fa.gz; gunzip ${dirName}/protein.fa.gz"]
                    Utilities.execute(logFile, verb, trainingInstance.accession_id, "gunzipProteinScript", cmd)

                    if (!new File(projectDir, "protein.fa").exists()) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gzipped Protein file is corrupt")
                        deleteDir()
                        flash.error = "The gzipped Protein file is corrupt."
                        cleanRedirect()
                        return
                    }
                }
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Uploaded protein file ${uploadedProteinFile.originalFilename} was renamed to protein.fa and moved to ${dirName}")
                // check fasta format
                // check that file contains protein sequence, here defined as not more than 5 percent C or c
                def cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
                def allAminoAcidsCounter = 0
                def metacharacterFlag = 0
                new File(projectDir, "protein.fa").eachLine{line ->
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = 1
                    }else{
                        if(!(line =~ /^[>AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx ]/) && !(line =~ /^$/)){ proteinFastaFlag = 1 }
                        if(!(line =~ /^>/)){
                            line.eachMatch(/[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/){allAminoAcidsCounter = allAminoAcidsCounter + 1}
                            line.eachMatch(/[Cc]/){cytosinCounter = cytosinCounter + 1}
                        }
                    }
                }
                if(metacharacterFlag == 1){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file contains metacharacters (e.g. * or ?).");
                    deleteDir()
                    flash.error = "The protein file contains metacharacters (*, ?, ...). This is not allowed."
                    cleanRedirect()
                    return
                }
                cRatio = cytosinCounter/allAminoAcidsCounter
                if (cRatio >= 0.05){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence).")
                    deleteDir()
                    flash.error = "Your protein file was not recognized as a protein file. It may be DNA file. The training job was not started. Please contact augustus@uni-greifswald.de if you are completely sure this file is a protein fasta file."
                    cleanRedirect()
                    return
                }
                if(proteinFastaFlag == 1) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not protein fasta.")
                    deleteDir()
                    flash.error = "Protein file ${uploadedProteinFile.originalFilename} is not in protein fasta format."
                    cleanRedirect()
                    return
                }
                proteinExistsFlag = 1
                
                def cmd = ["cksum ${dirName}/protein.fa"]
                trainingInstance.protein_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "(\\d*) \\d* ")
                trainingInstance.protein_size = uploadedProteinFile.size
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${trainingInstance.protein_cksum}.")
            }

            // retrieve beginning of protein file for format check
            if(!(trainingInstance.protein_ftp_link == null)){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein web-link is ${trainingInstance.protein_ftp_link}")
                projectDir.mkdirs()
                confirmationString = "${confirmationString}Protein file: ${trainingInstance.protein_ftp_link}\n"
                
                 // check whether URL exists
                def cmd = ['curl', '-o /dev/null', '--write-out', '%{http_code}', '--silent', '--head', trainingInstance.protein_ftp_link]
                error_code = Utilities.executeForInteger(logFile, 3, trainingInstance.accession_id, "urlExistsScript", cmd)
                if(!(error_code == 200) && !(error_code == 302)){
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
                def pattern = ".*Length: (\\d*).* "
                def protein_size = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "spiderScript", cmd, pattern)
                if(protein_size > maxFileSizeByWget){//1 GB
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Protein file size exceeds permitted ${maxFileSizeByWget} bytes by ${protein_size} bytes.")
                    deleteDir()
                    flash.error = "protein_size file is bigger than 1 GB bytes, which is our maximal size for file download from a web link."
                    cleanRedirect()
                    return
                }
                
                if(!("${trainingInstance.protein_ftp_link}" =~ /\.gz/)){
                    // checking web file for protein fasta format:
                    def URL url = new URL("${trainingInstance.protein_ftp_link}");
                    def URLConnection uc = url .openConnection()
                    def BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
                    try{
                        def String inputLine
                        def charCounter = 1
                        def char inputChar
                        def cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
                        def allAminoAcidsCounter = 0
                        while ( ((inputChar = br.read()) != null) && (charCounter <= 2000)) {
                            if(inputChar =~ />/){
                                inputLine = br.readLine();
                            }else if(!(inputChar =~ /^[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/) && !(inputChar =~ /^$/)){
                                proteinFastaFlag = 1
                            }else{
                                allAminoAcidsCounter += 1;
                                if(inputChar =~ /[Cc]/){
                                    cytosinCounter += 1;
                                }
                            }
                            charCounter++;
                        }
                        cRatio = allAminoAcidsCounter == 0 ? 1 : cytosinCounter/allAminoAcidsCounter
                    }finally{
                        br.close()
                    }
                    if (cRatio >= 0.05){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence).")
                        deleteDir()
                        flash.error = "Protein file ${trainingInstance.protein_ftp_link} does not contain protein sequences."
                        cleanRedirect()
                        return
                    }
                    if(proteinFastaFlag == 1) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not protein fasta.")
                        deleteDir()
                        flash.error = "Protein file ${trainingInstance.protein_ftp_link} is not in fasta format."
                        cleanRedirect()
                        return
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The linked Protein file is gzipped. Format will be checked later after extraction.")
                }

            }
            // send confirmation email and redirect
            trainingInstance.validate();
            if(!trainingInstance.hasErrors() && Utilities.saveDomainWithTransaction(trainingInstance)){
                Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "committed trainingInstance.id ${trainingInstance.id}")
                
                // generate empty results page
                def cmd = ["${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 0 &> /dev/null"]
                Utilities.execute(logFile, verb, trainingInstance.accession_id, "emptyPageScript", cmd)
                trainingInstance.job_status = 0
                mailStr = "Details of your job:\n\n${confirmationString}\n"
                trainingInstance.message = "----------------------------------------\n${today} - Message:\n"
                trainingInstance.message = "${trainingInstance.message}----------------------------------------\n\n${mailStr}"
                Utilities.saveDomainWithTransaction(trainingInstance)
                
                if(trainingInstance.email_adress != null){
                    msgStr = "Hello!\n\nThank you for submitting a job to train AUGUSTUS parameters for species ${trainingInstance.project_name}.\n\n${mailStr}The status/results page of your job is ${war_url}training/show/${trainingInstance.id}.\n\nYou will be notified by e-mail after computations of your job have finished.\n\nBest regards,\n\nthe AUGUSTUS web server team"
                    sendMail {
                        to "${trainingInstance.email_adress}"
                        subject "Your AUGUSTUS training job ${trainingInstance.accession_id}"
                        body """${msgStr}${footer}"""
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Confirmation e-mail sent.")
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Did not send confirmation e-mail because user stays anonymous, but everything is ok.")
                }
                redirect(action:'show', controller: 'training', id: trainingInstance.id)
            } else {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "An error occurred in the trainingInstance (e.g. E-Mail missing, see domain restrictions).")
                deleteDir()
                logAbort()
                render(view:'create', model:[training:trainingInstance])
                return
            }

            //---------------------  BACKGROUND PROCESS ----------------------------
            Thread.start{
                // retrieve genome file
                if(!(trainingInstance.genome_ftp_link == null)){
                    projectDir.mkdirs()
                    
                    def cmd = ["wget -O ${dirName}/genome.fa ${trainingInstance.genome_ftp_link}  &> /dev/null"]
                    Utilities.execute(logFile, verb, trainingInstance.accession_id, "getGenomeScript", cmd)
                    
                    if("${trainingInstance.genome_ftp_link}" =~ /\.gz/){
                        cmd = ["mv ${dirName}/genome.fa ${dirName}/genome.fa.gz; gunzip ${dirName}/genome.fa.gz"]
                        Utilities.execute(logFile, verb, trainingInstance.accession_id, "gunzipGenomeScript", cmd)
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Unpacked genome file.")
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "genome file upload finished, file stored as genome.fa at ${dirName}")
                    // check number of scaffolds (to avoid Java heapspace error in the next step)
                    cmd = ["grep -c '>' ${dirName}/genome.fa"]
                    def nSeqNumber = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "nSeqFile", cmd)
                    if(nSeqNumber > maxNSeqs){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.");
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name} was aborted\nbecause the provided genome file\n${trainingInstance.genome_ftp_link}\ncontains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds). This is not allowed.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "${trainingInstance.message}-----------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "${trainingInstance.message}------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    // check for fasta format & get seq names for gff validation:
                    def metacharacterFlag = 0
                    new File(projectDir, "genome.fa").eachLine{line ->
                        if(line =~ /\*/ || line =~ /\?/){
                            metacharacterFlag = 1
                        }else{
                            if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){ genomeFastaFlag = 1 }
                        }
                    }
                    if(metacharacterFlag == 1){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name} was aborted\nbecause the provided genome file\n${trainingInstance.genome_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "${trainingInstance.message}-----------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        // delete database entry
                        //trainingInstance.delete()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    if(genomeFastaFlag == 1) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file was not fasta. ${dirName} is deleted.")
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided genome file\n${trainingInstance.genome_ftp_link}\nwas not in DNA fasta format.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}----------------------------"
                        trainingInstance.message = "${trainingInstance.message}-----------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        // delete database entry
                        //trainingInstance.delete()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }

                    // check gff format
                    def gffColErrorFlag = 0
                    def gffNameErrorFlag = 0
                    if((!uploadedStructFile.empty) &&(!(trainingInstance.genome_ftp_link == null))){ //
                        // gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
                        Utilities.log(logFile, 2, verb, trainingInstance.accession_id, "Checking training-gene-structure.gff file format")
                        metacharacterFlag = 0
                        new File(projectDir, "training-gene-structure.gff").eachLine{line ->
                            if(line =~ /\*/ || line =~ /\?/){
                                metacharacterFlag = 1
                            }else{
                                if(line =~ /^LOCUS/){
                                    structureGbkFlag = 1
                                }
                            }
                        }
                        if(structureGbkFlag == 0){
                            def checkGffScript = new File(projectDir, "checkGff.sh")
                            def gffChkOutFile = "${dirName}/gffCheck.out"
                            def gffChkColsFile = "${dirName}/gffCols.out"
                            checkGffScript << "/usr/bin/perl ${AUGUSTUS_SCRIPTS_PATH}/findGffNamesInFasta.pl --gff=${dirName}/training-gene-structure.gff --genome=${dirName}/genome.fa --out=${gffChkColsFile} &> ${gffChkOutFile}"
                            cmdStr = "bash ${dirName}/checkGff.sh"
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
                            delProc = "${cmdStr}".execute()
                            delProc.waitFor()
                        }
                        if(metacharacterFlag == 1){
                            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The gene structure file contains metacharacters (e.g. * or ?).");
                            deleteDir()
                            logAbort()
                            mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided gene structure file contains metacharacters (e.g. * or ?).\nThis is not allowed.\n\n"
                            logDate = new Date()
                            trainingInstance.message = "${trainingInstance.message}----------------------------"
                            trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                            trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                            trainingInstance.message = "${trainingInstance.message}-----------------------------"
                            trainingInstance.message = "------\n\n${mailStr}"
                            Utilities.saveDomainWithTransaction(trainingInstance)
                            if(trainingInstance.email_adress != null){
                                msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                                sendMail {
                                    to "${trainingInstance.email_adress}"
                                    subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                    body """${msgStr}${footer}"""
                                }
                            }
                            // delete database entry
                            //trainingInstance.delete()
                            trainingInstance.results_urls = null
                            trainingInstance.job_status = 5
                            Utilities.saveDomainWithTransaction(trainingInstance)
                            return
                        }
                        if(gffColErrorFlag == 1 && structureGbkFlag == 0){
                            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Training gene structure file does not always contain 9 columns.")
                            mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided training gene structure file\n${trainingInstance.struct_file}\ndid not contain 9 columns in each line.\nPlease make sure the gff-format complies with the instructions in our 'Help' section before\nsubmitting another job!\n\n"
                            logDate = new Date()
                            trainingInstance.message = "${trainingInstance.message}----------------------------"
                            trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                            trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                            trainingInstance.message = "${trainingInstance.message}-----------------------------"
                            trainingInstance.message = "------\n\n${mailStr}"
                            Utilities.saveDomainWithTransaction(trainingInstance)
                            if(trainingInstance.email_adress != null){
                                msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                                sendMail {
                                    to "${trainingInstance.email_adress}"
                                    subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                    body """${msgStr}${footer}"""
                                }
                            }
                        }
                        if(gffNameErrorFlag == 1 && structureGbkFlag == 0){
                            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Training gene structure file contains entries that do not comply with genome sequence names.")
                            mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the sequence names in the provided training gene structure file\n${trainingInstance.struct_file}\ndid not comply with the sequence names in the supplied genome file\n${trainingInstance.genome_ftp_link}.\nPlease make sure the gff-format complies with the instructions in our 'Help' section\nbefore submitting another job!\n\n"
                            logDate = new Date()
                            trainingInstance.message = "${trainingInstance.message}-----------------------------"
                            trainingInstance.message = "${trainingInstance.message}-----------------\n${logDate}"
                            trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                            trainingInstance.message = "${trainingInstance.message}-----------------------------"
                            trainingInstance.message = "------\n\n${mailStr}"
                            Utilities.saveDomainWithTransaction(trainingInstance)
                            if(trainingInstance.email_adress != null){
                                msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                                sendMail {
                                    to "${trainingInstance.email_adress}"
                                    subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                    body """${msgStr}${footer}"""
                                }
                            }
                        }
                        if((gffColErrorFlag == 1 || gffNameErrorFlag == 1) && structureGbkFlag == 0){
                            deleteDir()
                            logAbort()
                            // delete database entry
                            //trainingInstance.delete()
                            trainingInstance.results_urls = null
                            trainingInstance.job_status = 5
                            Utilities.saveDomainWithTransaction(trainingInstance)
                            return
                        }
                    }
                    cmd = ["cksum ${dirName}/genome.fa"]
                    trainingInstance.genome_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
                    trainingInstance.genome_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ")
                } // end of if(!(trainingInstance.genome_ftp_link == null))

                // retrieve EST file


                if(!(trainingInstance.est_ftp_link == null)){
                    
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Retrieving EST/cDNA file ${trainingInstance.est_ftp_link}")
                    def cmd = ["wget -O ${dirName}/est.fa ${trainingInstance.est_ftp_link}  &> /dev/null"]
                    Utilities.execute(logFile, verb, trainingInstance.accession_id, "getEstScript", cmd)
                    
                    if("${trainingInstance.est_ftp_link}" =~ /\.gz/){
                        cmd = ["mv ${dirName}/est.fa ${dirName}/est.fa.gz; gunzip ${dirName}/est.fa.gz"]
                        Utilities.execute(logFile, verb, trainingInstance.accession_id, "gunzipEstScript", cmd)
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Unpacked EST file.")
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST/cDNA file upload finished, file stored as est.fa at ${dirName}")
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
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).");
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided cDNA file\n${trainingInstance.est_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}----------------------------"
                        trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        // delete database entry
                        //trainingInstance.delete()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    if(estFastaFlag == 1) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The EST/cDNA file was not fasta. ${dirName} is deleted.")
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided cDNA file\n${trainingInstance.est_ftp_link}\nwas not in DNA fasta format.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "${trainingInstance.message}-----------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    
                    estExistsFlag = 1

                    cmd = ["cksum ${dirName}/est.fa"]
                    trainingInstance.est_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
                    trainingInstance.est_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ")
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "est.fa is ${trainingInstance.est_size} big and has a cksum of ${trainingInstance.est_cksum}.")
                } // end of if(!(trainingInstance.est_ftp_link == null))

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
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data.")
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequences files\nwith an average sequence length shorter than ${estMinLen} might\ncontain RNAseq raw sequences. Currently, our web server application does not support\nthe integration of RNAseq raw sequences. Please either assemble\nyour sequences into longer contigs, or remove short sequences from your current file,\nor submit a new job without specifying a cDNA file.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}----------------------------"
                        trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        deleteDir()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }else if(avEstLen > estMaxLen){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data.")
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because\nthe sequences in your cDNA file have an average length of ${avEstLen}.\nWe suspect that sequences files with an average sequence length longer than ${estMaxLen}\nmight not contain ESTs or cDNAs. Please either remove long sequences from your\ncurrent file, or submit a new job without specifying a cDNA file.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}----------------------------"
                        trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        deleteDir()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                }

                // retrieve protein file
                if(!(trainingInstance.protein_ftp_link == null)){
                    
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Retrieving protein file ${trainingInstance.protein_ftp_link}")    
                    def cmd = ["wget -O ${dirName}/protein.fa ${trainingInstance.protein_ftp_link}  &> /dev/null"]
                    Utilities.execute(logFile, verb, trainingInstance.accession_id, "getProteinScript", cmd)
                    
                    if("${trainingInstance.protein_ftp_link}" =~ /\.gz/){
                        cmd = ["mv ${dirName}/protein.fa ${dirName}/protein.fa.gz; gunzip ${dirName}/protein.fa.gz"]
                        Utilities.execute(logFile, verb, trainingInstance.accession_id, "gunzipProteinScript", cmd)
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Unpacked protein file.")
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein file upload finished, file stored as protein.fa at ${dirName}")
                    
                    // check for fasta protein format:
                    def cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
                    def allAminoAcidsCounter = 0
                    def metacharacterFlag = 0
                    new File(projectDir, "protein.fa").eachLine{line ->
                        if(line =~ /\*/ || line =~ /\?/){
                            metacharacterFlag = 1
                        }else{
                            if(!(line =~ /^[>AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx ]/) && !(line =~ /^$/)){ proteinFastaFlag = 1 }
                            if(!(line =~ /^>/)){
                                line.eachMatch(/[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/){ allAminoAcidsCounter = allAminoAcidsCounter + 1 }
                                line.eachMatch(/[Cc]/){ cytosinCounter = cytosinCounter + 1 }
                            }
                        }
                    }
                    if(metacharacterFlag == 1){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file contains metacharacters (e.g. * or ?).");
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}----------------------------"
                        trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n-----------"
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        // delete database entry
                        //trainingInstance.delete()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    cRatio = cytosinCounter/allAminoAcidsCounter
                    if (cRatio >= 0.05){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence).")
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\nis suspected to contain DNA instead of protein sequences.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}----------------------------"
                        trainingInstance.message = "${trainingInstance.message}------------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n----------"
                        trainingInstance.message = "${trainingInstance.message}------------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        // delete database entry
                        //trainingInstance.delete()
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    
                    if(proteinFastaFlag == 1) {
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not protein fasta.")
                        deleteDir()
                        logAbort()
                        mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\nis not in fasta format.\n\n"
                        logDate = new Date()
                        trainingInstance.message = "${trainingInstance.message}-----------------------------"
                        trainingInstance.message = "${trainingInstance.message}-----------------\n${logDate}"
                        trainingInstance.message = "${trainingInstance.message} - Error Message:\n----------"
                        trainingInstance.message = "${trainingInstance.message}------------------------------"
                        trainingInstance.message = "------\n\n${mailStr}"
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        if(trainingInstance.email_adress != null){
                            msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                            sendMail {
                                to "${trainingInstance.email_adress}"
                                subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
                                body """${msgStr}${footer}"""
                            }
                        }
                        trainingInstance.results_urls = null
                        trainingInstance.job_status = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        return
                    }
                    
                    proteinExistsFlag = 1
                    
                    cmd = ["cksum ${dirName}/protein.fa"]
                    trainingInstance.protein_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "(\\d*) \\d* ")
                    trainingInstance.protein_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "\\d* (\\d*) ")
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${trainingInstance.protein_cksum}.")
                } // end of (!(trainingInstance.protein_ftp_link == null))

                // confirm file upload via e-mail
                if((!(trainingInstance.genome_ftp_link == null)) || (!(trainingInstance.protein_ftp_link == null)) || (!(trainingInstance.est_ftp_link == null))){
                    mailStr = "We have retrieved all files that you specified, successfully. You may delete\nthem from the public server, now, without affecting the AUGUSTUS training job.\n\n"
                    logDate = new Date()
                    trainingInstance.message = "${trainingInstance.message}----------------------------------------\n${logDate} - Message:\n----------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(trainingInstance)
                    if(trainingInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}Best regards,\n\nthe AUGUSTUS webserver team"
                        sendMail {
                            to "${trainingInstance.email_adress}"
                            subject "File upload has been completed for AUGUSTUS training job ${trainingInstance.accession_id}"
                            body """${msgStr}${footer}"""
                        }
                    }
                }

                // File formats appear to be ok.
                // check whether this job was submitted before:
                def Closure findPrediction = { Training.find { // query returns the first matching result
                        genome_cksum      == trainingInstance.genome_cksum && 
                        genome_size       == trainingInstance.genome_size && 
                        est_cksum         == trainingInstance.est_cksum && 
                        est_size          == trainingInstance.est_size && 
                        protein_cksum     == trainingInstance.protein_cksum && 
                        protein_size      == trainingInstance.protein_size && 
                        struct_cksum      == trainingInstance.struct_cksum
                        job_status != '6' && // ignore jobs targeted to an identical job
                        isNull('old_url') && // ignore jobs targeted to an identical job
                        job_status != '0' && // ignore jobs in prepare state
                        accession_id != trainingInstance.accession_id // not itself
                    }
                }
                def identicalPrediction = Utilities.executeWithTransaction(trainingInstance, findPrediction)

                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "identicalPrediction= ${identicalPrediction}")

                if (identicalPrediction != null) {
                    //job was submitted before. Send E-Mail to user with a link to the results.
                    def oldAccContent = identicalPrediction.accession_id
                    // oldID is a parameter that is used for showing redirects (see bottom)
                    def oldID = identicalPrediction.id
                    
                    mailStr = "You submitted job ${trainingInstance.accession_id}.\nThe job was aborted because the files that you submitted were submitted, before.\n\n"
                    trainingInstance.old_url = "${war_url}training/show/${oldID}"
                    logDate = new Date()
                    trainingInstance.message = "${trainingInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(trainingInstance)
                    if(trainingInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}The old job with identical input files and identical parameters"
                        msgStr = "${msgStr} is available at\n${war_url}training/show/${oldID}.\n\nBest regards,\n\n"
                        msgStr = "${msgStr}the AUGUSTUS web server team"
                        sendMail {
                            to "${trainingInstance.email_adress}"
                            subject "AUGUSTUS training job ${trainingInstance.accession_id} was submitted before as job ${oldAccContent}"
                            body """${msgStr}${footer}"""
                        }
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Data are identical to old job ${oldAccContent} with Accession-ID ${oldID}. ${dirName} is deleted.")
                    deleteDir()
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Web output directory is deleted")
                    def cmd = ["rm -r ${web_output_dir}/${trainingInstance.accession_id} &> /dev/null"]
                    Utilities.execute(logFile, 2, trainingInstance.accession_id, "removeWeb_output_dir", cmd)
                    logAbort()
                    trainingInstance.results_urls = null
                    trainingInstance.job_status = 6
                    Utilities.saveDomainWithTransaction(trainingInstance)
                    return
                } // end of job was submitted before check
                
                //Create a sge script:
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Writing SGE submission script.")
                def sgeFile = new File(projectDir, "augtrain.sh")
                // write command in script (according to uploaded files)
                sgeFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
                // this has been checked, works.
                if( estExistsFlag ==1 && proteinExistsFlag == 0 && structureExistsFlag == 0){
                    cmd2Script = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --cdna=${dirName}/est.fa --pasa --useGMAPforPASA -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH}  1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
                    sgeFile << "${cmd2Script}"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmd2Script}\"")
                    // this is currently tested
                }else if(estExistsFlag == 0 && proteinExistsFlag == 0 && structureExistsFlag == 1){
                    cmd2Script = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
                    sgeFile << "${cmd2Script}"
                    // this is currently tested
                }else if(estExistsFlag == 0 && proteinExistsFlag == 1 && structureExistsFlag == 0){
                    cmd2Script = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${dirName}/protein.fa -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} > ${dirName}/writeResults.log 1 2> ${dirName}/writeResults.err"
                    sgeFile << "${cmd2Script}"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmd2Script}\"")
                    // all following commands still need testing
                }else if(estExistsFlag == 1 && proteinExistsFlag == 1 && structureExistsFlag == 0){
                    cmd2Script = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --cdna=${dirName}/est.fa --trainingset=${dirName}/protein.fa -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
                    sgeFile << "${cmd2Script}"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmd2Script}\"")
                }else if(estExistsFlag == 1 && proteinExistsFlag == 0 && structureExistsFlag == 1){
                    cmd2Script = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --cdna=${dirName}/est.fa --trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
                    sgeFile << "${cmd2Script}"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmd2Script}\"")
                }else if(estExistsFlag == 0 && proteinExistsFlag == 1 && structureExistsFlag == 1){
                    sgeFile << "echo 'Simultaneous protein and structure file support are currently not implemented. Using the structure file, only.'\n\n${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmd2Script}\"")
                }else if(estExistsFlag == 1 && proteinExistsFlag == 1 && structureExistsFlag == 1){
                    cmd2Script = "echo Simultaneous protein and structure file support are currently not implemented.\n\nUsing the structure file, only.'\n\n${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${today}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
                    sgeFile << "${cmd2Script}"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmd2Script}\"")
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST: ${estExistsFlag} Protein: ${proteinExistsFlag} Structure: ${structureExistsFlag} SGE-script remains empty! This an error that should not be possible.")
                }
                Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile=${cmdStr}")
                // write submission script
                def submissionScript = new File(projectDir, "submit.sh")
                def fileID = "${dirName}/jobID"
                cmd2Script = "cd ${dirName}; qsub augtrain.sh > ${fileID} 2> /dev/null"
                submissionScript << "${cmd2Script}"
                Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "submissionScript << \"${cmd2Script}\"")
                // submit job
                cmdStr = "bash ${dirName}/submit.sh"
                def jobSubmission = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                jobSubmission.waitFor()
                // get job ID
                content = new File("${fileID}").text
                if (content.isEmpty()) {
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The augustus training job wasn't started")
                    trainingInstance.results_urls = null
                    trainingInstance.job_status = 5
                    Utilities.saveDomainWithTransaction(trainingInstance)
                    return
                }

                def jobID_array = content =~/Your job (\d*)/
                def jobID
                (1..jobID_array.groupCount()).each{jobID = "${jobID_array[0][it]}"}
                trainingInstance.job_id = jobID
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} submitted.")
                // check for job status
                trainingInstance.job_status = 1 // submitted
                Utilities.saveDomainWithTransaction(trainingInstance)
                def statusScript = new File(projectDir, "status.sh")
                def statusFile = "${dirName}/job.status"
                cmd2Script = "cd ${dirName}; /usr/bin/qstat -u \"*\" |grep augtrain |grep ${jobID} > ${statusFile} 2> /dev/null"
                statusScript << "${cmd2Script}"
                Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "statusScript << \"${cmd2Script}\"")
                def statusContent
                def statusCheck
                def qstat = 1
                def runFlag = 0;
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "checking job SGE status...")
                while(qstat == 1){
                    sleep(300000) // 300000 = 5 minutes
                    statusCheck = "bash ${dirName}/status.sh".execute()
                    statusCheck.waitFor()
                    sleep(100)
                    statusContent = new File("${statusFile}").text
                    if(statusContent =~ /qw/){
                        trainingInstance.job_status = 2
                    }else if( statusContent =~ /  r  / ){
                        trainingInstance.job_status = 3
                        if(runFlag == 0){
                            today = new Date()
                            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} begins running at ${today}.")
                        }
                        runFlag = 1
                    }else if(!statusContent.empty){
                        trainingInstance.job_status = 3
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} is neither in qw nor in r status but is still on the grid!")
                    }else{
                        trainingInstance.job_status = 4
                        qstat = 0
                        today = new Date()
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} left SGE at ${today}.")
                    }
                    Utilities.saveDomainWithTransaction(trainingInstance)
                }
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job status is ${trainingInstance.job_status} when job leaves SGE.")
                
                // set file rigths to readable by others
                Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "set file permissions on ${web_output_dir}/${trainingInstance.accession_id}")
                def webOutputDir = new File(web_output_dir, trainingInstance.accession_id)
                if (webOutputDir.exists()) {
                    webOutputDir.setReadable(true, false)
                    webOutputDir.setExecutable(true, false);
                    webOutputDir.eachFile { file -> file.setReadable(true, false) } // actually just predictions.tar.gz
                }
                // collect results link information
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/AutoAug.log").exists()){
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>Log-file</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/AutoAug.log\">AutoAug.log</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>Log-file</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/AutoAug.log\">AutoAug.log</a><br></p>"
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/AutoAug.log does exist and is linked.")
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/AutoAug.log is missing!")
                }
                Utilities.saveDomainWithTransaction(trainingInstance)
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/AutoAug.err").exists()){
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>Error-file</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/AutoAug.err\">AutoAug.err</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>Error-file</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/AutoAug.err\">AutoAug.err</a><br></p>"
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/AutoAug.err does exist and is linked.")
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/AutoAug.err is missing!")
                }
                Utilities.saveDomainWithTransaction(trainingInstance)
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/parameters.tar.gz").exists()){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/parameters.tar.gz does exist and is linked.")
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>Species parameter archive</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/parameters.tar.gz\">parameters.tar.gz</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>Species parameter archive</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/parameters.tar.gz\">parameters.tar.gz</a><br></p>"
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/training.gb.gz is missing!")
                }
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/training.gb.gz").exists()){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/training.gb.gz exists and is linked.")
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>Training genes</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/training.gb.gz\">training.gb.gz</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>Training genes</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/training.gb.gz\">training.gb.gz</a><br></p>"
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/training.gb.gz is missing!")
                }
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/ab_initio.tar.gz").exists()){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/ab_initio.tar.gz exists and is linked.")
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>Ab initiopredictions</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/ab_initio.tar.gz\">ab_initio.tar.gz</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>Ab initio predictions</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/ab_initio.tar.gz\">ab_initio.tar.gz</a><br></p>"
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/ab_initio.tar.gz is missing.")
                }
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/hints_pred.tar.gz").exists()){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/hints_pred.tar.gz exists and is linked.")
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>predictions with hints</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/hints_pred.tar.gz\">hints_pred.tar.gz</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>predictions with hints</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/hints_pred.tar.gz\">hints_pred.tar.gz</a><br></p>"
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/hints_pred.tar.gz is missing!")
                }
                if(new File("${web_output_dir}/${trainingInstance.accession_id}/hints_utr_pred.tar.gz").exists()){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/hints_utr_pred.tar.gz exists and is linked.")
                    if(trainingInstance.results_urls == null){
                        trainingInstance.results_urls = "<p><b>predictions with hints and UTRs</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/hints_utr_pred.tar.gz\">hints_utr_pred.tar.gz</a><br></p>"
                    }else{
                        trainingInstance.results_urls = "${trainingInstance.results_urls}<p><b>predictions with hints and UTRs</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/hints_utr_pred.tar.gz\">hints_utr_pred.tar.gz</a><br></p>"
                    }
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${web_output_dir}/${trainingInstance.accession_id}/hints_utr_pred.tar.gz is missing!")
                }

                // check whether errors occured by log-file-sizes
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Beginning to look for errors.")
                if(new File(projectDir, "AutoAug.err").exists()){
                    autoAugErrSize = new File(projectDir, "AutoAug.err").size()
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "autoAugErrorSize is ${autoAugErrSize}.")
                }else{
                    Utilities.log(logFile, 1, verb, "SEVERE", trainingInstance.accession_id, "autoAugError file was not created. Default size value is set to 10.")
                    autoAugErrSize = 10
                }
                if(new File(projectDir, "augtrain.sh.e${jobID}").exists()){
                    sgeErrSize = new File(projectDir, "augtrain.sh.e${jobID}").size()
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "sgeErrSize is ${sgeErrSize}.")

                }else{
                    Utilities.log(logFile, 1, verb, "SEVERE", trainingInstance.accession_id, "sgeErr file was not created. Default size value is set to 10.")
                    sgeErrSize = 10
                }
                if(new File(projectDir, "writeResults.err").exists()){
                    writeResultsErrSize = new File(projectDir, "writeResults.err").size()
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "writeResultsSize is ${writeResultsErrSize}.")
                }else{
                    Utilities.log(logFile, 1, verb, "SEVERE", trainingInstance.accession_id, "writeResultsErr file was not created. Default size value is set to 10.")
                    writeResultsErrSize = 10
                }
                if(autoAugErrSize==0 && sgeErrSize==0 && writeResultsErrSize==0){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "no errors occured (option 1).")

                    mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} finished.\n\n"
                    logDate = new Date()
                    trainingInstance.message = "${trainingInstance.message}----------------------------------------\n${logDate} - Message:\n----------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(trainingInstance)
                    if(trainingInstance.email_adress == null){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Computation was successful. Did not send e-mail to user because not e-mail adress was supplied.")
                    }
                    if(trainingInstance.email_adress != null){
                        msgStr = "Hello!\n\n${mailStr}You find the results at "
                        msgStr = "${msgStr}${war_url}training/show/${trainingInstance.id}.\n\nBest regards,\n\n"
                        msgStr = "${msgStr}the AUGUSTUS web server team"
                        sendMail {
                            to "${trainingInstance.email_adress}"
                            subject "AUGUSTUS training job ${trainingInstance.accession_id} is complete"
                            body """${msgStr}${footer}"""
                        }
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
                    def packResults = new File("${output_dir}/pack${trainingInstance.accession_id}.sh")
                    cmd2Script = "cd ${output_dir}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"
                    packResults << "${cmd2Script}"
                    Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "packResults << \"${cmd2Script}\"")
                    //packResults << "cd ${output_dir}; tar cf - ${trainingInstance.accession_id} | 7z a -si ${trainingInstance.accession_id}.tar.7z; rm -r ${trainingInstance.accession_id};"
                    cmdStr = "bash ${output_dir}/pack${trainingInstance.accession_id}.sh"
                    def cleanUp = "${cmdStr}".execute()
                    Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                    cleanUp.waitFor()
                    cmdStr = "rm ${output_dir}/pack${trainingInstance.accession_id}.sh &> /dev/null"
                    cleanUp = "${cmdStr}".execute()
                    Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                    deleteDir()
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "autoAug directory was packed with tar/gz.")
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job completed. Result: ok.")
                }else{
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "an error occured somewhere.")
                    if(!(autoAugErrSize == 0)){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "an error occured when ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl was executed!");
                        msgStr = "Hi ${admin_email}!\n\nJob: ${trainingInstance.accession_id}\n"
                        //msgStr = "${msgStr}IP: ${userIP}\n"
                        msgStr = "${msgStr}E-Mail: ${trainingInstance.email_adress}\n"
                        msgStr = "${msgStr}Link: ${war_url}training/show/${trainingInstance.id}\n\n"
                        msgStr = "${msgStr}An error occured in the autoAug pipeline. "
                        msgStr = "${msgStr}Please check manually what's wrong.  "
                        if(trainingInstance.email_adress == null){
                            msgStr = "${msgStr}The user has not been informed."
                            sendMail {
                                to "${admin_email}"
                                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                                body """${msgStr}${footer}"""
                            }
                        }else{
                            msgStr = "${msgStr}The user has been informed."
                            sendMail {
                                to "${admin_email}"
                                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                                body """${msgStr}${footer}"""
                            }
                        }
                        trainingInstance.job_error = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job status is ${trainingInstance.job_error} when autoAug error occured.")
                        def packResults = new File("${output_dir}/pack${trainingInstance.accession_id}.sh")
                        cmd2Script = "cd ${output_dir}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"
                        packResults << "${cmd2Script}"
                        Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "packResults << \"${cmd2Script}\"")
                        //packResults << "cd ${output_dir}; tar cf - ${trainingInstance.accession_id} | 7z a -si ${trainingInstance.accession_id}.tar.7z; rm -r ${trainingInstance.accession_id};"
                        cmdStr = "bash ${output_dir}/pack${trainingInstance.accession_id}.sh"
                        def cleanUp = "${cmdStr}".execute()
                        Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                        cleanUp.waitFor()
                        cmdStr = "rm ${output_dir}/pack${trainingInstance.accession_id}.sh &> /dev/null"
                        cleanUp = "${cmdStr}".execute()
                        Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                        cleanUp.waitFor()
                        deleteDir()
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "autoAug directory was packed with tar/gz.")
                    }
                    if(!(sgeErrSize == 0)){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "a SGE error occured!");
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "a SGE error occured!");
                        msgStr = "Hi ${admin_email}!\n\nJob: ${trainingInstance.accession_id}\n"
                        //msgStr = "${msgStr}IP: ${userIP}\n"
                        //msgStr = "${msgStr}E-Mail: ${trainingInstance.email_adress}\n"
                        msgStr = "${msgStr}Link: ${war_url}training/show/${trainingInstance.id}\n\n"
                        msgStr = "${msgStr}An SGE error occured. Please check manually what's wrong. "
                        if(trainingInstance.email_adress == null){
                            msgStr = "${msgStr}The user has not been informed."
                            sendMail {
                                to "${admin_email}"
                                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                                body """${msgStr}${footer}"""
                            }
                        }else{
                            msgStr = "${msgStr}The user has been informed."
                            sendMail {
                                to "${admin_email}"
                                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                                body """${msgStr}${footer}"""
                            }
                        }
                        trainingInstance.job_error = 5
                        Utilities.saveDomainWithTransaction(trainingInstance)
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job status is ${trainingInstance.job_error} when SGE error occured.")
                    }
                    if(!(writeResultsErrSize == 0)){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "an error occured during writing results!");
                        msgStr = "Hi ${admin_email}!\n\nJob: ${trainingInstance.accession_id}\n"
                        //msgStr = "${msgStr}IP: ${userIP\n"
                        //msgStr = "${msgStr}E-Mail: ${trainingInstance.email_adress}\n"
                        msgStr = "${msgStr}Link: ${war_url}training/show/${trainingInstance.id}\n\n"
                        msgStr = "${msgStr}An error occured during writing results. Please check manually what's wrong. "
                        if(trainingInstance.email_adress == null){
                            msgStr = "${msgStr}The user has not been informed."
                            sendMail {
                                to "${admin_email}"
                                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                                body """${msgStr}${footer}"""
                            }
                        }else{
                            msgStr = "${msgStr}The user has been informed."
                            sendMail {
                                to "${admin_email}"
                                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                                body """${msgStr}${footer}"""
                            }
                        }
                    }
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job error status is ${trainingInstance.job_error} after all errors have been checked.")
                    mailStr = "An error occured while running the AUGUSTUS training job ${trainingInstance.accession_id}.\n\nPlease check the log-files carefully before proceeding to work with the produced results.\n\n"
                    logDate = new Date()
                    trainingInstance.message = "${trainingInstance.message}----------------------------------------------\n${logDate} - Error Message:\n----------------------------------------------\n\n${mailStr}"
                    Utilities.saveDomainWithTransaction(trainingInstance)
                    if(trainingInstance.email_adress == null){
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The job is in an error state. Cound not send e-mail to anonymous user because no email adress was supplied.")
                    }else{
                        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
                        msgStr = "Hello!\n\n${mailStr}You find the results of your job at ${war_url}training/show/${trainingInstance.id}.\n\nThe administrator of the AUGUSTUS web server has been informed and"
                        msgStr = "${msgStr} will get back to you as soon as the problem is solved.\n\nBest regards,\n\n"
                        msgStr = "${msgStr}the AUGUSTUS web server team"
                        sendMail {
                            to "${trainingInstance.email_adress}"
                            subject "An error occured while executing AUGUSTUS training job ${trainingInstance.accession_id}"
                            body """${msgStr}${footer}"""
                        }
                    }
                }
            }
            //------------ END BACKGROUND PROCESS ----------------------------------
        }
    }
}
