"""
grader.py

Extracts then grades students. Outputs to a summary file with all test scores,
with individual log files and git diffs left in each student's directory.
"""

import argparse
import json
import os
import subprocess
from distutils.dir_util import copy_tree
from shutil import copyfile, rmtree
from threading import Timer

parser = argparse.ArgumentParser()
parser.add_argument(
    "-s",
    "--students",
    dest="students",
    help="Student submission directory",
    required=True)
parser.add_argument(
    "-n",
    "--lab-num",
    dest="lab_num",
    help="The number corresponding to the lab",
    required=True)
parser.add_argument(
    "-l",
    "--lab-name",
    dest='lab',
    help='The name of the lab, such as "lab1-clientserver"',
    required=True)
args = parser.parse_args()

STUDENT_SUBMISSION_DIR = args.students
HANDOUT_DIRECTORY = 'handout'

LAB_NAME = args.lab
LAB_NUMBER = args.lab_num

HANDOUT_DIRECTORY = 'handout'
TEST_DIRECTORY = os.path.join(HANDOUT_DIRECTORY, 'labs', LAB_NAME, 'tst')
RESULTS_DIR_NAME = 'results'
FULL_SUMMARY_NAME = 'test-summary.txt'
STUDENT_TEST_LOG_NAME = 'test-results'
STUDENT_DIFF_NAME = 'diff.txt'
TAR_NAME = 'submit.tar.gz'
GIT_DIFF_OUTPUT_NAME = 'diff.txt'
TIMEOUT = 10 * 60
TIMES_TO_RUN = 2

# Contains summary.txt, then a directory for each student containing src, diff, and full test output.
if os.path.exists(RESULTS_DIR_NAME):
    rmtree(RESULTS_DIR_NAME)
os.makedirs(RESULTS_DIR_NAME)

# Parse the test log and record these lines for the summary
SEARCH_TERMS = ['Tests passed', 'Points', 'Total time']

# Used to construct the final summary
SCORES = {}


# TODO Verify that this even works. I am not sure how it handles tests that will time out.
def run(cmd, out, cwd, timeout_sec):
    proc = subprocess.Popen(cmd, cwd=cwd, stdout=out, stderr=out)
    kill_proc = lambda p: p.kill()
    timer = Timer(timeout_sec, kill_proc, [proc])
    try:
        timer.start()
        proc.wait()
    finally:
        timer.cancel()


for student in os.listdir(STUDENT_SUBMISSION_DIR):
    try:
        print("Setting up student " + student)

        student_path = os.path.join(STUDENT_SUBMISSION_DIR, student)

        # Extract their solutions
        tar_path = os.path.join(student_path, TAR_NAME)
        subprocess.call(['tar', '-xf', tar_path, '--directory', student_path])
 
	# Copy over all test folders
	for lab in os.listdir(os.path.join(HANDOUT_DIRECTORY, 'labs')):
		src_test_path = os.path.join(HANDOUT_DIRECTORY, 'labs', lab, 'tst')
		dst_test_path = os.path.join(student_path, 'labs', lab, 'tst')
	        copy_tree(src_test_path, dst_test_path)
        
	for f in os.listdir(HANDOUT_DIRECTORY):
            full_file_path = os.path.join(HANDOUT_DIRECTORY, f)
            # Copy jars/Makefile/lombok.config/etc.
            if os.path.isfile(full_file_path):
                output_path = os.path.join(student_path, f)
                copyfile(full_file_path, output_path)

        student_result_path = os.path.join(RESULTS_DIR_NAME, student)
        if not os.path.exists(student_result_path):
            os.makedirs(student_result_path)

	# Remove all the MacOS files that have crazy characters in them
	subprocess.Popen(['find', '.', '-name', '._*', '|', 'xargs', 'rm'], cwd=student_path, shell=True)

	# Calculate the score for this student        
	SCORES[student] = {}
        for run_index in range(TIMES_TO_RUN):

            # Run tests and collect output
            log_out_path = os.path.join(student_result_path, STUDENT_TEST_LOG_NAME + '-' + str(run_index) + '.txt')
            with open(log_out_path, 'w+') as out:
                cmd = ['python', 'run-tests.py', '--lab', str(LAB_NUMBER)]
                run(cmd, out, student_path, TIMEOUT)

            # Add to the summary
            with open(log_out_path, 'r') as out:
                test_results = out.read()
		SCORES[student][run_index] = {}
                for line in test_results.split('\n'):
                    for term in SEARCH_TERMS:
                        if term in line:
                            SCORES[student][run_index][term] = line.split(':')[1].strip()

        # Copy source
        copy_tree(
            os.path.join(student_path, 'labs', LAB_NAME, 'src'),
            student_result_path + '/src')

        # Copy diff
        git_diff_stud_dir = os.path.join(student_path, 'labs', LAB_NAME, 'src')
        git_diff_handout_dir = os.path.join('handout', 'labs', LAB_NAME, 'src')
        git_diff_out_path = os.path.join(student_result_path,
                                         GIT_DIFF_OUTPUT_NAME)

        with open(git_diff_out_path, 'w+') as fd:
            git_diff_cmd = [
                'git', 'diff', '--no-index', '--color=always',
                git_diff_handout_dir, git_diff_stud_dir
            ]
            subprocess.call(git_diff_cmd, stdout=fd)

        print('Completed running student ' + student)

    except ValueError as e:
        print('Encountered ' + str(e))
        print('Could not grade student ' + student)
	raise(e)

# Write out the summary of all students
with open(os.path.join(RESULTS_DIR_NAME, FULL_SUMMARY_NAME), 'w+') as out:
    json.dump(SCORES, out)
