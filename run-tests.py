#!/usr/bin/env python3

"""Builds the handout directory and calls its run-tests.py in that context."""

import importlib
import os
import subprocess
import sys

runtests = importlib.import_module("handout-files.run-tests")

# Monkey-patch run-tests.py's make function to first build the handout
old_make = runtests.make
def make():
    make_q = subprocess.run(['make', '-q'], capture_output=True, check=False)
    if make_q.returncode != 0:
        try:
            subprocess.check_call(['make'])
            print()
        except subprocess.CalledProcessError:
            print("Could not compile handout.")
            sys.exit(3)

    os.chdir("build/handout/")

    old_make()
runtests.make = make

if __name__ == '__main__':
    runtests.main()
