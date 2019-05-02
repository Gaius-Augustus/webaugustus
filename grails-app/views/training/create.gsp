<!DOCTYPE html>
<html>
	<head>
		<meta name="layout" content="main"/>
		<title>Bioinformatics Web Server - University of Greifswald</title>
        <meta name="date" content="2018-07-17">
        <meta name="lastModified" content="2018-07-16">
	</head>
	<body>
               <g:if test="${flash.message}">
                  <div class="message">${flash.message}</div>
               </g:if>
               <g:hasErrors bean="${trainingInstance}">
                  <div class="errors">
                     <g:renderErrors bean="${trainingInstance}" as="list" />
                  </div>
               </g:hasErrors>
               <g:if test="${flash.error}">
                  <div class="errormessage">${flash.error}</div>
               </g:if>
               <main class="main-content">
                  <div id="c180465" class="csc-default">
                     <div class="csc-header csc-header-n1">
                        <h1 class="csc-firstHeader">Data Input for Training AUGUSTUS</h1>
                     </div>
                  </div>
                  <div id="c261665" class="csc-default">
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                     <noscript>
                        <p><b><span style="color:red">Please enable javascript in your browser in order to display the submission form correctly!</span></b> Form functionality is not affected significantly while javascript is disabled, but it looks less pretty.</p>
                     </noscript>
                     <g:uploadForm action="commit" method="post" >
                        <fieldset>
                        <p>
                        <div class="dialog">
                           <p>Use this form to submit data for training AUGUSTUS parameters for novel species/new genomic data.</p>
                           <p><b>Before submitting a training job</b> for your species of interest, please check whether parameters have already been trained and have been made publicly available for your species at <a href="../predictiontutorial.gsp#param_id">our species overview table</a></p>
                           <p>Please read the <a href="../trainingtutorial.gsp">training tutorial</a> before submitting a job for the first time. Example data for this form is available <a href="../trainingtutorial.gsp#exampledata">here</a>. You may also use the button below to insert sample data. Please note that you will always need to enter the verification string at the bottom of the page, yourself, in order to submit a job!</p>
                           <g:actionSubmit action="fillSample" value="Fill in Sample Data" />
                           <p>We strongly recommend that you specify an <b>E-mail address</b>! Please read the <a href="../help.gsp#email"><small>Help</small></a> page before submitting a job without e-mail address! You have to give a <b>species name</b>, and a <b>genome file</b>!</p>
                           <p><b>Current problem:</b> Regrettably, our server is currently connected to the internet via a rather unreliable connection. This may cause connection timeouts (caused by server side) when uploading big files. Please use the web link upload option, instead, if you experience such problems. We apologize for the inconvenience!</p>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="email_adress">E-mail</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:trainingInstance,field:'email_adress','errors')}">
                                       <input type="text" id="email_adress" name="email_adress" value="${fieldValue(bean:trainingInstance,field:'email_adress')}"/> 
                                       &nbsp; <a href="../help.gsp#email"><small>Help</small></a><br>
                                       <!--<g:if test="${trainingInstance.agree_email == true}"><div class="prop_warn"></g:if>-->
                                       &nbsp; 
                                       <g:checkBox name="agree_email" value="${trainingInstance?.agree_email}" />
                                       &nbsp;If I provide an e-mail address, I agree that it will be stored on the server until the computations of my job have finished. I agree to receive e-mails that are related to the particular AUGUSTUS job that I submitted.
                                       <!--<g:if test="${trainingInstance.agree_email == true}"></div></g:if>-->
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="project_name">Species name <font color="#FF0000">*</font></label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:trainingInstance,field:'project_name','errors')}">
                                       <input type="text" maxlength="30" id="project_name" name="project_name" value="${fieldValue(bean:trainingInstance,field:'project_name')}"/> &nbsp; <a href="../help.gsp#species_name"><small>Help</small></a>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <p>There are two options for sequence file (fasta format) transfer:<br>You may <b>either</b> upload data files from your computer <b>or</b> specify web links. &nbsp; <a href="../help.gsp#upload_link"><small>Help</small></a><br><br><font color="#FF0000">Please read our <a href="../help.gsp#most_common_problem">instructions about fasta headers</a> before using this web service!</font> Most problems with this web service are caused by a wrong fasta header format!</p>
                           <br>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="GenomeFile"><b>Genome file</b> <font color="#FF0000">*</font>&nbsp; (max. 250000 scaffolds) <a href="../help.gsp#genome_file"><small>Help</small></a></label>
                                    </td>
                                    <td valign="top">
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">Upload a file <font size="1">(max. 100 MB)</font>:</td>
                                    <td valign="top">
                                       <g:if test="${trainingInstance.has_genome_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="GenomeFile" name="GenomeFile"/>
                                       <g:if test="${trainingInstance.has_genome_file == true}">
                                          </div>
                                       </g:if>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td>&nbsp;<b>or</b>&nbsp;</td><td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="genome_ftp_link">specify web link to genome file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:trainingInstance,field:'genome_ftp_link','errors')}">
                                       <input type="text" id="genome_ftp_link" name="genome_ftp_link" value="${fieldValue(bean:trainingInstance,field:'genome_ftp_link')}"/> 
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           You need to specify <b>at least one</b> of the following files: <font color="#FF0000">*</font> <a href="../help.gsp#which_fiiles"><small>Help</small></a><br><br>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="EstFile"><b>cDNA file</b> &nbsp; <small><b><i>Non-commercial users only</i></b></small> &nbsp; <a href="../help.gsp#cDNA"><small>Help</small></a></label>
                                    </td>
                                    <td valign="top">
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">Upload a file <font size="1">(max. 100 MB)</font>:</td>
                                    <td valign="top">
                                       <g:if test="${trainingInstance.has_est_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="EstFile" name="EstFile"/>
                                       <g:if test="${trainingInstance.has_est_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td>&nbsp;<b>or</b>&nbsp;</td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="est_ftp_link">specify web link to cDNA file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:trainingInstance,field:'est_ftp_link','errors')}">
                                       <input type="text" id="est_ftp_link" name="est_ftp_link" value="${fieldValue(bean:trainingInstance,field:'est_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td><br></td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="ProteinFile"><b>Protein file</b> &nbsp; <small><b><i>Non-commercial users only</i></b></small> &nbsp; <a href="../help.gsp#protein"><small>Help</small></a></label>
                                    </td>
                                    <td valign="top">
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">Upload a file <font size="1">(max. 100 MB)</font>:</td>
                                    <td valign="top">
                                       <g:if test="${trainingInstance.has_protein_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="ProteinFile" name="ProteinFile"/>
                                       <g:if test="${trainingInstance.has_protein_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td>&nbsp;<b>or</b>&nbsp;</td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="protein_ftp_link">specify web link to protein file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:trainingInstance,field:'protein_ftp_link','errors')}">
                                       <input type="text" id="protein_ftp_link" name="protein_ftp_link" value="${fieldValue(bean:trainingInstance,field:'protein_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td><br></td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="struct_file"><b>Training gene structure file</b> &nbsp; <a href="../help.gsp#structure"><small>Help</small></a> <font color="#FF0000">(gff or gb format, no gzip!)</font></label>
                                    </td>
                                    <td valign="top"></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">Upload a file <font size="1">(max. 200 MB)</font>:</td>
                                    <td valign="top">
                                       <g:if test="${trainingInstance.has_struct_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="StructFile" name="StructFile"/> 
                                       <g:if test="${trainingInstance.has_struct_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           <!-- show some content upon click -->
                           <h2>Possible file combinations [<a title="show/hide" id="exp_file_options_link" href="javascript: void(0);" onclick="toggle(this, 'exp_file_options');"  style="text-decoration: none; color: #006699; ">click to minimize</a>]</h2>
                           <div id="exp_file_options" style="padding: 3px;">
                              <p>
                              <ul>
                                 <li><b>{genome file, cDNA file}</b><br>In this case, the cDNA file is used to create a training gene set. If cDNA quality is sufficient, also a UTR training set will be created.</li>
                                 <li><b>{genome file, protein file}</b><br>In this case, the protein file is used to create a training gene set. No UTR training set can be created.</li>
                                 <li><b>{genome file, gene structure file}</b><br>In this case, the gene structure file is used as a training gene set. If the gene structure file contains UTR elements, also a UTR training set will be created.</li>
                                 <li><b>{genome file, cDNA file, protein file}</b><br>In this case, the protein file will be used to create a training gene set. No UTR training set will be created. cDNA sequences will be used as evidence for prediction, only.</li>
                                 <li><b>{genome file, cDNA file, gene structure file}</b><br>In this case, the gene structure file is used as a training gene set. If the gene structure file contains UTR elements, also a UTR training set will be created. cDNA sequences will be used as evidence for prediction, only.</li>
                              </ul>
                              </p>
                              <h2>File combinations that are currently not supported</h2>
                              <p>
                              <ul>
                                 <li><b>{genome file, cDNA file, protein file, gene structure file}</b></li>
                                 <li><b>{genome file, protein file, gene structure file}</b></li>
                              </ul>
                              </p>
                           </div>
                           <script language="javascript">toggle(getObject('exp_file_options_link'), 'exp_file_options');</script>
                           <!-- end of javascript content on click -->
                           <br>
                           <p>
                              &nbsp; 
                              <g:checkBox name="agree_nonhuman" value="${trainingInstance?.agree_nonhuman}" />
                              &nbsp;<b>I am not submitting personalized human sequence data (mandatory).</b> <a href="../help.gsp#nonhuman"><small>Help</small></a>
                           </p>
                           <p>We use a <b>verification string</b> to figure out whether you are a <b>human</b>. Please type the text in the image below into the text field next to the image.
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${trainingInstance.warn == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <img src="${createLink(controller: 'simpleCaptcha', action: 'captcha')}"/> &nbsp; &nbsp; <g:textField name="captcha"/><font color="#FF0000">*</font>
                                       <g:if test="${trainingInstance.warn == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <p><font color="#FF0000">*</font>) mandatory input arguments</p>
                        </div>
                        <div class="buttons" onclick="toggle_visibility('aug-spinner');">
                           <span class="button"><input class="commit" type="submit" value="Start Training" /></span>
                        </div>
                     </g:uploadForm>
                     <br>
                     <table>
                        <tr>
                           <td>
                              <div id="aug-spinner" style='display:none;' align="center">
                                 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<g:img dir="images" file="compete.gif" border="0" alt="Spinner" /><br>
                                 <p>We are processing your request... please do not close this window and do not click on the submission button again, until this message disappears!</p>
                              </div>
                           </td>
                           <td><g:img dir="images" file="spacer.jpg" /></td>
                        </tr>
                     </table>
                  <p>&nbsp;</p>
                  <div class="csc-default">
                     <div class="divider">
                        <hr>
                     </div>
                  </div>
            </div>
            </main>
    </body>
</html>

