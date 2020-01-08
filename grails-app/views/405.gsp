<%@ page contentType="text/html;charset=UTF-8" %>

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>405 Method Not Allowed</title>
    </head>
    <body>
        <h1>Method Not Allowed</h1>
        <p>The specified HTTP method ${request.getMethod()} is not allowed for the requested resource URL ${request.forwardURI} or no data was send in a POST request.</p>
        <hr>
    </body>
</html>
