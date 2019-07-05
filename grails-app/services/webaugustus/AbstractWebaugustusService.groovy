package webaugustus

import grails.gorm.transactions.Transactional
import webaugustus.AbstractWebAugustusDomainClass

@Transactional
abstract class AbstractWebaugustusService {
    
    protected final static String AUGUSTUS_CONFIG_PATH = "/usr/share/augustus/config"   // adapt to the actual situation
    protected final static String AUGUSTUS_SCRIPTS_PATH = "/usr/share/augustus/scripts" // adapt to the actual situation
    // Admin mail for errors
    protected final static String admin_email = "xxx@email.com" // adapt to the actual situation
    protected final static String emailFooter = "\n\n------------------------------------------------------------------------------------\nThis is an automatically generated message.\n\nhttp://bioinf.uni-greifswald.de/webaugustus" // footer of e-mail
    
    // max length of the job queue, when is reached "the server is busy" will be displayed
    protected final static int maxJobsCount = 20;
    
    protected final static int maxNSeqs = 250000 // maximal number of scaffolds allowed in genome file
    // EST sequence properties (length)
    protected final static int estMinLen = 250
    protected final static int estMaxLen = 20000
    
    public static String getAugustusConfigPath() {
        return AUGUSTUS_CONFIG_PATH
    }
    
    public static String getAugustusScriptPath() {
        return AUGUSTUS_SCRIPTS_PATH
    }
    
    protected static String getAdminEmailAdress() {
        return admin_email
    }
    
    protected static String getEmailFooter() {
        return emailFooter
    }
    
    public static int getMaxJobsCount() {
        return maxJobsCount
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
    
    public void sendMailToUser(String email_adress, String subjectString, String message) {
        if (email_adress != null) {
            String footer = getEmailFooter()
            String msgStr = "Hello!\n\n${message}Best regards,\n\nthe AUGUSTUS webserver team${emailFooter}"
            sendMail {
                to "${email_adress}"
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
    
    public abstract String getWarURL()
    
    public abstract File getLogFile()
    
    public abstract int getVerboseLevel()
    
    public abstract String getServiceName()
    
    public void startWorkerThread() {
        synchronized(LOCK) {
            Utilities.log(getLogFile(), 1, getVerboseLevel(), getServiceName(), "start worker thread" + (workerThread != null && workerThread.isAlive() ? " - still running" : ""))

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
            Utilities.log(getLogFile(), 1, getVerboseLevel(), getServiceName(), "finish worker thread")
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
                    loadDataAndStartJob(instance)
                    sleep(500) // just wait a bit for the job to get startet
                }                
                
                submittedJobs = findSubmittedJobs()
                submittedJobs.each { instance ->
                    boolean jobDone = checkJobReadyness(instance)
                    if (jobDone) {
                        finishJob(instance)
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
     * Returns all training instances where the user has committed a job, but this job has not yet startet
     */
    protected abstract List<AbstractWebAugustusDomainClass> findCommittedJobs() 

    /**
     * Returns all training instances where the the augustus job is started
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
}
