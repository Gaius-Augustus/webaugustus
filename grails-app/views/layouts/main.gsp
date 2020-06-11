<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html lang="en" class="no-js"><!--<![endif]-->
	<head>
        <title><g:layoutTitle default="AUGUSTUS Training Server"/></title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
        <meta name="DC.Creator" content="Katharina J. Hoff">
        <meta name="DC.Publisher" content="Institute for Mathematics and Computer Science, Bioinformatics Group, Greifswald.">
        <meta name="DC.Publisher" content="Katharina J. Hoff">
        <meta name="DC.Format" content="text/html">
        <meta name="DC.Language" SCHEME="ISO639-1" CONTENT="en">
        <meta name="DC.Title" content="Web interface to AUGUSTUS training">
        <meta name="DC.Subject" content="Eukaryotic gene finding program">
        <meta name="DC.Description" content="Web interface to AUGUSTUS training">
        <meta name="DC.Type" content="Service">
        <meta name="DC.Identifier" content="//bioinf.uni-greifswald.de/webaugustus/">
        <meta name="DC.Date" content="2011-05-04">
        <meta charset="utf-8">
        <meta name="robots" content="index, follow">
        <meta name="revisit-after" content="7 days">
        <meta name="abstract" content="Bioinformatics Greifswald">
        <meta name="keywords" content="Bioinformatics Greifswald">
        <meta name="description" content="University of Greifswald">
        <meta property="author" content="University of Greifswald">

        <link rel="shortcut icon" href="${assetPath(src: 'favicon.ico')}" type="image/x-icon">
        <link rel="apple-touch-icon" href="${assetPath(src: 'apple-touch-icon.png')}">
        <link rel="apple-touch-icon" sizes="114x114" href="${assetPath(src: 'apple-touch-icon-retina.png')}">
        <asset:stylesheet src="application.css"/>
        <asset:javascript src="application.js"/>
        <g:layoutHead />
        <style>.stickyFixed {position: fixed !important;  top:0 !important;} </style>
        <!--[if IE ]>
        <style type="text/css">
            body {width: 120ex;}
        </style>        
       <![endif]-->

    </head>
	<body id="page-6289" class="">
      <!-- dark header topbar -->
      <div class="topbar">
      </div>
      <!-- header with uni logo -->
      <header class="header">
         <a name="seitenanfang"></a>
         <div class="header__content">
            <div class="header__top-wrapper">
               <!-- left side banner white spacer -->
               <div class="header__submenu"></div>
               <div class="logo">
                  <a href="https://www.uni-greifswald.de/" title="Universität Greifswald" class="logo-main">
                  <img src="/bioinf/images/uni-greifswald_opt.svg" width="400" height="118" alt="Universität Greifswald" title="Universität Greifswald" >
                  </a>
               </div>
               <!-- middle part of header -->
               <div class="organization">
                  <a href="//bioinf.uni-greifswald.de/">
                     <h3>Bioinformatics Web Server</h3>
                  </a>
               </div>
            </div>
            <nav id="nav" class="navigation">
               <ul class="navigation-list navigation-list--table">
                  <li class="navigation-list__item navigation-list__item--level-1 navigation-list__item--active" data-dropdown="true"><a href="//bioinf.uni-greifswald.de/">Bioinformatics Group</a></li>
                  <li class="navigation-list__item navigation-list__item--level-1" data-dropdown="true"><a href="https://math-inf.uni-greifswald.de/">Mathematics and Computer Science</a></li>
                  <li class="navigation-list__item navigation-list__item--level-1" data-dropdown="true"><a href="https://mnf.uni-greifswald.de/en/faculty/">Faculty of Math and Natural Sciences</a></li>
               </ul>
            </nav>
         </div>
      </header>
      <div class="container">
         <div class="grid">
            <div class="column-1 grid__column grid__column--md-3">
               <ul class="navigation-sub">
                  <li class="navigation-sub__item">
                     <span class="navigation-sub__headline">AUGUSTUS Web Server Navigation</span>
                     <ul class="navigation-sub">
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/index')}">Introduction</a></li>
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/about')}">About AUGUSTUS</a></li>
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/accuracy')}">Accuracy</a></li>
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/trainingtutorial')}">Training Tutorial</a></li>
                        <li class="navigation-sub__item"><g:link controller="training" action="create">Submit Training</g:link></li>
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/predictiontutorial')}">Prediction Tutorial</a></li>
                        <li class="navigation-sub__item"><g:link controller="prediction" action="create">Submit Prediction</g:link></li>
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/datasets')}">Datasets for Download</a></li>
                        <li class="navigation-sub__item"><a href="${createLink(uri:'/references')}">Links & References</a></li>
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/bioinf/impressum.html">Impressum</a></li>
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/bioinf/datenschutz.html">Data Privacy Protection</a></li>
                     </ul>
                  </li>
                  <li class="navigation-sub__item">
                     <span class="navigation-sub__headline">Other AUGUSTUS Resources</span>
                     <ul class="navigation-sub">
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/bioinf/wiki/pmwiki.php?n=Augustus.Augustus">AUGUSTUS Wiki</a></li>
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/bioinf/forum/">AUGUSTUS Forum</a></li>
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/augustus/downloads/index.php">Download AUGUSTUS</a></li>
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/augustus/">Old AUGUSTUS web server</a></li>
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de/bioinf/braker/">BRAKER</a></li>
                     </ul>
                  </li>
                  <li class="navigation-sub__item">
                     <span class="navigation-sub__headline">Other Links</span>
                     <ul class="navigation-sub">
                        <li class="navigation-sub__item"><a href="//bioinf.uni-greifswald.de">Bioinformatics Greifswald</a></li>
                     </ul>
                  </li>
               </ul>
            </div>
            <div class="column-2 grid__column grid__column--md-9">
                <g:layoutBody />
           </div>
         </div>
      </div>
      <footer class="footer footer--padding-bottom">
         <div class="footer-column footer-column--dark">
            <div class="footer__content-wrapper">
               <div class="footer-bottom">
                  <div class="footer-bottom__copyright">
                     <div style="float: left;">©&nbsp;2019&nbsp; Universität Greifswald</div>
                     <div style="text-align: center;"><a href="//bioinf.uni-greifswald.de/bioinf/datenschutz.html">&nbsp;Data&nbsp;Privacy&nbsp;Protection&nbsp;</a></div>
                  </div>
               </div>
            </div>
         </div>
      </footer>
        <div id="spinner" class="spinner" style="display:none;"><g:message code="spinner.alt" default="Loading&hellip;"/></div>
	</body>
</html>
