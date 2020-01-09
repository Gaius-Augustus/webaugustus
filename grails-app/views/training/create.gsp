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
               <g:hasErrors bean="${training}">
                  <div class="errors">
                     <g:renderErrors bean="${training}" as="list" />
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
                           <p><b>Before submitting a training job</b> for your species of interest, please check whether parameters have already been trained and have been made publicly available for your species at <a href="${createLink(uri:'/predictiontutorial#param_id')}">our species overview table</a></p>
                           <p>Please read the <a href="${createLink(uri:'/trainingtutorial')}">training tutorial</a> before submitting a job for the first time. Example data for this form is available <a href="${createLink(uri:'/trainingtutorial#exampledata')}">here</a>. You may also use the button below to insert sample data. Please note that you will always need to enter the verification string at the bottom of the page, yourself, in order to submit a job!</p>
                           <p><g:actionSubmit action="fillSample" value="Fill in Sample Data" /></p>
                           <p>We strongly recommend that you specify an <b>e-mail address</b>! Please read the <a href="${createLink(uri:'/help#email')}"><small>Help</small></a> page before submitting a job without e-mail address! You have to give a <b>species name</b>, and a <b>genome file</b>!</p>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="email_adress">e-mail</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:training,field:'email_adress','errors')}">
                                       <input type="text" id="email_adress" name="email_adress" value="${fieldValue(bean:training,field:'email_adress')}"/> 
                                       &nbsp;<a href="${createLink(uri:'/help#email')}"><small>Help</small></a><br>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop" colspan="2">
                                       <%--<g:if test="${training.agree_email == true}"><div class="prop_warn"></g:if>--%>
                                       <table><tr><td class="prop">
                                          <g:checkBox id="agree_email" name="agree_email" value="${training.agree_email}" />
                                       </td><td class="prop">
                                           <label for="agree_email">
                                               If I provide an e-mail address, I consent to the processing of my personal data in 
                                               accordance with the <a href="//bioinf.uni-greifswald.de/bioinf/datenschutz.html">Data Privacy Protection</a> declaration.<br>
                                               I agree to receive e-mails that are related to the particular AUGUSTUS job that I submitted.
                                           </label>
                                       </td></tr></table>
                                       <%--<g:if test="${training.agree_email == true}"></div></g:if>--%>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="project_name">Species name <font color="#FF0000">*</font></label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:training,field:'project_name','errors')}">
                                       <input type="text" maxlength="30" id="project_name" name="project_name" value="${fieldValue(bean:training,field:'project_name')}"/> &nbsp; <a href="${createLink(uri:'/help#species_name')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" style="padding-left: 0px">
                                       <br>
                                       There are two options for sequence file (fasta format) transfer:
                                       <br>You may <b>either</b> upload data files from your computer <b>or</b> specify web links. &nbsp; 
                                       <a href="${createLink(uri:'/help#upload_link')}"><small>Help</small></a><br><br>
                                       <font color="#FF0000">Please read our <a href="${createLink(uri:'/help#most_common_problem')}">instructions about fasta headers</a>
                                       before using this web service!</font>
                                       Most problems with this web service are caused by a wrong fasta header format!
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>Genome file</b> <font color="#FF0000">*</font>&nbsp; (max. 250000 scaffolds) <a href="${createLink(uri:'/help#genome_file')}"><small>Help</small></a
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="GenomeFile">Upload a file <font size="1">(max. 100 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${training?.has_genome_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="GenomeFile" name="GenomeFile"/>
                                       <g:if test="${training?.has_genome_file == true}">
                                          </div>
                                       </g:if>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="genome_ftp_link">specify web link to genome file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:training,field:'genome_ftp_link','errors')}">
                                       <input type="text" id="genome_ftp_link" name="genome_ftp_link" value="${fieldValue(bean:training,field:'genome_ftp_link')}"/> 
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" style="padding-left: 0px">
                                       <br>
                                       You need to specify <b>at least one</b> of the following files: <font color="#FF0000">*</font> <a href="${createLink(uri:'/help#which_files')}"><small>Help</small></a><br><br>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>cDNA file</b> &nbsp; <small><b><i>Non-commercial users only</i></b></small> &nbsp; <a href="${createLink(uri:'/help#cDNA')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="EstFile">Upload a file <font size="1">(max. 100 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${training?.has_est_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="EstFile" name="EstFile"/>
                                       <g:if test="${training?.has_est_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="est_ftp_link">specify web link to cDNA file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:training,field:'est_ftp_link','errors')}">
                                       <input type="text" id="est_ftp_link" name="est_ftp_link" value="${fieldValue(bean:training,field:'est_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td><br></td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>Protein file</b> &nbsp; <small><b><i>Non-commercial users only</i></b></small> &nbsp; <a href="${createLink(uri:'/help#protein')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="ProteinFile">Upload a file <font size="1">(max. 100 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${training?.has_protein_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="ProteinFile" name="ProteinFile"/>
                                       <g:if test="${training?.has_protein_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="protein_ftp_link">specify web link to protein file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:training,field:'protein_ftp_link','errors')}">
                                       <input type="text" id="protein_ftp_link" name="protein_ftp_link" value="${fieldValue(bean:training,field:'protein_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td><br></td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>Training gene structure file</b> &nbsp; <a href="${createLink(uri:'/help#structure')}"><small>Help</small></a> <font color="#FF0000">(gff or gb format, no gzip!)</font>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="StructFile">Upload a file <font size="1">(max. 200 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${training?.has_struct_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="StructFile" name="StructFile"/> 
                                       <g:if test="${training?.has_struct_file == true}"></div></g:if>
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
                           <br><br>
                           <p> 
                              <label style="display: inline;">
                                  <g:checkBox name="agree_nonhuman" value="${training.agree_nonhuman}" />
                                  &nbsp;<b>I am not submitting personalized human sequence data (mandatory).
                              </label>
                              &nbsp;<font color="#FF0000">*</font></b> 
                              &nbsp;<a href="${createLink(uri:'/help#nonhuman')}"><small>Help</small></a>
                           </p>
                           <br>
                           <p>We use a <b>verification string</b> to figure out whether you are a <b>human</b>. Please type the text in the image below into the text field next to the image.
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${training?.warn == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <label style="display: inline;">
                                           <img src="${createLink(controller: 'simpleCaptcha', action: 'captcha')}" style="vertical-align: bottom;"/> &nbsp; &nbsp; 
                                           <g:textField name="captcha"/>
                                       </label>
                                       &nbsp;<font color="#FF0000">*</font>
                                       <g:if test="${training?.warn == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <p><font color="#FF0000">*</font>) mandatory input arguments</p>
                        </div>
                        <div id="commit-button" class="buttons" onclick="toggle_visibility('commit-button');toggle_visibility('aug-spinner');" style="display:block;">
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

