package webaugustus

import grails.gorm.transactions.Transactional
import grails.util.Holders

@Transactional
abstract class AbstractWebaugustusService {
    
    // max length of the job queue for a service, when is reached "the server is busy" will be displayed
    protected final static int maxJobQueueLength = Holders.getConfig().getProperty('job.queue.maxSize', Integer, 20);
    
    // max amount of jobs for a service started on computing cluster - has to be lower than AbstractWebaugustusService.maxJobQueueLength
    protected final static int maxStartedJobCount = Holders.getConfig().getProperty('job.submit.maxSize', Integer, 2);

    protected final static int maxNSeqs = 250000 // maximal number of scaffolds allowed in genome file
    protected final static int minPSeqs = 400 // minimal number of proteins in protein file needed for training
    
    // EST sequence properties (length)
    protected final static int estMinLen = 250
    protected final static int estMaxLen = 20000
    
    public static String getAbsoluteURL() {
        return Holders.getConfig().getProperty('url.abs', String)
    }
    
    public static String getRelativeURL() {
        return Holders.getConfig().getProperty('url.rel', String)
    }
    
    public static String getAugustusConfigPath() {
        return Holders.getConfig().getProperty('augustus.path.config', String)
    }
    
    public static String getAugustusSpeciesPath() {
        return Holders.getConfig().getProperty('augustus.path.species', String)
    }
    
    public static String getAugustusScriptPath() {
        return Holders.getConfig().getProperty('augustus.path.scripts', String)
    }
    
    protected static String getAdminEmailAddress() {
        return Holders.getConfig().getProperty('grails.mail.admin', String)
    }
    
    public static String getWebaugustusEmailAddress() {
        return Holders.getConfig().getProperty('grails.mail.default.from', String)
    }
    
    private final static String EMAIL_FOOTER = "\n\n------------------------------------------------------------------------------------\nThis is an automatically generated message.\n\n${getAbsoluteURL()}" // footer of e-mail
    
    protected static String getEmailFooter() {
        return EMAIL_FOOTER
    }
    
    public static int getMaxNSeqs() {
        return maxNSeqs;
    }
    
    public static int getMinPSeqs() {
        return minPSeqs;
    }
    
    public static int getEstMinLen() {
        return estMinLen;
    }
    
    public static int getEstMaxLen() {
        return estMaxLen;
    }
    
    public int getMaxJobQueueLength() {
        return maxJobQueueLength
    }
    
    public int getMaxRunningJobCount() {
        return maxStartedJobCount
    }
    
    public void sendMailToUser(String email_address, String subjectString, String message) {
        String msgStr = "Hello!\n\n${message}Best regards,\n\nthe AUGUSTUS webserver team"
        sendMailInternal(email_address, subjectString, msgStr, "User")
    }
    
    public void sendMailToAdmin(String subjectString, String message) {
        sendMailInternal(getAdminEmailAddress(), subjectString, message, "Admin")
    }
    
    private void sendMailInternal(String email_address, String subjectString, String message, String receiver) {
        if (email_address == null) {
            return
        
        }
        String msgStr = message + getEmailFooter()
        try {
            sendMail {
                to "${email_address}"
                subject "${subjectString}"
                text "${msgStr}"
            }
        }
        catch (Throwable t) {
            System.err.println("Exception catched in sendMailTo${receiver}, service=" + getServiceName())
            System.err.println("Exception catched in sendMailTo${receiver}, exceptionMessage=" + t.getMessage())
            System.err.println("Exception catched in sendMailTo${receiver}, exception=" + t)
            t.printStackTrace(System.err)
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in sendMailTo${receiver} for email_address=" + email_address)
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in sendMailTo${receiver} for subjectString=" + subjectString)
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in sendMailTo${receiver} for message=" + msgStr)
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in sendMailTo${receiver} for exceptionMessage=" + t.getMessage())
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in sendMailTo${receiver} for exception=" + t)                
        }    
    }
    
    private final Object LOCK = new Object()
    
    private Thread workerThread = null
    
    public abstract String getOutputDir()
    
    public abstract String getWebOutputDir()
    
    public abstract String getWebOutputURL()
    
    public abstract String getHttpBaseURL()
    
    public abstract File getLogFile()
    
    public abstract int getLogLevel()
    
    public abstract String getServiceName()
    
