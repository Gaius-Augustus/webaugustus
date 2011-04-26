

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
        <meta name="layout" content="main" />
        <title>Create Prediction</title>         
    </head>
    <body>
        <div class="headline" id="headline">
                <h1 class="title" id="title"><a href="create.gsp:" id="applink" name="applink">AUGUSTUS</a> <span class="subtitle" id="subtitle">[Prediction Submission]</span></h1>
         </div>
         <div id="appnav">
             <ul>
                 <li><a href="../index.gsp" >Introduction</a></li>
                 <li><g:link controller="training" action="create">Training Submission</g:link></li>
                 <li><g:link controller="prediction" action="create">Prediction Submission</g:link></li>
                 <li><g:link controller="help" action="list">Help</g:link></li>
                 <li><a href="../references.gsp">Links & References</a></li>
                 <li><a href="http://gobics.de/department/" title="Our department's homepage">Department</a></li>
             </ul>
         </div>
        <div class="body">
            <g:if test="${flash.message}">
            <div class="message">${flash.message}</div>
            </g:if>
            <g:hasErrors bean="${predictionInstance}">
            <div class="errors">
                <g:renderErrors bean="${predictionInstance}" as="list" />
            </div>
            </g:hasErrors>
            <g:if test="${flash.error}">
                 <div class="errors">
                    &nbsp; <img src="../images/skin/exclamation.png"> &nbsp; ${flash.error}
                 </div>
            </g:if>
            <div class="main" id="main">
            <g:uploadForm action="commit" method="post" >
            <fieldset><legend><b>Data Input for running AUGUSTUS with pre-trained parameters</b></legend><p>
                <div class="dialog">
                    <table>
                        <tbody>
                                <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="email_adress">E-mail</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'email_adress','errors')}">
                                    <input type="text" id="email_adress" name="email_adress" value="${fieldValue(bean:predictionInstance,field:'email_adress')}"/> &nbsp; <g:link controller="help" action="list" fragment="email"><small>Help</small></g:link>
                                </td> 
                                </tr>
                          </tbody> 
                      </table>
                      <br>
                      You must <b>either</b> upload a *.tar.gz archive with AUGUSTUS species parameters from your computer <b>or</b> specify a project identifier: &nbsp; <g:link controller="help" action="list" fragment="which_files_pred"><small>Help</small></g:link>
                      <br>
                      <br>
                      <table>
                         <tbody>
                             <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="ArchiveFile">Parameter Archive</label>
                                </td>
                                <td valitn="top">
                                    <input type="file" id="ArchiveFile" name="ArchiveFile"/> &nbsp; <g:link controller="help" action="list" fragment="archive"><small>Help</small></g:link>
                                </td>
                                <td>&nbsp;<b>or</b>&nbsp;</td>
                                <td valign="top" class="name">
                                    <label for="genome_ftp_link">project identifier</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'project_id','errors')}">
                                    <input type="text" id="project_id" name="project_id" value="${fieldValue(bean:predictionInstance,field:'project_id')}"/> &nbsp; <g:link controller="help" action="list" fragment="project_id"><small>Help</small></g:link>
                                </td>
                            </tr> 
                        </tbody>
                    </table>
                    <br>
                      You must <b>either</b> upload a genome file from your computer <b>or</b> specify a web link to a genome file: &nbsp; <g:link controller="help" action="list" fragment="upload_link"><small>Help</small></g:link>
                      <br>
                      <br>
                      <table>
                         <tbody>
                            <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="GenomeFile"><b>Genome file</b></label>
                                </td>
                                <td valitn="top">
                                    <input type="file" id="GenomeFile" name="GenomeFile"/>
                                </td>
                                <td>&nbsp;<b>or</b>&nbsp;</td>
                                <td valign="top" class="name">
                                    <label for="genome_ftp_link">web link to genome file</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'genome_ftp_link','errors')}">
                                    <input type="text" id="genome_ftp_link" name="genome_ftp_link" value="${fieldValue(bean:predictionInstance,field:'genome_ftp_link')}"/> &nbsp; <g:link controller="help" action="list" fragment="genome_file"><small>Help</small></g:link>
                                </td>
                            </tr> 
                          </tbody>
                        </table>
                        <br>
                        You may (optional) also specify these files: <g:link controller="help" action="list" fragment="which_files"><small>Help</small></g:link><br><br>
                        <table>
                          <tbody>
                            <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="EstFile">cDNA file</label>
                                </td>
                                <td valign="top">
                                    <input type="file" id="EstFile" name="EstFile"/>
                                </td>
                            <td>&nbsp;<b>or</b>&nbsp;</td>
                                <td valign="top" class="name">
                                    <label for="est_ftp_link">web link to cDNA file</label>
                                </td>
                                <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'est_ftp_link','errors')}">
                                    <input type="text" id="est_ftp_link" name="est_ftp_link" value="${fieldValue(bean:predictionInstance,field:'est_ftp_link')}"/> &nbsp; <g:link controller="help" action="list" fragment="cDNA"><small>Help</small></g:link>
                                </td>
                            </tr> 
                            <tr class="prop">
                                <td valign="top" class="name">
                                    <label for="hint_file">Hint file</label>
                                </td>
                                <td valign="top">
                                    <input type="file" id="HintFile" name="HintFile"/> 
                                </td>
                            <td><g:link controller="help" action="list" fragment="structure"><small>Help</small></g:link></td>
                                <td valign="top" class="name">
                                   
                                </td>
                                <td valign="top">
                                    
                                </td>
                            </tr> 
                        </tbody>
                    </table>
                    <br>
                    <g:checkBox name="utr" value="${false}" /> predict UTRs <g:link controller="help" action="list" fragment="utr"><small>Help</small></g:link>

                    <br><br>
                </div>
                <div class="buttons">
                    <span class="button"><input class="commit" type="submit" value="Start Predicting" /></span>
                </div>
            </g:uploadForm>
            </div>
            <p>&nbsp;</p>
            <p style="text-align:right;">
            <small>Please direct your questions and comments to <a href="mailto:augustus-training@gobics.de">augustus-training@gobics.de</a></small>
            </p>
        </div>
    </body>
</html>
