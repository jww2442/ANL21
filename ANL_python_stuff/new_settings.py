import yaml
import pprint
import copy
import sys

"""
How to use
Takes 1 or 2 command line inputs
1st - a .yaml file containing the parties filepaths and parameters to use
2nd - an output location for the new settings file. Defaults to "settings.yaml"

input .yaml should have a dictionary with 3 key/value pairs...
deadline : double # deadline in seconds
parties : dictionary # parties facing off in tournament
    each party is a dictionary of a filepath and a parameter dictionary
domains : list of domains used in the tournament
    each domain is a list of profile party_path

The output is a .yaml file that can be used as a settings.yaml for the docker negotiation runner
"""

def new_party_data(party_path, params):
	party_data = {
		"party_path" : party_path,
		"params" : str(params)
	}
	return party_data

def new_party(party_path, profile_path, params):
	party = {
		"party" : party_path,
		"profile" : profile_path,
		"parameters" : str(params)
	}
	return party

def new_nego(deadline, party1, party2):
	parties = [
		party1, party2
	]
	negotiation = {
		"deadline" : float(deadline),
		"parties" : parties
	}
	return {
		"negotiation" : negotiation
	}

def new_learn(deadline, *party_path):
	parties = party_path
	learn = {
		"deadline" : deadline,
		"parties" : parties
	}
	return {
		"learn" : learn
	}

def new_tournament(party_datas, domains, deadline):
	tournament = []
	for domain in domains:
		for pf1 in range(len(domain) - 1):
			for pf2 in range(pf1 + 1, len(domain)):
				prof_path_1 = domain[pf1]
				prof_path_2 = domain[pf2]
				for pt1 in range(len(party_datas) - 1):
					for pt2 in range(pt1 + 1, len(party_datas)):
						party_data_1 = party_datas[pt1]
						party_data_2 = party_datas[pt2]
						party_1_prof_1 = new_party(party_data_1["party_path"], prof_path_1, party_data_1["params"])
						party_2_prof_2 = new_party(party_data_2["party_path"], prof_path_2, party_data_2["params"])
						negotiation_1 = new_nego(deadline, party_1_prof_1, party_2_prof_2)
						party_1_prof_2 = new_party(party_data_1["party_path"], prof_path_2, party_data_1["params"])
						party_2_prof_1 = new_party(party_data_2["party_path"], prof_path_1, party_data_2["params"])
						negotiation_2 = new_nego(deadline, party_2_prof_1, party_1_prof_2)
						tournament.append(negotiation_1)
						tournament.append(negotiation_2)
	return tournament

def read_parts_yaml(filepath):
	with open(filepath, 'r') as f:
		parts = yaml.safe_load(f)
	return parts[0]

def write_settings_yaml(filepath, settings_data):
	with open(filepath, 'w') as f:
		documents = yaml.dump(settings_data, f)
	with open(filepath, 'r') as in_f:
		doc = in_f.read()
	doc = doc.replace("\'", "")
	with open(filepath, 'w') as out_f:
		out_f.write(doc)

def main():
	inpath = sys.argv[1]
	outpath = "settings.yaml" if len(sys.argv) < 3 else sys.argv[2]

	parts = read_parts_yaml(inpath)
	party_datas = parts["parties"]
	domains = parts["domains"]
	"""
	party_paths = [
		"parties/boulware-1.6.0-jar-with-dependencies.jar",
		"parties/boulware-1.6.0-jar-with-dependencies.jar",
		"parties/boulware-1.6.0-jar-with-dependencies.jar"
	]
	profile_paths = [
		"profiles/fitness/fitness1.json",
		"profiles/fitness/fitness2.json",
		"profiles/fitness/fitness3.json"
	]
	"""
	deadline = parts["deadline"]
	"""
	party_datas = []
	party_datas.append(new_party_data(party_paths[0], "{\"e\":0.5}"))
	party_datas.append(new_party_data(party_paths[1], "{\"e\":1.0}"))
	party_datas.append(new_party_data(party_paths[2], "{\"e\":2.0}"))
	deadline_all = 2
	tournament = new_tournament(party_datas, [profile_paths], deadline_all)
	"""
	tournament = new_tournament(party_datas, domains, deadline)
	write_settings_yaml(outpath, tournament)

if __name__ == "__main__":
	main()