package webaugustus

import grails.gorm.transactions.Transactional
import grails.util.Holders
import javax.annotation.PostConstruct
import org.springframework.context.MessageSource

/**
 * The class TrainingService controls everything that is related to a job for training AUGUSTUS through the webserver:
 *    - it handles the file upload by wget
 *    - format check
 *    - job submission and status checks
 *    - rendering of results/job status page
 *    - sending E-Mails concerning the job status (downloaded files, errors, finished)
 */
@Transactional
class TrainingService extends AbstractWebaugustusService {
    
    MessageSource messageSource     // inject the messageSource
    
    public void sendMailToUser(Training trainingInstance, String subjectString, String message) {
        sendMailToUser(trainingInstance.email_adress, subjectString, message)
    }
    
    @PostConstruct
    def init() {
        Utilities.log(getLogFile(), 1, 1, "startup      ", "TrainingService")
        Thread.start(getServiceName()+"WorkerThread pre start", {
                sleep(60000) // start training worker thread a bit after prediction worker thread
                startWorkerThread()
        })
    }

    // This is where uploaded files and results will be saved.
    public String getOutputDir() {
        return Holders.getConfig().getProperty('data.path.training.dir', String)
    }
    
    // directory to the results that are downloadable by end users
    // must be writable to webserver application
    public String getWebOutputDir() {
        return Holders.getConfig().getProperty('data.path.training.web', String)
    }
    
    public String getWebOutputURL() {
        return Holders.getConfig().getProperty('url.training.result.rel', String)
    }
    
    public String getHttpBaseURL() {
        return Holders.getConfig().getProperty('url.training.abs', String)
    }
    
    // this log File contains the "process log", what was happening with which job when.
    private static File logFile = null
    
    public File getLogFile() {
        if (TrainingService.logFile == null) {
            TrainingService.logFile = new File("${getOutputDir()}/train.log")
        }
        return TrainingService.logFile
    }
    
    // 1 only basic log messages, 2 all issued commands, 3 also script content
    public int getLogLevel() {
        return Holders.getConfig().getProperty('log.level.training', Integer)
    }
    
    public String getServiceName() {
        return "TrainingService"
    }
    
    /**
     * Returns all training instances where the user has committed a job, but this job has not yet startet
     */
    protected List<Training> findCommittedJobs() {
        return Training.withTransaction { Training.findAll(sort:"dateCreated", order: "asc") { // query returns all committed jobs
            job_status == '0' && 
            job_error == '0'
        } }
    }

    /**
     * Returns all training instances where the job is started
     */
    protected List<Training> findSubmittedJobs() {
        return Training.withTransaction { Training.findAll(sort:"dateCreated", order: "asc") { // query returns all submitted jobs
            (job_status == '1' || 
            job_status == '2' || 
            job_status == '3') &&
            job_error == '0'
        } }
    }
    
    private void deleteDir(Training trainingInstance) {
        String dirName = "${getOutputDir()}/${trainingInstance.accession_id}"
        Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Project directory ${dirName} is deleted")
        def cmd = ["rm -r ${dirName} &> /dev/null"]
        Utilities.execute(getLogFile(), 2, trainingInstance.accession_id, "removeProjectDir", cmd)
    }
    
    private void abortJob(Training trainingInstance) {
        abortJob(trainingInstance, null)
    }
    
    private void abortJob(Training trainingInstance, String message) {
        abortJob(trainingInstance, message, "5")
    }
    
    private void abortJob(Training trainingInstance, String message, String jobStatus) {
        deleteDir(trainingInstance)
        
        if(trainingInstance.email_adress == null) {
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job ${trainingInstance.accession_id} by anonymous user is aborted!")
        }
        else {
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job ${trainingInstance.accession_id} is aborted!")
        }
        
        if (message != null) {
            trainingInstance.message = "${trainingInstance.message}-----------------------------"
            trainingInstance.message = "${trainingInstance.message}-----------------\n${new Date()}"
            trainingInstance.message = "${trainingInstance.message} - Error Message:\n----------"
            trainingInstance.message = "${trainingInstance.message}-----------------------------"
            trainingInstance.message = "${trainingInstance.message}------\n\n${message}"

            sendMailToUser(trainingInstance, "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted", message)
        }
        
        trainingInstance.results_urls = null
        trainingInstance.job_status = jobStatus
        trainingInstance.save(flush: true)
    }
    
