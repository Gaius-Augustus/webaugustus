package webaugustus

import grails.util.Holders

/**
 * Class to start a job and return status messages on a Slurm server.
 * 
 * If slurm isn't run locally there are some restrictions:
 * - the script and config path of the local augustus installation has to be the same as in the singularity image
 *    see AbstractWebaugustusService.getAugustusConfigPath()
 *    see AbstractWebaugustusService.getAugustusScriptPath()
 *    actually needed is no complete locally augustus installation but 
 *    - in the config path just a "species" folder containing the species for augustus and
 *    - in the script path just the files 
 *      - writeResultsPage.pl, webserver-results.head and webserver-results.tail
 *      - checkParamArchive.pl
 *      - findGffNamesInFasta.pl
 *      - moveParameters.pl
 * - the paths in 
 *     - PredictionService.getOutputDir() 
 *     - PredictionService.getWebOutputDir()
 *     - TrainingService.getOutputDir()
 *     - TrainingService.getWebOutputDir()
 *   should appear in submit file templates 
 *     - SLURM_SBATCH_PREDICTION_TEMPLATE
 *     - SLURM_SBATCH_TRAINING_TEMPLATE
 *   and this template file should contain a "PLACEHOLDER" string in the data paths to be replaced by the accession_id
 */
class SlurmJobExecution extends webaugustus.JobExecution {
    
    /** the user using slurm */
    private String getSlurmUserName() {
        return Holders.getConfig().getProperty('slurm.username', String)
    }
    
    /** the server running slurm. set to null if it is the same server as the webserver where this application runs */
    private String getSlurmHost() {
        return Holders.getConfig().getProperty('slurm.host', String)
    }
    
    /** the prediction sbatch template */
    private String getSlurmPredictionSubmitFilePath() {
        return Holders.getConfig().getProperty('slurm.path.prediction.submitScriptTemplate', String)
    }
    
    /** the training sbatch template */
    private String getSlurmTrainingSubmitFilePath() {
        return Holders.getConfig().getProperty('slurm.path.training.submitScriptTemplate', String)
    }
    
    private String getSlurmPredictionSubmitFileName() {
        return Holders.getConfig().getProperty('slurm.path.prediction.submitScript', String)
    }
    
    private String getSlurmTrainingSubmitFileName() {
        return Holders.getConfig().getProperty('slurm.path.training.submitScript', String)
    }
    
    private String getSlurmSpeciesDir() {
      return Holders.getConfig().getProperty('slurm.path.species', String)  
    }
    
    private String getSlurmSSHParam() {
        String file = Holders.getConfig().getProperty('slurm.path.sshkey', String);
        return (file == null || file.trim().length() == 0) ? "" : ("-i " + file)
    }

    private boolean isSlurmLocal() {
        return getSlurmHost() == null || getSlurmHost().isEmpty()
    }
    
