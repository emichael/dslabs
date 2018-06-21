#!/usr/bin/env python

'''
Created by David Porter, Spring 2018
'''

import os
import sys
import pyoo
import argparse
import time
import json
import re
import subprocess

def create_grade_map(grades_json_file_path):
	net_id_to_grade = {}
	with open(grades_json_file_path) as json_data:
		data = json.load(json_data)
	
		for net_id in data:
			if not net_id or net_id == "" or net_id == " ":
				continue

			net_id_data = data[net_id]

			if "Points" in net_id_data:
				points = net_id_data["Points"]
				# Regex to get "180" from string like 180/260 (69.23%)
				grade_re = re.findall('(\d+)\/', points)
				
				if not grade_re:
					print "Can't parse grade for " + net_id
					continue

				grade = grade_re[0]
				net_id_to_grade[net_id] = grade

			else:
				print "WARNING: " + net_id + " does not have Points"
	
	return net_id_to_grade

def update_excel(excel_gradebook_path, target_col_points, target_col_name, output_xls_path, grade_map):
	proc = subprocess.Popen('soffice --accept="socket,host=localhost,port=2003;urp;" --norestore --nologo --nodefault', shell=True)
	# Let libreoffice spin up
	time.sleep(2)	

	desktop = pyoo.Desktop('localhost', 2003)
	if not os.path.isfile(excel_gradebook_path):
		print 'Could not find gradebook .xls file exported from catalyst'
		sys.exit()

	doc = desktop.open_spreadsheet(excel_gradebook_path)
	scores = doc.sheets[0]

	# scores[2,0] is username, keep going to find right col
	assert scores[2,0].value == 'Username'
	ex_col = 0
	while True:
		cur_col_name = scores[2,ex_col].value
		
		if ex_col >= 100:
			print 'Could not find column', target_col_name, " Did you add it?"
			proc.terminate()
			sys.exit()

		if cur_col_name == target_col_name:
			break
		else:
			ex_col += 1
	
	print 'Found column for', target_col_name

	# sanity checks...
	assert scores[2+1, ex_col].value == 'Points'
	assert scores[2+2, ex_col].value == target_col_points

	studentStart = 3
	inputed = 0
	
	USERNAME_COLUMN = 0 # netid
	STATUS_COLUMN = 5 # Active or Dropped
	
	print 'Starting to add grades!'
	while inputed != len(grade_map):
		cur_student_netid = scores[2 + studentStart, USERNAME_COLUMN].value
		net_id_status = scores[2 + studentStart, STATUS_COLUMN].value

		if net_id_status == 'Dropped':
			studentStart += 1
			continue

		if cur_student_netid in grade_map:
			scores[2+studentStart, ex_col].value = grade_map[cur_student_netid]
			inputed += 1
			studentStart += 1
		else:
			studentStart += 1
			print 'WARNING: No grade for', cur_student_netid
	
	filename = output_xls_path + '.xlsx'
	doc.save(filename, pyoo.FILTER_EXCEL_2007)
	doc.close()
	proc.terminate()

	print 'DONE! Generated update gradebook. Now import his gradebook to catalyst', filename

def main():
	parser = argparse.ArgumentParser()
	parser.add_argument('-c', '--col', dest="target_col_name", required=True, help="The name of the column e.g. lab1")
	parser.add_argument('-p', '--col-points', dest="target_col_points", required=True, help="The number of points for this column")
	parser.add_argument('-j', '--json', dest="json_grades_path", required=True, help="Grades json file path")
	parser.add_argument('-i', '--gradebook-xls', dest="gradebook_xls", required=True, help="The gradebook excel file")
	parser.add_argument('-o', '--output-xls', dest="output_xls_path", required=True, help="The output gradebook excel file")

	args = parser.parse_args()
	grade_map = create_grade_map(args.json_grades_path)
	update_excel(args.gradebook_xls, args.target_col_points, args.target_col_name, args.output_xls_path, grade_map)
	
if __name__ == '__main__':
	main()
