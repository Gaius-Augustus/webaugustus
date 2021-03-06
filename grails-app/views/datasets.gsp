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
                        <h1 class="csc-firstHeader">Datasets for Download</h1>
                     </div>
                  </div>
                  <div id="c261665" class="csc-default">
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                     <p>The following sequence files were used to <em>train</em> AUGUSTUS
                        or to <em>test</em> its accuracy. Some of the datasets are described in the
                        paper &ldquo;Gene Prediction with a Hidden Markov Model and a new Intron Submodel&rdquo;,
                        which was presented at the European Conference on Computational Biology
                        in September 2003 and appeared in the proceedings.
                     </p>
                     <h2>Test sets:</h2>
                     <br>
                     <h3>human:</h3>
                     <br>
                     <h4>h178:</h4>
                     <p>178 single-gene short human sequences <br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/h178.gb.gz">h178.gb.gz</a> (gzipped genbank format)
                     </p>
                     <h4>sag178:</h4>
                     <p>
                        semi artificial genomic sequences from Guigo et al.:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/sag178.gb.gz">sag178.gb.gz</a> (gzipped genbank format)<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/sag178.fa.gz">sag178.fa.gz</a> (gzipped fasta format)<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/sag178.gff">sag178.gff</a> (annotation in gff format)
                     </p>
                     <h3>fly:</h3>
                     <br>
                     <h4>fly100:</h4>
                     <p>100 single gene sequences from FlyBase:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/fly100.gb.gz">fly100.gb.gz</a> (gzipped Genbank format)
                     </p>
                     <h4>adh122:</h4>
                     <p>
                        A 2.9 Mb long sequence from the Drosophila adh region (copied from the
                        <a href="http://www.fruitfly.org/GASP1/data/standard.html">GASP dataset page</a>)<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/adh.fa.gz">adh.fa.gz</a> (gzipped fasta format)<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/adh.std1.gff_corrected">adh.std1.gff_corrected</a> (gff format)<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/adh.std1+3.gff">adh.std1+3.gff</a> (gff format)
                     </p>
                     <h3>Arabidopsis thaliana:</h3>
                     <p>Araset. 74 sequences with 168 genes.<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/araset.gb.gz">araset.gb.gz</a> (gzipped genbank format)
                     </p>
                     <h2>Training sets:</h2>
                     <br>
                     <h3>human:</h3>
                     <p>
                        single gene sequences from genbank (1284 genes):<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/human.train.gb.gz">human.train.gb.gz</a> (gzipped genbank format)
                     <p>11739 human splice sites, originally from Guig&oacute; et al., but filtered for similarities to h178, sag178:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/splicesites.gz">splicesites.gz</a> (gzipped flat file)
                     </p>
                     <h3>fly:</h3>
                     <p>
                        320 single gene sequences from FlyBase, disjoint with fly100:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/fly.train.gb.gz">fly.train.gb.gz</a> (gzipped genbank format)
                     </p>
                     <p>
                        400 single gene sequences from FlyBase, disjoint with adh122:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/adh.train.gb.gz">adh.train.gb.gz</a> (gzipped genbank format)
                     </p>
                     <h3>Arabidopsis:</h3>
                     <p>249 single gene sequences obtained by deleting the sequences from the Araball set which overlap with the sequences from Araset:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/araball.train.gb.gz">araball.train.gb</a> (gzipped Genbank format)
                     </p>
                     <h3>Coprinus cinereus (a fungus):</h3>
                     <p>
                        851 single gene sequences predicted by genewise and compiled by Jason Stajich. 261 genes are complete, 590 genes are incomplete at the 3' end.
                        Genes redundand with those in the Genbank annotations were deleted:<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/cop.genomewise.gb.gz">cop.genomewise.gb.gz</a> (gzipped Genbank format)
                     </p>
                     <p>
                        91 sequences containing 93 genes from Genbank. Genes in Genbank with nothing else than the coding sequence were omitted. Identical or extremely 
                        similar genes in genbank were used only once. This set has first been used as a test set for above training set. The Coprinus version
                        here used :<br>
                        <a href="//bioinf.uni-greifswald.de/augustus/datasets/cop.gb.clean.gb.gz">cop.gb.clean.gb.gz</a> (gzipped Genbank format)
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
