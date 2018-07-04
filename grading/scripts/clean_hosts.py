import sys
import json
import subprocess

config = sys.argv[1]

with open(config, 'r') as fd:
	for host in json.loads(fd.read())['hosts']:
		subprocess.call(['ssh', host, 'rm', '-rf', '/tmp/GRADING'])

