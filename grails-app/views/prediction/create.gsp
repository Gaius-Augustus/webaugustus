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
               <g:hasErrors bean="${prediction}">
                  <div class="errors">
                     <g:renderErrors bean="${prediction}" as="list" />
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
                           <p>Please read the <a href="${createLink(uri:'/predictiontutorial')}">prediction tutorial</a> before submitting a job for the first time. Example data for this form is available <a href="${createLink(uri:'/predictiontutorial#exampledata')}">here</a>. You may also use the button below to insert sample data. Please note that you will always need to enter the verification string at the bottom of the page, yourself, in order to submit a job!</p>
                           <p><g:actionSubmit action="fillSample" value="Fill in Sample Data" /></p>
                           <p>We recommend that you specify an <b>e-mail address</b>.</p>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="email_adress">e-mail</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:prediction,field:'email_adress','errors')}">
                                       <input type="text" id="email_adress" name="email_adress" value="${fieldValue(bean:prediction,field:'email_adress')}"/>
                                        &nbsp;<a href="${createLink(uri:'/help#email')}"><small>Help</small></a><br>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop" colspan="2">
                                       <table><tr><td class="prop">
                                          <g:checkBox id="agree_email" name="agree_email" value="${prediction?.agree_email}" />
                                       </td><td class="prop">
                                           <label for="agree_email">
                                               If I provide an e-mail address, I consent to the processing of my personal data in 
                                               accordance with the <a href="//bioinf.uni-greifswald.de/bioinf/datenschutz.html">Data Privacy Protection</a> declaration.<br>
                                               I agree to receive e-mails that are related to the particular AUGUSTUS job that I submitted.
                                           </label>
                                       </td></tr></table>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" style="padding-left: 0px">
                                       <br>
                                       You must <b>either</b> upload a *.tar.gz archive with AUGUSTUS species parameters from your computer 
                                       <br><b>or</b> specify a project identifier: &nbsp; <a href="${createLink(uri:'/help#which_files_pred')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>AUGUSTUS species parameters</b> <font color="#FF0000">*</font>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top">
                                        <label for="ArchiveFile" style="display: inline;">Upload an archive file <font size="1">(max. 100 MB)</font>: </label>
                                        &nbsp; <a href="${createLink(uri:'/help#archive')}"><small>Help</small></a>
                                    </td>
                                    <td valign="top">
                                       <g:if test="${prediction?.has_param_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="ArchiveFile" name="ArchiveFile"/></label>
                                       <g:if test="${prediction?.has_param_file == true}"></div></g:if>
                                          
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="project_id">specify a project identifier:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:prediction,field:'project_id','errors')}">
                                       <input type="text" id="project_id" name="project_id" value="${fieldValue(bean:prediction,field:'project_id')}"/> <a href="${createLink(uri:'/help#project_id')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="species_select">select an organism:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:prediction,field:'species_select','errors')}">
                                       <g:select id="species_select" name="species_select" from="${[
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
                                          value="${fieldValue(bean:prediction,field:'species_select')}" noSelection="${['null':'Select One...']}"/>
                                       <a href="${createLink(uri:'/help#project_id')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" style="padding-left: 0px; padding-bottom: 0px;">
                                       <br>
                                       You must <b>either</b> upload a genome file from your computer  <b>or</b> 
                                       specify a web link to a genome file: &nbsp; <a href="${createLink(uri:'/help#upload_link')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>Genome file</b> <font color="#FF0000">*</font>&nbsp; (max. 250000 scaffolds) <a href="${createLink(uri:'/help#genome_file')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="GenomeFile">Upload a file <font size="1">(max. 100 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${prediction?.has_genome_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="GenomeFile" name="GenomeFile"/>
                                       <g:if test="${prediction?.has_genome_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="genome_ftp_link">specify web link to genome file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:prediction,field:'genome_ftp_link','errors')}">
                                       <input type="text" id="genome_ftp_link" name="genome_ftp_link" value="${fieldValue(bean:prediction,field:'genome_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" style="padding-left: 0px; padding-bottom: 0px">
                                       <br>
                                       You may (optionally) also specify one or several of the following files that contain external evidence for protein coding genes: 
                                       <a href="${createLink(uri:'/help#which_files_pred')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>cDNA file</b> &nbsp; <small><b><i>Non-commercial users only</i></b></small> &nbsp;<a href="${createLink(uri:'/help#cDNA')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="EstFile">Upload a file <font size="1">(max. 100 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${prediction?.has_est_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="EstFile" name="EstFile"/>
                                       <g:if test="${prediction?.has_est_file == true}"></div></g:if>
                                    </td>
                                 </tr>
                                 <tr>
                                    <td class="prop_or" colspan="2">&nbsp;<b>or</b>&nbsp;</td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label for="est_ftp_link">specify web link to cDNA file <font size="1">(max. 1 GB)</font>:</label>
                                    </td>
                                    <td valign="top" class="value ${hasErrors(bean:prediction,field:'est_ftp_link','errors')}">
                                       <input type="text" id="est_ftp_link" name="est_ftp_link" value="${fieldValue(bean:prediction,field:'est_ftp_link')}"/>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td><br></td>
                                    <td></td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" colspan="2" class="name">
                                       <b>Hints file</b> &nbsp; <a href="${createLink(uri:'/help#hints')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top"><label for="HintFile">Upload a file <font size="1">(max. 200 MB)</font>:</label></td>
                                    <td valign="top">
                                       <g:if test="${prediction?.has_hint_file == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <input type="file" id="HintFile" name="HintFile"/>
                                       <g:if test="${prediction?.has_hint_file == true}"></div></g:if>
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
                                       <b>UTR prediction</b> &nbsp; <a href="${createLink(uri:'/help#utr')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label><g:checkBox name="utr" value="${prediction?.utr}" /> predict UTRs (requires species-specific UTR parameters)</label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <br><b>Report genes on</b></label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label style="display: inline;"><g:radio name="pred_strand" value="1" checked="${prediction?.pred_strand == 1}"/> both strands </label> &nbsp; &nbsp; 
                                       <label style="display: inline;"><g:radio name="pred_strand" value="2" checked="${prediction?.pred_strand == 2}"/> forward strand only </label> &nbsp; &nbsp; 
                                       <label style="display: inline;"><g:radio name="pred_strand" value="3" checked="${prediction?.pred_strand == 3}"/> reverse strand only </label>
                                    </td>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <br><b>Alternative transcripts:</b>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label style="display: inline;"><g:radio name="alt_transcripts" value="1" checked="${prediction?.alt_transcripts == 1}"/> none </label> &nbsp; &nbsp; 
                                       <label style="display: inline;"><g:radio name="alt_transcripts" value="2" checked="${prediction?.alt_transcripts == 2}"/> few </label> &nbsp; &nbsp; 
                                       <label style="display: inline;"><g:radio name="alt_transcripts" value="3" checked="${prediction?.alt_transcripts == 3}"/> medium </label> &nbsp; &nbsp; 
                                       <label style="display: inline;"><g:radio name="alt_transcripts" value="4" checked="${prediction?.alt_transcripts == 4}"/> many </label>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <br><b>Allowed gene structure:</b>&nbsp; <a href="${createLink(uri:'/help#allowedGeneStructure')}"><small>Help</small></a>
                                    </td>
                                 </tr>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <label style="margin-bottom:0px;"><g:radio name="allowed_structures" value="1" checked="${prediction?.allowed_structures == 1}"/> predict any number of (possibly partial) genes</label>
                                       <label style="margin-bottom:0px;"><g:radio name="allowed_structures" value="2" checked="${prediction?.allowed_structures == 2}"/> only predict complete genes</label>
                                       <label style="margin-bottom:0px;"><g:radio name="allowed_structures" value="3" checked="${prediction?.allowed_structures == 3}"/> only predict complete genes - at least one</label>
                                       <label style="margin-bottom:0px;"><g:radio name="allowed_structures" value="4" checked="${prediction?.allowed_structures == 4}"/> predict exactly one gene</label>
                                       <br>
                                       <label><g:checkBox name="ignore_conflicts" value="${prediction?.ignore_conflicts}" /> ignore conflicts with other strand</label>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <br><br>
                           <p>
                              <label style="display: inline;">
                                  <g:checkBox name="agree_nonhuman" value="${prediction?.agree_nonhuman}" />
                                  &nbsp;<b>I am not submitting personalized human sequence data (mandatory).
                              </label>
                              &nbsp;<font color="#FF0000">*</font></b>
                              &nbsp;<a href="${createLink(uri:'/help#nonhuman')}"><small>Help</small></a>
                           </p>
                           <br>
                           <p>We use a <b>verification string</b> to figure out whether you are a <b>human</b> person. Please type the text in the image below into the text field next to the image.
                           <table>
                              <tbody>
                                 <tr class="prop">
                                    <td valign="top" class="name">
                                       <g:if test="${prediction?.warn == true}">
                                          <div class="prop_warn">
                                       </g:if>
                                       <label style="display: inline;">
                                           <img src="${createLink(controller: 'simpleCaptcha', action: 'captcha')}" style="vertical-align: bottom;"/> &nbsp; &nbsp; 
                                           <g:textField name="captcha"/>
                                       </label>
                                       &nbsp;<font color="#FF0000">*</font>
                                       <g:if test="${prediction?.warn == true}"></div></g:if>
                                    </td>
                                 </tr>
                              </tbody>
                           </table>
                           <p><font color="#FF0000">*</font>) mandatory input arguments</p>
                        </div>
                        <div id="commit-button" class="buttons" onclick="toggle_visibility('commit-button');toggle_visibility('aug-spinner');" style="display:block;">
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

