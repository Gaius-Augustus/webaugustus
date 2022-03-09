package webaugustus

import grails.gorm.transactions.Transactional
import grails.util.Holders
import javax.annotation.PostConstruct
import org.springframework.context.MessageSource

/**
 * The class PredictionService controls everything that is related to a job for predicting genes with pre-trained parameters on a novel genome
 *    - it handles the file upload by wget
 *    - format check
 *    - job submission and status checks
 *    - rendering of results/job status page
 *    - sending E-Mails concerning the job status (downloaded files, errors, finished)
 */
@Transactional
class PredictionService extends AbstractWebaugustusService {
    
    MessageSource messageSource     // inject the messageSource

    @PostConstruct
    def init() {
        Utilities.log(getLogFile(), 1, 1, "startup     ", "PredictionService")
        startWorkerThread()
    }
    
    /**
     * number of cpus/cores/threads used by the worker machine running the autoAug pipeline
     * 
     * @return cpu count
     */ 
    public int countWorkerCPUs() {
        return Holders.getConfig().getProperty('worker.prediction.cpuCount', Integer, 1)
    }
    
    public void sendMailToUser(Prediction predictionInstance, String subjectString, String message) {
        sendMailToUser(predictionInstance.email_adress, subjectString, message)
    }
    
    // This is where uploaded files and results will be saved.
    public String getOutputDir() {
        return Holders.getConfig().getProperty('data.path.prediction.dir', String)
    }
    
    // directory to the results that are downloadable by end users
    // must be writable to webserver application
    public String getWebOutputDir() {        
        return Holders.getConfig().getProperty('data.path.prediction.web', String)
    }
    
    public String getWebOutputURL() {
        return Holders.getConfig().getProperty('url.prediction.result.rel', String)
    }
    
    public String getHttpBaseURL() {
        return Holders.getConfig().getProperty('url.prediction.abs', String)
    }
    
    // this log File contains the "process log", what was happening with which job when.
    private static File logFile = null
    
    public File getLogFile() {
        if (PredictionService.logFile == null) {
            PredictionService.logFile = new File("${getOutputDir()}/pred.log")
        }
        return PredictionService.logFile
    }
    
    // 1 only basic log messages, 2 all issued commands, 3 also script content
    public int getLogLevel() {
        return Holders.getConfig().getProperty('log.level.prediction', Integer)
    }
    
    public String getServiceName() {
        return "PredictionService"
    }
    
    /**
     * Returns all prediction instances where the user has committed a job, but this job has not yet startet
     */
    protected List<Prediction> findCommittedJobs() {
        return Prediction.withTransaction { Prediction.findAll(sort:"dateCreated", order: "asc"){ // query returns all committed jobs
            job_status == '0'
        } }
    }

    /**
     * Returns all prediction instances where the job is started
     */
    protected List<Prediction> findSubmittedJobs() {
        return Prediction.withTransaction { Prediction.findAll(sort:"dateCreated", order: "asc"){ // query returns all submitted jobs
            job_status == '1' || 
            job_status == '2' || 
            job_status == '3'
        } }
    }
    
    private void deleteDir(Prediction predictionInstance) {
        String dirName = "${getOutputDir()}/${predictionInstance.accession_id}"
        Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Project directory ${dirName} is deleted")
        def cmd = ["rm -r ${dirName} &> /dev/null"]
        Utilities.execute(getLogFile(), 2, predictionInstance.accession_id, "removeProjectDir", cmd)
    }
    
    private void abortJob(Prediction predictionInstance) {
        abortJob(predictionInstance, null)
    }
    
    private void abortJob(Prediction predictionInstance, String message) {
        abortJob(predictionInstance, message, "5")
    }
    
    private void abortJob(Prediction predictionInstance, String message, String jobStatus) {
        deleteDir(predictionInstance)
        
        if(predictionInstance.email_adress == null) {
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous user is aborted!")
        }
        else {
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Job ${predictionInstance.accession_id} is aborted!")
        }
        
        if (message != null) {
            predictionInstance.message = "${predictionInstance.message}-----------------------------"
            predictionInstance.message = "${predictionInstance.message}-----------------\n${new Date()}"
            predictionInstance.message = "${predictionInstance.message} - Error Message:\n----------"
            predictionInstance.message = "${predictionInstance.message}-----------------------------"
            predictionInstance.message = "${predictionInstance.message}------\n\n${message}"

            sendMailToUser(predictionInstance, "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted", message)
        }
        
        predictionInstance.results_urls = null
        predictionInstance.job_status = jobStatus
        Utilities.saveDomainWithTransaction(predictionInstance)
    }
    
