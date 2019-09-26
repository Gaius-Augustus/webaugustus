<%@ page contentType="text/html;charset=UTF-8" %>

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>404 Not Found</title>
    </head>
    <body>
        <h1>Not Found</h1>
        <p>The requested URL ${request.forwardURI} was not found on this server.</p>
        <p>Please send an e-mail to 
            <a href="mailto:${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAdress()}">${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAdress()}</a>
            if a link on our server is broken.
        </p>
        <hr>
    </body>
</html>
