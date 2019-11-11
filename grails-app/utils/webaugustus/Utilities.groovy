package webaugustus

import org.apache.commons.lang.StringEscapeUtils

/**
 *
 * 
 */
class Utilities {
	
    static def log(File logFile, int loglevel, int maxLogLevel, String process, String message) {
        if(loglevel <= maxLogLevel) {
            Date logDate = new Date()
            logFile << "${logDate} ${process} v${loglevel} - ${message}\n"
        }
    }
    
    static def log(File logFile, int loglevel, int maxLogLevel, String warnLevel, String process, String message) {
        if(loglevel <= maxLogLevel) {
            Date logDate = new Date()
            logFile << "${warnLevel} ${logDate} ${process} v${loglevel} - ${message}\n"
        }
    }
    
    static String cleanCommandForLog(String cmd) {
        String log = StringEscapeUtils.escapeJava(cmd).trim()
        if (log.startsWith("ssh")) {
            log = log.substring(log.indexOf("'"), log.lastIndexOf("'")+1);
        }
        else if (log.startsWith("rsync")) {
            log = "rsync";
        }
        return log
    }
    
    /**
     * Executes the given command and returns a String
     * 
     * @param cmd a list either containing just one entry, which is executed 
     * or containing multiple entries, where the first is the command and then 
     * each next entry contains a parameter or the parameter value.
     * The last option with the command separated from the parameters and its 
     * value is used for commands where the command executor has difficulties
     * to separate the command, the parameters and its values like curl.
     * 
     * @return the return value of the command or null in case of an error
     */
    static def executeForString(File logFile, int maxLogLevel, String process, String scriptName, List cmd) {
        def cmd2Script = cmd.join(" ")
        def logScript = cleanCommandForLog(cmd2Script)
        log(logFile, 3, maxLogLevel, process, "${scriptName} << \"${logScript}\"")
        
        def execute = cmd.size == 1 ? ['bash', '-c', cmd2Script].execute() : cmd.execute()
        execute.waitFor()
        
        if (execute.exitValue()) {
            log(logFile, 1, maxLogLevel, process, "Exception status code=${execute.exitValue()} (${execute.err.text})")
        }
        
        def outputValue = execute.text.trim()
        log(logFile, 3, maxLogLevel, process, "${scriptName} returned \"${outputValue}\"")
        if (execute.exitValue() && outputValue.isEmpty()) {
            return null
        }
        return outputValue
    }
    
    /**
     * Executes the given command
     * 
     * @param cmd a list either containing just one entry, which is executed 
     * or containing multiple entries, where the first is the command and then 
     * each next entry contains a paramenter or the parameter value.
     * The last option with the command separated from the parameters and its 
     * value is used for commands where the command executor has difficulties
     * to separate the command, the parameters and its values like curl.
     * 
     * @return 0 if everything is ok else a status code from the executed command
     */
    static int execute(File logFile, int maxLogLevel, String process, String scriptName, List cmd) {
        def cmd2Script = cmd.join(" ")
        def logScript = cleanCommandForLog(cmd2Script)
        log(logFile, 3, maxLogLevel, process, "${scriptName} << \"${logScript}\"")
        
        def execute = cmd.size == 1 ? ['bash', '-c', cmd2Script].execute() : cmd.execute()
        execute.waitFor()
        
        def exitCode = execute.exitValue()
        
        if (exitCode) {
            def errorValue = "Null"
            
            try {
                errorValue = execute.err.text
            }
            catch (IOException e) {
                errorValue = "IOException: " + e.getMessage()
            }
            
            def outputValue = "Null"

            try {
                outputValue = execute.text.trim()
            }
            catch (IOException e) {
                outputValue = "IOException: " + e.getMessage()
            }
            
            log(logFile, 1, maxLogLevel, process, "Exception status code=${exitCode} (${errorValue})  outputValue=${outputValue}")
        }
        else {
            def outputValue = "Null"

            try {
                outputValue = execute.text.trim()
            }
            catch (IOException e) {
                outputValue = "IOException: " + e.getMessage()
            }

            
            log(logFile, 3, maxLogLevel, process, "${scriptName} returned \"${outputValue}\"")
        }
        
        
        return exitCode
    }
    
    /**
     * Executes the given command and returns an Integer.
     * 
     * @param cmd a list either containing just one entry, which is executed 
     * or containing multiple entries, where the first is the command and then 
     * each next entry contains a paramenter or the parameter value.
     * The last option with the command separated from the parameters and its 
     * value is used for commands where the command executor has difficulties
     * to separate the command, the parameters and its values like curl.
     * @param pattern a regex pattern, where the expression to be extracted is enclosed by parenthesis
     * if this pattern is not used, the command should return just the integer on stdout and nothing more.
     * @return returns the command output as integer (filtered by pattern if given) or zero if the output doesn't contain an integer
     * or null in case of an error
     */
    static Integer executeForInteger(File logFile, int maxLogLevel, String process, String scriptName, List cmd, String pattern="") {
        def value = executeForString(logFile, maxLogLevel, process, scriptName, cmd)
        
        if (value == null) {
            return null
        }
        
        if (pattern.isEmpty()) {
            return value.toInteger()
        }
        else {
            def output_array = value =~ /${pattern}/
            def outputValue = 0
            (1..output_array.groupCount()).each{outputValue = "${output_array[0][it]}"}
            log(logFile, 3, maxLogLevel, process, "${scriptName} returned \"${outputValue}\" as integer by using this pattern \"${pattern}\"")
            return outputValue.toInteger()
        }
    }
    
    /**
     * Executes the given command and returns an Long.
     * 
     * @param cmd a list either containing just one entry, which is executed 
     * or containing multiple entries, where the first is the command and then 
     * each next entry contains a paramenter or the parameter value.
     * The last option with the command separated from the parameters and its 
     * value is used for commands where the command executor has difficulties
     * to separate the command, the parameters and its values like curl.
     * 
     * @param pattern a regex pattern, where the expression to be extracted is enclosed by parenthesis
     * if this pattern is not used, the command should return just the long on stdout and nothing more.
     * @return returns the command output as long (filtered by pattern if given) or zero if the output doesn't contain a long value
     * or null in case of an error
     */
    static Long executeForLong(File logFile, int maxLogLevel, String process, String scriptName, List cmd, String pattern="") {
        def value = executeForString(logFile, maxLogLevel, process, scriptName, cmd)
        
        if (value == null) {
            return null
        }
        
        if (pattern.isEmpty()) {
            return value.toLong()
        }
        else {
            def output_array = value =~ /${pattern}/
            def outputValue = 0
            (1..output_array.groupCount()).each{outputValue = "${output_array[0][it]}"}
            log(logFile, 3, maxLogLevel, process, "${scriptName} returned \"${outputValue}\" as long by using this pattern \"${pattern}\"")
            return outputValue.toLong()
        }
    }
    
    /**
     * save the domain class instance in the database using a transaction
     * @return true if transcation was successfully
     */
    static boolean saveDomainWithTransaction(AbstractWebAugustusDomainClass domainclass) {
        def returnValue = domainclass.withTransaction {
            domainclass.save(flush: true)
        }
        return returnValue != null;
    }
    
    /**
     * execute the specified query
     * 
     * @return 
     */
    static Object executeWithTransaction(AbstractWebAugustusDomainClass domainclass, Closure query) {
        return domainclass.withTransaction {
            query()
        }
    }
    
    
}

