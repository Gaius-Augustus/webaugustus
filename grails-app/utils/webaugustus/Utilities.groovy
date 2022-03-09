package webaugustus

import org.apache.commons.lang.StringEscapeUtils
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.MessageSource

/**
 *
 * 
 */
class Utilities {
    
    /**
     * 
     * TO_LONG_EST_HEADER: the part of an est fasta file header until the first space MUST be less or equal to 90 characters,
     * this is a limitation of the PASA pipeline, seqclean allows max 510 characters, the PASA MySQL database max 100 (minus the 
     * name of the alignment program - so choose 90 to be safe)
     */
    public enum FastaStatus {
        VALID_FASTA,
        NO_VALID_FASTA,
        NO_PROTEIN_FASTA,
        CONTAINS_METACHARACTERS,
        TO_LONG_EST_HEADER,
        SEQUENCE_NAMES_NOT_UNIQUE
    }
    
    public enum FastaDataType {
        GENOME,
        EST,
        PROTEIN
    }
    
    private static final VALID_FASTA_CHECK_RESULT = new FastaCheckResult()
    
    public static class FastaCheckResult {
        final FastaStatus status
        String errorMessage
        
        private FastaCheckResult() {
            this(FastaStatus.VALID_FASTA)
        }
        
        public FastaCheckResult(FastaStatus status) {
            this(status, null)
        }
        
        public FastaCheckResult(FastaStatus status, String errorMessage) {
            this.status = status
            this.errorMessage = errorMessage
        }
        
        public boolean isValidFasta() {
            return FastaStatus.VALID_FASTA.equals(status)
        }
        
        public FastaStatus getFastaStatus() {
            return status
        }
        
        public boolean hasErrorMessage() {
            return errorMessage != null
        }
        
        public String getErrorMessage() {
            return errorMessage
        }
        
    }
    
    private static getFileTypeName(Utilities.FastaDataType fastaType) {
        switch (fastaType) {
            case FastaDataType.GENOME:
                return "genome"
            case FastaDataType.EST:                
                return"cDNA"
            case FastaDataType.PROTEIN:                
                return "protein"
            default:
                return""
        }
    }
    
     private static getSequenceTypeName(Utilities.FastaDataType fastaType) {
        switch (fastaType) {
            case FastaDataType.GENOME:
            case FastaDataType.EST:                
                return"DNA"
            case FastaDataType.PROTEIN:                
                return "protein"
            default:
                return""
        }
    }
    
    /** 
     * Check if the fasta file at the specified url is well formatted.
     * 
     * @param file
     * @param origFileName the file name at upload time
     * @param fastaType the data which is in the fasta file: COMMOM, GENOME, PROTEIN, GENE
     * @return FastaCheckResult with status VALID_FASTA, CONTAINS_METACHARACTERS, SEQUENCE_NAMES_NOT_UNIQUE, NO_VALID_FASTA 
     * or NO_PROTEIN_FASTA and eventually an error message
     */
    static Utilities.FastaCheckResult checkFastaFormat(File file, String origFileName, Utilities.FastaDataType fastaType, MessageSource messageSource) {
        return checkFastaFormat(file, origFileName, null, fastaType, messageSource)
    }
    
