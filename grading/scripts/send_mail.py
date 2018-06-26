# Import smtplib for the actual sending function
import smtplib

# Import the email modules we'll need
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email.mime.multipart import MIMEMultipart
from email import encoders

import argparse
import json
import sys
import os
import tarfile

# Args parser
parser = argparse.ArgumentParser(description='Sends homework feedback')
parser.add_argument('senddir', help='Directory filled with student directories, which contain test logs.')
parser.add_argument('smtpconfig', help='JSON Configuration file for use with SMTP')
parser.add_argument('outdir', help='Directory to be filled with tar archives, which contain test logs.')
parser.add_argument('hwname', help='The name of the homework to be used for the subject line of the email')
parser.add_argument('email_contents', help='Path to file used to fill the contents of the sent out email')
args = parser.parse_args()

smtpconfig = json.load(open(args.smtpconfig))
fields = ["username", "password", "domain"]
if not all(field in smtpconfig for field in fields):
	print("SMTP configuration requires: " + ", ".join(fields))
	sys.exit(1)

# Change to your domain if not at UW
student_email_domain = "@uw.edu"
s = smtplib.SMTP(smtpconfig['domain'], 587)

s.ehlo()
s.starttls()

# Login to email
s.login(smtpconfig['username'], smtpconfig['password'])

# Read in the body of the email
with open(args.email_contents, 'r') as fd:
	contents = fd.read()

try:
	# Create tgz archives
	for student in os.listdir(args.senddir):
		print('Creating tar for ' + student)
		# Skip any random added files
		if not os.path.isdir(os.path.join(args.senddir, student)):
			continue
		with tarfile.open(os.path.join(args.outdir, student + '.tgz'), "w:gz") as tar:
			source_dir = os.path.join(args.senddir, student)
			tar.add(source_dir, arcname=os.path.basename(source_dir))
	# Send the emails
	for student in os.listdir(args.outdir):
		# Skip any random added files
		if not student.endswith('.tgz'):
			continue

		netid = os.path.splitext(student)[0]
		hw_name = args.hwname
			
		print('Sending feedback to %s' % (netid + student_email_domain,))
		
		msg = MIMEMultipart()

		# Add body
		msg.attach(MIMEText(contents))

		tgz_path = os.path.join(args.outdir, student)
		part = MIMEBase('application', "octet-stream")
		with open(tgz_path, 'rb') as file:
			part.set_payload(file.read())
			encoders.encode_base64(part)
			part.add_header('Content-Disposition',
				'attachment; filename="{}"'.format(os.path.basename(tgz_path)))
			msg.attach(part)


		msg['Subject'] = hw_name.upper() + " Feedback" 
		msg['From'] = smtpconfig['username']
		msg['To'] = netid + student_email_domain

		# Send the message via our own SMTP server.
		s.sendmail(msg["From"], msg['To'], msg.as_string())
except Exception as e:
	print(e)
finally:
	s.quit()


