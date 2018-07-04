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