    /** 
     * Check if the fasta file at the specified url is well formatted.
     * 
     * @param file
     * @param origFileName the file name at upload time
     * @param seqNames add header lines to this list
     * @param fastaType the data which is in the fasta file: COMMOM, GENOME, PROTEIN, GENE
     * @return FastaCheckResult with status VALID_FASTA, CONTAINS_METACHARACTERS, SEQUENCE_NAMES_NOT_UNIQUE, NO_VALID_FASTA 
     * or NO_PROTEIN_FASTA and eventually an error message
     */
    static Utilities.FastaCheckResult checkFastaFormat(File file, String origFileName, List seqNames, Utilities.FastaDataType fastaType, MessageSource messageSource) {
        String fileType = getFileTypeName(fastaType)
        String sequenceType = getSequenceTypeName(fastaType)
        
        int cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
        int allAminoAcidsCounter = 0
        boolean metacharacterFlag = false
        String metacharacter = null
        boolean fastaFlag = true
        boolean toLongEstHeader = false
        String notUniqueId = null
        Set uniqueIdSet = [] as HashSet
        boolean checkFirstLineStart = true
        boolean checkProtein = Utilities.FastaDataType.PROTEIN.equals(fastaType)
        boolean checkEst = Utilities.FastaDataType.EST.equals(fastaType)
        boolean hasSequence = false
        
        file.eachLine{line ->
            if (notUniqueId != null || metacharacterFlag || line.isEmpty()) {
                return
            }
            if (checkFirstLineStart) {
                checkFirstLineStart = false
                if (!line.startsWith(">")) {
                    fastaFlag = false
                }
            }
            if (line.contains("*")) {
                metacharacterFlag = true
                metacharacter = "*"
                return
            }
            if (line.contains("?")) {
                metacharacterFlag = true
                metacharacter = "?"
                return
            }
            if (!fastaFlag) {
                return
            }
            if (line.startsWith(">")) {
                line = line.substring(1).trim() // remove '>'
                if (line.isEmpty()) {
                    fastaFlag = false 
                    return
                }
                if (seqNames != null) {
                    seqNames << line
                }
                String id = line.split("\\s", 2)[0] // cut line at first white space
                if (!uniqueIdSet.add(id)) {
                    notUniqueId = id
                    fastaFlag = false
                    return
                }
                if (checkEst) {
                    if (id.size() > 90) {
                        // seqclean allows up to 510 characters, PASA mysql database just 100 but store the aligment program 
                        // name (gmap-) in some fields to - so 90 should be safe
                        toLongEstHeader = true
                        fastaFlag = false
                        return
                    }
                    if (id.contains("[") || id.contains("]")) {
                        // PASA script "assemble_clusters.dbi" throws Error "Didn't obtain assemblies describing all maximal assemblies."
                        // if the ID contains square brackets
                        // search internet for "comp24382_c0_seq1_FPKM_all_18.154_FPKM_rel_18.154_len_304_path_[0]"
                        // - this is the best hit about this constraint
                        metacharacterFlag = true
                        metacharacter = "[]"
                        return
                    }
                    if (id.contains(",")) {
                        // PASA script "assemble_clusters.dbi" throws Error ..."accession contains comma(s).  This is not allowed."
                        // if the ID contain a comma
                        metacharacterFlag = true
                        metacharacter = ","
                        return
                    }
                }
            }
            else {
                if (!hasSequence) {
                    hasSequence = line.trim().size() > 0
                }
                if (checkProtein) {
                    if ( !(line =~ /^[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvUuOoBbZzJjXx]*$/) ) {
                        fastaFlag = false
                        return;
                    }
                    allAminoAcidsCounter += line.size()
                    line.eachMatch(/[Cc]/) {
                        cytosinCounter++
                    }
                }
                else if ( !(line =~ /^[AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]*$/) ) {
                    fastaFlag = false
                    return
                }
            }
        }
        if (notUniqueId != null) {
            return new FastaCheckResult(FastaStatus.SEQUENCE_NAMES_NOT_UNIQUE,
                messageSource.getMessage('errormessage.no_unique_sequence_names', [ fileType, origFileName, notUniqueId ] as Object[], 'SEQUENCE_NAMES_NOT_UNIQUE', LocaleContextHolder.locale) )
        }
        if (metacharacterFlag) {
            return new FastaCheckResult(FastaStatus.CONTAINS_METACHARACTERS, 
                messageSource.getMessage('errormessage.metacharacters', [ fileType, origFileName, metacharacter ] as Object[], 'CONTAINS_METACHARACTERS', LocaleContextHolder.locale) )
        }
        if (toLongEstHeader) {
            return new FastaCheckResult(FastaStatus.TO_LONG_EST_HEADER,
                messageSource.getMessage('errormessage.est.header_exceed_90_characters', [ fileType, origFileName ] as Object[], 'TO_LONG_EST_HEADER', LocaleContextHolder.locale) )
        }
        if (checkProtein) {
            if (allAminoAcidsCounter == 0) {
                return new FastaCheckResult(FastaStatus.NO_VALID_FASTA, 
                    messageSource.getMessage('errormessage.not_fasta', [ fileType, origFileName, sequenceType ] as Object[], 'NO_VALID_FASTA', LocaleContextHolder.locale) )
            }
            double cRatio = ((double) cytosinCounter)/allAminoAcidsCounter
            if (cRatio >= 0.05) {
                // check that file contains protein sequence, here defined as not more than 5 percent C or c
                return new FastaCheckResult(FastaStatus.NO_PROTEIN_FASTA,
                    messageSource.getMessage('errormessage.no_protein_fasta', [ origFileName ] as Object[], 'NO_PROTEIN_FASTA', LocaleContextHolder.locale) )
            }
        }
        if (!fastaFlag) {
            return new FastaCheckResult(FastaStatus.NO_VALID_FASTA, 
                messageSource.getMessage('errormessage.not_fasta', [ fileType, origFileName, sequenceType ] as Object[], 'NO_VALID_FASTA', LocaleContextHolder.locale) )
        }
        if (!hasSequence) {
            return new FastaCheckResult(FastaStatus.NO_VALID_FASTA, 
                messageSource.getMessage('errormessage.not_fasta', [ fileType, origFileName, sequenceType ] as Object[], 'NO_VALID_FASTA', LocaleContextHolder.locale) )
        }
        return VALID_FASTA_CHECK_RESULT
    }
    
