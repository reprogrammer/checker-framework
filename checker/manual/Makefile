# Put user-specific changes in your own Makefile.user.
# Make will silently continue if that file does not exist.
-include Makefile.user

# To regenerate this list, run:   latex-process-inputs -makefilelist manual.tex
TEX_FILES = \
manual.tex \
manual-style.tex \
introduction.tex \
nullness-checker.tex \
interning-checker.tex \
lock-checker.tex \
fenum-checker.tex \
tainting-checker.tex \
regex-checker.tex \
formatter-checker.tex \
propkey-checker.tex \
signature-checker.tex \
guieffect-checker.tex \
units-checker.tex \
linear-checker.tex \
igj-checker.tex \
javari-checker.tex \
subtyping-checker.tex \
external-checkers.tex \
typestate-checker.tex \
generics.tex \
advanced-features.tex \
warnings.tex \
annotating-libraries.tex \
creating-a-checker.tex \
external-tools.tex \
faq.tex \
troubleshooting.tex

all: figures-all doc manual.pdf manual.html

.PHONY: figures-all
figures-all:
	${MAKE} -C figures all

doc:
	ln -sf ../doc doc

manual.dvi: ${TEX_FILES} bib-update
	$(MAKE) -C figures
	latex manual.tex
	bibtex manual
	latex manual.tex
	while grep "Rerun to get" manual.log; do latex manual.tex; done


manual.pdf: manual.dvi bib-update
	pdflatex manual.tex
	bibtex manual
	pdflatex manual.tex
	while grep "Rerun to get" manual.log; do pdflatex manual.tex; done


manual.ps: manual.dvi
	dvips -P cmz -t letter -o manual.ps manual.dvi

html: manual.html
manual.html: manual.dvi
	hevea -fix -exec xxdate.exe urlhref.hva manual.tex
# This was necessary once, but it makes Hevea 2.06 use wrong section anchors.
# 	hevea -exec xxdate.exe urlhref.hva manual.tex
# The following three lines are only necessary when using Hevea before version 2.04
# With version 2.04 they have no effect but do no harm.
	\mv -f manual.html manual.html-with-htoc
	./hevea-retarget-crossrefs < manual.html-with-htoc > manual.html
	\rm -f manual.html-with-htoc
# The below is not necessary since Hevea mkes good tables of contents itself.
#	perl -pi -e 's/(<!--TOC section Introduction-->)/$$1\n\n<p>Contents:<\/p>\n<!-- start toc.  do not edit; run html-update-toc instead -->\n<!-- end toc -->\n\n<br>\n/' manual.html
#	html-update-toc manual.html

export BIBINPUTS = $BIBINPUTS:.:bib

bib:
	hg clone https://code.google.com/p/plume-bib/ bib
.PHONY: bib-update
bib-update: bib
# To skip running the hg command, invoke make like this:  make NOHG=1 ...
ifndef NOHG
	cd bib && hg pull -u && make
endif

clean:
	@\rm -f doc *.aux *.blg *.haux *.htoc *.log *.pdf *.ps *.dvi *.idx *.ind *.ilg *.toc

very_clean: clean
	@\rm manual.html

.PHONY: checklink
checklink:
	${INV}/scripts/checklink -q -e `cat ${INV}/scripts/checklink-args.txt` manual.html

tags: TAGS
TAGS: ${TEX_FILES}
	etags ${TEX_FILES}
