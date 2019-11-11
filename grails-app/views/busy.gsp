<!DOCTYPE html>
<html>
    <head>
        <meta name="layout" content="main"/>
        <title>Bioinformatics Web Server - University of Greifswald</title>
        <meta name="date" content="2018-07-17">
        <meta name="lastModified" content="2018-07-16">
    </head>
    <body>
        <main class="main-content">
            <div id="c180465" class="csc-default">
                <div class="csc-header csc-header-n1">
                    <h1 class="csc-firstHeader">The Server is Busy</h1>
                </div>
            </div>
            <div id="c261665" class="csc-default">
                <div class="csc-default">
                    <div class="divider">
                        <hr>
                    </div>
                </div>
                <p>
                    ${message1 ?: 'You tried to access a AUGUSTUS Web Service job submission page.'}
                </p>
                <p>
                    ${message2 ?: 'Predicting genes and training parameters with AUGUSTUS are a \n\
                    process that takes a lot of computation time. \n\
                    We estimate that one process requires at most approximately ten days. '}
                    Our web server is able to process a certain number of jobs in parallel, 
                    and we established a waiting queue. The waiting queue has a limited length, though. 
                    Currently, all slots for computation and for waiting are occupied.
                </p>
                <p>
                    We apologize for the inconvenience! Please try to submit your job later.
                </p>
                <p>
                    Feel free to contact <a href="mailto:${webaugustus.AbstractWebaugustusService.getWebaugustusEmailAddress()}">us</a> in case your job is particularly urgent.
                </p>
                <div class="csc-default">
                    <div class="divider">
                        <hr>
                    </div>
                </div>
            </div>
        </main>
    </body>
</html>