    /** 
     * Check if the fasta file at the specified url is well formatted.
     * Checks only the head of the file.
     * 
     * @param url
     * @param ftp_link the name at upload time
     * @param fastaType the data which is in the fasta file: COMMOM, GENOME, PROTEIN, GENE
     * @return FastaCheckResult with status VALID_FASTA, CONTAINS_METACHARACTERS, SEQUENCE_NAMES_NOT_UNIQUE, NO_VALID_FASTA 
     * or NO_PROTEIN_FASTA and eventually an error message
     */
    static Utilities.FastaCheckResult checkFastaFormat(String ftp_link, Utilities.FastaDataType fastaType, MessageSource messageSource) {
        String fileType = getFileTypeName(fastaType)
        String sequenceType = getSequenceTypeName(fastaType)
        URL url = new URL(ftp_link)
        URLConnection uc = url.openConnection()
        BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
        int cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
        int allAminoAcidsCounter = 0
        boolean checkProtein = Utilities.FastaDataType.PROTEIN.equals(fastaType)
        boolean checkEst = Utilities.FastaDataType.EST.equals(fastaType)
        Set uniqueIdSet = [] as HashSet
        
        try{
            int charCounter = 1
            int inputInt
            char inputChar
            int maxCharacters = checkProtein ? 2000 : 1000
            
            while ( ((inputInt = br.read()) != -1) && (charCounter <= maxCharacters)) {
                if (inputInt == 65279) { //BOM
                    continue;
                }
                inputChar = (char) inputInt                
                if (inputChar =~ /^$/) { // /n /r
                    continue;
                }
                if (inputChar =~ />/) {
                    String line = br.readLine() // no further checks until end of this line
                    String id = line.split("\\s", 2)[0] // cut line at first white space
                    if (!uniqueIdSet.add(id)) {
                        return new FastaCheckResult(FastaStatus.SEQUENCE_NAMES_NOT_UNIQUE,
                            messageSource.getMessage('errormessage.no_unique_sequence_names', [ fileType, ftp_link, id ] as Object[], 'SEQUENCE_NAMES_NOT_UNIQUE', LocaleContextHolder.locale) )
                    }
                    if (checkEst) {                        
                        if (id.size() > 90) { 
                            // seqclean allows up to 510 characters, PASA mysql database just 100 but store the aligment program 
                            // name (gmap-) in some fields to - so 90 should be safe
                            // search internet for "Which is longer than the pasa mysql database field length, which truncates it"
                            // - this is the best hit about this constraint
                            return new FastaCheckResult(FastaStatus.TO_LONG_EST_HEADER,
                                messageSource.getMessage('errormessage.est.header_exceed_90_characters', [ fileType, ftp_link ] as Object[], 'TO_LONG_EST_HEADER', LocaleContextHolder.locale) )
                        }
                        if (id.contains("[") || id.contains("]")) {
                            // PASA script "assemble_clusters.dbi" throws Error "Didn't obtain assemblies describing all maximal assemblies."
                            // if the ID contains square brackets
                            // search internet for "comp24382_c0_seq1_FPKM_all_18.154_FPKM_rel_18.154_len_304_path_[0]"
                            // - this is the best hit about this constraint
                            return new FastaCheckResult(FastaStatus.CONTAINS_METACHARACTERS, 
                                messageSource.getMessage('errormessage.metacharacters', [ fileType, ftp_link, "[]" ] as Object[], 'CONTAINS_METACHARACTERS', LocaleContextHolder.locale) )
                        }
                        if (id.contains(",")) {
                            // PASA script "assemble_clusters.dbi" throws Error ..."accession contains comma(s).  This is not allowed."
                            // if the ID contain a comma
                            return new FastaCheckResult(FastaStatus.CONTAINS_METACHARACTERS, 
                                messageSource.getMessage('errormessage.metacharacters', [ fileType, ftp_link, "," ] as Object[], 'CONTAINS_METACHARACTERS', LocaleContextHolder.locale) )
                        }
                    }
                }
                else if (charCounter == 1) {
                    // if the first character is not '>'
                    return new FastaCheckResult(FastaStatus.NO_VALID_FASTA, 
                        messageSource.getMessage('errormessage.not_fasta', [ fileType, ftp_link, sequenceType ] as Object[], 'NO_VALID_FASTA', LocaleContextHolder.locale) )
                }
                else if (Utilities.FastaDataType.PROTEIN.equals(fastaType)) {
                    if ( !(inputChar =~ /[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvUuOoBbZzJjXx]/) ) {
                        // if not contains a valid character
                        return new FastaCheckResult(FastaStatus.NO_VALID_FASTA, 
                            messageSource.getMessage('errormessage.not_fasta', [ fileType, ftp_link, sequenceType ] as Object[], 'NO_VALID_FASTA', LocaleContextHolder.locale) )
                    }
                    allAminoAcidsCounter++;
                    if (inputChar =~ /[Cc]/) {
                        cytosinCounter++;
                    }
                }
                else {
                    if ( !(inputChar =~ /^[AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]/) ) {
                        // if not contains a valid character
                        return new FastaCheckResult(FastaStatus.NO_VALID_FASTA, 
                            messageSource.getMessage('errormessage.not_fasta', [ fileType, ftp_link, sequenceType ] as Object[], 'NO_VALID_FASTA', LocaleContextHolder.locale) )
                    }
                }
                charCounter++;
            }

        }finally{
            br.close()
        }
        
        if (checkProtein) {
            double cRatio = allAminoAcidsCounter == 0 ? 1 : ((double) cytosinCounter)/allAminoAcidsCounter
            if (cRatio >= 0.05) {
                // check that file contains protein sequence, here defined as not more than 5 percent C or c
                return new FastaCheckResult(FastaStatus.NO_PROTEIN_FASTA,
                    messageSource.getMessage('errormessage.no_protein_fasta', [ ftp_link ] as Object[], 'NO_PROTEIN_FASTA', LocaleContextHolder.locale) )
            }
        }
        
        return VALID_FASTA_CHECK_RESULT
    }
    
