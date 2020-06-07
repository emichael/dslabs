################################################################################
# Java compiler settings
################################################################################
JC = javac -source 14 -g


################################################################################
# Folders, files
################################################################################
OUT = out

SRC_JARS = jars/framework.jar:jars/framework-deps.jar
TST_JARS = $(SRC_JARS):jars/grader.jar:jars/grader-deps.jar

JAVA_SRC = $(shell find labs/*/src -type f -name "*.java")
JAVA_TST = $(shell find labs/*/tst -type f -name "*.java")


################################################################################
# Targets
################################################################################
.PHONY: all clean

all: $(OUT)/src/ $(OUT)/tst/


$(OUT)/src/: $(JAVA_SRC)
	@ echo "[javac] $@"
	@ mkdir -p $@
	@ $(JC) -d $@ -cp $(SRC_JARS) $(JAVA_SRC)

$(OUT)/tst/: $(JAVA_TST) $(OUT)/src
	@ echo "[javac] $@"
	@ mkdir -p $@
	@ $(JC) -d $@ -cp $(TST_JARS):$(OUT)/src $(JAVA_TST)


submit.tar.gz:
	@ echo "[tar] submit.tar.gz"
	@ tar -cvzf $@ labs/*/src


clean:
	@ echo "[clean]"
	@ rm -rf $(OUT) submit.tar.gz
