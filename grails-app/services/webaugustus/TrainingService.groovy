package webaugustus

import grails.gorm.transactions.Transactional
import javax.annotation.PostConstruct
import webaugustus.AbstractWebAugustusDomainClass

/**
 * The class TrainingService controls everything that is related to a job for training AUGUSTUS through the webserver:
 *    - it handles the file upload by wget
 *    - format check
 *    - SGE job submission and status checks
 *    - rendering of results/job status page
 *    - sending E-Mails concerning the job status (downloaded files, errors, finished)
 */
@Transactional
class TrainingService extends AbstractWebaugustusService {

    // need to adjust the output dir to whatever working dir! This is where uploaded files and results will be saved.
    private static final String output_dir =     "/data/www/webaugustus/webdata/augtrain" // adapt to the actual situation // should be something in home of webserver user and augustus frontend user.
    private static final String web_output_dir = "/data/www/webaugustus/training-results" // adapt to the actual situation // must be writable to webserver application
    // web-output - directory to the results that are downloadable by end users
    private static final String web_output_url = "http://webaugustus.uni-greifswald.de/training-results/" // adapt to the actual situation
    private static final String war_url =        "http://webaugustus.uni-greifswald.de/webaugustus/"      // adapt to the actual situation
    
    // this log File contains the "process log", what was happening with which job when.
    private static final File logFile = new File("${output_dir}/train.log")
    private static final int verb = 3 // 1 only basic log messages, 2 all issued commands, 3 also script content
    
    public void sendMailToUser(Training trainingInstance, String subjectString, String message) {
        sendMailToUser(trainingInstance.email_adress, subjectString, message)
    }
    
    @PostConstruct
    def init() {
        Utilities.log(logFile, 1, verb, "startup      ", "TrainingService")
        startWorkerThread()
    }

    public String getOutputDir() {
        return TrainingService.output_dir
    }
    
    public String getWebOutputDir() {
        return TrainingService.web_output_dir
    }
    
    public String getWebOutputURL() {
        return TrainingService.web_output_url
    }
    
    public String getWarURL() {
        return TrainingService.war_url
    }
    
    public File getLogFile() {
        return TrainingService.logFile
    }
    
