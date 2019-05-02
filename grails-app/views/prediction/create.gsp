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
               <g:hasErrors bean="${predictionInstance}">
                  <div class="errors">
                     <g:renderErrors bean="${predictionInstance}" as="list" />
                  </div>
               </g:hasErrors>
               <g:if test="${flash.error}">
                  <div class="errormessage">${flash.error}</div>
               </g:if>
               
               <main class="main-content">
                  <div id="c180465" class="csc-default">
                     <div class="csc-header csc-header-n1">
                        <h1 class="csc-firstHeader">Data Input for Running AUGUSTUS</h1>
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
                     <g:uploadForm action="commit" method="post" name="submissionform">
                        <fieldset>
                        <p>
                        <div class="dialog">
                           <p>Use this form to submit your data for running AUGUSTUS on new genomic data with already available pre-trained parameters.</p>
                           <p>Please read the <a href="../predictiontutorial.gsp">prediction tutorial</a> before submitting a job for the first time. Example data for this form is available <a href="../predictiontutorial.gsp#exampledata">here</a>. You may also use the button below to insert sample data. Please note that you will always need to enter the verification string at the bottom of the page, yourself, in order to submit a job!</p>
                           <p><b>Current problem:</b> Regrettably, our server is currently connected to the internet via a rather unreliable connection. This may cause connection timeouts (caused by server side) when uploading big files. Please use the web link upload option, instead, if you experience such problems. We apologize for the inconvenience!</p>
                           <g:actionSubmit action="fillSample" value="Fill in Sample Data" />
                           <p>We recommend that you specify an <b>E-mail address</b>.</p>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="email_adress">E-mail</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'email_adress','errors')}">
                                       <input type="text" id="email_adress" name="email_adress" value="${fieldValue(bean:predictionInstance,field:'email_adress')}"/> &nbsp;
                                       <g:checkBox name="agree_email" value="${predictionInstance?.agree_email}" />
                                       &nbsp;If I provide an e-mail address, I agree that it will be stored on the server until the computations of my job have finished. I agree to receive e-mails that are related to the particular AUGUSTUS job that I submitted. <a href="../help.gsp#email"><small>Help</small></a>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           You must <b>either</b> upload a *.tar.gz archive with AUGUSTUS species parameters from your computer <b>or</b> specify a project identifier: &nbsp; <a href="../help.gsp#which_files_pred"><small>Help</small></a>
                           <br>
                           <br>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="ArchiveFile"><b>AUGUSTUS species parameters</b> <font color="#FF0000">*</font></label>
                                    </td>
                                    <td valitn="top">
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valitn="top">Upload an archive file  <font size="1">(max. 100 MB)</font>: &nbsp; <a href="../help.gsp#archive"><small>Help</small></a>
                                    </td>
                                    <td valitn="top">
                                       <g:if test="${predictionInstance.has_param_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="ArchiveFile" name="ArchiveFile"/></label>
                                       <g:if test="${predictionInstance.has_param_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td>&nbsp;<b>or</b>&nbsp;</td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="project_id">specify a project identifier:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'project_id','errors')}">
                                       <input type="text" id="project_id" name="project_id" value="${fieldValue(bean:predictionInstance,field:'project_id')}"/> <a href="../help.gsp#project_id"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td>&nbsp;<b>or</b>&nbsp;</td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="project_id">select an organism:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'species_select','errors')}">
                                       <g:if test="${predictionInstance.has_select == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <g:select name="species_select" from="${[
                                          'Acyrthosiphon pisum (animal)', 
                                          'Aedes aegypti (animal)', 
                                          'Amphimedon queenslandica (animal)', 
                                          'Apis mellifera (animal)', 
                                          'Bombus terrestris (animal)',
                                          'Brugia malayi (animal)', 
                                          'Caenorhabditis elegans (animal)', 
                                          'Callorhinchus milii (animal)', 
                                          'Camponotus floridanus (animal)',
                                          'Danio rerio (animal)',
                                          'Drosophila melanogaster (animal)', 
                                          'Gallus gallus domesticus (animal)',
                                          'Heliconius melpomene (animal)',
                                          'Homo sapiens (animal)', 
                                          'Nasonia vitripennis (animal)', 
                                          'Petromyzon marinus (animal)',
                                          'Rhodnius prolixus (animal)', 
                                          'Schistosoma mansoni (animal)', 
                                          'Tribolium castaneum (animal)', 
                                          'Trichinella spiralis (animal)', 
                                          'Tetrahymena thermophila (alveolata)',
                                          'Toxoplasma gondii (alveolata)',
                                          'Leishmania tarantolae (protozoa)',
                                          'Arabidopsis thaliana (plant)',
                                          'Chlamydomonas reinhardtii (alga)',
                                          'Galdieria sulphuraria (alga)',
                                          'Solaneum lycopersicum (plant)',
                                          'Triticum/wheat (plant)',
                                          'Zea mays (plant)',
                                          'Aspergillus fumigatus (fungus)',
                                          'Aspergillus nidulans (fungus)',
                                          'Aspergillus oryzae (fungus)',
                                          'Aspergillus terreus (fungus)',
                                          'Botrytis cinerea (fungus)',
                                          'Candida albicans (fungus)',
                                          'Candida guilliermondii (fungus)',
                                          'Candida tropicalis (fungus)',
                                          'Chaetomium globosum (fungus)',
                                          'Coccidioides immitis (fungus)',
                                          'Conidiobolus coronatus (fungus)',
                                          'Coprinus cinereus (fungus)',
                                          'Cryptococcus neoformans (fungus)',
                                          'Debarymomyces hansenii (fungus)',
                                          'Encephalitozoon cuniculi (fungus)',
                                          'Eremothecium gossypii (fungus)',
                                          'Fusarium graminearum (fungus)',
                                          'Histoplasma capsulatum (fungus)',
                                          'Kluyveromyces lactis (fungus)',
                                          'Laccaria bicolor (fungus)',
                                          'Lodderomyces elongisporus (fungus)',
                                          'Magnaporthe grisea (fungus)',
                                          'Neurospora crassa (fungus)',
                                          'Phanerochaete chrysosporium (fungus)',
                                          'Pichia stipitis (fungus)',
                                          'Rhizopus oryzae (fungus)',
                                          'Saccharomyces cerevisiae (fungus)',
                                          'Schizosaccharomyces pombe (fungus)',
                                          'Ustilago maydis (fungus)',
                                          'Verticillium longisporum (fungus)',
                                          'Yarrowia lipolytica (fungus)',
                                          'Sulfolobus solfataricus (archaeon)',
                                          'Escherichia coli (bacterium)',
                                          'Thermoanaerobacter tengcongensis (bacterium)'
                                          ]}" 
                                          value="${fieldValue(bean:predictionInstance,field:'species_select')}" noSelection="${['null':'Select One...']}"/>
                                       <a href="../help.gsp#project_id"><small>Help</small></a>
                                       <g:if test="${predictionInstance.has_select == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           You must <b>either</b> upload a genome file from your computer <b>or</b> specify a web link to a genome file: &nbsp; <a href="../help.gsp#upload_link"><small>Help</small></a>
                           <br>
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
                                       <g:if test="${predictionInstance.has_genome_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="GenomeFile" name="GenomeFile"/>
                                       <g:if test="${predictionInstance.has_genome_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td>&nbsp;<b>or</b>&nbsp;</td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="genome_ftp_link">specify web link to genome file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'genome_ftp_link','errors')}">
                                       <input type="text" id="genome_ftp_link" name="genome_ftp_link" value="${fieldValue(bean:predictionInstance,field:'genome_ftp_link')}"/>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           You may (optionally) also specify one or several of the following files that contain external evidence for protein coding genes: <a href="../help.gsp#which_files_pred"><small>Help</small></a><br><br>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="EstFile"><b>cDNA file</b> &nbsp; <small><b><i>Non-commercial users only</i></b></small> &nbsp;<a href="../help.gsp#cDNA"><small>Help</small></a></label>
                                    </td>
                                    <td valign="top">
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">Upload a file <font size="1">(max. 100 MB)</font>:</td>
                                    <td valign="top">
                                       <g:if test="${predictionInstance.has_est_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="EstFile" name="EstFile"/>
                                       <g:if test="${predictionInstance.has_est_file == true}"></div></g:if>
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
                                    <td valign="top" class="value ${hasErrors(bean:predictionInstance,field:'est_ftp_link','errors')}">
                                       <input type="text" id="est_ftp_link" name="est_ftp_link" value="${fieldValue(bean:predictionInstance,field:'est_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td><br></td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="hint_file"><b>Hints file</b> &nbsp; <a href="../help.gsp#hints"><small>Help</small></a></label>
                                    </td>
                                    <td valign="top">
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">Upload a file <font size="1">(max. 200 MB)</font>:</td>
                                    <td valign="top">
                                       <g:if test="${predictionInstance.has_hint_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="HintFile" name="HintFile"/>
                                       <g:if test="${predictionInstance.has_hint_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           <p>The following checkboxes allow you to modify the gene prediction behavior of AUGUSTUS:</p>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="UtrPrediction"><b>UTR prediction</b> &nbsp; <a href="../help.gsp#utr"><small>Help</small></a></label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${predictionInstance.has_utr == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <g:checkBox name="utr" value="${false}" /> predict UTRs (requires species-specific UTR parameters)
                                       <g:if test="${predictionInstance.has_utr == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <br><label for="StrandPrediction"><b>Report genes on</b></label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${predictionInstance.has_strand == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <g:radio name="pred_strand" value="1" checked="true"/> both strands  &nbsp; &nbsp; <g:radio name="pred_strand" value="2"/> forward strand only &nbsp; &nbsp; <g:radio name="pred_strand" value="3"/> reverse strand only
                                       <g:if test="${predictionInstance.has_strand == true}"></div></g:if>
                                    </td>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <br><label for="AlternativeTranscripts"><b>Alternative transcripts:</b></label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:radio name="alt_transcripts" value="1" checked="true"/>
                                       none  &nbsp; &nbsp; 
                                       <g:radio name="alt_transcripts" value="2"/>
                                       few &nbsp; &nbsp; 
                                       <g:radio name="alt_transcripts" value="3"/>
                                       medium &nbsp; &nbsp; 
                                       <g:radio name="alt_transcripts" value="4"/>
                                       many 
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <br><label for="GeneStructure"><b>Allowed gene structure:</b>&nbsp; <a href="../help.gsp#allowedGeneStructure"><small>Help</small></a></label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${predictionInstance.has_structures == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <g:radio name="allowed_structures" value="1" checked="true"/> predict any number of (possibly partial) genes<br>
                                       <g:radio name="allowed_structures" value="2"/> only predict complete genes<br>
                                       <g:radio name="allowed_structures" value="3"/> only predict complete genes - at least one<br>
                                       <g:radio name="allowed_structures" value="4"/>  predict exactly one gene<br><br>
                                       <g:if test="${predictionInstance.has_structures == true}">
                                       </div></g:if>
                                       <g:if test="${predictionInstance.has_conflicts == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <g:checkBox name="ignore_conflicts" value="${false}" /> ignore conflicts with other strand
                                       <g:if test="${predictionInstance.has_conflicts == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br>
                           <p>
                              &nbsp; 
                              <g:checkBox name="agree_nonhuman" value="${predictionInstance?.agree_nonhuman}" />
                              &nbsp;<b>I am not submitting personalized human sequence data (mandatory).</b>
                           </p>
                           <p>We use a <b>verification string</b> to figure out whether you are a <b>human</b> person. Please type the text in the image below into the text field next to the image.
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${predictionInstance.warn == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <img src="${createLink(controller: 'simpleCaptcha', action: 'captcha')}"/> &nbsp; &nbsp; <g:textField name="captcha"/> <font color="#FF0000">*</font>
                                       <g:if test="${predictionInstance.warn == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <p><font color="#FF0000">*</font>) mandatory input arguments</p>
                        </div>
                        <div class="buttons" onclick="toggle_visibility('aug-spinner');">
                           <span class="button"><input class="commit" type="submit" value="Start Predicting" /></span><br>
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

