################################################################################
# Java compiler settings
################################################################################
JC = javac -source 17 -g


################################################################################
# Folders, files
################################################################################
OUT = out

# Windows uses ; as a path separator while Linux and Mac use :
SEP = :
ifeq ($(OS),Windows_NT)
	SEP = ;
endif

SRC_JARS = jars/framework.jar$(SEP)jars/framework-deps.jar
TST_JARS = $(SRC_JARS)$(SEP)jars/grader.jar$(SEP)jars/grader-deps.jar

JAVA_SRC = $(shell find labs/*/src -type f -name "*.java")
JAVA_TST = $(shell find labs/*/tst -type f -name "*.java")


################################################################################
# Targets
################################################################################
.PHONY: all format clean clean-all

all: $(OUT)/src/ $(OUT)/tst/


$(OUT)/src/: $(JAVA_SRC)
	@ echo "[javac] $@"
	@ mkdir -p $@
	@ touch $@
	@ $(JC) -d $@ -cp "$(SRC_JARS)" $(JAVA_SRC)

$(OUT)/tst/: $(JAVA_TST) $(OUT)/src
	@ echo "[javac] $@"
	@ mkdir -p $@
	@ touch $@
	@ $(JC) -d $@ -cp "$(TST_JARS)$(SEP)$(OUT)/src" $(JAVA_TST)


format:
	@ echo "[format]"
	@ java -jar jars/google-java-format.jar -i $(JAVA_SRC)


submit.tar.gz: $(JAVA_SRC)
	@ echo "[format]"
	@ java -jar jars/google-java-format.jar -i $(JAVA_SRC)
	@ echo "[tar] submit.tar.gz"
	@ # prevent MacOS extended attrs from generating extra files
	@ COPYFILE_DISABLE=1 tar -cvzf $@ labs/*/src


clean:
	@ echo "[clean]"
	@ rm -rf $(OUT) submit.tar.gz

clean-all: clean
	@ echo "[clean-all]"
	@ rm -rf traces
