# 452 Grading

## Files
- config.json: contains the test configuration (what tests to run, where they are, where the student submissions are, what servers to use, where the jars/etc are). There are some old configuration parameters for git integration, but I didn't finish that up.
- distributor.py: splits up the students into groups for each of the hosts that the tests will be run on. Rsyncs the students to grade onto each host and starts to run grader.py.
- grader.py: grades on a local machine with many command line parameters corresponding to all of the required items.
- test-resources: These are the test resources needed to compile: things like the framework and test jars, also the Makefile that run-tests.py uses.
- Grades: Where I will move the grades after I invoke the grader.
- Grade Exporter: A script that can import the grades into catalyst. Look at the [readme](grade_exporter/README.md) for more info.

## Overview
The distributor.py file distributes the student submissions between all hosts, creating new directories that each host will receive to grade. Then, the distributor will rsync all of the tests, the test/project resources (the jars/lombok.config/Makefile), and then any scripts that are needed. Then, the distributor will start the grading on the remote machine by invoking grader.py, which internally will run-tests.py, then generate a json file corresponding to the test results. 
