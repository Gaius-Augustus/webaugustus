package webaugustus

/**
 * Class to start a job and return status messages on a Sun Grid Engine
 */
class SunGridEngineJobExecution extends webaugustus.JobExecution {

    /**
     * Get the name for the computing cluster.
     * 
     * @return 
     */
    public String getName() {
        return "SGE         ";
    }
    
    /**
     * Count jobs submitted to the worker queue.
     * 
     * @return the job number of queued and running jobs or -1 if an exception occured
     */
    public int countStartedJobs(File logFile, int maxLogLevel) {
        def processForLog = "SGE         "
        def cmd = ["qstat -u \"*\" | grep \"qw\\|  r  \"| wc -l"]
        return Utilities.executeForInteger(logFile, maxLogLevel, processForLog, "qstatScript", cmd)
    }

	
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
        def cmd = ["cd ${parentPath}; qsub ${scriptName} 2> /dev/null"]
        Integer jobID = Utilities.executeForInteger(logFile, maxLogLevel, processName, "startJobScript", cmd, "Your job (\\d+).*")
        Utilities.log(logFile, 1, maxLogLevel, processName, "start SGE job with ${scriptName} in ${parentPath} with jobID ${jobID}")
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
        Utilities.log(logFile, 1, maxLogLevel, processName, "checking job SGE status...")
        def cmd = ['qstat -u "*" | grep ' + "' ${jobIdentifier} '"]
        def statusContent = Utilities.executeForString(logFile, maxLogLevel, processName, "statusScript", cmd)
        
        if (statusContent == null) {
            return JobStatus.UNKNOWN
        }
        else if (statusContent =~ /qw/) {
            return JobExecution.JobStatus.WAITING_FOR_EXECUTION
        }
        else if ( statusContent =~ /  r  / ) {
            return JobExecution.JobStatus.COMPUTING
        }
        else if (!statusContent.empty) {
            Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} is neither in qw nor in r status but is still on the grid! (${statusContent})")
            return JobExecution.JobStatus.COMPUTING
        }
        else {
            Utilities.log(logFile, 1, maxLogLevel, processName, "Job ${jobIdentifier} left SGE at ${new Date()}.")
            return JobExecution.JobStatus.FINISHED
        }
    }
    
    /**
     * Cleanup after a job is done.
     *
     * @param parentPath parent path of the executed script wich contains al results
     * @param jobType is it a prediction or training job
     * @return 0 if everything is ok else a status code from the executed command if available else 1
     */
    public int cleanupJob(String parentPath, AbstractWebaugustusService serviceInstance, JobType jobType, File logFile, int maxLogLevel, String processName) {
        // nothing to do - sge running on none local server is not yet implemented
        return 0
    }
}