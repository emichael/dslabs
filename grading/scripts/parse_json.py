import json

'''
The results from the grader come out from the grader rsynced back into
separate directories. Then, we can copy these json files and merge them
into a single JSON file.

In the current arrangement, the JSON will resemble:

{ 
	"nja4": {
		"1:"{
			"Points": "300/300"
			...
		}
 	},
	...
}

So the goal is to iterate through this JSON and somehow combine
all of the scores. This file will average the top n results,
and you can investigate how top n averaging affects the scores
that students end up getting with the "num_saved_by_avg" variable.
'''


MERGED_SCORES = 'merged.json'

num_to_avg = 3
num_saved_by_avg = 0

student_to_average = {}

with open(MERGED_SCORES, 'r') as fd:
	student_scores = json.loads(fd.read())
	for student, runs in student_scores.items():
		scores_to_avg = []
		for run, stats in runs.items():
			# The scores in the log file resemble:
			# Points: 300/300
			points_str = stats['Points']
			score = int(points_str[:points_str.index('/')])
			scores_to_avg += [score]
		
		student_to_average[student] = sum(sorted(scores_to_avg, reverse=True)[:num_to_avg])/num_to_avg
		
		if student_to_average[student] > min(scores_to_avg):
			print('%s was saved by top %d averaging, got %f, whereas their min was %f!' \
				% (student, num_to_avg, student_to_average[student],  min(scores_to_avg)))
			num_saved_by_avg += 1

# To evaluate how many students benefitted from the averaging behavior.
# print('%d students saved by top %d averaging' % (num_saved_by_avg, num_to_avg))

# For a text file of "netid score" pairs on each line, uncomment the following.
# Then you can just do "python3 parse-json.py > out.txt"
# for student, score in student_to_average.items():
# 	print("%s %f" % (student, score))
