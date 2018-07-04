# TODO

- Test time limits (make sure people's scripts don't run forever)
- Test file size limits (sort of accomplished by time limits)
- Move to paramiko for doing ssh things for readability
- Instead of parsing summaries at the very end, don't look at the summaries - just do the scanning at the very end. If one of the workers doesn't quite finish, then you still want to get as far as you can get
- Factor more into global variables/better structure. Maybe read everything in from a configuration file?>
- Is there a smarter way of doing this than having a grader.py file run on a remote host? Seems like there should be a better solution than this. 