    public void startWorkerThread() {
        synchronized(LOCK) {
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "start worker thread" + (workerThread != null && workerThread.isAlive() ? " - still running" : ""))
            
            if (workerThread == null || !workerThread.isAlive()) {
                workerThread = Thread.start(getServiceName()+"WorkerThread", getTask())
            }
            else {
                LOCK.notify() // wake up the waiting thread
            }
        }
    }
    
    private void finishWorkerThread() {
        synchronized(LOCK) {
            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "finish worker thread")
            workerThread = null
        }
    }
    
    private Object getLock() {
        return LOCK
    }
    
    private Closure getTask() {
        return {
            Object LOCK = getLock()
            
            List<AbstractWebAugustusDomainClass> committedJobs
            List<AbstractWebAugustusDomainClass> submittedJobs
            
            while (true) {
                
                committedJobs = findCommittedJobs()
                committedJobs.each { instance ->
                    // if there are jobs to start and the amount of currently runnning jobs is less getMaxRunningJobCount()
                    if (getRunningJobCount() < getMaxRunningJobCount()) {
                        try {
                            loadDataAndStartJob(instance)
                        }
                        catch (Throwable t) {
                            System.err.println("Exception catched in loadDataAndStartJob for \"" + instance + "\", message=" + t.getMessage() + " class=" + t.getClass().getName())
                            t.printStackTrace(System.err)
                            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in loadDataAndStartJob for \"" + instance + "\", message=" + t.getMessage() + " class=" + t.getClass().getName())
                        }
                        sleep(120000) // just wait a bit for the job to get startet
                        deleteEmailAddressAfterJobEnd(instance) // just in case the job was aborted
                    }
                }
                
                submittedJobs = findSubmittedJobs()
                submittedJobs.each { instance ->
                    try {
                        JobExecution.JobStatus jobStatus = checkJobReadyness(instance)
                        if (isJobDone(jobStatus)) {
                            finishJob(instance, jobStatus)
                            deleteEmailAddressAfterJobEnd(instance)
                        }
                    }
                    catch (Throwable t) {
                        System.err.println("Exception catched in finishJob for \"" + instance + "\", message=" + t.getMessage() + " class=" + t.getClass().getName())
                        t.printStackTrace(System.err)
                        Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in finishJob for \"" + instance + "\", message=" + t.getMessage() + " class=" + t.getClass().getName())
                    }                        
                }
                
                if (committedJobs.isEmpty() && submittedJobs.isEmpty()) {
                    synchronized(LOCK) {
                        committedJobs = findCommittedJobs() // find eventually just added jobs
                        submittedJobs = findSubmittedJobs()
                        if (committedJobs.isEmpty() && submittedJobs.isEmpty()) {
                            finishWorkerThread()
                            break
                        }
                    }
                }
                
                synchronized(LOCK) {
                    LOCK.wait(300000) // 300000 = 5 minutes
                }
            }
        }
    }
    
    /**
     * 
     * @param status the job status (either WAITING_FOR_EXECUTION, COMPUTING, TIMEOUT, OUT_OF_MEMORY, UNKNOWN, ERROR or FINISHED)
     * @return true if the job is done
     */
    private boolean isJobDone(JobExecution.JobStatus jobStatus) {
        return jobStatus != null && 
            (  JobExecution.JobStatus.TIMEOUT.equals(jobStatus)
            || JobExecution.JobStatus.OUT_OF_MEMORY.equals(jobStatus)
            || JobExecution.JobStatus.ERROR.equals(jobStatus)
            || JobExecution.JobStatus.FINISHED.equals(jobStatus))
    }
    
    /**
     * Count all jobs currently submitted to the worker (and there pending or running) and committed (waiting for a free slot
     * on the worker)
     */
    public int getJobQueueLength() {
        return findCommittedJobs().size() + findSubmittedJobs().size()
    }
    
    /**
     * Count all jobs currently submitted to the worker (and there pending or running)
     */ 
    public int getRunningJobCount() {
        return findSubmittedJobs().size()
    }
    
    /**
     * Returns all instances where the user has committed a job, but this job has not yet startet
     */
    protected abstract List<AbstractWebAugustusDomainClass> findCommittedJobs() 

    /**
     * Returns all instances where the the augustus job is started
     */
    protected abstract List<AbstractWebAugustusDomainClass> findSubmittedJobs()
    
    /**
     * Download for the given instance all data provided by urls, check the data and start the augustus job
     */
    @Transactional
    protected abstract void loadDataAndStartJob(AbstractWebAugustusDomainClass instance)
    
    /**
     * Check if the augustus job is still running and set the job_status accordingly
     * 
     * @return the job status (either WAITING_FOR_EXECUTION, COMPUTING, TIMEOUT, OUT_OF_MEMORY, UNKNOWN, ERROR or FINISHED)
     */
    @Transactional
    protected abstract JobExecution.JobStatus checkJobReadyness(AbstractWebAugustusDomainClass instance)
    
    /**
     * Do all tasks needed to process the job data and cleanup
     * 
     * @param jobStatus the job status (either TIMEOUT, OUT_OF_MEMORY, ERROR or FINISHED)
     */
    @Transactional
    protected abstract void finishJob(AbstractWebAugustusDomainClass instance, JobExecution.JobStatus jobStatus)
    
    /**
     * delete the email address after the job is done or aborted
     */
    @Transactional
    protected abstract void deleteEmailAddressAfterJobEnd(AbstractWebAugustusDomainClass instance)
}
