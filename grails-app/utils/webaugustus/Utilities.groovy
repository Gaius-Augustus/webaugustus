package webaugustus

import org.apache.commons.lang.StringEscapeUtils

/**
 *
 * 
 */
class Utilities {
    
    public enum FastaStatus {
        VALID_FASTA,
        NO_VALID_FASTA,
        NO_PROTEIN_FASTA,
        CONTAINS_METACHARACTERS        
    }
    
    static Utilities.FastaStatus checkFastaFormat(File file) {
        return checkFastaFormat(file, null, false)
    }
    
    static Utilities.FastaStatus checkFastaFormat(File file, List seqNames) {
        return checkFastaFormat(file, seqNames, false)
    }
    static Utilities.FastaStatus checkFastaFormat(File file, boolean isProtein) {
        return checkFastaFormat(file, null, isProtein)
    }
    
    static Utilities.FastaStatus checkFastaFormat(File file, List seqNames, boolean isProtein) {
        int cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
        int allAminoAcidsCounter = 0
        boolean metacharacterFlag = false
        boolean fastaFlag = true
        boolean checkFirstLineStart = true
        
        file.eachLine{line ->
            if (metacharacterFlag || line.isEmpty()) {
                return
            }
            if (line =~ /\*/ || line =~ /\?/){
                metacharacterFlag = true
                return
            }
            if (!fastaFlag) {
                return
            }
            if (checkFirstLineStart) {
                checkFirstLineStart = false
                if (!line.startsWith(">")) {
                    fastaFlag = false
                }
            }
            if (isProtein) {
                if ( !(line =~ /^[>AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx ]/) ) { 
                    fastaFlag = false
                    return;
                }
                if (!line.startsWith(">")) {
                    line.eachMatch(/[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/) {
                        allAminoAcidsCounter++
                    }
                    line.eachMatch(/[Cc]/) {
                        cytosinCounter++
                    }
                }
            }
            else if ( !(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]/) ) {
                fastaFlag = false
                return
            }
            if (seqNames != null && line.startsWith(">")) {
                line = line.substring(1).trim()
                if (line.isEmpty()) {
                    fastaFlag = false 
                    return
                }
                seqNames << line
            }
        }
        if (metacharacterFlag) {
            return Utilities.FastaStatus.CONTAINS_METACHARACTERS
        }        
        if (isProtein) {
            if (allAminoAcidsCounter == 0) {
                return Utilities.FastaStatus.NO_VALID_FASTA
            }
            double cRatio = ((double) cytosinCounter)/allAminoAcidsCounter
            if (cRatio >= 0.05) {
                // check that file contains protein sequence, here defined as not more than 5 percent C or c
                return Utilities.FastaStatus.NO_PROTEIN_FASTA
            }
        }
        if (!fastaFlag) {
            return Utilities.FastaStatus.NO_VALID_FASTA
        }
        return Utilities.FastaStatus.VALID_FASTA
    }
    
    static Utilities.FastaStatus checkFastaFormat(URL url) {
        return checkFastaFormat(url, false)
    }
    
    static Utilities.FastaStatus checkFastaFormat(URL url, boolean isProtein) {
        URLConnection uc = url.openConnection()
        BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
        int cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
        int allAminoAcidsCounter = 0
        try{
            String inputLine
            int charCounter = 1
            char inputChar
            int maxCharacters = isProtein ? 2000 : 1000

            while ( ((inputChar = br.read()) != null) && (charCounter <= maxCharacters)) {
                if (inputChar =~ /^$/) {
                    continue;
                }
                if (inputChar =~ />/) {
                    inputLine = br.readLine();
                }
                else if (charCounter == 1) {
                    // if the first character is not '>'
                    return Utilities.FastaStatus.NO_VALID_FASTA
                }
                else if (isProtein) {
                    if ( !(inputChar =~ /^[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/) ) {
                        // if not contains a valid character
                        return Utilities.FastaStatus.NO_VALID_FASTA
                    }
                    allAminoAcidsCounter++;
                    if (inputChar =~ /[Cc]/) {
                        cytosinCounter++;
                    }
                }
                else {
                    if ( !(inputChar =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]/) ) {
                        // if not contains a valid character
                        return Utilities.FastaStatus.NO_VALID_FASTA
                    }
                }
                charCounter++;
            }

        }finally{
            br.close()
        }
        
        if (isProtein) {
            double cRatio = allAminoAcidsCounter == 0 ? 1 : ((double) cytosinCounter)/allAminoAcidsCounter
            if (cRatio >= 0.05) {
                // check that file contains protein sequence, here defined as not more than 5 percent C or c
                return Utilities.FastaStatus.NO_PROTEIN_FASTA
            }
        }
        
        return Utilities.FastaStatus.VALID_FASTA
    }
    
    static Set<String> SUPPORTED_COMPRESS_FORMATS = new HashSet<>()
    static Set<String> UNSUPPORTED_COMPRESS_FORMATS = new HashSet<>()
    static {
        SUPPORTED_COMPRESS_FORMATS.add("gz")
        UNSUPPORTED_COMPRESS_FORMATS.add("tar.gz")
        UNSUPPORTED_COMPRESS_FORMATS.add("zip")
        UNSUPPORTED_COMPRESS_FORMATS.add("rar")
        UNSUPPORTED_COMPRESS_FORMATS.add("7z")
    }
    
    static boolean isCompressed(String filename) {
        return isUnSupportedCompressMode(filename) || isSupportedCompressMode(filename)
    }
    
    static boolean isUnSupportedCompressMode(String filename) {
        String lowercase = filename.toLowerCase()
        for(extension in UNSUPPORTED_COMPRESS_FORMATS) { 
            if (lowercase.endsWith("." + extension))
            return true
        }
        return false
    }
    
    static boolean isSupportedCompressMode(String filename) {
        if (isUnSupportedCompressMode(filename)) { // don't return true for xyz.tar.gz
            return false
        }
        String lowercase = filename.toLowerCase()
        for(extension in SUPPORTED_COMPRESS_FORMATS) { 
            if (lowercase.endsWith("." + extension))
            return true
        }
        return false
    }
    
    static boolean deCompress(String filename, String compressFormat, File logFile, int maxLogLevel, String process) {
        if (!SUPPORTED_COMPRESS_FORMATS.contains(compressFormat)) {
            return false
        }
        if ("gz".equals(compressFormat)) {
            String compressedFileName = filename + ".gz"
            def cmd = ["mv ${filename} ${compressedFileName}; gunzip ${compressedFileName}"]
            Utilities.execute(logFile, maxLogLevel, process, "gunzipScript", cmd)
            return new File(filename).exists()
        }
        return false
    }
    
	
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
            if (!output_array.find()) {
                return null
            }
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
            if (!output_array.find()) {
                return null
            }
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

