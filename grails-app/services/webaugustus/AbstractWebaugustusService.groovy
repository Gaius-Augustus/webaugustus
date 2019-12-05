package webaugustus

import grails.gorm.transactions.Transactional
import grails.util.Holders
import webaugustus.AbstractWebAugustusDomainClass

@Transactional
abstract class AbstractWebaugustusService {
    
    // max length of the job queue for a service, when is reached "the server is busy" will be displayed
    protected final static int maxJobQueueLength = Holders.getConfig().getProperty('job.queue.maxSize', Integer, 20);
    
    // max amount of jobs for a service started on computing cluster - has to be lower than AbstractWebaugustusService.maxJobQueueLength
    protected final static int maxStartedJobCount = Holders.getConfig().getProperty('job.submit.maxSize', Integer, 2);

    protected final static int maxNSeqs = 250000 // maximal number of scaffolds allowed in genome file
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
        if (email_address != null) {
            String footer = getEmailFooter()
            String msgStr = "Hello!\n\n${message}Best regards,\n\nthe AUGUSTUS webserver team${getEmailFooter()}"
            sendMail {
                to "${email_address}"
                subject "${subjectString}"
                text "${msgStr}"
            }
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
                            System.err.println("Exception catched in loadDataAndStartJob for \"" + instance + "\", message=" + t.getMessage())
                            t.printStackTrace(System.err)
                            Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in loadDataAndStartJob for \"" + instance + "\", message=" + t.getMessage())
                        }
                        sleep(1000) // just wait a bit for the job to get startet
                        deleteEmailAddress(instance) // just in case the job was aborted
                    }
                }
             
                submittedJobs = findSubmittedJobs()
                submittedJobs.each { instance ->
                    try {
                        boolean jobDone = checkJobReadyness(instance)
                        if (jobDone) {
                            finishJob(instance)
                            deleteEmailAddress(instance)
                        }
                    }
                    catch (Throwable t) {
                        System.err.println("Exception catched in finishJob for \"" + instance + "\", message=" + t.getMessage())
                        t.printStackTrace(System.err)
                        Utilities.log(getLogFile(), 1, getLogLevel(), getServiceName(), "Exception catched in finishJob for \"" + instance + "\", message=" + t.getMessage())
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
     * @returns true if the job is done
     */
    @Transactional
    protected abstract boolean checkJobReadyness(AbstractWebAugustusDomainClass instance)
    
    /**
     * Do all tasks needed to process the job data and cleanup
     */
    @Transactional
    protected abstract void finishJob(AbstractWebAugustusDomainClass instance)
    
    /**
     * delete the email address after the job is done or aborted
     */
    @Transactional
    protected abstract void deleteEmailAddress(AbstractWebAugustusDomainClass instance)
}
