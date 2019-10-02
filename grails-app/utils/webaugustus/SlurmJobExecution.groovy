package webaugustus

/**
 * Class to start a job and return status messages on a Slurm server.
 * 
 * If slurm isn't run locally there are some restrictions:
 * - the script and config path of the local augustus installation has to be the same as in the singularity image
 *    see AbstractWebaugustusService.AUGUSTUS_CONFIG_PATH
 *    see AbstractWebaugustusService.AUGUSTUS_SCRIPTS_PATH
 *    actually needed is no complete locally augustus installation but 
 *    - in the config path just a "species" folder containing the species for augustus and
 *    - in the script path just the files 
 *      - writeResultsPage.pl, webserver-results.head and webserver-results.tail
 *      - checkParamArchive.pl
 *      - findGffNamesInFasta.pl
 *      - moveParameters.pl
 * - the paths in 
 *     - PredictionService.output_dir 
 *     - PredictionService.web_output_dir
 *     - TrainingService.output_dir
 *     - TrainingService.web_output_dir
 *   should appear in submit file templates 
 *     - SLURM_SBATCH_PREDICTION_TEMPLATE
 *     - SLURM_SBATCH_TRAINING_TEMPLATE
 *   and this template file should contain a "PLACEHOLDER" string in the data paths to be replaced by the accession_id
 */
class SlurmJobExecution extends webaugustus.JobExecution {
    
    /** the user using slurm */
    protected final static String SLURM_USER_NAME = "xxx" // adapt to the actual situation
    /** the server running slurm. set to null if it is the same server as the webserver where this application runs */
    protected final static String SLURM_SERVER_NAME = "xxx.yyy.zzz" // adapt to the actual situation
    /** the prediction sbatch template */
    protected final static String SLURM_PREDICTION_SUBMIT_FILE_TEMPLATE_PATH = "webaugustus/augpred_slurm_submit_script_template.sh"
    /** the training sbatch template */
    protected final static String SLURM_TRAINING_SUBMIT_FILE_TEMPLATE_PATH = "webaugustus/augtrain_slurm_submit_script_template.sh"
    
    protected final static String SLURM_PREDICTION_SUBMIT_FILENAME = "augpred_slurm_submit_script.sh"
    protected final static String SLURM_TRAINING_SUBMIT_FILENAME = "augtrain_slurm_submit_script.sh"
    
    protected final static String SLURM_SPECIES_DIR = "webaugustus/species"
    
    private final static String SSH_CONFIGURATION_FILE = "-i /path/to/.ssh/id_rsa"

    private static boolean isSlurmLocal() {
        return SLURM_SERVER_NAME == null || SLURM_SERVER_NAME.isEmpty()
    }
    
    private static String getAsSSHCommand(String command) {
        if (isSlurmLocal()) {
            return command
        }
        return "ssh ${SSH_CONFIGURATION_FILE} ${SLURM_USER_NAME}@${SLURM_SERVER_NAME} '${command}'"
    }
    
    /**
     * Get the name for the computing cluster.
     * 
     * @return 
     */
    public String getName() {
        return "SLURM       "
    }
    
    /**
     * Count jobs submitted to the worker queue.
     * 
     * @return the job number of queued and running jobs or -1 if an exception occured
     */
    public int countStartedJobs(File logFile, int maxLogLevel) {
        def processForLog = "SLURM       "
        def cmd = [getAsSSHCommand("module load slurm; squeue -u ${SLURM_USER_NAME} | wc -l")]
        Integer count = Utilities.executeForInteger(logFile, maxLogLevel, processForLog, "qstatScript", cmd)
        
        if (count == null) {
            return -1
        }
        if (count > 0) {
            count-- // remove header line
        }
        Utilities.log(logFile, 3, maxLogLevel, processForLog, "countStartedJobs=${count}")
        return count
    }

	// TODO: $ ssh root@server 'bash -s' < script.sh
    
