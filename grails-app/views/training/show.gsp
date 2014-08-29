<html>
    <head>
        <META HTTP-EQUIV="Refresh" CONTENT="60">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="layout" content="main" />
        <title>Show Training</title>
    </head>
    <body>
<!-- ***** Start: Kopfbereich ********************************************// -->
<p class="unsichtbar">
  <a href="#inhalt" title="Directly to Contents">Directly to Contents</a>
</p>

<div id="navigation_oben">
  <a name="seitenanfang"></a>
  <table width="100%" border="0" cellpadding="0" cellspacing="1">
    <tr>
      <td nowrap="nowrap">
        <a href="http://www.uni-greifswald.de" target="_blank" class="mainleveltop_" >University of Greifswald</a><span class="mainleveltop_">&nbsp;|&nbsp; </span><a href="http://www.mnf.uni-greifswald.de/" target="_blank" class="mainleveltop_" >Faculty</a><span class="mainleveltop_">&nbsp;|&nbsp; </span><a href="http://www.math-inf.uni-greifswald.de/" target="_blank" class="mainleveltop_" >Institute</a><span class="mainleveltop_">&nbsp;|&nbsp;</span><a href="http://bioinf.uni-greifswald.de/" target="_blank" class="mainleveltop_">Bioinformatics Group</a>
      </td>
    </tr>
  </table>
</div>
<div id="banner">
   <div id="banner_links">
       <a href="http://www.math-inf.uni-greifswald.de/mathe/index.php" title="Institut f&uuml;r Mathematik und Informatik"><img src="../../images/header.gif" alt="Directly to home" /> </a>
   </div>
   <div id="banner_mitte">
      <div id="bannertitel1">
        Bioinformatics Web Server at University of Greifswald
      </div>
      <div id="bannertitel2">
        Gene Prediction with AUGUSTUS
      </div>
   </div>
   <div id="banner_rechts">
     <a href="http://www.math-inf.uni-greifswald.de/mathe/index.php/geschichte-und-kultur/167" title="Voderberg-Doppelspirale">
     <img src="../../images/spirale.gif" align="left" />
     </a>
   </div>
</div>

<div id="wegweiser">
  Navigation for: &nbsp; &nbsp;<span class="breadcrumbs pathway">
    Training Results
</span>

  <div class="beendeFluss"></div>
</div>
<!-- ***** Ende: Kopfbereich *********************************************// -->
<!-- ***** Start: Koerper ************************************************// -->
<div id="koerper">

  <div id="linke_spalte">
     <ul class="menu">
         <li><div id="linksMenuText">AUGUSTUS Web Server Navigation</div></li>
         <li><a href="../../index.gsp"><span>Introduction</span></a></li>
         <li><a href="../../about.gsp"><span>About AUGUSTUS</span></a></li>
         <li><a href="../../accuracy.gsp"><span>Accuracy</span></a></li>
         <li><a href="../../trainingtutorial.gsp"><span>Training Tutorial</span></a></li>
         <li><g:link controller="training" action="create"><span>Submit Training</span></g:link></li>
         <li><a href="../../predictiontutorial.gsp"><span>Prediction Tutorial</span></a></li>
         <li><g:link controller="prediction" action="create"><span>Submit Prediction</span></g:link></li>
         <li><a href="../../help.gsp"><span>Help</span></a></li>
         <li><a href="../../datasets.gsp"><span>Datasets for Download</span></a></li>
         <li><a href="../../predictions_for_download.gsp"><span>Predictions for Download</span></a></li>
         <li><a href="../../references.gsp"><span>Links & References</span></a></li>
         <li><a href="../../impressum.gsp"><span>Impressum</span></a></li>
	  <li>&nbsp;</li>
         <li><div id="linksMenuText">Other AUGUSTUS Resources</div></li>
         <li><a href="http://bioinf.uni-greifswald.de/bioinf/wiki/pmwiki.php?n=Augustus.Augustus">AUGUSTUS Wiki</a></li>
         <li><a href="http://bioinf.uni-greifswald.de/bioinf/wiki/pmwiki.php?n=Forum.Forum">AUGUSTUS Forum</a></li>
         <li><a href="http://bioinf.uni-greifswald.de/augustus/downloads/index.php">Download AUGUSTUS</a></li>
         <li><a href="http://bioinf.uni-greifswald.de/augustus">Old AUGUSTUS gene prediction web server</a></li>
	  <li>&nbsp;</li>
         <li><div id="linksMenuText">Other Links</div></li>
         <li><a href="http://bioinf.uni-greifswald.de"><span>Bioinformatics Group Greifswald</span></a></li>
     </ul>
  </div>