    /**
     * Check hints file format
     * 
     * @param hintsFile hints file
     * @param origFileName uploaded file name 
     * @param seqNames sequence names in hintsfile must be in this list
     * @return empty list if format is ok, else this list contains messages about all format errors
     */
    static List checkHintsGffFormat(File hintsFile, String origFileName, List seqNames) {
        
        List errors = []
        
        // gff format validation: number of columns 9, + or - in column 7, column 1 must be  member of seqNames
        Set seqNamesSet = seqNames.toSet()
        Set allowedFeatures = ["start", "stop", "tss", "tts", "ass", "dss", "exonpart", "exon", "intronpart", "intron",
            "CDSpart", "CDS", "UTRpart", "UTR", "irpart", "nonexonpart", "genicpart"] as HashSet
        
        boolean emptyFlag = false
        boolean commentFlag = false
        boolean metacharacterFlag = false
        boolean gffColErrorFlag = false
        boolean gffNameErrorFlag = false
        boolean gffSourceErrorFlag = false
        boolean gffFeatureErrorFlag = false
        
        hintsFile.eachLine{line ->
            line = line.trim()
            if (line.size() == 0) {
                if (!emptyFlag) {
                    emptyFlag = true
                    errors.add("Hints file \"${origFileName}\" contains empty lines. This is not allowed.")
                }
                return
            }
            if (line.startsWith("#")) {
                if (!commentFlag) {
                    commentFlag = true
                    errors.add("Hints file \"${origFileName}\" contains comments. This is not allowed.")
                }
                return
            }                    
            if (!metacharacterFlag && (line.contains("*") || line.contains("?"))) {
                metacharacterFlag = true
                errors.add("Hints file \"${origFileName}\" contains metacharacters (* ? ...). This is not allowed.")
            }

            if (gffColErrorFlag && gffNameErrorFlag && gffSourceErrorFlag && gffFeatureErrorFlag) {
                return
            }

            def gffArray = line.split("\t")
            if (gffArray.size() != 9) {
                if (!gffColErrorFlag) {
                    gffColErrorFlag = true
                    errors.add("Hints file \"${origFileName}\" is not in a compatible gff format (did not contain 9 columns in each line).")
                }
                return
            }
            if (!gffNameErrorFlag && !seqNamesSet.contains(gffArray[0])) {
                gffNameErrorFlag = true
                errors.add("The sequence names in the hints file \"${origFileName}\" did not comply with the sequence names in the supplied genome file (e.g. \"${gffArray[0]}\").")
            }
            if (!gffFeatureErrorFlag && !allowedFeatures.contains(gffArray[2])) {
                gffFeatureErrorFlag = true
                errors.add("Entries in the third column of the hints file \"${origFileName}\" contains unsupported features (e.g. \"${gffArray[2]}\").")
            }
            if (!gffSourceErrorFlag && (!gffArray[8].contains("source=M") && !gffArray[8].contains("src=M"))) {
                gffSourceErrorFlag = true
                errors.add("Entries in the last column of the hints file \"${origFileName}\" does not contain \"source=M\" but \"${gffArray[8]}\".")
            }
        }
        return errors
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
    static String executeForString(File logFile, int maxLogLevel, String process, String scriptName, List cmd) {
        def cmd2Script = cmd.join(" ")
        def logScript = cleanCommandForLog(cmd2Script)
        log(logFile, 3, maxLogLevel, process, "${scriptName} << \"${logScript}\"")
        
        def execute = cmd.size == 1 ? ['bash', '-c', cmd2Script].execute() : cmd.execute()
        execute.waitFor()
        
        if (execute.exitValue()) {
            log(logFile, 1, maxLogLevel, process, "Exception status code=${execute.exitValue()} (${execute.err.text})")
        }
        
        String outputValue = execute.text.trim()
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
            
            log(logFile, 1, maxLogLevel, process, "Exception status code=${exitCode} outputValue=${outputValue} (errorValue=${errorValue})")
        }
        else {
            
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

            
            log(logFile, 3, maxLogLevel, process, "${scriptName} returned \"${outputValue}\" (errorValue=${errorValue})")
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
        String value = executeForString(logFile, maxLogLevel, process, scriptName, cmd)
        
        if (value == null || value.trim().isEmpty()) {
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
            if (outputValue == null || outputValue.trim().isEmpty()) {
                return null
            }
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
        String value = executeForString(logFile, maxLogLevel, process, scriptName, cmd)
        
        if (value == null || value.trim().isEmpty()) {
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
            if (outputValue == null || outputValue.trim().isEmpty()) {
                return null
            }
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

