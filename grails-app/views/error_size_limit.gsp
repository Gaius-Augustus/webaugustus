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
            A SizeLimitExceededException occurred.
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
        <h2>An error occurred</h2>
        <p>
            You tried to upload files greater than the allowed size (100 MB for all files, except for gene structure file with 200 MB).<br>
            Please consider to gzip files before uploading and/or provide a web link when uploading large files (up to 1 GB). Have a look at our tutorials for these topics.<br>
            For any question, please send an e-mail to 
            <a href="mailto:${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}">${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}</a>.
        </p>
    </g:else>
</body>
</html>
