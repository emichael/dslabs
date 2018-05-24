.PHONY: all clean clean-all

DVIZ_URL = https://github.com/uwplse/dviz/releases/download/v0.26a/dviz.jar

FRAMEWORK_FILES_FOLDER = framework
LAB_FILES_FOLDER = labs
HANDOUT_FILES_FOLDER = handout-files

FRAMEWORK_FILES = $(shell find framework -type f | sed 's/ /\\ /g')
LAB_FILES = $(shell find $(LAB_FILES_FOLDER) -type f | sed 's/ /\\ /g')
HANDOUT_FILES = $(shell find $(HANDOUT_FILES_FOLDER) -type f | sed 's/ /\\ /g')
JAR_FILES = jars/framework.jar \
						jars/framework-sources.jar \
						jars/framework-compile.jar
OTHER_FILES = lombok.config \
						  doc/ \
						  dviz.jar


ifeq ($(shell uname -s),Darwin)
	TAR = gtar
else
	TAR = tar
endif

ifeq ($(shell uname -s),Darwin)
	CP = gcp
else
	CP = cp
endif


all: handout/ handout.tar.gz


$(JAR_FILES) doc/: $(FRAMEWORK_FILES)
	ant jar-framework jar-framework-sources jar-framework-compile javadoc
	touch $@

dviz.jar:
	wget -O $@ $(DVIZ_URL)

handout/: $(LAB_FILES) $(JAR_FILES) $(HANDOUT_FILES) $(OTHER_FILES)
	rm -rf $@
	mkdir $@
	$(CP) -r $(LAB_FILES_FOLDER) $(HANDOUT_FILES_FOLDER)/. $(JAR_FILES) $(OTHER_FILES) $@

handout.tar.gz: handout/
	$(TAR) -czf $@ --transform "s/^handout/dslabs/" $^


clean:
	ant clean
	rm -rf handout handout.tar.gz

clean-all: clean
	rm -rf dviz.jar
	ant clean-all
