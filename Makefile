.PHONY: all test dependencies clean clean-all

ODDITY_URL = https://github.com/uwplse/oddity/releases/download/v0.39a/oddity.jar

FRAMEWORK_FILES = $(shell find framework -type f | sed 's/ /\\ /g')
LAB_FILES = $(shell find labs -type f | sed 's/ /\\ /g')
HANDOUT_FILES = $(shell find handout-files -type f | sed 's/ /\\ /g')

JAR_FILES = build/libs/framework.jar \
						build/libs/grader.jar \
						build/libs/framework-sources.jar \
						build/libs/grader-sources.jar \
						build/libs/framework-deps.jar \
						build/libs/grader-deps.jar \
						build/libs/framework-deps-sources.jar
OTHER_FILES = build/doc/ lombok.config


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


all: build/handout/

dependencies: deps/oddity.jar
	./gradlew copyDependencies

build/libs/: $(FRAMEWORK_FILES)
	./gradlew assemble
	touch $@

build/doc/: $(FRAMEWORK_FILES)
	./gradlew javadoc
	touch $@

deps/oddity.jar:
	mkdir -p deps
	wget -O $@ $(ODDITY_URL)

build/handout/: $(LAB_FILES) $(HANDOUT_FILES) $(OTHER_FILES) build/libs/ deps/oddity.jar
	rm -rf $@
	mkdir $@ build/handout/jars
	$(CP) -r labs handout-files/. $(OTHER_FILES) $@
	$(CP) $(JAR_FILES) deps/oddity.jar build/handout/jars

build/handout.tar.gz: build/handout/
	$(TAR) -czf $@ --transform "s/^build\/handout/dslabs/" $^

test:
	./gradlew test

clean:
	rm -rf build

clean-all: clean
	rm -rf deps .gradle
