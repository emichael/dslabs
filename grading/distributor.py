import json
import math
import os
import shutil
import subprocess
import threading
import sys
import json
from distutils.dir_util import copy_tree

# Read configuration file
with open('config.json', 'r') as fd:
    config = json.loads(fd.read())

STUDENT_SUBMISSION_PATH = config['submission_path']
HOSTS = config['hosts']
HANDOUT_PATH = config['handout_path']
LAB_NAME = config['lab_name']
LAB_NUMBER = config['lab_number']
GRADE_SCRIPT_PATH = 'grader.py'
HOST_SUBDIVISION_DIRECTORY = 'students'
RESULTS_DIRECTORY = 'results'
MERGED_OUT_NAME = 'merged.json'

TEMP_GRADE_DIR_NAME = '452GRADING'
TEST_DIR_PATH = os.path.join(HANDOUT_PATH, LAB_NAME, 'tst', 'dslabs')

# Distribute students to grade into folders for each host
students = os.listdir(STUDENT_SUBMISSION_PATH)
num_to_grade = int(math.ceil(len(students) / len(HOSTS)))


for i in range(len(HOSTS)):
    host = HOSTS[i]
    # The studentu for a given host to grade will be in students-<host name>
    target_dir = os.path.join(HOST_SUBDIVISION_DIRECTORY, 'students-' + host)
    if os.path.exists(target_dir):
        shutil.rmtree(target_dir)
    os.makedirs(target_dir, exist_ok=True)
    # Copy this host's students to its directory
    for student_index in range(num_to_grade * i, min(num_to_grade * (i + 1), len(students))):
        original_submission_path = os.path.join(STUDENT_SUBMISSION_PATH, students[student_index])
        host_to_grade_path = os.path.join(target_dir, students[student_index])
        copy_tree(original_submission_path, host_to_grade_path)

# Create the results directory if it does not exist
if not os.path.exists(RESULTS_DIRECTORY):
	os.mkdir(RESULTS_DIRECTORY)

def run_host(host):
	ssh = ['ssh', host]
	clean_host = ['rm', '-rf', '/tmp/' + TEMP_GRADE_DIR_NAME, '&&',
		      'mkdir', '-m', '700', '-p', '/tmp/' + TEMP_GRADE_DIR_NAME + '/students', '&&',
		      'chmod', '700', '/tmp/' + TEMP_GRADE_DIR_NAME]
	subprocess.call(ssh + clean_host)

	print('Copying student submissions...')
	subprocess.call(['rsync', '-a', HOST_SUBDIVISION_DIRECTORY + '/students-' + host + '/', host + ':/tmp/' + TEMP_GRADE_DIR_NAME + '/students'])
	print('Done!')

	print('Copying handout...')
	subprocess.call(['rsync', '-a', HANDOUT_PATH + '/', host + ':/tmp/' + TEMP_GRADE_DIR_NAME + '/handout'])
	print('Done!')

	print('Copying grading script...')
	subprocess.call(['rsync', '-a', GRADE_SCRIPT_PATH, host + ':/tmp/' + TEMP_GRADE_DIR_NAME])
	print('Done!')

	# Runs the script that was copied to the remote server
	run_grader = ['python', 'grader.py',
		      '-s', 'students',
		      '-n', LAB_NUMBER,
		      '-l', LAB_NAME,
		      ]

	# Run the grader
	subprocess.call(ssh + ['cd', '/tmp/' + TEMP_GRADE_DIR_NAME, '&&'] + run_grader)

	# Copy results back to this machine
	subprocess.call(['rsync', '-a', host + ':/tmp/' + TEMP_GRADE_DIR_NAME + '/results', os.path.join(RESULTS_DIRECTORY, host + '-results')])

	
# Spin off each host in a thread
threads = []
for host in HOSTS:
	threads.append(threading.Thread(target=run_host, args=(host,)))
	threads[-1].start()

for t in threads:
	t.join()

# Merge all of the results
host_result_paths = []
for host in HOSTS:
	result_path = h + ':/tmp/' + TEMP_GRADE_DIR_NAME + '/results'
	if not os.exists(result_path):
		print('ERROR: host %s\'s summary file could not be found!' % host)
		# TODO Fall back to scanning through all of the logs that you DO have		
	
		"""
from os import listdir
from os.path import join, isdir
from re import search

to_avg = 3
for student in [d for d in listdir('.') if isdir(d)]:
	scores = []
	for log_path in [f for f in listdir(student) if f.startswith('test-results')]:
		with open(join(student, log_path), 'r') as fd:
			try:
				score_group = search('Points: (\d+)', fd.read())
				scores += [float(score_group.group(1))]
			except Exception as e:
				pass
	if len(scores) > 0:
		print('%s %f' % (student, sum(sorted(scores)[-to_avg:]) / min(to_avg, len(scores))))

		"""
	else:
		host_result_paths.append(result_path)

# TODO Finish integrating the merging and printing of a single log file
merged = {}
for f in host_result_paths:
        with open(f, 'r') as fd:
                merged.update(json.loads(fd.read()))

with open(os.path.join(RESULTS_DIRECTORY, MERGED_OUT_NAME), 'w+') as fd:
        fd.write(json.dumps(merged))
	

print('Exiting...')
