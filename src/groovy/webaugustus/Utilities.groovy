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
    
    /**
     * Executes the given command and returns a String containing a single number
     * If something goes wrong "0" is returned
     * 
     * @param pattern a regex pattern, where the expression to be extracted is enclosed by parenthesis
     * @deprecated as it use files to execute the cmd
     */
    static int executeForIntegerPattern(String tempDir, File logFile, int maxLogLevel, String process, String scriptName, String cmd, String pattern = "(\\d*)") {
        
        def scriptFileName ="${tempDir}/${scriptName}.sh"
        def scriptFile = new File("${scriptFileName}")
        def outputFileName = "${tempDir}/${scriptName}.out"
        def outputFile = new File("${outputFileName}")
        
        def delProc2 = "rm ${scriptFileName} &> /dev/null".execute()
        delProc2.waitFor()
        def delProc3 = "rm ${outputFileName} &> /dev/null".execute()
        delProc3.waitFor()

        def cmd2Script = "${cmd} > ${outputFileName} 2> /dev/null"
        scriptFile << cmd2Script
        
        log(logFile, 3, maxLogLevel, process, "${scriptName} << \"${cmd2Script}\"")
        if(!scriptFile.exists()){
            log(logFile, 1, maxLogLevel, process, "${scriptFileName} does not exist!")
        }
        
        def cmdStr = "bash ${scriptFileName}"
        def cmdProcess = "${cmdStr}".execute()
        cmdProcess.waitFor()
        
        if (!outputFile.exists()) {
            return 0;
        }
        def outputContent = outputFile.text
        if (outputContent.isEmpty()) {
            return 0;
        }
        
        def output_array = outputContent =~ /${pattern}/
        def outputValue = 0
        (1..output_array.groupCount()).each{outputValue = "${output_array[0][it]}"}

        delProc2 = "rm ${scriptFileName} &> /dev/null".execute()
        delProc2.waitFor()
        delProc3 = "rm ${outputFileName} &> /dev/null".execute()
        delProc3.waitFor()

        log(logFile, 3, maxLogLevel, process, "${scriptName} returned ${outputValue}")
        return outputValue.toInteger()
    }
    
    /**
     * Executes the given command and returns a String
     * 
     * @param cmd a list either containing just one entry, which is executed 
     * or containing multiple entries, where the first is the command and then 
     * each next entry contains a paramenter or the parameter value.
     * The last option with the command separated from the parameters and its 
     * value is used for commands where the command executor has difficulties
     * to separate the command, the parameters and its values like curl.
     */
    static def executeForString(File logFile, int maxLogLevel, String process, String scriptName, List cmd) {
        def cmd2Script = cmd.join(" ")
        def logScript = StringEscapeUtils.escapeJava(cmd2Script)
        log(logFile, 3, maxLogLevel, process, "${scriptName} << \"${logScript}\"")
        
        def execute = cmd.size == 1 ? ['bash', '-c', cmd2Script].execute() : cmd.execute()
        execute.waitFor()
        def err = execute.err.text
        if (!err.isEmpty()) {
           log(logFile, 1, maxLogLevel, process, "Exception=${err}")
        }
        
        def outputValue = execute.text.trim()
        log(logFile, 3, maxLogLevel, process, "${scriptName} returned ${outputValue}")
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
     */
    static execute(File logFile, int maxLogLevel, String process, String scriptName, List cmd) {
        executeForString(logFile, maxLogLevel, process, scriptName, cmd)
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
     */
    static int executeForInteger(File logFile, int maxLogLevel, String process, String scriptName, List cmd, String pattern="") {
        def value = executeForString(logFile, maxLogLevel, process, scriptName, cmd)
        
        if (pattern.isEmpty()) {
            return value.toInteger();
        }
        else {
            def output_array = value =~ /${pattern}/
            def outputValue = 0
            (1..output_array.groupCount()).each{outputValue = "${output_array[0][it]}"}
            log(logFile, 3, maxLogLevel, process, "${scriptName} returned ${outputValue} as integer by using this pattern ${pattern}")
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
     */
    static long executeForLong(File logFile, int maxLogLevel, String process, String scriptName, List cmd, String pattern="") {
        def value = executeForString(logFile, maxLogLevel, process, scriptName, cmd)
        
        if (pattern.isEmpty()) {
            return value.toLong();
        }
        else {
            def output_array = value =~ /${pattern}/
            def outputValue = 0
            (1..output_array.groupCount()).each{outputValue = "${output_array[0][it]}"}
            log(logFile, 3, maxLogLevel, process, "${scriptName} returned ${outputValue} as long by using this pattern ${pattern}")
            return outputValue.toLong()
        }
    }
    
    /**
     * save the domain class instance in the database using a new Hibernate session
     * use this method if the context to an underlying Hibernate Session is lost
     */
    static saveDomainWithNewSession(AbstractWebAugustusDomainClass domainclass) {
        domainclass.withNewSession {
            domainclass.save(flush: true)
        }
    }
    
    
    
}