    /**
     * Start a job identified by the specified script.
     *
     * @param parentPath parent path of the script
     * @param scriptName file name of the script
     * @param jobType is it a prediction or training job
     * 
     * @return the job identifier or null if the jobs wasn't started
     */
    public String startJob(String parentPath, String scriptName, JobType jobType, File logFile, int maxLogLevel, String processName) {
        String parentFolderName = new File(parentPath).getName()
        String serverDataPath = parentPath
            
        if (!isSlurmLocal()) {
            
            // copy job data to slurm server
            if (parentPath.startsWith("/")) {
                serverDataPath = parentPath.substring(1)
            }
            
            def cmd = ["rsync -av -e 'ssh ${SSH_CONFIGURATION_FILE}' ${parentPath}/ ${SLURM_USER_NAME}@${SLURM_SERVER_NAME}:${serverDataPath}/"]
            Utilities.execute(logFile, maxLogLevel, processName, "copyDataToSlurmServer", cmd)
            Utilities.log(logFile, 1, maxLogLevel, processName, "copied data from ${parentPath} to ${serverDataPath} on ${SLURM_SERVER_NAME}")
            
            // copy the list of species to slurm server
            String AUGUSTUS_SPECIES_PATH = AbstractWebaugustusService.getAugustusSpeciesPath()
            cmd = ["rsync -auv -e 'ssh ${SSH_CONFIGURATION_FILE}' ${AUGUSTUS_SPECIES_PATH}/ ${SLURM_USER_NAME}@${SLURM_SERVER_NAME}:${SLURM_SPECIES_DIR}/"]
            Utilities.execute(logFile, maxLogLevel, processName, "copySpeciesToSlurmServer", cmd)
            Utilities.log(logFile, 1, maxLogLevel, processName, "copied species from ${AUGUSTUS_SPECIES_PATH} to ${SLURM_SPECIES_DIR} on ${SLURM_SERVER_NAME}")
        }
        
        // copy the slurm submit file and replace PLACEHOLDER 
        String submitFileTemplatePath
        String submitFileName
        if (JobType.PREDICTION.equals(jobType)) {
            submitFileTemplatePath = SLURM_PREDICTION_SUBMIT_FILE_TEMPLATE_PATH
            submitFileName = SLURM_PREDICTION_SUBMIT_FILENAME
        }
        else if (JobType.TRAINING.equals(jobType)) {
            submitFileTemplatePath = SLURM_TRAINING_SUBMIT_FILE_TEMPLATE_PATH
            submitFileName = SLURM_TRAINING_SUBMIT_FILENAME
        }
        else {
            Utilities.log(logFile, 1, maxLogLevel, processName, "Could not start a job: unrecognized jobType ${jobType}")
            return "0"
        }

        def cmd = [getAsSSHCommand("cat ${submitFileTemplatePath} | sed \"s#PLACEHOLDER#${parentFolderName}#g\" > ${serverDataPath}/${submitFileName}")]
        int exitCode = Utilities.execute(logFile, maxLogLevel, processName, "handleSubmitFile", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "copied and adpated slurm submit file ${submitFileName} on ${SLURM_SERVER_NAME} exitCode=${exitCode}")
        if (exitCode != 0) {
            return null
        }
        
        // start job
        cmd = [getAsSSHCommand("module load slurm; sbatch ${serverDataPath}/${submitFileName}")]
        Integer jobID = Utilities.executeForInteger(logFile, maxLogLevel, processName, "startJobScript", cmd, ".*\\s*job\\s*(\\d+).*")
        Utilities.log(logFile, 1, maxLogLevel, processName, "start SLURM job with ${scriptName} in ${parentPath} with jobID ${jobID}")
        if (jobID == null || jobID == 0) {
            return null
        }
        else {
            return ""+jobID
        }
    }
    
