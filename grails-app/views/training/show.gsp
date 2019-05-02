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
                        <h1 class="csc-firstHeader">Training AUGUSTUS<br>Job ${trainingInstance.accession_id}</h1>
                     </div>
                  </div>
                  <div id="c261665" class="csc-default">
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                  </div>
                  <g:if test="${flash.message}">
                     <div class="message">${flash.message}</div>
                  </g:if>
                  <p>
                     <font color="#FF0000"><b>Please bookmark this page!</b></font> Training AUGUSTUS may take up to several weeks depending on the input data properties. Bookmarking this page ensures that you will be able to return to this page in order to find the results of your job, later.
                  </p>
                  <hr>
                  <h2><font color="#006699">Job Status</font></h2>
                  <p>
                     <g:if test = "${fieldValue(bean:trainingInstance, field:'job_status') == '0' || fieldValue(bean:trainingInstance, field:'job_status') == '1' || fieldValue(bean:trainingInstance, field:'job_status') == '2' || fieldValue(bean:trainingInstance, field:'job_status') == '3'}">
                        <g:if test = "${trainingInstance.old_url == null}">
                  <div style="width:600px;height:30px;border:1px solid #d2d2dc">
                  <p>
                  <g:if test = "${fieldValue(bean:trainingInstance, field:'job_status') == '0'|| fieldValue(bean:trainingInstance, field:'job_status') == '1'}">
                  <b><font color="#006699" size=2>Job submitted</font> <font color="#d2d2dc" size=2>&rarr; waiting for execution &rarr; computing &rarr; finished!</font></b><br>
                  </g:if>
                  <g:if test = "${fieldValue(bean:trainingInstance, field:'job_status') == '2'}">
                  <b><font color="#d2d2dc" size=2>Job submitted</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#006699" size=2>waiting for execution</font> <font color="#d2d2dc" size=2>&rarr; computing &rarr; finished!</font></b><br>
                  </g:if>
                  <g:if test = "${fieldValue(bean:trainingInstance, field:'job_status') == '3'}">
                  <b><font color="#d2d2dc" size=2>Job submitted</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#d2d2dc" size=2>waiting for execution</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#006699" size=2>computing</font> <font color="#d2d2dc" size=2>&rarr; finished!</font></b><br>
                  </g:if>
                  <g:if test = "${fieldValue(bean:trainingInstance, field:'job_status') == '4'}">
                  <b><font color="#d2d2dc" size=2>Job submitted</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#d2d2dc" size=2>waiting for execution</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#d2d2dc" size=2>computing</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#006699" size=2>finished!</font></b><br>
                  </g:if>
                  </p>
                  </div>
                  </g:if>
                  </g:if>
                  </p>
                  <g:if test ="${fieldValue(bean:trainingInstance, field:'job_status') == '4'}">
                     <p>
                        <b><font color="#d2d2dc" size=2>Job submitted</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#d2d2dc" size=2>waiting for execution</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#d2d2dc" size=2>computing</font> <font color="#ffb22a" size=2>&rarr;</font> <font color="#006699" size=2>finished!</font></b>
                     </p>
                  </g:if>
                  <g:if test ="${fieldValue(bean:trainingInstance, field:'job_error') == '5' || fieldValue(bean:trainingInstance, field:'job_status') == '5'}">
                     <p>
                        <b><font color="#f40707" size=2>An error occured when executing this job!</font></b>
                     </p>
                     <g:if test = "${trainingInstance.old_url != null}">
                        <p><b><font color="#FF0000">Data duplication!</font></b> A job with identical data was submitted before. You find the old job at <a href="${trainingInstance.old_url}">${trainingInstance.old_url}</a>.
                        </p>
                     </g:if>
                  </g:if>
                  <g:if test="${trainingInstance.job_status >= '4' && trainingInstance.results_urls != null}">
                     <hr>
                     <h2><font color="#006699">Results</font></h2>
                     ${raw(trainingInstance.results_urls)}
                     <p><b>Instructions</b></p>
                     <p>Please download the files listed above by clicking on the links.</p>
                     <p>All files and folders (except for the *.log and *.err file) are compressed. To unpack <tt>*.tar.gz</tt> archives, e.g. on linux type<br><br>
                        <tt>tar -xzvf *.tar.gz</tt><br><br>
                        For unpacking <tt>*.gz</tt> files, e.g. on linux type<br><br>
                        <tt>gunzip *.gz.</tt>
                     </p>
                     <p>Further instructions about results contents are given at the <a href="../../trainingtutorial.gsp">Training Tutorial</a> and the <a href="../../predictiontutorial.gsp">Prediction Tutorial</a>. Typical error messages from the log files are explained at <a href="../../help.gsp#noResults">Help</a>.
                     </p>
                  </g:if>
                  <h2><font color="#006699">Messages</font></h2>
                  <p>
                  <pre>${trainingInstance.message}</pre>
                  </p>
                  <hr>
                  <div class="csc-default">
                     <div class="divider">
                        <hr>
                     </div>
                  </div>
            </div>
            </main>
    </body>
</html>

