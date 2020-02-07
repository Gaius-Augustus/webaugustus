<!DOCTYPE html>
<html>
<head>
    <title>
        <g:if env="development">
            Grails Exception: org.apache.tomcat.util.http.fileupload.FileUploadBase$SizeLimitExceededException
        </g:if>
        <g:else>
            File Size Error
        </g:else>
    </title>
    <meta name="layout" content="main">
    <asset:stylesheet src="errors.css"/>
</head>
<body>
    <g:if env="development">
        <p>
            A SizeLimitExceededException occured.
        </p>
        <g:if test="${Throwable.isInstance(exception)}">
            <g:renderException exception="${exception}" />
        </g:if>
        <g:elseif test="${request.getAttribute('javax.servlet.error.exception')}">
            <g:renderException exception="${request.getAttribute('javax.servlet.error.exception')}" />
        </g:elseif>
        <g:else>
            <ul class="errors">
                <li>An error has occurred</li>
                <li>Exception: ${exception}</li>
                <li>Message: ${message}</li>
                <li>Path: ${path}</li>
            </ul>
        </g:else>

    </g:if>
    <g:else>
        <h2>An error has occurred</h2>
        <p>
            You tried to upload files greater than the allowed size.<br>
            Give considerations to gzip the file before uploading or provide a web link to the file and have a look at the tutorials for this topics.<br>
            For any question please send an e-mail to 
            <a href="mailto:${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}">${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}</a>. <br>
            Please tell us date and time and any actions from your side that may have caused this problem.
        </p>
    </g:else>
</body>
</html>
