package webaugustus

/**
 * Interface to start a job on a worker queue and return status messages
 */
abstract class JobExecution {
    
    public enum JobStatus {
        WAITING_FOR_EXECUTION,
        COMPUTING,
        FINISHED
    }
    
    public enum JobType {
        PREDICTION,
        TRAINING
    }
    
    public static JobExecution getDefaultJobExecution() {
        return new SlurmJobExecution();
    }

    /**
     * Get the name for the computing cluster.
     * 
     * @return 
     */
    public abstract String getName()
    
    /**
     * Count jobs submitted to the worker queue.
     * 
     * @return the job number of queued and running jobs or -1 if an exception occured
     */
    public abstract int countStartedJobs(File logFile, int maxLogLevel)
	
    /**
     * Start a job identified by the specified script.
     *
     * @param parentPath parent path of the script
     * @param scriptName file name of the script
     * @param jobType is it a prediction or training job
     * 
     * @return the job identifier or null if the jobs wasn't started
     */
    public abstract String startJob(String parentPath, String scriptName, JobType jobType, File logFile, int maxLogLevel, String processName)
    
    /**
     * Returns the job status 
     *
     * @param job identifier - returned by method startJob
     * @return the job status (either WAITING_FOR_EXECUTION, COMPUTING or FINISHED) or null if an exception occured
     */
    public abstract JobExecution.JobStatus getJobStatus(String identifier, File logFile, int maxLogLevel, String processName)
	
    /**
     * Cleanup after a job is done.
     *
     * @param parentPath parent path of the executed script wich contains al results
     * @param jobType is it a prediction or training job
     * @return 0 if everything is ok else a status code from the executed command if available else 1
     */
    public abstract int cleanupJob(String parentPath, AbstractWebaugustusService serviceInstance, JobType jobType, File logFile, int maxLogLevel, String processName)
}