<div id="mittel_spalte">
    <a name="inhalt" id="inhalt"></a>
    <table class="contentpaneopen">
      <tr>
	<td class="contentheading" width="100%">
	  <font color="#006699">Training AUGUSTUS<br>Job ${trainingInstance.accession_id}</font>
        </td>
      </tr>
    </table>
		
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
				<p><b><font color="#FF0000">Data duplication!</font></b> A job with identical data was submitted before. You find the old job at <a href="${trainingInstance.old_url}">${trainingInstance.old_url}</a>.</p>
			</g:if>
		</g:if>

		
		<g:if test="${trainingInstance.job_status >= '4' && trainingInstance.results_urls != null}">
			<hr>
			<h2><font color="#006699">Results</font></h2>
			${trainingInstance.results_urls}
			<p><b>Instructions</b></p>
			<p>Please download the files listed above by clicking on the links.</p>
			<p>All files and folders (except for the *.log and *.err file) are compressed. To unpack <tt>*.tar.gz</tt> archives, e.g. on linux type<br><br>
			<tt>tar -xzvf *.tar.gz</tt><br><br>
			For unpacking <tt>*.gz</tt> files, e.g. on linux type<br><br>
			<tt>gunzip *.gz.</tt></p>
			<p>Further instructions about results contents are given at the <a href="../../trainingtutorial.gsp">Training Tutorial</a> and the <a href="../../predictiontutorial.gsp">Prediction Tutorial</a>. Typical error messages from the log files are explained at <a href="../../help.gsp#noResults">Help</a>.</p>
		</g:if>
		<hr>
		<h2><font color="#006699">Messages</font></h2>
		<p><pre>${trainingInstance.message}</pre></p>	
		<hr>
</div>

<div id="rechte_spalte">
    <div class="linien_div">
      <h5 class="ueberschrift_spezial">CONTACT</h5>
      <strong>Institute for Mathematics und Computer Sciences</strong><br/>
      <strong>Bioinformatics Group</strong><br />
      Walther-Rathenau-Stra&szlig;e 47<br />
      17487 Greifswald<br />
      Germany<br />
      Tel.: +49 (0)3834 86 - 46 24<br/>
      Fax:  +49 (0)3834 86 - 46 40<br /><br />
      <a href="mailto:augustus-web@uni-greifswald.de" title="E-Mail augustus-web@uni-greifswald.de, opens the standard mail program">augustus-web@uni-greifswald.de</a>
    </div>

    <div class="beendeFluss"></div>
</div>
</div>

<!-- ***** Ende: Koerper *************************************************// -->
<!-- ***** Start: Fuss ***************************************************// -->
<div id="fuss">
  <div id="fuss_links"><p class="copyright">&copy; 2011 University of Greifswald</p></div>
  <div id="fuss_mitte">
  <div class="bannergroup">
 </div>
 </div>
 <div id="fuss_rechts" >
  <ul>
   <li>
    <a href="#seitenanfang">
     <img hspace="5" height="4" border="0" width="7" alt="Seitenanfang" src="../images/top.gif" />
     Top of page
    </a>
   </li>
  </ul>
 </div>
 <div class="beendeFluss"></div>
</div>
<!-- ***** Ende: Fuss ***************************************************// -->

    </body>
</html>
