# Grading Distributed Systems

Contact nj.anderson at outlook.com if you have any questions; I am more than happy to help!

## Quick Start
Build handouts in the directory above this one (just run make) to make sure you have the test suite to be run for a given lab, then add your hosts (make sure you can ssh without a password prompt from this machine), set your directory for your student submissions, and set the name/number of the lab you are trying to grade. Then, just run distributor.py (./distributor.py) and wait for it to complete. I suggest using tmux or some other means of persistence to run the distribtor because this can take awhile (and with tmux, you can check on it conveniently).

## Files
- config.json: contains the test configuration (what tests to run, where they are, where the student submissions are, what servers to use, where the jars/etc are).
- distributor.py: splits up the students into groups for each of the hosts that the tests will be run on. Rsyncs the students to grade onto each host and starts to run grader.py. Rsyncs results back.
- grader.py: grades on a local machine with many command line parameters corresponding to all of the required items.

## Overview
The distributor.py file distributes the student submissions between all hosts, creating new directories that each host will receive to grade. Then, the distributor will rsync all of the tests, the test/project resources (the jars/lombok.config/Makefile), and then any scripts that are needed. Then, the distributor will start the grading on the remote machine by invoking grader.py, which internally will run-tests.py, then generate a json file corresponding to the test results. 

After running, there will be a results directory, which will have a folder for each of the remote hosts. Each of these folders will contain the rsync'ed results folder from the remote machine's test run. Then, the results from each of these is merged into a merged.json (whose full path should be results/merged.json) and a merged folder containing the diffs, logs, and src for each student (whose full path should be in results/merged).
