.PHONY: all test clean clean-all

ODDITY_URL = https://github.com/uwplse/oddity/releases/download/v0.36a/oddity.jar

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
						  oddity.jar


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

oddity.jar:
	wget -O $@ $(ODDITY_URL)

handout/: $(LAB_FILES) $(JAR_FILES) $(HANDOUT_FILES) $(OTHER_FILES)
	rm -rf $@
	mkdir $@
	$(CP) -r $(LAB_FILES_FOLDER) $(HANDOUT_FILES_FOLDER)/. $(JAR_FILES) $(OTHER_FILES) $@

handout.tar.gz: handout/
	$(TAR) -czf $@ --transform "s/^handout/dslabs/" $^

test:
	ant self-test

clean:
	ant clean
	rm -rf handout handout.tar.gz

clean-all: clean
	rm -rf oddity.jar
	ant clean-all
