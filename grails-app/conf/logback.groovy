import grails.util.BuildSettings
import grails.util.Environment
import org.springframework.boot.logging.logback.ColorConverter
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter

import java.nio.charset.StandardCharsets
import ch.qos.logback.core.util.FileSize

import ch.qos.logback.classic.boolex.JaninoEventEvaluator
import ch.qos.logback.core.filter.EvaluatorFilter

import static ch.qos.logback.core.spi.FilterReply.DENY
import static ch.qos.logback.core.spi.FilterReply.NEUTRAL

conversionRule 'clr', ColorConverter
conversionRule 'wex', WhitespaceThrowableProxyConverter

// See http://logback.qos.ch/manual/groovy.html for details on configuration
appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        charset = StandardCharsets.UTF_8

        pattern =
                '%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} ' + // Date
                        '%clr(%5p) ' + // Log level
                        '%clr(---){faint} %clr([%15.15t]){faint} ' + // Thread
                        '%clr(%-40.40logger{39}){cyan} %clr(:){faint} ' + // Logger
                        '%m%n%wex' // Message
    }
}

appender('ROLLING', RollingFileAppender) {
    // relative to /var/lib/tomcat8 - soft linked to /var/log/tomcat8/stacktrace.out
    // extension ".out" is used instead of ".log" to circumvent zipping in /etc/cron.daily/tomcat8
    // The rollover period is inferred from the value of fileNamePattern.
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = "./logs/stacktrace.%d{yyyy-MM-dd}.out"
        maxHistory = 7
        totalSizeCap = FileSize.valueOf("2GB")
    }

    // filter out EOFException - this Exception is thrown if a POST with a file upload is cancelled, 
    // e.g. a user select a file to upload in a form, submit this form and then cancel the submission by pressing the cancel/stop button of the browser
    filter(EvaluatorFilter) {
        evaluator(JaninoEventEvaluator) {
            expression = 'return throwable != null && (java.io.EOFException.class.isInstance(throwable) || (throwable.getMessage() != null && throwable.getMessage().endsWith("EOFException")));'
        }
        onMatch = DENY
        onMismatch = NEUTRAL
    }

    encoder(PatternLayoutEncoder) {
        charset = StandardCharsets.UTF_8

        pattern =
            '%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} ' + // Date
            '%clr(%5p) ' + // Log level
            '%clr(---){faint} %clr([%15.15t]){faint} ' + // Thread
            '%clr(%-40.40logger{39}){cyan} %clr(:){faint} ' + // Logger
            '%m%n%wex' // Message
    }
}

def targetDir = BuildSettings.TARGET_DIR
if (Environment.isDevelopmentMode() && targetDir != null) {
    appender("FULL_STACKTRACE", FileAppender) {
        file = "${targetDir}/stacktrace.log"
        append = true
        encoder(PatternLayoutEncoder) {
            charset = StandardCharsets.UTF_8
            pattern = "%level %logger - %msg%n"
        }
    }
    logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
    root(ERROR, ['STDOUT'])
}
else {
    logger("StackTrace", ERROR, ['ROLLING'], false)
    root(ERROR, ['ROLLING'])
}