    /**
     * Download for the given prediction instance all data provided by urls, check the data and start the augustus job
     */
    @Transactional
    protected void loadDataAndStartJob(AbstractWebAugustusDomainClass instance) {
        
        String AUGUSTUS_CONFIG_PATH = getAugustusConfigPath()
        String AUGUSTUS_SPECIES_PATH = getAugustusSpeciesPath()
        String AUGUSTUS_SCRIPTS_PATH = getAugustusScriptPath()
        
        Prediction predictionInstance = (Prediction) instance
        Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "load and start job ")
        
        String dirName = "${getOutputDir()}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
        
        boolean ftpFileUploaded = false            
        // retrieve genome file if not already done
        if (predictionInstance.genome_ftp_link != null && (predictionInstance.genome_size == null || predictionInstance.genome_size.equals("0"))) {
            projectDir.mkdirs()

            def cmd = ["wget -O ${dirName}/genome.fa ${predictionInstance.genome_ftp_link}  &> /dev/null"]
            Utilities.execute(getLogFile(), getLogLevel(), predictionInstance.accession_id, "getGenomeScript", cmd)

            if (Utilities.isSupportedCompressMode(predictionInstance.genome_ftp_link)) {
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Genome file ${predictionInstance.genome_ftp_link} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/genome.fa", "gz", getLogFile(), getLogLevel(), predictionInstance.accession_id)) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "The gzipped Genome file is corrupt.");
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided gzipped genome file\n${predictionInstance.genome_ftp_link} is corrupt.\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "genome file upload finished, file stored as genome.fa at ${dirName}")
            // check number of scaffolds (to avoid Java heapspace error in the next step)
            cmd = ["grep -c '>' ${dirName}/genome.fa"]
            Long nSeqNumber = Utilities.executeForLong(getLogFile(), getLogLevel(), predictionInstance.accession_id, "nSeqFile", cmd)
            int maxNSeqs = getMaxNSeqs()
            if(nSeqNumber == null || nSeqNumber > maxNSeqs){
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "The genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.");
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided genome file\n${predictionInstance.genome_ftp_link}\ncontains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds). This is not allowed!\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }

            // check for fasta format & get seq names for gff validation:
            def seqNames = []
            Utilities.FastaCheckResult fastaCheckResult = Utilities.checkFastaFormat(new File(projectDir, "genome.fa"), 
                predictionInstance.genome_ftp_link, seqNames, Utilities.FastaDataType.GENOME, messageSource)
            
            if (!fastaCheckResult.isValidFasta()) {
                String errorMessage = fastaCheckResult.getErrorMessage()
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, fastaCheckResult.getErrorMessage().replace("\n", " "));
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause ${errorMessage}\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            
            // check gff format
            def gffColErrorFlag = 0
            def gffNameErrorFlag = 0
            def gffSourceErrorFlag = 0
            File structFile = new File(projectDir, "hints.gff")
            if (structFile.exists()) { // if seqNames already exists
                // gff format validation: number of columns 9, + or - in column 7, column 1 has to be member of seqNames
                Utilities.log(getLogFile(), 2, getLogLevel(), predictionInstance.accession_id, "Checking hints.gff file format")
                
                List errors = Utilities.checkHintsGffFormat(structFile, predictionInstance.hint_file, seqNames)
                if (!errors.isEmpty()) {
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because: \n"
                    for (error in errors) {
                        Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, error)
                        mailStr += " - " + error + "\n"
                    }
                    mailStr +="Please make sure the gff-format complies with the instructions in our 'Help' section!\n"
                    mailStr += "\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
            }
            
            cmd = ["sed -i '/^ *\$/d' ${dirName}/genome.fa"]
            Utilities.execute(getLogFile(), getLogLevel(), predictionInstance.accession_id, "remove empty lines from genome.fa", cmd)
            
            cmd = ["cksum ${dirName}/genome.fa"]
            predictionInstance.genome_cksum = Utilities.executeForLong(getLogFile(), getLogLevel(), predictionInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.genome_size =  Utilities.executeForLong(getLogFile(), getLogLevel(), predictionInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "genome.fa is ${predictionInstance.genome_size} big and has a cksum of ${predictionInstance.genome_cksum}.")
            ftpFileUploaded = true
            Utilities.saveDomainWithTransaction(predictionInstance)
        } // end of if(!(predictionInstance.genome_ftp_link == null))

        // retrieve EST file if not already done
        if (predictionInstance.est_ftp_link != null && (predictionInstance.est_size == null || predictionInstance.est_size.equals("0"))) {
        
            def cmd = ["wget -O ${dirName}/est.fa ${predictionInstance.est_ftp_link}  &> /dev/null"]
            Utilities.execute(getLogFile(), getLogLevel(), predictionInstance.accession_id, "getEstScript", cmd)

            if (Utilities.isSupportedCompressMode(predictionInstance.est_ftp_link)) {
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "EST file ${predictionInstance.est_ftp_link} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/est.fa", "gz", getLogFile(), getLogLevel(), predictionInstance.accession_id)) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "The gzipped EST file is corrupt.");
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided gzipped cDNA file\n${predictionInstance.est_ftp_link} is corrupt.\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "EST/cDNA file upload finished, file stored as est.fa at ${dirName}")
            // check for fasta format:
            Utilities.FastaCheckResult fastaCheckResult = Utilities.checkFastaFormat(new File(projectDir, "est.fa"),
                predictionInstance.est_ftp_link, Utilities.FastaDataType.EST, messageSource)
            
            if (!fastaCheckResult.isValidFasta()) {
                String errorMessage = fastaCheckResult.getErrorMessage()
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, fastaCheckResult.getErrorMessage().replace("\n", " "));
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause ${errorMessage}\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            
            cmd = ["sed -i '/^ *\$/d' ${dirName}/est.fa"]
            Utilities.execute(getLogFile(), getLogLevel(), predictionInstance.accession_id, "remove empty lines from est.fa", cmd)
            
            cmd = ["cksum ${dirName}/est.fa"]
            predictionInstance.est_cksum = Utilities.executeForLong(getLogFile(), getLogLevel(), predictionInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.est_size =  Utilities.executeForLong(getLogFile(), getLogLevel(), predictionInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "est.fa is ${predictionInstance.est_size} big and has a cksum of ${predictionInstance.est_cksum}.")
            ftpFileUploaded = true
            Utilities.saveDomainWithTransaction(predictionInstance)
        } // end of if(!(predictionInstance.est_ftp_link == null))

        // check whether EST file is NOT RNAseq, i.e. does not contain on average very short entries
        int nEntries = 0
        int totalLen = 0
        File estFile = new File(projectDir, "est.fa")
        boolean estExistsFlag = estFile.exists()
        if (estExistsFlag) {
            estFile.eachLine{line ->
                if (line.startsWith(">")) {
                    nEntries = nEntries + 1
                }else{
                    totalLen = totalLen + line.size()
                }
            }
            if (nEntries == 0) {
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "EST sequence file is not in fasta format. It doesn't contain \">\" characters.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because your\ncDNA file is not in fasta format. \n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            def avEstLen = totalLen/nEntries
            int estMinLen = getEstMinLen()
            int estMaxLen = getEstMaxLen()
            if(avEstLen < estMinLen){
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequences files\nwith an average sequence length shorter than ${estMinLen} might contain RNAseq\nraw sequences. Currently, our web server application does not support the integration\nof RNAseq raw sequences. Please either assemble your sequences into longer contigs,\nor remove short sequences from your current file, or submit a new job without\nspecifying a cDNA file.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }else if(avEstLen > estMaxLen){
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequence\nfiles with an average sequence length longer than ${estMaxLen} might not contain\nESTs or cDNAs. Please either remove long sequences from your current file, or\nsubmit a new job without specifying a cDNA file.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
        }

        // confirm file upload via e-mail
        if (ftpFileUploaded) {
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Retrieved all ftp files successfully.")
            String mailStr = "We have retrieved all files that you specified, successfully. You may delete them\nfrom the public server, now, without affecting the AUGUSTUS prediction job.\n\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n${mailStr}"
            Utilities.saveDomainWithTransaction(predictionInstance)
            
            sendMailToUser(predictionInstance, "File upload has been completed for AUGUSTUS prediction job ${predictionInstance.accession_id}", mailStr)
        }

        // File formats appear to be ok.
        // check whether this job was submitted before:
        Closure findPrediction = { Prediction.find { // query returns the first matching result
                project_id          == predictionInstance.project_id && 
                genome_cksum        == predictionInstance.genome_cksum && 
                genome_size         == predictionInstance.genome_size && 
                est_cksum           == predictionInstance.est_cksum && 
                est_size            == predictionInstance.est_size && 
                hint_cksum          == predictionInstance.hint_cksum && 
                hint_size           == predictionInstance.hint_size && 
                archive_cksum       == predictionInstance.archive_cksum && 
                archive_size        == predictionInstance.archive_size && 
                utr                 == predictionInstance.utr &&
                pred_strand         == predictionInstance.pred_strand && 
                alt_transcripts     == predictionInstance.alt_transcripts && 
                allowed_structures  == predictionInstance.allowed_structures && 
                ignore_conflicts    == predictionInstance.ignore_conflicts
                job_status != '6' && // ignore jobs targeted to an identical job
                isNull('old_url') && // ignore jobs targeted to an identical job
                accession_id != predictionInstance.accession_id && // not itself
                dateCreated < predictionInstance.dateCreated // created before
            }
        }
        
        def identicalPrediction = findPrediction()

        if (identicalPrediction != null) {
            //job was submitted before. Send E-Mail to user with a link to the results.
            def oldAccContent = identicalPrediction.accession_id
            // oldID is a parameter that is used for showing redirects (see bottom)
            def oldID = identicalPrediction.id

            String mailStr = "You submitted job ${predictionInstance.accession_id}.\nThe job was aborted because the files that you submitted were submitted, before.\n\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${mailStr}"
            predictionInstance.old_url = "${getRelativeURL()}prediction/show/${oldID}"
            Utilities.saveDomainWithTransaction(predictionInstance)
            
            mailStr += "The old job with identical input files and identical parameters "
            mailStr += "is available at\n${getHttpBaseURL()}show/${oldID}.\n\n"
            
            sendMailToUser(predictionInstance, "AUGUSTUS prediction job ${predictionInstance.accession_id} was submitted before as job ${oldAccContent}", mailStr)
            
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Data are identical to old job ${oldAccContent} with Accession-ID ${oldID}.")
            abortJob(predictionInstance, null, "6")
            
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Web output directory is deleted")
            def cmd = ["rm -r ${getWebOutputDir()}/${predictionInstance.accession_id} &> /dev/null"]
            Utilities.execute(getLogFile(), 2, predictionInstance.accession_id, "removeWeb_output_dir", cmd)
            
            return
        } // end of job was submitted before check
        
        //rename and move parameters
        // species name for AUGUSTUS
        String species = predictionInstance.project_id
        String cmdStr
        File paramArchFile = new File(projectDir, "parameters.tar.gz")
        if(paramArchFile.exists()){
            def mvParamsScript = new File(projectDir, "mvParams.sh")
            cmdStr = "${AUGUSTUS_SCRIPTS_PATH}/moveParameters.pl ${dirName}/params ${predictionInstance.accession_id} ${AUGUSTUS_SPECIES_PATH} 2> /dev/null\n"
            mvParamsScript << "${cmdStr}"
            Utilities.log(getLogFile(), 3, getLogLevel(), predictionInstance.accession_id, "mvParamsScript << \"${cmdStr}\"")
            cmdStr = "bash ${mvParamsScript}"
            def mvParamsRunning = "${cmdStr}".execute()
            Utilities.log(getLogFile(), 2, getLogLevel(), predictionInstance.accession_id, cmdStr)
            mvParamsRunning.waitFor()
            species = "${predictionInstance.accession_id}"
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Moved uploaded parameters and renamed species to ${predictionInstance.accession_id}")
        }
        //Create script:
        String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
        Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Writing ${computeClusterName} submission script.")
        
        int countCPUs = 1
        if (estExistsFlag) {
            countCPUs = countWorkerCPUs()
        }
        else {
            int fastaEntryCount = Utilities.executeForInteger(getLogFile(), 3, predictionInstance.accession_id, "countSequences", ["grep '>' ${dirName}/genome.fa  | wc -l"])
            countCPUs = Math.min(countWorkerCPUs(), fastaEntryCount)
        }
        
        File jobFile = new File(projectDir, "aug-pred.sh")
        if (jobFile.exists()) {
            jobFile.delete()
        }
        // write command in script (according to uploaded files)
        jobFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
        cmdStr = "mkdir -p ${dirName}/augustus\n"
        if(estExistsFlag){
            if (countCPUs <= 1) {
                cmdStr += "blat"
            }
            else {
                cmdStr += "pblat -threads=" + countCPUs
            }
            cmdStr = "${cmdStr} -noHead ${dirName}/genome.fa ${dirName}/est.fa ${dirName}/est.psl\n"
            cmdStr = "${cmdStr}cat ${dirName}/est.psl | sort -n -k 16,16 | sort -s -k 14,14 > ${dirName}/est.s.psl\n"
            cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/blat2hints.pl --in=${dirName}/est.s.psl --out=${dirName}/est.hints --source=E\n"
            cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/blat2gbrowse.pl ${dirName}/est.s.psl ${dirName}/est.gbrowse\n"
        }
        boolean hintExistsFlag = new File(projectDir, "hints.gff").exists()
        if(hintExistsFlag) {
            cmdStr = "${cmdStr}cat ${dirName}/hints.gff >> ${dirName}/est.hints\n"
        }
        
        def radioParameterString = getParameterString(predictionInstance)
        
        if(hintExistsFlag || estExistsFlag) {
            radioParameterString += " --hintsfile=${dirName}/est.hints --extrinsicCfgFile=${AUGUSTUS_CONFIG_PATH}/extrinsic/extrinsic.ME.cfg"
        }
        cmdStr += "cd ${dirName}/augustus\n"
        if (countCPUs <= 1) {
            cmdStr += "augustus --species=${species} ${radioParameterString} ${dirName}/genome.fa --codingseq=on --exonnames=on > ${dirName}/augustus/augustus.gff\n"
        }
        else {
            cmdStr += "mkdir -p ${dirName}/autoAug\n"
            cmdStr += "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && "
            cmdStr += "${AUGUSTUS_SCRIPTS_PATH}/autoAugPred.pl -v -v -v --cpus=${countCPUs} --singleCPU "
            cmdStr += "--workingdir=${dirName}/autoAug "
            cmdStr += "--predictionresultsdir=${dirName}/autoAug/autoAugPred "
            cmdStr += "--species=${species} --genome=${dirName}/genome.fa "
            cmdStr += "--augustusoptions=\"${radioParameterString} --codingseq=on --exonnames=on\"\n"
            cmdStr += "cp ${dirName}/autoAug/autoAugPred/predictions/augustus.gff ${dirName}/augustus/augustus.gff\n"
        }        
        
        cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/getAnnoFasta.pl --seqfile=${dirName}/genome.fa ${dirName}/augustus/augustus.gff\n"
        cmdStr = "${cmdStr}cat ${dirName}/augustus/augustus.gff | perl -ne 'if(m/\\tAUGUSTUS\\t/){print;}' > ${dirName}/augustus/augustus.gtf\n"
        cmdStr = "${cmdStr}cat ${dirName}/augustus/augustus.gff | ${AUGUSTUS_SCRIPTS_PATH}/augustus2gbrowse.pl > ${dirName}/augustus/augustus.gbrowse\n"
        cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${predictionInstance.accession_id} null '${predictionInstance.dateCreated}' ${getOutputDir()} ${getWebOutputDir()} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 2> ${dirName}/writeResults.err"
        jobFile << "${cmdStr}"
        Utilities.log(getLogFile(), 3, getLogLevel(), predictionInstance.accession_id, "jobFile=${cmdStr}")
        
        jobFile.setExecutable(true, false)
        
        // commit any changes - if starting the job takes longer than the jdbc connections 'wait_timeout' 
        // a CommunicationsException and than a TransactionException is thrown when the commit is done after the job start
        Utilities.saveDomainWithTransaction(predictionInstance)
        
        String jobID = JobExecution.getDefaultJobExecution().startJob(dirName, jobFile.getName(), JobExecution.JobType.PREDICTION, getLogFile(), getLogLevel(), predictionInstance.accession_id, countCPUs)

        if (jobID == null) {
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "The augustus job wasn't started")
            predictionInstance.results_urls = null
            predictionInstance.job_status = 5
            
            String senderAdress = PredictionService.getWebaugustusEmailAddress()
            String userMailStr = "An error occurred while running the AUGUSTUS prediction job ${predictionInstance.accession_id}.\n\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${userMailStr}"
            predictionInstance.message = "${predictionInstance.message}Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
            
            Utilities.saveDomainWithTransaction(predictionInstance)
            
            if (predictionInstance.email_adress == null) {
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "The job is in an error state. Could not send e-mail to anonymous user because no email address was supplied.")
            }
            else {
                userMailStr += "The administrator of the AUGUSTUS web server has been informed.\n"
                userMailStr += "Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
                sendMailToUser(predictionInstance, "An error occurred while executing AUGUSTUS prediction job ${predictionInstance.accession_id}", userMailStr)
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
            }
            
            String admin_email = getAdminEmailAddress()
            String adminMailStr = "Hi ${admin_email}!\n\nJob: ${predictionInstance.accession_id}\n"
            adminMailStr += "Link: ${getHttpBaseURL()}show/${predictionInstance.id}\n\n"
            adminMailStr += "The job was not started on ${computeClusterName}. \n"
            adminMailStr += "Please check manually what's wrong.\n"
            adminMailStr += "The user has "
            if (predictionInstance.email_adress == null) {
                adminMailStr += "not "
            }
            adminMailStr += "been informed."
            sendMailToAdmin("Error in AUGUSTUS prediction job ${predictionInstance.accession_id}", adminMailStr)
            
            return
        }

        predictionInstance.job_id = jobID
        predictionInstance.job_status = 1 // submitted
        Utilities.saveDomainWithTransaction(predictionInstance)
        Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Job ${jobID} submitted.")
    }
    
    private String getParameterString(Prediction predictionInstance) {
        String dirName = "${getOutputDir()}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
        
        
        String species = predictionInstance.project_id
        boolean overRideUtrFlag = predictionInstance.utr
        
        if (overRideUtrFlag) {
            File archCheckLog = new File(projectDir, "archCheck.log")
            if (archCheckLog.exists()) {
                int archCheckLogSize = archCheckLog.text.size()
                if(archCheckLogSize > 0) {
                    overRideUtrFlag = false // UTR predictions are now permanently disabled
                }
            }
        }
        if (overRideUtrFlag) {
            String AUGUSTUS_SPECIES_PATH = getAugustusSpeciesPath()
            def utrParamContent = new File("${AUGUSTUS_SPECIES_PATH}/${species}/${species}_utr_probs.pbl")
            if (!utrParamContent.exists()) {
                overRideUtrFlag = false;
            }
        }
        if (overRideUtrFlag && predictionInstance.allowed_structures != 1 && predictionInstance.allowed_structures != 2) {
            overRideUtrFlag = false;
        }
        
        String radioParameterString = overRideUtrFlag ? " --UTR=on" : " --UTR=off"
        // strand prediction radio buttons
        if (predictionInstance.pred_strand == 1) {
            radioParameterString += " --strand=both"
        }
        else if(predictionInstance.pred_strand == 2) {
            radioParameterString += " --strand=forward"
        }
        else {
            radioParameterString += " --strand=backward"
        }
        // alternative transcript radio buttons
        if(predictionInstance.alt_transcripts == 1) {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=false"
        }
        else if(predictionInstance.alt_transcripts == 2) {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.2 --minmeanexonintronprob=0.5 --maxtracks=2"
        }
        else if(predictionInstance.alt_transcripts == 3) {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.08 --minmeanexonintronprob=0.4 --maxtracks=3"
        }
        else {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.08 --minmeanexonintronprob=0.3 --maxtracks=20"
        }
        // gene structure radio buttons
        if(predictionInstance.allowed_structures == 1) {
            radioParameterString += " --genemodel=partial"
        }
        else if(predictionInstance.allowed_structures == 2) {
            radioParameterString += " --genemodel=complete"
        }
        else if(predictionInstance.allowed_structures == 3) {
            radioParameterString += " --genemodel=atleastone"
        }
        else{
            radioParameterString += " --genemodel=exactlyone"
        }
        // ignore gene structure conflicts with other strand checkbox
        if(predictionInstance.ignore_conflicts) {
            radioParameterString += " --singlestrand=true"
        }    
        
        return radioParameterString
    }
    
    /**
     * Check if the augustus job is still running and set the job_status accordingly
     * 
     * @return the job status (either WAITING_FOR_EXECUTION, COMPUTING, TIMEOUT, UNKNOWN, ERROR or FINISHED)
     */
    @Transactional
    protected JobExecution.JobStatus checkJobReadyness(AbstractWebAugustusDomainClass instance) {
        Prediction predictionInstance = (Prediction) instance
        String jobID = predictionInstance.job_id
        
        JobExecution.JobStatus status = JobExecution.getDefaultJobExecution().getJobStatus(jobID, getLogFile(), getLogLevel(), predictionInstance.accession_id)
        
        if (status == null) {
            return JobExecution.JobStatus.UNKNOWN
        }
        else if (JobExecution.JobStatus.WAITING_FOR_EXECUTION.equals(status)) {
            if (!predictionInstance.job_status.equals("2")) {
                predictionInstance.job_status = '2'
                Utilities.saveDomainWithTransaction(predictionInstance)
            }
        }
        else if (JobExecution.JobStatus.COMPUTING.equals(status)) {
            if (!predictionInstance.job_status.equals("3")) {
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Job ${jobID} begins running at ${new Date()}.")
                predictionInstance.job_status = '3'
                Utilities.saveDomainWithTransaction(predictionInstance)
            }
        }
        
        return status
    }
    
    /**
     * Do all tasks needed to process the job data and cleanup
     * 
     * @param jobStatus the job status (either TIMEOUT, ERROR or FINISHED)
     */
    @Transactional
    protected void finishJob(AbstractWebAugustusDomainClass instance, JobExecution.JobStatus jobStatus) {
        Prediction predictionInstance = (Prediction) instance
        Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "finishJob jobStatus=${jobStatus}")
        
        String jobID = predictionInstance.job_id
        String dirName = "${getOutputDir()}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
        
        int exitCode = JobExecution.getDefaultJobExecution().cleanupJob(dirName, this, JobExecution.JobType.PREDICTION, getLogFile(), getLogLevel(), predictionInstance.accession_id)
        
        // set file rigths to readable by others
        Utilities.log(getLogFile(), 3, getLogLevel(), predictionInstance.accession_id, "set file permissions on ${getWebOutputDir()}/${predictionInstance.accession_id}")
        def webOutputDir = new File(getWebOutputDir(), predictionInstance.accession_id) 
        if (webOutputDir.exists()) {
            webOutputDir.setReadable(true, false)
            webOutputDir.setExecutable(true, false);
            webOutputDir.eachFile { file -> file.setReadable(true, false) } // actually just predictions.tar.gz
        }
        // collect results link information
        if(new File("${getWebOutputDir()}/${predictionInstance.accession_id}/predictions.tar.gz").exists()){
            predictionInstance.results_urls = "<p><b>Prediction archive</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${predictionInstance.accession_id}/predictions.tar.gz\">predictions.tar.gz</a><br></p>"
            Utilities.saveDomainWithTransaction(predictionInstance)
        }
        // check whether errors occurred by log-file-sizes
        boolean sgeErr = false
        boolean executionErr = false
        boolean writeResultsErr = false
        File sgeErrFile = new File(projectDir, "aug-pred.sh.e${jobID}")
        File writeResultsErrFile = new File(projectDir, "writeResults.err")
        if (exitCode != 0) {
            sgeErr = true
            String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", predictionInstance.accession_id, "cleanupJob failed. A ${computeClusterName} error occurred!")
        }
        else {
            if (sgeErrFile.exists()) {
                executionErr = sgeErrFile.size() > 0
                if (executionErr) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", predictionInstance.accession_id, "an error occurred during script execution!");
                }
            }else{
                executionErr = true
                Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", predictionInstance.accession_id, "the aug-pred.sh.e${jobID} output file was not created, probably the script was not started")
            }
        }
        if (!sgeErr && (JobExecution.JobStatus.TIMEOUT.equals(jobStatus) || JobExecution.JobStatus.ERROR.equals(jobStatus)) ) {
            sgeErr = true
            String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", predictionInstance.accession_id, "A ${computeClusterName} error occurred! jobStatus=${jobStatus}")
        }
        if (writeResultsErrFile.exists()) {
            writeResultsErr = writeResultsErrFile.size() > 0
            if (writeResultsErr) {
                Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", predictionInstance.accession_id, "an error occurred during writing results!");
            }
        }else{
            writeResultsErr = true
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", predictionInstance.accession_id, "writeResults.err file was not created.")
        }

        if (!sgeErr && !executionErr && !writeResultsErr) {
            
            String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} finished.\n\n"
            predictionInstance.message += "----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n"
            predictionInstance.message += mailStr
            predictionInstance.message += "Results of your job are deleted from our server after 180 days.\n\n"
            predictionInstance.job_status = 4
            Utilities.saveDomainWithTransaction(predictionInstance)
            
            if(predictionInstance.email_adress == null){
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Computation was successful. Did not send e-mail to user because no e-mail address was supplied.")
            }
            else {
                String msgStr = "${mailStr}You find the results at "
                msgStr += "${getHttpBaseURL()}show/${predictionInstance.id}.\n\n"
                msgStr += "Results of your job are deleted from our server after 180 days.\n\n"
                sendMailToUser(predictionInstance, "AUGUSTUS prediction job ${predictionInstance.accession_id} is complete", msgStr)
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
            }
            
            if (projectDir.exists()) {
                def cmd = ["cd ${getOutputDir()}; tar -czvf ${predictionInstance.accession_id}.tar.gz ${predictionInstance.accession_id} &> /dev/null"]
                Utilities.execute(getLogFile(), 2, predictionInstance.accession_id, "packResults", cmd)
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "job directory was packed with tar/gz.")            
            }
            deleteDir(predictionInstance)
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Job completed. Result: ok.")
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "an error occurred somewhere: sgeErr=${sgeErr}, executionErr=${executionErr}, writeResultsErr=${writeResultsErr}, jobStatus=${jobStatus}")
            String admin_email = getAdminEmailAddress()
            String msgStr = "Hi ${admin_email}!\n\nJob: ${predictionInstance.accession_id}\n"
            msgStr += "Link: ${getHttpBaseURL()}show/${predictionInstance.id}\n\n"
            if (sgeErr) {
                String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
                msgStr += "A ${computeClusterName} error occurred."
            }
            else if (executionErr) {
                msgStr += "An error occurred."
            }
            else if (writeResultsErr) {
                msgStr += "An error occurred during writing results."
            }
            msgStr += " Please check manually what's wrong.\n"
            msgStr += "The job status is ${jobStatus}\n"
            msgStr += "The user has "
            if (predictionInstance.email_adress == null) {
                msgStr += "not "
            }
            msgStr += "been informed.\n\n"
            
            try {
                msgStr += "Details:\n"
                if (sgeErr) {
                    if (exitCode != 0) {
                        msgStr += "ExitCode=${exitCode}\n\n"
                    }
                    else if (JobExecution.JobStatus.TIMEOUT.equals(jobStatus)) {
                        msgStr += "JobStatus = TIMEOUT\n\n"
                    }
                    else if (JobExecution.JobStatus.ERROR.equals(jobStatus)) {
                        msgStr += "JobStatus = ERROR\n\n"
                    }
                }
                if (executionErr) {
                    String fileName = sgeErrFile.getName()
                    if (sgeErrFile.exists()) {
                         msgStr += "Last 10 lines of ${fileName} :\n"
                        def cmd = ["tail --lines=10 " + sgeErrFile.getAbsolutePath()]
                        msgStr += Utilities.executeForString(getLogFile(), getLogLevel(), predictionInstance.accession_id, "tail " + fileName, cmd)
                        msgStr += "\n\n"
                    }
                    else {
                        msgStr += "- Expected file \"${fileName}\" doesn't exists. \n\n"
                    }
                }
                if (writeResultsErr) {
                    String fileName = writeResultsErrFile.getName()
                    if (writeResultsErrFile.exists()) {
                         msgStr += "Last 10 lines of ${fileName} :\n"
                        def cmd = ["tail --lines=10 " + writeResultsErrFile.getAbsolutePath()]
                        msgStr += Utilities.executeForString(getLogFile(), getLogLevel(), predictionInstance.accession_id, "tail " + fileName, cmd)
                        msgStr += "\n\n"
                    }
                    else {
                        msgStr += "- Expected file \"${fileName}\" doesn't exists. \n\n"
                    }
                }
            }
            catch (Throwable t) {
                System.err.println("Exception catched. message=" + t.getMessage())
                System.err.println("Exception catched. exception=" + t)
                t.printStackTrace(System.err)
            }
            
            sendMailToAdmin("Error in AUGUSTUS prediction job ${predictionInstance.accession_id}", msgStr)
            
            String senderAdress = PredictionService.getWebaugustusEmailAddress()
            String mailStr = ""
            if (JobExecution.JobStatus.TIMEOUT.equals(jobStatus)) {
                mailStr += "The AUGUSTUS prediction job ${predictionInstance.accession_id} was cancelled, the maximum computation time has been reached.\n"
                mailStr += "If your genome file contains multiple fasta entries, split this file into smaller parts and try again.\n\n"
            }
            else {
                mailStr += "An error occurred while running the AUGUSTUS prediction job ${predictionInstance.accession_id}.\n\n"
                mailStr += "Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
            }            
            
            predictionInstance.message += "----------------------------------------------\n${new Date()} - Error Message:\n"
            predictionInstance.message += "----------------------------------------------\n\n"
            predictionInstance.message += mailStr
            
            if(predictionInstance.email_adress == null){
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "The job is in an error state. Could not send e-mail to anonymous user because no email address was supplied.")
            }else{
                msgStr = "${mailStr}The administrator of the AUGUSTUS web server has been informed.\n"
                sendMailToUser(predictionInstance, "An error occurred while executing AUGUSTUS prediction job ${predictionInstance.accession_id}", msgStr)
                Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
            }
            predictionInstance.job_status = 5
            Utilities.saveDomainWithTransaction(predictionInstance)
        }
    }
    
    /**
     * delete the email address after the job is done or aborted
     */
    @Transactional
    protected void deleteEmailAddressAfterJobEnd(AbstractWebAugustusDomainClass instance) {
        Prediction predictionInstance = (Prediction) instance
        if (predictionInstance.email_adress != null && 
            ("4".equals(predictionInstance.job_status) || "5".equals(predictionInstance.job_status) || "6".equals(predictionInstance.job_status))) {

            predictionInstance.email_adress = null
            Utilities.saveDomainWithTransaction(predictionInstance)
            Utilities.log(getLogFile(), 1, getLogLevel(), predictionInstance.accession_id, "delete email address of user")
        }
    }
}
