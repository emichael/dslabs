import sys
import pandas

pandas.read_json(sys.argv[1]).to_csv('out.csv')