    public int getVerboseLevel() {
        return TrainingService.verb
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
        String dirName = "${output_dir}/${trainingInstance.accession_id}"
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Project directory ${dirName} is deleted")
        def cmd = ["rm -r ${dirName} &> /dev/null"]
        Utilities.execute(logFile, 2, trainingInstance.accession_id, "removeProjectDir", cmd)
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
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} by anonymous user is aborted!")
        }
        else {
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${trainingInstance.accession_id} is aborted!")
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
        Training trainingInstance = (Training) instance
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "load and start job ")
        
        String dirName = "${output_dir}/${trainingInstance.accession_id}"
        File projectDir = new File(dirName)
        
        boolean estExistsFlag = false
        boolean proteinExistsFlag = false
        boolean structureExistsFlag = false
        
        // retrieve genome file
        if (trainingInstance.genome_ftp_link != null) {
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
            Long nSeqNumber = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "nSeqFile", cmd)
            int maxNSeqs = getMaxNSeqs()
            if(nSeqNumber == null || nSeqNumber > maxNSeqs){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.");
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name} was aborted\nbecause the provided genome file\n${trainingInstance.genome_ftp_link}\ncontains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds). This is not allowed.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            
            // check for fasta format & get seq names for gff validation:
            def metacharacterFlag = 0
            def genomeFastaFlag = 0 
            new File(projectDir, "genome.fa").eachLine{line ->
                if(line =~ /\*/ || line =~ /\?/){
                    metacharacterFlag = 1
                }else{
                    if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){ genomeFastaFlag = 1 }
                }
            }
            if(metacharacterFlag == 1){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name} was aborted\nbecause the provided genome file\n${trainingInstance.genome_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            if(genomeFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The genome file was not fasta. ${dirName} is deleted.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided genome file\n${trainingInstance.genome_ftp_link}\nwas not in DNA fasta format.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }

            // check gff format
            def gffColErrorFlag = 0
            def gffNameErrorFlag = 0
            def structureGbkFlag = 0
            File structFile = new File(projectDir, "training-gene-structure.gff")
            structureExistsFlag = structFile.exists()
            if (structureExistsFlag) {
                // gff format validation: number of columns 9, + or - in column 7, column 1 has to be member of seqNames
                Utilities.log(logFile, 2, verb, trainingInstance.accession_id, "Checking training-gene-structure.gff file format")
                metacharacterFlag = 0
                structFile.eachLine{line ->
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
                    String cmdStr = "bash ${dirName}/checkGff.sh"
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, cmdStr)
                    def ckStr = "${cmdStr}".execute()
                    ckStr.waitFor()
                    def ckContent = new File("${gffChkOutFile}").text
                    def stContent = new Scanner(ckContent)
                    long gffErrorStatus = stContent.nextLong();
                    ckContent = new File("${gffChkColsFile}").text
                    stContent = new Scanner(ckContent)
                    long gffColStatus = stContent.nextLong()
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
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided gene structure file contains metacharacters (e.g. * or ?).\nThis is not allowed.\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
                if(gffColErrorFlag == 1 && structureGbkFlag == 0){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Training gene structure file does not always contain 9 columns.")
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided training gene structure file\n${trainingInstance.struct_file}\ndid not contain 9 columns in each line.\nPlease make sure the gff-format complies with the instructions in our 'Help' section before\nsubmitting another job!\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
                if(gffNameErrorFlag == 1 && structureGbkFlag == 0){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Training gene structure file contains entries that do not comply with genome sequence names.")
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the sequence names in the provided training gene structure file\n${trainingInstance.struct_file}\ndid not comply with the sequence names in the supplied genome file\n${trainingInstance.genome_ftp_link}.\nPlease make sure the gff-format complies with the instructions in our 'Help' section\nbefore submitting another job!\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
            }
            cmd = ["cksum ${dirName}/genome.fa"]
            trainingInstance.genome_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.genome_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ")
        } // end of if(!(trainingInstance.genome_ftp_link == null))

        // retrieve EST file
        if (trainingInstance.est_ftp_link != null) {

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
            def estFastaFlag = 0
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
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided cDNA file\n${trainingInstance.est_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            if(estFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The EST/cDNA file was not fasta. ${dirName} is deleted.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided cDNA file\n${trainingInstance.est_ftp_link}\nwas not in DNA fasta format.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }

            cmd = ["cksum ${dirName}/est.fa"]
            trainingInstance.est_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.est_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "est.fa is ${trainingInstance.est_size} big and has a cksum of ${trainingInstance.est_cksum}.")
        } // end of if(!(trainingInstance.est_ftp_link == null))

        // check whether EST file is NOT RNAseq, i.e. does not contain on average very short entries
        int nEntries = 0
        int totalLen = 0
        File estFile = new File(projectDir, "est.fa")
        estExistsFlag = estFile.exists()
        if (estExistsFlag) {
            estFile.eachLine{line ->
                if(line =~ /^>/){
                    nEntries = nEntries + 1
                }else{
                    totalLen = totalLen + line.size()
                }
            }
            if (nEntries == 0) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST sequence file is not in fasta format. It doesn't contain \">\" characters.")
                String mailStr = "Your AUGUSTUS training job ${predictionInstance.accession_id} was aborted because your\ncDNA file is not in fasta format. \n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            def avEstLen = totalLen/nEntries
            def estMinLen = getEstMinLen()
            def estMaxLen = getEstMaxLen()
            if(avEstLen < estMinLen){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequences files\nwith an average sequence length shorter than ${estMinLen} might\ncontain RNAseq raw sequences. Currently, our web server application does not support\nthe integration of RNAseq raw sequences. Please either assemble\nyour sequences into longer contigs, or remove short sequences from your current file,\nor submit a new job without specifying a cDNA file.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }else if(avEstLen > estMaxLen){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted because\nthe sequences in your cDNA file have an average length of ${avEstLen}.\nWe suspect that sequences files with an average sequence length longer than ${estMaxLen}\nmight not contain ESTs or cDNAs. Please either remove long sequences from your\ncurrent file, or submit a new job without specifying a cDNA file.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
        }

        // retrieve protein file
        if (trainingInstance.protein_ftp_link != null) {

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
            def proteinFastaFlag = 0
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
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                abortJob(trainingInstance, mailStr)
                return
            }
            if (allAminoAcidsCounter > 0) {
                def cRatio = cytosinCounter/allAminoAcidsCounter
                if (cRatio >= 0.05){
                    Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence).")
                    String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\nis suspected to contain DNA instead of protein sequences.\n\n"
                    abortJob(trainingInstance, mailStr)
                    return
                }
            }
            if(allAminoAcidsCounter == 0 || proteinFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The protein file was not protein fasta.")
                String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} for species\n${trainingInstance.project_name}\nwas aborted because the provided protein file\n${trainingInstance.protein_ftp_link}\nis not in fasta format.\n\n"
                abortJob(trainingInstance, mailStr)
            }

            proteinExistsFlag = true

            cmd = ["cksum ${dirName}/protein.fa"]
            trainingInstance.protein_cksum = Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "(\\d*) \\d* ")
            trainingInstance.protein_size =  Utilities.executeForLong(logFile, verb, trainingInstance.accession_id, "proteinCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${trainingInstance.protein_cksum}.")
        } // end of (!(trainingInstance.protein_ftp_link == null))

        // confirm file upload via e-mail
        if (trainingInstance.genome_ftp_link != null || trainingInstance.protein_ftp_link != null || trainingInstance.est_ftp_link != null) {
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Retrieved all ftp files successfully.")
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
                accession_id != trainingInstance.accession_id // not itself
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
            trainingInstance.old_url = "${war_url}training/show/${oldID}"
            trainingInstance.save(flush: true)
            
            mailStr += "The old job with identical input files and identical parameters "
            mailStr += "is available at\n${war_url}training/show/${oldID}.\n\n"
            
            sendMailToUser(trainingInstance, "AUGUSTUS training job ${trainingInstance.accession_id} was submitted before as job ${oldAccContent}", mailStr)
            
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Data are identical to old job ${oldAccContent} with Accession-ID ${oldID}. ${dirName} is deleted.")
            abortJob(trainingInstance, null, "6")
            
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Web output directory is deleted")
            def cmd = ["rm -r ${web_output_dir}/${trainingInstance.accession_id} &> /dev/null"]
            Utilities.execute(logFile, 2, trainingInstance.accession_id, "removeWeb_output_dir", cmd)
            
            return
        } // end of job was submitted before check

        //Create a sge script:
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Writing SGE submission script.")
        File sgeFile = new File(projectDir, "augtrain.sh")
        // write command in script (according to uploaded files)
        sgeFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
        String cmdStr = "export AUGUSTUS_CONFIG_PATH=${AUGUSTUS_CONFIG_PATH} && ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl --genome=${dirName}/genome.fa --species=${trainingInstance.accession_id} "
        // this has been checked, works.
        if (estExistsFlag && !proteinExistsFlag && !structureExistsFlag) {
            cmdStr += "--cdna=${dirName}/est.fa --pasa --useGMAPforPASA -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
            // this is currently tested
        }else if (!estExistsFlag && !proteinExistsFlag && structureExistsFlag) {
            cmdStr += "--trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
            // this is currently tested
        }else if (!estExistsFlag && proteinExistsFlag && !structureExistsFlag) {
            cmdStr += "--trainingset=${dirName}/protein.fa -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
            // all following commands still need testing
        }else if (estExistsFlag && proteinExistsFlag && !structureExistsFlag) {
            cmdStr += "--cdna=${dirName}/est.fa --trainingset=${dirName}/protein.fa -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
        }else if (estExistsFlag && !proteinExistsFlag && structureExistsFlag) {
            cmdStr += "--cdna=${dirName}/est.fa --trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
        }else if(proteinExistsFlag && structureExistsFlag) {
            cmdStr = "echo 'Simultaneous protein and structure file support are currently not implemented. Using the structure file, only.'\n\n${cmdStr}"
            cmdStr += "--trainingset=${dirName}/training-gene-structure.gff -v --singleCPU --workingdir=${dirName} > ${dirName}/AutoAug.log 2> ${dirName}/AutoAug.err\n\n"
        }else{
            cmdStr = null
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "EST: ${estExistsFlag} Protein: ${proteinExistsFlag} Structure: ${structureExistsFlag} SGE-script remains empty! This an error that should not be possible.")
        }
        if (cmdStr != null) {
            cmdStr += "${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} '${trainingInstance.dateCreated}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${dirName}/writeResults.log 2> ${dirName}/writeResults.err"
            sgeFile << "${cmdStr}"
            Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile << \"${cmdStr}\"")
        }
        Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "sgeFile=${cmdStr}")
        // write submission script
        File submissionScript = new File(projectDir, "submit.sh")
        String fileID = "${dirName}/jobID"
        cmdStr = "cd ${dirName}; qsub augtrain.sh > ${fileID} 2> /dev/null"
        submissionScript << "${cmdStr}"
        Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "submissionScript << \"${cmdStr}\"")
        // submit job
        cmdStr = "bash ${dirName}/submit.sh"
        def jobSubmission = "${cmdStr}".execute()
        Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
        jobSubmission.waitFor()
        // get job ID
        def content = new File(fileID).text
        if (content.isEmpty()) {
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The augustus training job wasn't started")
            trainingInstance.results_urls = null
            trainingInstance.job_status = 5
            trainingInstance.save(flush: true)
            return
        }

        def jobID_array = content =~/Your job (\d*)/
        def jobID
        (1..jobID_array.groupCount()).each{jobID = "${jobID_array[0][it]}"}
        trainingInstance.job_id = jobID
        trainingInstance.job_status = 1 // submitted
        trainingInstance.save(flush: true)
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} submitted.")
    }
    
    /**
     * Check if the augustus job is still running and set the job_status accordingly
     * 
     * @returns true if the job is done
     * 
     */
    @Transactional
    protected boolean checkJobReadyness(AbstractWebAugustusDomainClass instance) {
        Training trainingInstance = (Training) instance
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "checking job SGE status...")
        
        String jobID = trainingInstance.job_id
        def cmd = ['qstat -u "*" | grep augtrain | grep ' + "' ${jobID} '"]
        def statusContent = Utilities.executeForString(logFile, verb, trainingInstance.accession_id, "statusScript", cmd)

        if (statusContent == null) {
            return false
        }
        else if (statusContent =~ /qw/) {
            trainingInstance.job_status = 2
        }
        else if ( statusContent =~ /  r  / ) {
            if (trainingInstance.job_status != "3") {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} begins running at ${new Date()}.")
            }
            trainingInstance.job_status = 3
        }
        else if (!statusContent.empty) {
            trainingInstance.job_status = 3
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} is neither in qw nor in r status but is still on the grid!")
        }
        else {
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job ${jobID} left SGE at ${new Date()}.")
            return true
        }
        trainingInstance.save(flush: true)
        return false
    }
    
    /**
     * Do all tasks needed to process the job data and cleanup
     */
    @Transactional
    protected void finishJob(AbstractWebAugustusDomainClass instance) {
        Training trainingInstance = (Training) instance
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "finishJob")
        
        String jobID = trainingInstance.job_id
        String dirName = "${output_dir}/${trainingInstance.accession_id}"
        File projectDir = new File(dirName)
        
        // set file rigths to readable by others
        Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "set file permissions on ${web_output_dir}/${trainingInstance.accession_id}")
        def webOutputDir = new File(web_output_dir, trainingInstance.accession_id)
        if (webOutputDir.exists()) {
            webOutputDir.setReadable(true, false)
            webOutputDir.setExecutable(true, false);
            webOutputDir.eachFile { file -> file.setReadable(true, false) }
        }
        // collect results link information
        String autoAugLogPath = "${web_output_dir}/${trainingInstance.accession_id}/AutoAug.log"
        if(new File(autoAugLogPath).exists()){
            String autoAugLogURL = "<p><b>Log-file</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/AutoAug.log\">AutoAug.log</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = autoAugLogURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${autoAugLogURL}"
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${autoAugLogPath} does exist and is linked.")
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${autoAugLogPath} is missing!")
        }
        String autoAugErrPath = "${web_output_dir}/${trainingInstance.accession_id}/AutoAug.err"
        if(new File(autoAugErrPath).exists()){
            String autoAugErrURL = "<p><b>Error-file</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/AutoAug.err\">AutoAug.err</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = autoAugErrURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${autoAugErrURL}"
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${autoAugErrPath} does exist and is linked.")
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${autoAugErrPath} is missing!")
        }
        String parameterGzPath = "${web_output_dir}/${trainingInstance.accession_id}/parameters.tar.gz"
        if(new File(parameterGzPath).exists()){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${parameterGzPath} does exist and is linked.")
            String parameterGzURL = "<p><b>Species parameter archive</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/parameters.tar.gz\">parameters.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = parameterGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${parameterGzURL}"
            }
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${parameterGzPath} is missing!")
        }
        String trainingGzPath = "${web_output_dir}/${trainingInstance.accession_id}/training.gb.gz"
        if(new File(trainingGzPath).exists()){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${trainingGzPath} exists and is linked.")
            String trainingGzURL = "<p><b>Training genes</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/training.gb.gz\">training.gb.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = trainingGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${trainingGzURL}"
            }
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${trainingGzPath} is missing!")
        }
        String abInitioGzPath = "${web_output_dir}/${trainingInstance.accession_id}/ab_initio.tar.gz"
        if(new File(abInitioGzPath).exists()){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${abInitioGzPath} exists and is linked.")
            String abInitioGzURL = "<p><b>Ab initio predictions</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/ab_initio.tar.gz\">ab_initio.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = abInitioGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${abInitioGzURL}"
            }
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${abInitioGzPath} is missing.")
        }
        String hintPredGzPath = "${web_output_dir}/${trainingInstance.accession_id}/hints_pred.tar.gz"
        if(new File(hintPredGzPath).exists()){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${hintPredGzPath} exists and is linked.")
            String hintPredGzURL = "<p><b>predictions with hints</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/hints_pred.tar.gz\">hints_pred.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = hintPredGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${hintPredGzURL}"
            }
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${hintPredGzPath} is missing!")
        }
        String hintUtrPredGzPath = "${web_output_dir}/${trainingInstance.accession_id}/hints_utr_pred.tar.gz"
        if(new File(hintUtrPredGzPath).exists()){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${hintUtrPredGzPath} exists and is linked.")
            String hintUtrPredGzURL = "<p><b>predictions with hints and UTRs</b>&nbsp;&nbsp;<a href=\"${web_output_url}${trainingInstance.accession_id}/hints_utr_pred.tar.gz\">hints_utr_pred.tar.gz</a><br></p>"
            if(trainingInstance.results_urls == null){
                trainingInstance.results_urls = hintUtrPredGzURL
            }else{
                trainingInstance.results_urls = "${trainingInstance.results_urls}${hintUtrPredGzURL}"
            }
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "${hintUtrPredGzPath} is missing!")
        }
        
        // check whether errors occured by log-file-sizes
        Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Beginning to look for errors.")
        def autoAugErrSize = 10 // default: error
        def sgeErrSize = 10 // default: error
        def writeResultsErrSize = 10 // default: error
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

        String admin_email = getAdminEmailAdress()
        String footer = getEmailFooter()
        
        if(autoAugErrSize==0 && sgeErrSize==0 && writeResultsErrSize==0){
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "no errors occured (option 1).")

            String mailStr = "Your AUGUSTUS training job ${trainingInstance.accession_id} finished.\n\n"
            trainingInstance.message = "${trainingInstance.message}----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n${mailStr}"
            trainingInstance.job_status = 4
            trainingInstance.save(flush: true)
            
            if(trainingInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Computation was successful. Did not send e-mail to user because not e-mail adress was supplied.")
            }
            else {
                def msgStr = "${mailStr}You find the results at "
                msgStr += "${war_url}training/show/${trainingInstance.id}.\n\n"
                sendMailToUser(trainingInstance, "AUGUSTUS training job ${trainingInstance.accession_id} is complete", msgStr)
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
            }
            
            def packResults = new File("${output_dir}/pack${trainingInstance.accession_id}.sh")
            String cmdStr = "cd ${output_dir}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"
            packResults << "${cmdStr}"
            Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "packResults << \"${cmdStr}\"")
            //packResults << "cd ${output_dir}; tar cf - ${trainingInstance.accession_id} | 7z a -si ${trainingInstance.accession_id}.tar.7z; rm -r ${trainingInstance.accession_id};"
            cmdStr = "bash ${output_dir}/pack${trainingInstance.accession_id}.sh"
            def cleanUp = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
            cleanUp.waitFor()
            cmdStr = "rm ${output_dir}/pack${trainingInstance.accession_id}.sh &> /dev/null"
            cleanUp = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
            cleanUp.waitFor()
            deleteDir(trainingInstance)
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "autoAug directory was packed with tar/gz.")
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job completed. Result: ok.")
        }else{
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "an error occured somewhere.")
            String msgStr = "Hi ${admin_email}!\n\nJob: ${trainingInstance.accession_id}\n"
            if (trainingInstance.email_adress != null) {
                msgStr += "E-Mail: ${trainingInstance.email_adress}\n"
            }
            msgStr += "Link: ${war_url}training/show/${trainingInstance.id}\n\n"
            if (autoAugErrSize != 0) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "an error occured when ${AUGUSTUS_SCRIPTS_PATH}/autoAug.pl was executed!");
                msgStr += "An error occured in the autoAug pipeline. "
            }
            if (sgeErrSize != 0) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "a SGE error occured!");
                msgStr += "An SGE error occured. "
            }
            if (writeResultsErrSize != 0) {
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "an error occured during writing results!");
                msgStr += "An error occured during writing results. "
            }
            msgStr += "Please check manually what's wrong.\n"
            msgStr += "The user has "
            if (trainingInstance.email_adress == null) {
                msgStr += "not "
            }
            msgStr += "been informed."
            sendMail {
                to "${admin_email}"
                subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
                text """${msgStr}${footer}"""
            }
               
            if (autoAugErrSize != 0) {
                trainingInstance.job_error = 5
                trainingInstance.job_status = 4
                
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job status is ${trainingInstance.job_error} when autoAug error occured.")
                def packResults = new File("${output_dir}/pack${trainingInstance.accession_id}.sh")
                String cmdStr = "cd ${output_dir}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"
                packResults << "${cmdStr}"
                Utilities.log(logFile, 3, verb, trainingInstance.accession_id, "packResults << \"${cmdStr}\"")
                //packResults << "cd ${output_dir}; tar cf - ${trainingInstance.accession_id} | 7z a -si ${trainingInstance.accession_id}.tar.7z; rm -r ${trainingInstance.accession_id};"
                cmdStr = "bash ${output_dir}/pack${trainingInstance.accession_id}.sh"
                def cleanUp = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                cleanUp.waitFor()
                cmdStr = "rm ${output_dir}/pack${trainingInstance.accession_id}.sh &> /dev/null"
                cleanUp = "${cmdStr}".execute()
                Utilities.log(logFile, 2, verb, trainingInstance.accession_id, cmdStr)
                cleanUp.waitFor()
                deleteDir(trainingInstance)
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "autoAug directory was packed with tar/gz.")
            }
            if (sgeErrSize != 0) {
                trainingInstance.job_error = 5
                trainingInstance.job_status = 4
                
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job status is ${trainingInstance.job_error} when SGE error occured.")
            }
            if (writeResultsErrSize != 0) {
                trainingInstance.job_status = 4
            }
            Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Job error status is ${trainingInstance.job_error} after all errors have been checked.")
            String mailStr = "An error occured while running the AUGUSTUS training job ${trainingInstance.accession_id}.\n\nPlease check the log-files carefully before proceeding to work with the produced results.\n\n"
            trainingInstance.message = "${trainingInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${mailStr}"
            trainingInstance.save(flush: true)
            
            if(trainingInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "The job is in an error state. Cound not send e-mail to anonymous user because no email adress was supplied.")
            }else{
                msgStr = "${mailStr}You find the results of your job at ${war_url}training/show/${trainingInstance.id}.\n\n"
                msgStr += "The administrator of the AUGUSTUS web server has been informed and "
                msgStr += "will get back to you as soon as the problem is solved.\n\n"
                sendMailToUser(trainingInstance, "An error occured while executing AUGUSTUS training job ${trainingInstance.accession_id}", msgStr)
                Utilities.log(logFile, 1, verb, trainingInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
            }
        }
    }
}
