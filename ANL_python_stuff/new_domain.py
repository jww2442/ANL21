import json
import random
import os
import decimal

def gen_issue_sizes():
	issue_sizes = []
	for i in range(1, 7):
		issue_sizes.append(random.randrange(2, 6))
	issue_sizes[5] -= 1
	if issue_sizes[5] == 1:
		issue_sizes.remove(1)
	issue_sizes.sort()
	issue_sizes.reverse()
	return issue_sizes

def gen_issuesValues(issue_sizes):
	issuesValues = {}
	for i in range(len(issue_sizes)):
		issue_name = chr(ord('A') + i)
		values = []
		for j in range(issue_sizes[i]):
			issueValue_name = issue_name + chr(ord('A') + j)
			values.append(issueValue_name)
		issuesValues[issue_name] = {"values": values}
	return issuesValues

def gen_weights(issuesValues):
	total = 1.0
	weights = {}
	raw_weights = [0.0, 1.0]
	for i in range(len(issuesValues) - 1):
		raw_weights.append(round(random.random(), 5))
	raw_weights.sort()
	for index, issue in enumerate(issuesValues):
		weights[issue] = round(raw_weights[index + 1] - raw_weights[index], 5)
	return weights

def gen_issueUtils(issuesValues, issue):
	values = issuesValues[issue]["values"]
	utils = [1.0]
	while len(utils) < len(values):
		utils.append(random.random())
	random.shuffle(utils)
	valueUtils = {}
	for value in values:
		valueUtils[value] = utils.pop()
	assert len(utils) == 0
	return {"discreteutils": {"valueUtilities": valueUtils}}
	
def main():
	profile_count = 3

	domain = {}
	issue_sizes = gen_issue_sizes()

	name = "custom"
	for issue_size in issue_sizes:
		name += str(issue_size)
	name += "d"
	domain["name"] = name
	profile_spaces = [{"name": name + str(i+1)} for i in range(profile_count)]

	issuesValues = gen_issuesValues(issue_sizes)
	domain["issuesValues"] = issuesValues

	for profile_space in profile_spaces:
		weights = gen_weights(issuesValues)
		profile_space["issueWeights"] = weights
		issueUtilities = {}
		for issue in issuesValues:
			issueUtils = gen_issueUtils(issuesValues, issue)
			issueUtilities[issue] = issueUtils
		profile_space["issueUtilities"] = issueUtilities
		profile_space["domain"] = domain
	profiles = [{"LinearAdditiveUtilitySpace":profile_space} for profile_space in profile_spaces]

	os.mkdir(path=name)
	with open(name+"/"+name+".json", "w") as d:
		d.write(json.dumps(domain, indent="\t"))
	for profile in profiles:
		with open(name+"/"+profile["LinearAdditiveUtilitySpace"]["name"]+".json", "w") as p:
			p.write(json.dumps(profile, indent="\t"))
			
if __name__ == "__main__":
	main()