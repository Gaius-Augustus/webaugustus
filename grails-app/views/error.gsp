<!DOCTYPE html>
<html>
	<head>
		<title><g:if env="development">Grails Runtime Exception</g:if><g:else>Error</g:else></title>
		<meta name="layout" content="main">
		<asset:stylesheet src="errors.css"/>
	</head>
	<body>
<!--        <g:if env="development">
-->
            <p>
                Ooops... this looks ugly! Our apologies! Please send an e-mail to 
                <a href="mailto:augustus-web@uni-greifswald.de">augustus-web@uni-greifswald.de</a>. 
                Please tell us date and time and any actions from your side that 
                may have caused this problem. Please also attach a copy of the below 
                shown error log to your e-mail. Thanks for you help!
            </p>
            <g:renderException exception="${exception}" />
<!--        </g:if>
        <g:else>
			<ul class="errors">
				<li>An error has occurred</li>
			</ul>
            <p>
                Ooops... this looks ugly! Our apologies! Please send an e-mail to 
                <a href="mailto:augustus-web@uni-greifswald.de">augustus-web@uni-greifswald.de</a>. 
                Please tell us date and time and any actions from your side that 
                may have caused this problem.
            </p>
        </g:else>
-->
	</body>
</html>
