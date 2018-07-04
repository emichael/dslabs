import sys
import json

MERGED_OUT_NAME = 'merged.json'

merged = {}
for f in sys.argv[1:]:
	with open(f, 'r') as fd:
		merged.update(json.loads(fd.read()))

with open(MERGED_OUT_NAME, 'w+') as fd:
	fd.write(json.dumps(merged))
				