    /**
     * Download for the given training instance all data provided by urls, check the data and start the augustus job
     * 
     */
    @Transactional
    protected void loadDataAndStartJob(AbstractWebAugustusDomainClass instance) {
        
        String AUGUSTUS_CONFIG_PATH = getAugustusConfigPath()
        String AUGUSTUS_SCRIPTS_PATH = getAugustusScriptPath()
        
        Training trainingInstance = (Training) instance
        Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "load and start job ")
        
        String dirName = "${getOutputDir()}/${trainingInstance.accession_id}"
        File projectDir = new File(dirName)
        
        // retrieve genome file
        if (trainingInstance.genome_ftp_link != null) {
            projectDir.mkdirs()

            def cmd = ["wget -O ${dirName}/genome.fa ${trainingInstance.genome_ftp_link}  &> /dev/null"]
            Utilities.execute(getLogFile(), getLogLevel(), trainingInstance.accession_id, "getGenomeScript", cmd)

            if (Utilities.isSupportedCompressMode(trainingInstance.genome_ftp_link)) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Genome file ${trainingInstance.genome_ftp_link} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/genome.fa", "gz", getLogFile(), getLogLevel(), trainingInstance.accession_id)) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The gzipped Genome file is corrupt.");
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted\nbecause the provided gzipped genome file\n${trainingInstance.genome_ftp_link} is corrupt.\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "genome file upload finished, file stored as genome.fa at ${dirName}")
            // check number of scaffolds (to avoid Java heapspace error in the next step)
            cmd = ["grep -c '>' ${dirName}/genome.fa"]
            Long nSeqNumber = Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "nSeqFile", cmd)
            int maxNSeqs = getMaxNSeqs()
            if(nSeqNumber == null || nSeqNumber > maxNSeqs){
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.");
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name} was aborted\nbecause the provided genome file\n${trainingInstance.genome_ftp_link}\ncontains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds).\nThis is not allowed.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            
            // check for fasta format:
            Utilities.FastaCheckResult fastaCheckResult = Utilities.checkFastaFormat(new File(projectDir, "genome.fa"),
                trainingInstance.genome_ftp_link, Utilities.FastaDataType.GENOME, messageSource)
            
            if (!fastaCheckResult.isValidFasta()) {
                String errorMessage = fastaCheckResult.getErrorMessage()
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, fastaCheckResult.getErrorMessage().replace("\n", " "));
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name} was aborted\nbecause ${errorMessage}\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            
            // check gff format
            boolean metacharacterFlag = false
            boolean gffColErrorFlag = false
            boolean gffNameErrorFlag = false
            boolean structureGbkFlag = false
            File structFile = new File(projectDir, "training-gene-structure.gff")
            if (structFile.exists()) {
                // gff format validation: number of columns 9, + or - in column 7, column 1 has to be member of seqNames
                Utilities.log(getLogFile(), 2, getLogLevel(), trainingInstance.accession_id, "Checking training-gene-structure.gff file format")
                metacharacterFlag = false
                structFile.eachLine{line ->
                    if (metacharacterFlag) {
                        return
                    }
                    // check whether weird metacharacters are included
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = true
                    } 
                    else if (line.startsWith("LOCUS")) {
                        structureGbkFlag = true
                    }
                }
                if (metacharacterFlag) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The gene structure file contains metacharacters (e.g. * or ?).");
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided gene structure file contains metacharacters (e.g. * or ?).\nThis is not allowed.\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
                if (!structureGbkFlag) {
                    def checkGffScript = new File(projectDir, "checkGff.sh")
                    def gffChkOutFile = "${dirName}/gffCheck.out"
                    def gffChkColsFile = "${dirName}/gffCols.out"
                    checkGffScript << "/usr/bin/perl ${AUGUSTUS_SCRIPTS_PATH}/findGffNamesInFasta.pl --gff=${dirName}/training-gene-structure.gff --genome=${dirName}/genome.fa --out=${gffChkColsFile} &> ${gffChkOutFile}"
                    String cmdStr = "bash ${dirName}/checkGff.sh"
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, cmdStr)
                    def ckStr = "${cmdStr}".execute()
                    ckStr.waitFor()
                    def ckContent = new File("${gffChkOutFile}").text
                    def stContent = new Scanner(ckContent)
                    long gffErrorStatus = stContent.nextLong();
                    ckContent = new File("${gffChkColsFile}").text
                    stContent = new Scanner(ckContent)
                    long gffColStatus = stContent.nextLong()
                    if(gffErrorStatus == 1){
                        gffNameErrorFlag = true
                    }
                    if(gffColStatus == 1){
                        gffColErrorFlag = true
                    }
                    cmdStr = "rm ${dirName}/checkGff.sh ${gffChkOutFile} ${gffChkColsFile}"
                    def delProc = "${cmdStr}".execute()
                    delProc.waitFor()
                }
                if (gffColErrorFlag && !structureGbkFlag) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Training gene structure file does not always contain 9 columns.")
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided training gene structure file\n${trainingInstance.struct_file}\ndid not contain 9 columns in each line.\nPlease make sure the gff-format complies with the instructions in our 'Help' section before\nsubmitting another job!\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
                if (gffNameErrorFlag && !structureGbkFlag) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Training gene structure file contains entries that do not comply with genome sequence names.")
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the sequence names in the provided training gene structure file\n${trainingInstance.struct_file}\ndid not comply with the sequence names in the supplied genome file\n${trainingInstance.genome_ftp_link}.\nPlease make sure the gff-format complies with the instructions in our 'Help' section\nbefore submitting another job!\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
            }
            cmd = ["cksum ${dirName}/genome.fa"]
            trainingInstance.genome_cksum = Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.genome_size =  Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ")
        } // end of if(!(trainingInstance.genome_ftp_link == null))

        // retrieve EST file
        if (trainingInstance.est_ftp_link != null) {

            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Retrieving EST/cDNA file ${trainingInstance.est_ftp_link}")
            def cmd = ["wget -O ${dirName}/est.fa ${trainingInstance.est_ftp_link}  &> /dev/null"]
            Utilities.execute(getLogFile(), getLogLevel(), trainingInstance.accession_id, "getEstScript", cmd)

            if (Utilities.isSupportedCompressMode(trainingInstance.est_ftp_link)) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "EST file ${trainingInstance.est_ftp_link} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/est.fa", "gz", getLogFile(), getLogLevel(), trainingInstance.accession_id)) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The gzipped EST file is corrupt.");
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted\nbecause the provided gzipped EST/cDNA file\n${trainingInstance.est_ftp_link} is corrupt.\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "EST/cDNA file upload finished, file stored as est.fa at ${dirName}")
            File estFile = new File(projectDir, "est.fa")
            if (!estFile.exists() || estFile.length() <= 2) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The EST/cDNA file doesn't exists or is empty. ${dirName} is deleted.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided cDNA file\n${trainingInstance.est_ftp_link}\n does not exist or is empty.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            // check for fasta format:
            Utilities.FastaCheckResult fastaCheckResult = Utilities.checkFastaFormat(estFile, 
                trainingInstance.est_ftp_link, Utilities.FastaDataType.EST, messageSource)
            
            if (!fastaCheckResult.isValidFasta()) {
                String errorMessage = fastaCheckResult.getErrorMessage()
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, fastaCheckResult.getErrorMessage().replace("\n", " "));
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted\nbecause ${errorMessage}\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            
            cmd = ["cksum ${dirName}/est.fa"]
            trainingInstance.est_cksum = Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.est_size =  Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "est.fa is ${trainingInstance.est_size} big and has a cksum of ${trainingInstance.est_cksum}.")
        } // end of if(!(trainingInstance.est_ftp_link == null))

        // check whether EST file is NOT RNAseq, i.e. does not contain on average very short entries
        int nEntries = 0
        int totalLen = 0
        File estFile = new File(projectDir, "est.fa")
        if (estFile.exists()) {
            estFile.eachLine{line ->
                if (line.startsWith(">")) {
                    nEntries = nEntries + 1
                }else{
                    totalLen = totalLen + line.size()
                }
            }
            if (nEntries == 0) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "EST sequence file is not in fasta format. It doesn't contain \">\" characters.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because your\ncDNA file is not in fasta format. \n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            def avEstLen = totalLen/nEntries
            def estMinLen = getEstMinLen()
            def estMaxLen = getEstMaxLen()
            if(avEstLen < estMinLen){
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequences files\nwith an average sequence length shorter than ${estMinLen} might\ncontain RNAseq raw sequences. Currently, our web server application does not support\nthe integration of RNAseq raw sequences. Please either assemble\nyour sequences into longer contigs, or remove short sequences from your current file,\nor submit a new job without specifying a cDNA file.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }else if(avEstLen > estMaxLen){
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because\nthe sequences in your cDNA file have an average length of ${avEstLen}.\nWe suspect that sequences files with an average sequence length longer than ${estMaxLen}\nmight not contain ESTs or cDNAs. Please either remove long sequences from your\ncurrent file, or submit a new job without specifying a cDNA file.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
        }

        // retrieve protein file
        if (trainingInstance.protein_ftp_link != null) {

            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Retrieving protein file ${trainingInstance.protein_ftp_link}")    
            def cmd = ["wget -O ${dirName}/protein.fa ${trainingInstance.protein_ftp_link}  &> /dev/null"]
            Utilities.execute(getLogFile(), getLogLevel(), trainingInstance.accession_id, "getProteinScript", cmd)

            if (Utilities.isSupportedCompressMode(trainingInstance.protein_ftp_link)) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Protein file ${trainingInstance.protein_ftp_link} is gzipped.")
                if ( !Utilities.deCompress("${dirName}/protein.fa", "gz", getLogFile(), getLogLevel(), trainingInstance.accession_id)) {
                    Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The gzipped Protein file is corrupt.");
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted\nbecause the provided gzipped protein file\n${trainingInstance.protein_ftp_link} is corrupt.\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "protein file upload finished, file stored as protein.fa at ${dirName}")

            // check for fasta protein format:
            Utilities.FastaCheckResult fastaCheckResult = Utilities.checkFastaFormat(new File(projectDir, "protein.fa"), 
                trainingInstance.protein_ftp_link, Utilities.FastaDataType.PROTEIN, messageSource)
            
            if (!fastaCheckResult.isValidFasta()) {
                String errorMessage = fastaCheckResult.getErrorMessage()
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, fastaCheckResult.getErrorMessage().replace("\n", " "));
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted\nbecause ${errorMessage}\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            
            cmd = ["grep -c '>' ${dirName}/protein.fa"]
            Long countProteins = Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "countProteinsScript", cmd)
            int minPSeqs = TrainingService.getMinPSeqs()
            if (countProteins == null || countProteins < minPSeqs) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The protein file contains just ${countProteins} proteins, but at least ${minPSeqs} are needed.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\ncontains just ${countProteins} proteins, but at least ${minPSeqs} are needed.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }

            cmd = ["cksum ${dirName}/protein.fa"]
            trainingInstance.protein_cksum = Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "proteinCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.protein_size =  Utilities.executeForLong(getLogFile(), getLogLevel(), trainingInstance.accession_id, "proteinCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${trainingInstance.protein_cksum}.")
        } // end of (!(trainingInstance.protein_ftp_link == null))
        
        // confirm file upload via e-mail
        if (trainingInstance.genome_ftp_link != null || trainingInstance.protein_ftp_link != null || trainingInstance.est_ftp_link != null) {
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Retrieved all ftp files successfully.")
            String mailStr = "We have retrieved all files that you specified, successfully. You may delete\nthem from the public server, now, without affecting the AUGUSTUS training job.\n\n"
            trainingInstance.message = "${trainingInstance.message}----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n${mailStr}"
            trainingInstance.save(flush: true)
            
            sendMailToUser(trainingInstance, "File upload has been completed for AUGUSTUS training job ${trainingInstance.accession_id}", mailStr)
        }

        // File formats appear to be ok.
        // check whether this job was submitted before:
        def Closure findTraining = { Training.find { // query returns the first matching result
                genome_cksum      == trainingInstance.genome_cksum && 
                genome_size       == trainingInstance.genome_size && 
                est_cksum         == trainingInstance.est_cksum && 
                est_size          == trainingInstance.est_size && 
                protein_cksum     == trainingInstance.protein_cksum && 
                protein_size      == trainingInstance.protein_size && 
                struct_cksum      == trainingInstance.struct_cksum
                job_status != '6' && // ignore jobs targeted to an identical job
                isNull('old_url') && // ignore jobs targeted to an identical job
                accession_id != trainingInstance.accession_id && // not itself
                dateCreated < trainingInstance.dateCreated // created before
            }
        }
        def identicalTraining = findTraining()

        if (identicalTraining != null) {
            //job was submitted before. Send E-Mail to user with a link to the results.
            def oldAccContent = identicalTraining.accession_id
            // oldID is a parameter that is used for showing redirects (see bottom)
            def oldID = identicalTraining.id

            String mailStr = "You submitted job ${trainingInstance.accession_id}.\nThe job was aborted because the files that you submitted were submitted, before.\n\n"
            trainingInstance.message = "${trainingInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${mailStr}"
            trainingInstance.old_url = "${getRelativeURL()}training/show/${oldID}"
            trainingInstance.save(flush: true)
            
            mailStr += "The old job with identical input files and identical parameters "
            mailStr += "is available at\n${getHttpBaseURL()}show/${oldID}.\n\n"
            
            sendMailToUser(trainingInstance, "AUGUSTUS training job ${trainingInstance.accession_id} was submitted before as job ${oldAccContent}", mailStr)
            
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Data are identical to old job ${oldAccContent} with Accession-ID ${oldID}. ${dirName} is deleted.")
            abortJob(trainingInstance, null, "6")
            
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Web output directory is deleted")
            def cmd = ["rm -r ${getWebOutputDir()}/${trainingInstance.accession_id} &> /dev/null"]
            Utilities.execute(getLogFile(), 2, trainingInstance.accession_id, "removeWeb_output_dir", cmd)
            
            return
        } // end of job was submitted before check
        
        
        boolean structureExistsFlag = (new File(projectDir, "training-gene-structure.gff")).exists()
        boolean estExistsFlag = (new File(projectDir, "est.fa")).exists()
        boolean proteinExistsFlag = (new File(projectDir, "protein.fa")).exists()
        
        //Create a sge script:
        String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
        Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Writing ${computeClusterName} submission script: EST: ${estExistsFlag} Protein: ${proteinExistsFlag} Structure: ${structureExistsFlag} ")
        File jobFile = new File(projectDir, "augtrain.sh")
        if (jobFile.exists()) {
            jobFile.delete()
        }
        // write command in script (according to uploaded files)
        jobFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
        String cmdStr = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} "
        // this has been checked, works.
        if (estExistsFlag && !proteinExistsFlag && !structureExistsFlag) {
            cmdStr += "--cdna=${dirName}/est.fa --pasa --useGMAPforPASA "
            // this is currently tested
        }else if (!estExistsFlag && !proteinExistsFlag && structureExistsFlag) {
            cmdStr += "--trainingset=${dirName}/training-gene-structure.gff "
            // this is currently tested
        }else if (!estExistsFlag && proteinExistsFlag && !structureExistsFlag) {
            cmdStr += "--trainingset=${dirName}/protein.fa "
            // all following commands still need testing
        }else if (estExistsFlag && proteinExistsFlag && !structureExistsFlag) {
            cmdStr += "--cdna=${dirName}/est.fa --trainingset=${dirName}/protein.fa "
        }else if (estExistsFlag && !proteinExistsFlag && structureExistsFlag) {
            cmdStr += "--cdna=${dirName}/est.fa --trainingset=${dirName}/training-gene-structure.gff "
        }else if(proteinExistsFlag && structureExistsFlag) {
            cmdStr = "echo 'Simultaneous protein and structure file support are currently not implemented. Using the structure file, only.'\n\n${cmdStr}"
            cmdStr += "--trainingset=${dirName}/training-gene-structure.gff "
        }else{
            cmdStr = null
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "EST: ${estExistsFlag} Protein: ${proteinExistsFlag} Structure: ${structureExistsFlag} ${computeClusterName}-script remains empty! This an error that should not be possible.")
        }
        if (cmdStr != null) {
            if (countWorkerCPUs() > 1) {
                cmdStr += "--cpus=" + countWorkerCPUs() + " "
            }
            cmdStr += "-v --singleCPU --webaugustus "
            cmdStr += "--workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
            cmdStr += "${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${trainingInstance.dateCreated}' ${getOutputDir()} ${getWebOutputDir()} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
            jobFile << "${cmdStr}"
            Utilities.log(getLogFile(), 3, getLogLevel(), trainingInstance.accession_id, "jobFile << \"${cmdStr}\"")
        }
        Utilities.log(getLogFile(), 3, getLogLevel(), trainingInstance.accession_id, "jobFile=${cmdStr}")

        jobFile.setExecutable(true, false);
        
        String jobID = JobExecution.getDefaultJobExecution().startJob(dirName, jobFile.getName(), JobExecution.JobType.TRAINING, getLogFile(), getLogLevel(), trainingInstance.accession_id)

        if (jobID == null) {
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The augustus training job wasn't started")
            trainingInstance.results_urls = null
            trainingInstance.job_status = '5'
            
            String senderAdress = TrainingService.getWebaugustusEmailAddress()
            String userMailStr = "An error occurred while running the AUGUSTUS training job ${trainingInstance.accession_id}.\n\n"
            trainingInstance.message = "${trainingInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${userMailStr}"
            trainingInstance.message = "${trainingInstance.message}Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
            
            trainingInstance.save(flush: true)
            
            if (trainingInstance.email_adress == null) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The job is in an error state. Could not send e-mail to anonymous user because no email address was supplied.")
            }
            else {
                userMailStr += "The administrator of the AUGUSTUS web server has been informed.\n"
                userMailStr += "Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
                sendMailToUser(trainingInstance, "An error occurred while executing AUGUSTUS training job ${trainingInstance.accession_id}", userMailStr)
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
            }
            
            String admin_email = getAdminEmailAddress()
            String adminMailStr = "Hi ${admin_email}!\n\nJob: ${trainingInstance.accession_id}\n"
            adminMailStr += "Link: ${getHttpBaseURL()}show/${trainingInstance.id}\n\n"
            adminMailStr += "The job was not started on ${computeClusterName}. \n"
            adminMailStr += "Please check manually what's wrong.\n"
            adminMailStr += "The user has "
            if (trainingInstance.email_adress == null) {
                adminMailStr += "not "
            }
            adminMailStr += "been informed."
            sendMailToAdmin("Error in AUGUSTUS training job ${trainingInstance.accession_id}", adminMailStr)
            
            return
        }

        trainingInstance.job_id = jobID
        trainingInstance.job_status = '1' // submitted
        trainingInstance.save(flush: true)
        Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job ${jobID} submitted.")
    }
    
    /**
     * Check if the augustus job is still running and set the job_status accordingly
     * 
     * @return the job status (either WAITING_FOR_EXECUTION, COMPUTING, TIMEOUT, UNKNOWN, ERROR or FINISHED)
     */
    @Transactional
    protected JobExecution.JobStatus checkJobReadyness(AbstractWebAugustusDomainClass instance) {
        Training trainingInstance = (Training) instance
        String jobID = trainingInstance.job_id
        
        JobExecution.JobStatus status = JobExecution.getDefaultJobExecution().getJobStatus(jobID, getLogFile(), getLogLevel(), trainingInstance.accession_id)
        
        if (status == null) {
            return JobExecution.JobStatus.UNKNOWN
        }
        else if (JobExecution.JobStatus.WAITING_FOR_EXECUTION.equals(status)) {
            if (!trainingInstance.job_status.equals("2")) {
                trainingInstance.job_status = '2'
                trainingInstance.save(flush: true)
            }
        }
        else if (JobExecution.JobStatus.COMPUTING.equals(status)) {
            if (!trainingInstance.job_status.equals("3")) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job ${jobID} begins running at ${new Date()}.")
                trainingInstance.job_status = '3'
                trainingInstance.save(flush: true)
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
        
        String AUGUSTUS_CONFIG_PATH = getAugustusConfigPath()
        String AUGUSTUS_SCRIPTS_PATH = getAugustusScriptPath()
        
        Training trainingInstance = (Training) instance
        Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "finishJob jobStatus=${jobStatus}")
        
        String jobID = trainingInstance.job_id
        String dirName = "${getOutputDir()}/${trainingInstance.accession_id}"
        File projectDir = new File(dirName)
        
        int exitCode = JobExecution.getDefaultJobExecution().cleanupJob(dirName, this, JobExecution.JobType.TRAINING, getLogFile(), getLogLevel(), trainingInstance.accession_id)
        
        // set file rigths to readable by others
        Utilities.log(getLogFile(), 3, getLogLevel(), trainingInstance.accession_id, "set file permissions on ${getWebOutputDir()}/${trainingInstance.accession_id}")
        def webOutputDir = new File(getWebOutputDir(), trainingInstance.accession_id)
        if (webOutputDir.exists()) {
            webOutputDir.setReadable(true, false)
            webOutputDir.setExecutable(true, false);
            webOutputDir.eachFile { file -> file.setReadable(true, false) }
        }
        // collect results link information
        String autoAugLogPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/AutoAug.log"
        if(new File(autoAugLogPath).exists()){
            String autoAugLogURL = "<p><b>Log-file</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/AutoAug.log\">AutoAug.log</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = autoAugLogURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${autoAugLogURL}"
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${autoAugLogPath} does exist and is linked.")
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${autoAugLogPath} is missing!")
        }
        String autoAugErrPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/AutoAug.err"
        if(new File(autoAugErrPath).exists()){
            String autoAugErrURL = "<p><b>Error-file</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/AutoAug.err\">AutoAug.err</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = autoAugErrURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${autoAugErrURL}"
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${autoAugErrPath} does exist and is linked.")
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${autoAugErrPath} is missing!")
        }
        String parameterGzPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/parameters.tar.gz"
        if(new File(parameterGzPath).exists()){
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${parameterGzPath} does exist and is linked.")
            String parameterGzURL = "<p><b>Species parameter archive</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/parameters.tar.gz\">parameters.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = parameterGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${parameterGzURL}"
            }
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${parameterGzPath} is missing!")
        }
        String trainingGzPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/training.gb.gz"
        if(new File(trainingGzPath).exists()){
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${trainingGzPath} exists and is linked.")
            String trainingGzURL = "<p><b>Training genes</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/training.gb.gz\">training.gb.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = trainingGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${trainingGzURL}"
            }
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${trainingGzPath} is missing!")
        }
        String abInitioGzPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/ab_initio.tar.gz"
        if(new File(abInitioGzPath).exists()){
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${abInitioGzPath} exists and is linked.")
            String abInitioGzURL = "<p><b>Ab initio predictions</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/ab_initio.tar.gz\">ab_initio.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = abInitioGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${abInitioGzURL}"
            }
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${abInitioGzPath} is missing.")
        }
        String hintPredGzPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/hints_pred.tar.gz"
        if(new File(hintPredGzPath).exists()){
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${hintPredGzPath} exists and is linked.")
            String hintPredGzURL = "<p><b>predictions with hints</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/hints_pred.tar.gz\">hints_pred.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = hintPredGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${hintPredGzURL}"
            }
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${hintPredGzPath} is missing!")
        }
        String hintUtrPredGzPath = "${getWebOutputDir()}/${trainingInstance.accession_id}/hints_utr_pred.tar.gz"
        if(new File(hintUtrPredGzPath).exists()){
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${hintUtrPredGzPath} exists and is linked.")
            String hintUtrPredGzURL = "<p><b>predictions with hints and UTRs</b>&nbsp;&nbsp;<a href=\"${getWebOutputURL()}${trainingInstance.accession_id}/hints_utr_pred.tar.gz\">hints_utr_pred.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = hintUtrPredGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${hintUtrPredGzURL}"
            }
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "${hintUtrPredGzPath} is missing!")
        }
        
        // check whether errors occurred by log-file-sizes
        Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Beginning to look for errors.")
        boolean autoAugErr = false
        boolean sgeErr = false
        boolean writeResultsErr = false
        if(new File(projectDir, "AutoAug.err").exists()){
            autoAugErr = new File(projectDir, "AutoAug.err").size() > 0
            if (autoAugErr) {
                Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "an error occurred when ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl was executed!")
            }
        }else{
            autoAugErr = true
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "an error occurred when ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl was executed! The AutoAug.err file was not created.")            
        }
        if (exitCode != 0) {
            sgeErr = true
            String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "cleanupJob failed. A ${computeClusterName} error occurred!")
        }
        else {
            if(new File(projectDir, "augtrain.sh.e${jobID}").exists()){
                sgeErr = new File(projectDir, "augtrain.sh.e${jobID}").size() > 0
                if (sgeErr) {
                    String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
                    Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "A ${computeClusterName} error occurred!")
                }
            }else{
                sgeErr = true
                Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "sgeErr file was not created.")
            }
        }
        if (!sgeErr && (JobExecution.JobStatus.TIMEOUT.equals(jobStatus) || JobExecution.JobStatus.ERROR.equals(jobStatus)) ) {
            sgeErr = true
            String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "A ${computeClusterName} error occurred! jobStatus=${jobStatus}")
        }
        if(new File(projectDir, "writeResults.err").exists()){
            writeResultsErr = new File(projectDir, "writeResults.err").size() > 0
            if (writeResultsErr) {
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "an error occurred during writing results!");
            }
        }else{
            writeResultsErr = true
            Utilities.log(getLogFile(), 1, getLogLevel(), "SEVERE", trainingInstance.accession_id, "writeResults.err file was not created.")
        }

        if (!autoAugErr && !sgeErr && !writeResultsErr) {
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "no errors occurred (option 1).")

            String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} finished.\n\n"
            trainingInstance.message += "----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n"
            trainingInstance.message += mailStr
            trainingInstance.message += "Results of your job are deleted from our server after 180 days.\n\n"
            trainingInstance.job_status = 4
            trainingInstance.save(flush: true)
            
            if(trainingInstance.email_adress == null){
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Computation was successful. Did not send e-mail to user because no e-mail address was supplied.")
            }
            else {
                def msgStr = "${mailStr}You find the results at "
                msgStr += "${getHttpBaseURL()}show/${trainingInstance.id}.\n\n"
                msgStr += "Results of your job are deleted from our server after 180 days.\n\n"
                sendMailToUser(trainingInstance, "AUGUSTUS training job ${trainingInstance.accession_id} is complete", msgStr)
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
            }
            
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job completed. Result: ok.")
        }else{
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "an error occurred somewhere: autoAugErr=${autoAugErr}, sgeErr=${sgeErr}, writeResultsErr=${writeResultsErr}, jobStatus=${jobStatus}")
            String admin_email = getAdminEmailAddress()
            String msgStr = "Hi ${admin_email}!\n\nJob: ${trainingInstance.accession_id}\n"
            msgStr += "Link: ${getHttpBaseURL()}show/${trainingInstance.id}\n\n"
            if (autoAugErr) {
                msgStr += "An error occurred in the autoAug pipeline. "
            }
            if (sgeErr) {
                String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
                msgStr += "A ${computeClusterName} error occurred. "
            }
            if (writeResultsErr) {
                msgStr += "An error occurred during writing results. "
            }
            msgStr += "Please check manually what's wrong.\n"
            msgStr += "The job status is ${jobStatus}\n"
            msgStr += "The user has "
            if (trainingInstance.email_adress == null) {
                msgStr += "not "
            }
            msgStr += "been informed."
            sendMailToAdmin("Error in AUGUSTUS training job ${trainingInstance.accession_id}", msgStr)
               
            if (autoAugErr) {
                trainingInstance.job_error = 5
                trainingInstance.job_status = 4
                
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job status is ${trainingInstance.job_error} when autoAug error occurred.")
            }
            if (sgeErr) {
                trainingInstance.job_error = 5
                trainingInstance.job_status = 4
                
                String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job status is ${trainingInstance.job_error} when ${computeClusterName} error occurred.")
            }
            if (writeResultsErr) {
                trainingInstance.job_status = 4
            }
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Job error status is ${trainingInstance.job_error} after all errors have been checked.")
            
            String mailStr = ""
            if (JobExecution.JobStatus.TIMEOUT.equals(jobStatus)) {
                mailStr += "The AUGUSTUS training job ${trainingInstance.accession_id} was cancelled, the maximum computation time has been reached.\n"
                mailStr += "Maybe you can start a new training job with a smaller training set.\n\n"
            }
            else {
                mailStr += "An error occurred while running the AUGUSTUS training job ${trainingInstance.accession_id}.\n\n"
                mailStr += "Please check the log-files carefully before proceeding to work with the produced results.\n\n"
            }
            trainingInstance.message += "----------------------------------------------\n${new Date()} - Error Message:\n"
            trainingInstance.message += "----------------------------------------------\n\n"
            trainingInstance.message += mailStr
            if (!JobExecution.JobStatus.TIMEOUT.equals(jobStatus)) {
                trainingInstance.message += "Results of your job are deleted from our server after 180 days.\n\n"
            }
            trainingInstance.save(flush: true)
            
            if(trainingInstance.email_adress == null){
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "The job is in an error state. Could not send e-mail to anonymous user because no email address was supplied.")
            }else{
                String senderAdress = TrainingService.getWebaugustusEmailAddress()
                msgStr = "${mailStr}You find the results of your job at ${getHttpBaseURL()}/show/${trainingInstance.id}.\n\n"
                msgStr += "The administrator of the AUGUSTUS web server has been informed.\n"
                if (!JobExecution.JobStatus.TIMEOUT.equals(jobStatus)) {
                    msgStr += "Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
                    msgStr += "Results of your job are deleted from our server after 180 days.\n\n"
                }
                sendMailToUser(trainingInstance, "An error occurred while executing AUGUSTUS training job ${trainingInstance.accession_id}", msgStr)
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
            }
        }
        // pack project directory and delete it
        if ((!sgeErr && !writeResultsErr) || autoAugErr) {
            if (projectDir.exists()) {
                def cmd = ["cd ${getOutputDir()}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"]
                Utilities.execute(getLogFile(), 2, trainingInstance.accession_id, "packResults", cmd)
                Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "autoAug directory was packed with tar/gz.")
            }
            deleteDir(trainingInstance)
        }
    }
    
    /**
     * delete the email address after the job is done or aborted
     */
    @Transactional
    protected void deleteEmailAddressAfterJobEnd(AbstractWebAugustusDomainClass instance) {
        Training trainingInstance = (Training) instance
        if (trainingInstance.email_adress != null && 
            ("4".equals(trainingInstance.job_status) || "5".equals(trainingInstance.job_status) || "6".equals(trainingInstance.job_status))) {
            
            trainingInstance.email_adress = null
            trainingInstance.save(flush: true)
            Utilities.log(getLogFile(), 1, getLogLevel(), trainingInstance.accession_id, "delete email address of user")
        }
    }
}
