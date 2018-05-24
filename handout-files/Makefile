################################################################################
# Java compiler settings
################################################################################
JC = javac -source 1.8 -g


################################################################################
# Folders, files
################################################################################
OUT = out

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
	@ $(JC) -d $@ -cp framework-compile.jar $^

$(OUT)/tst/: $(JAVA_TST) $(OUT)/src
	@ echo "[javac] $@"
	@ mkdir -p $@
	@ $(JC) -d $@ -cp framework.jar:$(OUT)/src $(JAVA_TST)


submit.tar.gz:
	@ echo "[tar] submit.tar.gz"
	@ tar -cvzf $@ labs/*/src


clean:
	@ echo "[clean]"
	@ rm -rf $(OUT) submit.tar.gz
