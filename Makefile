.PHONY: all test format check-format dependencies serve clean clean-all
.FORCE:

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
LICENSE_NOTICE = build/reports/licenses/THIRD-PARTY-NOTICES.txt
OTHER_FILES = build/doc/ lombok.config


ifeq ($(shell uname -s),Darwin)
	CP = gcp
	TAR = gtar
	SED = gsed
else
	CP = cp
	TAR = tar
	SED = sed
endif


all: build/handout/

dependencies:
	./gradlew copyDependencies

deps/formatter/google-java-format.jar:
	$(eval GVF_VERSION := $(shell $(SED) -n -E "s/^.*name:\s*'google-java-format',\s*version:\s*'([^']+)'.*$$/\1/p" build.gradle))
	@echo "Downloading Google Java Format version $(GVF_VERSION)..."
	# Check to make sure we extracted the version number from build.gradle
	test -n "$(GVF_VERSION)"
	mkdir -p deps/formatter/
	wget "https://github.com/google/google-java-format/releases/download/v$(GVF_VERSION)/google-java-format-$(GVF_VERSION)-all-deps.jar" -O $@

build/libs/: $(FRAMEWORK_FILES)
	./gradlew assemble
	touch $@

build/doc/: $(FRAMEWORK_FILES)
	./gradlew javadoc
	touch $@

$(LICENSE_NOTICE): build.gradle
	./gradlew generateLicenseReport

build/handout/: $(LAB_FILES) $(HANDOUT_FILES) $(OTHER_FILES) build/libs/ deps/formatter/google-java-format.jar $(LICENSE_NOTICE)
	rm -rf $@
	mkdir $@ build/handout/jars
	$(CP) -r labs handout-files/. $(OTHER_FILES) $@
	$(CP) $(JAR_FILES) build/handout/jars
	$(CP) deps/formatter/google-java-format.jar build/handout/jars
	# Strip out the date from the license report for a reproducible build
	$(SED) -Ez -e 's/This report was generated at .*//' $(LICENSE_NOTICE) > build/handout/jars/THIRD-PARTY-NOTICES.txt

build/handout.tar.gz: build/handout/
	$(TAR) -czf $@ --transform "s/^build\/handout/dslabs/" $^

test:
	./gradlew test

check-format:
	./gradlew spotlessCheck

format:
	./gradlew spotlessApply

www/javadoc/: build/doc/
	rsync -a --delete $< $@

build/www/: www/javadoc/ .FORCE
	mkdir -p $@
	cd ./www;	bundle exec jekyll build -d ../$@
	touch $@

serve: www/javadoc/
	cd www; watchy -w _config.yml -- bundle exec jekyll serve --watch -d ../build/www/

clean:
	rm -rf build www/javadoc www/.jekyll-cache

clean-all: clean
	rm -rf deps .gradle traces
