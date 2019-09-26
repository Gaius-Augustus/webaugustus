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
                        <h1 class="csc-firstHeader">Welcome to the WebAUGUSTUS Service</h1>
                     </div>
                  </div>
                  <div id="c261665" class="csc-default">
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
    <p> AUGUSTUS is a program that predicts genes in eukaryotic genomic sequences. This web server provides an interface for training AUGUSTUS for predicting genes in genomes of novel species. It also enables you to predict genes in a genome sequence with already trained parameters.</p>
    <p>AUGUSTUS usually belongs to the most accurate programs for the species it is trained for. Often it is the most accurate ab initio program. For example, at the independent gene finder assessment (EGASP) on the human ENCODE regions AUGUSTUS was the most accurate gene finder among the tested ab initio programs. At the more recent nGASP (worm), it was among the best in the ab initio and transcript-based categories. See <a href="${createLink(uri:'/accuracy')}">accuracy statistics</a> for further details.</p>
    <p>Please be aware that gene prediction accuracy of AUGUSTUS always depends on the quality of the training gene set that was used for training species specific parameters. You should not expect the greatest accuracy from fully automated training gene generation as provided by this web server application. Instead, you should manually inspect (and maybe interatively improve) the training gene set.</p>
<p>AUGUSTUS is already trained for a number of genomes and you find the according parameter sets at <a href="${createLink(uri:'/predictiontutorial#param_id')}">the prediction tutorial</a>. <b>Please check whether AUGUSTUS was already trained for your species before submitting a new training job.</b></p>
    <p><a href="//bioinf.uni-greifswald.de/augustus/">The Old AUGUSTUS web server</a> offers similar gene prediction services but no parameter training service.</p>
<br><br>
<h2>OK, I got it! Take me straight to...</h2>
<p>
<ul>
  <li><g:link controller="training" action="create"><span>AUGUSTUS training submission</span></g:link></li>
  <li><g:link controller="prediction" action="create"><span>AUGUSTUS prediction submission</span></g:link></li>
</ul>
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