    /**
     * Returns the job status 
     *
     * @param jobIdentifier the job identifier - returned by method startJob
     * @return the job status (either WAITING_FOR_EXECUTION, COMPUTING or FINISHED) or null in case of an error
     */
    public JobExecution.JobStatus getJobStatus(String jobIdentifier, File logFile, int maxLogLevel, String processName) {
        Utilities.log(logFile, 1, maxLogLevel, processName, "checking slurm job status...")
        def cmd = [getAsSSHCommand("module load slurm; sacct -j ${jobIdentifier} -n --format=JobID,State")]
        def statusContent = Utilities.executeForString(logFile, maxLogLevel, processName, "statusScript", cmd)
        
        if (statusContent == null) {
            Utilities.log(logFile, 1, maxLogLevel, processName, "slurm job status -> null")
            return null
        }
        else if ( statusContent =~ /${jobIdentifier}/) {
            if ( (statusContent =~ / PD /) || (statusContent =~ /PENDING/) ) {
                return JobStatus.WAITING_FOR_EXECUTION
            }
            else if ( (statusContent =~ /  R /) || (statusContent =~ /RUNNING/) ) {
                return JobStatus.COMPUTING
            }
            else if ( (statusContent =~ /  CD /) || (statusContent =~ /COMPLETED/) 
                || (statusContent =~ /  TO /) || (statusContent =~ /TIMEOUT/) 
                || (statusContent =~ /  F /) || (statusContent =~ /FAILED/) 
                || (statusContent =~ /  CA /) || (statusContent =~ /CANCELLED/) ) {
                Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left slurm at ${new Date()}.")
                return JobStatus.FINISHED
            }
            else {
                Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left slurm at ${new Date()} for an unexpected reason: ${statusContent}.")
                return JobStatus.FINISHED
            }
        }
        return null
    }
    
    /**
     * Cleanup after a job is done.
     *
     * @param parentPath parent path of the executed script wich contains al results
     * @param jobType is it a prediction or training job
     * @return 0 if everything is ok else a status code from the executed command if available else 1
     */
    public int cleanupJob(String parentPath, AbstractWebaugustusService serviceInstance, JobType jobType, File logFile, int maxLogLevel, String processName) {
        if (isSlurmLocal()) {
            return // nothing to do
        }
        String parentFolderName = new File(parentPath).getName()
        String serverDataPath = parentPath
        if (parentPath.startsWith("/")) {
            serverDataPath = parentPath.substring(1)
        }
        
        // copy results into output_dir
        def cmd = ["rsync -auv -e 'ssh ${SSH_CONFIGURATION_FILE}' ${SLURM_USER_NAME}@${SLURM_SERVER_NAME}:${serverDataPath}/ ${parentPath}/"]
        Integer statusDataCopy = Utilities.execute(logFile, maxLogLevel, processName, "copyDataFromSlurmServer", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "copied data from ${serverDataPath} on ${SLURM_SERVER_NAME} to ${parentPath}, status=${statusDataCopy}")
        
        
        // copy results into web_output_dir
        String localWebPath = serviceInstance.getWebOutputDir()+ "/" + parentFolderName
        String serverWebPath = localWebPath
        if (localWebPath.startsWith("/")) {
            serverWebPath = localWebPath.substring(1)
        }
        cmd = ["rsync -auv -e 'ssh ${SSH_CONFIGURATION_FILE}' ${SLURM_USER_NAME}@${SLURM_SERVER_NAME}:${serverWebPath}/ ${localWebPath}/"]
        int statusWebCopy = Utilities.execute(logFile, maxLogLevel, processName, "copyDataFromSlurmServer", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "copied data from ${serverWebPath} on ${SLURM_SERVER_NAME} to ${localWebPath}, status=${statusWebCopy}")
        
        if (statusDataCopy != 0) {
            return statusDataCopy
        }
        if (statusWebCopy != 0) {
            return statusWebCopy
        }
        
        cmd = [getAsSSHCommand("rm -rf ${serverDataPath} ${serverWebPath}")]
        Utilities.execute(logFile, maxLogLevel, processName, "deleteDataFoldersOnSlurmServer", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "delete data on ${SLURM_SERVER_NAME}")
        
        if (JobType.TRAINING.equals(jobType)) {
            // copy the list of species from slurm server
            String AUGUSTUS_SPECIES_PATH = AbstractWebaugustusService.getAugustusSpeciesPath()
            cmd = ["rsync -auv -e 'ssh ${SSH_CONFIGURATION_FILE}' ${SLURM_USER_NAME}@${SLURM_SERVER_NAME}:${SLURM_SPECIES_DIR}/ ${AUGUSTUS_SPECIES_PATH}/"]
            Utilities.execute(logFile, maxLogLevel, processName, "copySpeciesToSlurmServer", cmd)
            Utilities.log(logFile, 1, maxLogLevel, processName, "copied species from ${SLURM_SPECIES_DIR} on ${SLURM_SERVER_NAME} to ${AUGUSTUS_SPECIES_PATH}")
        }
        
        return 0
        
    }
}