    private String getAsSSHCommand(String command) {
        if (isSlurmLocal()) {
            return command
        }
        return "ssh ${getSlurmSSHParam()} ${getSlurmUserName()}@${getSlurmHost()} \"bash --login -c '${command}'\""
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
        def processForLog = getName()
        def cmd = [getAsSSHCommand("module load slurm; squeue -u ${getSlurmUserName()} | wc -l")]
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

    /**
     * Start a job identified by the specified script.
     *
     * @param parentPath parent path of the script
     * @param scriptName file name of the script
     * @param jobType is it a prediction or training job
     * 
     * @return the job identifier or null if the job wasn't started
     */
    public String startJob(String parentPath, String scriptName, JobType jobType, File logFile, int maxLogLevel, String processName) {
        String jobID = startJobInternal(parentPath, scriptName, jobType, logFile, maxLogLevel, processName)
        if (jobID == null && !isSlurmLocal()) {
            // try again later - perhaps a ssh connection was cut
            for (int i = 0; i < 10; i++) {
                Utilities.log(logFile, 1, maxLogLevel, "SEVERE", processName, "startJob failed - try again.")
                sleep(600000) // 600000 = 10 minutes
                jobID = startJobInternal(parentPath, scriptName, jobType, logFile, maxLogLevel, processName)
                if (jobID != null) {
                    return jobID
                }
            }
        }
        return jobID
    }
    
	/**
     * Start a job identified by the specified script.
     *
     * @param parentPath parent path of the script
     * @param scriptName file name of the script
     * @param jobType is it a prediction or training job
     * 
     * @return the job identifier or null if the job wasn't started
     */
    public String startJobInternal(String parentPath, String scriptName, JobType jobType, File logFile, int maxLogLevel, String processName) {
        String parentFolderName = new File(parentPath).getName()
        String serverDataPath = parentPath
            
        if (!isSlurmLocal()) {
            
            // copy job data to slurm server
            if (parentPath.startsWith("/")) {
                serverDataPath = parentPath.substring(1)
            }
            
            def cmd = ["rsync -av -e 'ssh ${getSlurmSSHParam()}' ${parentPath}/ ${getSlurmUserName()}@${getSlurmHost()}:${serverDataPath}/"]
            Utilities.execute(logFile, maxLogLevel, processName, "copyDataToSlurmServer", cmd)
            Utilities.log(logFile, 1, maxLogLevel, processName, "copied data from ${parentPath} to ${serverDataPath} on ${getSlurmHost()}")
            
            // copy the list of species to slurm server
            String AUGUSTUS_SPECIES_PATH = AbstractWebaugustusService.getAugustusSpeciesPath()
            cmd = ["rsync -auv -e 'ssh ${getSlurmSSHParam()}' ${AUGUSTUS_SPECIES_PATH}/ ${getSlurmUserName()}@${getSlurmHost()}:${getSlurmSpeciesDir()}/"]
            int exitCode = Utilities.execute(logFile, maxLogLevel, processName, "copySpeciesToSlurmServer", cmd)
            Utilities.log(logFile, 1, maxLogLevel, processName, "copied species from ${AUGUSTUS_SPECIES_PATH} to ${getSlurmSpeciesDir()} on ${getSlurmHost()}, exitCode=${exitCode}")
            if (exitCode != 0) {
                return null
            }
        }
        
        // copy the slurm submit file and replace PLACEHOLDER by parentFolderName
        String submitFileTemplatePath
        String submitFileName
        if (JobType.PREDICTION.equals(jobType)) {
            submitFileTemplatePath = getSlurmPredictionSubmitFilePath()
            submitFileName = getSlurmPredictionSubmitFileName()
        }
        else if (JobType.TRAINING.equals(jobType)) {
            submitFileTemplatePath = getSlurmTrainingSubmitFilePath()
            submitFileName = getSlurmTrainingSubmitFileName()
        }
        else {
            Utilities.log(logFile, 1, maxLogLevel, processName, "Could not start a job: unrecognized jobType ${jobType}")
            return "0"
        }

        def cmd = [getAsSSHCommand("cat ${submitFileTemplatePath} | sed \"s#PLACEHOLDER#${parentFolderName}#g\" > ${serverDataPath}/${submitFileName}")]
        int exitCode = Utilities.execute(logFile, maxLogLevel, processName, "handleSubmitFile", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "copied and adpated slurm submit file ${submitFileName} on ${getSlurmHost()} exitCode=${exitCode}")
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
     * @return the job status (either WAITING_FOR_EXECUTION, COMPUTING, TIMEOUT, UNKNOWN, ERROR or FINISHED)
     */
    public JobExecution.JobStatus getJobStatus(String jobIdentifier, File logFile, int maxLogLevel, String processName) {
        Utilities.log(logFile, 1, maxLogLevel, processName, "checking slurm job status...")
        def cmd = [getAsSSHCommand("module load slurm; sacct -j ${jobIdentifier} -n --format=JobID,State,Reason")]
        // or 
        // def cmd = [getAsSSHCommand("module load slurm; squeue -h -j ${jobIdentifier} -o '%i %T %r'")]
        
        def statusContent = Utilities.executeForString(logFile, maxLogLevel, processName, "statusScript", cmd)
        
        if (statusContent == null) {
            Utilities.log(logFile, 1, maxLogLevel, processName, "slurm job status -> null")
            return JobStatus.UNKNOWN
        }
        else if ( statusContent =~ /${jobIdentifier}/) {
            
            if ( (statusContent =~ /JobHeldUser/) || (statusContent =~ /launch failed requeued held/) ) {
                if (   (statusContent =~ / PD /) || (statusContent =~ /PENDING/) 
                    || (statusContent =~ / NF /) || (statusContent =~ /NODE_FAIL/) ) {
                    
                    releaseJob(jobIdentifier, logFile, maxLogLevel, processName)
                    return JobStatus.UNKNOWN
                }
            }
            
            if ( (statusContent =~ /  NF /) || (statusContent =~ /NODE_FAIL/) 
                || (statusContent =~ /  CG /) || (statusContent =~ /COMPLETING/) ) {
                // wait for the final message
                return JobStatus.UNKNOWN
            }
            else if ( (statusContent =~ / PD /) || (statusContent =~ /PENDING/)
                || (statusContent =~ / RQ /) || (statusContent =~ /REQUEUED/)) {
                return JobStatus.WAITING_FOR_EXECUTION
            }
            else if ( (statusContent =~ /  R /) || (statusContent =~ /RUNNING/) ) {
                return JobStatus.COMPUTING
            }
            else if ( (statusContent =~ /  TO /) || (statusContent =~ /TIMEOUT/)  ) {
                Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left slurm at ${new Date()} by TIMEOUT.")
                return JobStatus.TIMEOUT
            }
            else if ( (statusContent =~ /  F /) || (statusContent =~ /FAILED/) 
                || (statusContent =~ /  CA /) || (statusContent =~ /CANCELLED/) ) {
                Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left slurm at ${new Date()} by ${statusContent}.")
                return JobStatus.ERROR
            }
            else if ( (statusContent =~ /  CD /) || (statusContent =~ /COMPLETED/) ) {
                Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left slurm at ${new Date()} by COMPLETED.")
                return JobStatus.FINISHED
            }
            else {
                Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left slurm at ${new Date()} for an unexpected reason: ${statusContent}.")
                return JobStatus.ERROR
            }
        }
        return JobStatus.UNKNOWN
    }
    
    /**
     * release a job if the job is held
     */
    private void releaseJob(String jobIdentifier, File logFile, int maxLogLevel, String processName) {
        Utilities.log(logFile, 1, maxLogLevel, processName, "release job after JobHeldUser")
        def cmd = [getAsSSHCommand("module load slurm; scontrol release ${jobIdentifier}")]
        def statusContent = Utilities.executeForString(logFile, maxLogLevel, processName, "releaseJob", cmd)
    }
    
    /**
     * Cleanup after a job is done.
     *
     * @param parentPath parent path of the executed script wich contains all results
     * @param jobType is it a prediction or training job
     * @return 0 if everything is ok else a exit code from the executed command if available else 1
     */
    public int cleanupJob(String parentPath, AbstractWebaugustusService serviceInstance, JobType jobType, File logFile, int maxLogLevel, String processName) {
        int exitCode = cleanupJobInternal(parentPath, serviceInstance, jobType, logFile, maxLogLevel, processName)
        
        if (exitCode != 0 && !isSlurmLocal()) {
        // try again later - perhaps a ssh connection was cut
            for (int i = 0; i < 10; i++) {
                Utilities.log(logFile, 1, maxLogLevel, "SEVERE", processName, "cleanupJob failed - try again.")
                sleep(600000) // 600000 = 10 minutes
                exitCode = cleanupJobInternal(parentPath, serviceInstance, jobType, logFile, maxLogLevel, processName)
                if (exitCode == 0) {
                    return exitCode
                }
            }
        }
        return exitCode
    }
    
    /**
     * Cleanup after a job is done.
     *
     * @param parentPath parent path of the executed script wich contains all results
     * @param jobType is it a prediction or training job
     * @return 0 if everything is ok else a exit code from the executed command if available else 1
     */
    public int cleanupJobInternal(String parentPath, AbstractWebaugustusService serviceInstance, JobType jobType, File logFile, int maxLogLevel, String processName) {
        if (isSlurmLocal()) {
            return 0 // nothing to do
        }
        String parentFolderName = new File(parentPath).getName()
        String serverDataPath = parentPath
        if (parentPath.startsWith("/")) {
            serverDataPath = parentPath.substring(1)
        }
        
        // copy results into output_dir
        Utilities.log(logFile, 1, maxLogLevel, processName, "copy data from ${serverDataPath} on ${getSlurmHost()} to ${parentPath}")
        def cmd = ["rsync -auv --ignore-missing-args -e 'ssh ${getSlurmSSHParam()}' ${getSlurmUserName()}@${getSlurmHost()}:${serverDataPath}/ ${parentPath}/"]
        int exitCodeDataCopy = Utilities.execute(logFile, maxLogLevel, processName, "copyDataFromSlurmServer", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "copied data from ${serverDataPath} on ${getSlurmHost()} to ${parentPath}, exitCode=${exitCodeDataCopy}")
        
        
        // copy results into web_output_dir
        String localWebPath = serviceInstance.getWebOutputDir() + "/" + parentFolderName
        String serverWebPath = localWebPath
        if (localWebPath.startsWith("/")) {
            serverWebPath = localWebPath.substring(1)
        }
        Utilities.log(logFile, 1, maxLogLevel, processName, "copy web data from ${serverWebPath} on ${getSlurmHost()} to ${localWebPath}")
        cmd = ["rsync -auv --ignore-missing-args -e 'ssh ${getSlurmSSHParam()}' ${getSlurmUserName()}@${getSlurmHost()}:${serverWebPath}/ ${localWebPath}/"]
        int exitCodeWebCopy = Utilities.execute(logFile, maxLogLevel, processName, "copyWebDataFromSlurmServer", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "copied web data from ${serverWebPath} on ${getSlurmHost()} to ${localWebPath}, exitCode=${exitCodeWebCopy}")
        
        if (exitCodeDataCopy != 0) {
            return exitCodeDataCopy
        }
        if (exitCodeWebCopy != 0) {
            return exitCodeWebCopy
        }
        
        cmd = [getAsSSHCommand("rm -rf ${serverDataPath} ${serverWebPath}")]
        Utilities.execute(logFile, maxLogLevel, processName, "deleteDataFoldersOnSlurmServer", cmd)
        Utilities.log(logFile, 1, maxLogLevel, processName, "delete data on ${getSlurmHost()}")
        
        if (JobType.TRAINING.equals(jobType)) {
            // copy the list of species from slurm server
            String AUGUSTUS_SPECIES_PATH = AbstractWebaugustusService.getAugustusSpeciesPath()
            
            if (AUGUSTUS_SPECIES_PATH != null && AUGUSTUS_SPECIES_PATH.trim().length() > 0) { // actually this copy is not needed, as we always use the species on slurm server
                String localSpeciesPath = AUGUSTUS_SPECIES_PATH + "/"
                String serverSpeciesPath = getSlurmSpeciesDir() + "/" + parentFolderName

                Utilities.log(logFile, 1, maxLogLevel, processName, "copied species from ${serverSpeciesPath} on ${getSlurmHost()} to ${localSpeciesPath}")
                cmd = ["rsync -auv --ignore-missing-args -e 'ssh ${getSlurmSSHParam()}' ${getSlurmUserName()}@${getSlurmHost()}:${serverSpeciesPath} ${localSpeciesPath}"]
                int exitCodeSpeciesCopy = Utilities.execute(logFile, maxLogLevel, processName, "copySpeciesToSlurmServer", cmd)
                Utilities.log(logFile, 1, maxLogLevel, processName, "copied species from ${serverSpeciesPath} on ${getSlurmHost()} to ${localSpeciesPath}, exitCode=${exitCodeSpeciesCopy}")

                if (exitCodeSpeciesCopy != 0) {
                    return exitCodeSpeciesCopy
                }
            }
        }
        
        return 0        
    }
}
