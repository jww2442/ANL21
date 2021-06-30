import csv
import sys
import numpy as np

"""
How to use

Takes 1-2 arguments
1st - input .csv file. summary.csv is the name of the file that the docker runner generates 
2nd - output .json file. Outputs ranking order only if this filepath is specified
"""

class agent:
	def __init__(self, name):
		self.name = name
		self.utils = {}
		self.opp_utils = {}
		self.agreements = {}

	def add_match(self, opp_name, self_util, opp_util, agreement):
		if opp_name not in self.utils.keys():
			self.utils[opp_name] = []
		if opp_name not in self.opp_utils.keys():
			self.opp_utils[opp_name] = []
		if opp_name not in self.agreements.keys():
			self.agreements[opp_name] = []
		self.utils[opp_name].append(self_util)
		self.opp_utils[opp_name].append(opp_util)
		self.agreements[opp_name].append(agreement)

	def match_count(self):
		return np.sum([len(x) for x in list(self.agreements.values())])

	def avg_self_util(self):
		return np.average(list(self.utils.values()))

	def avg_opp_util(self):
		return np.average(list(self.opp_utils.values()))

	def percent_agreement(self):
		agreements = np.sum([x.count(True) for x in list(self.agreements.values())])
		rounds = self.match_count()
		return agreements / rounds

	def match_count_against(self, opp_name):
		return 0 if opp_name not in list(self.agreements.keys()) else len(self.agreements[opp_name])

	def avg_self_util_against(self, opp_name):
		return np.average(self.utils[opp_name])

	def avg_opp_util_against(self, opp_name):
		return np.average(self.opp_utils[opp_name])

	def percent_agreement_against(self, opp_name):
		agreements = list(self.agreements[opp_name]).count(True)
		rounds = self.match_count_against(opp_name)
		return agreements / rounds

class profile:
	def __init__(self, name):
		self.name = name
		self.utils = {}
		self.opp_utils = {}
		self.agreements = {}

	def add_match(self, opp_prof_name, self_util, opp_util, agreement):
		if opp_prof_name not in self.utils.keys():
			self.utils[opp_prof_name] = []
		if opp_prof_name not in self.opp_utils.keys():
			self.opp_utils[opp_prof_name] = []
		if opp_prof_name not in self.agreements.keys():
			self.agreements[opp_prof_name] = []
		self.utils[opp_prof_name].append(self_util)
		self.opp_utils[opp_prof_name].append(opp_util)
		self.agreements[opp_prof_name].append(agreement)

	def match_count(self):
		return np.sum([len(x) for x in list(self.agreements.values())])

	def avg_self_util(self):
		return np.average(list(self.utils.values()))

	def avg_opp_util(self):
		return np.average(list(self.opp_utils.values()))

	def percent_agreement(self):
		agreements = np.sum([x.count(True) for x in list(self.agreements.values())])
		rounds = self.match_count()
		return agreements / rounds

	def match_count_against(self, opp_prof_name):
		return 0 if opp_prof_name not in list(self.agreements.keys()) else len(self.agreements[opp_prof_name])

	def avg_self_util_against(self, opp_prof_name):
		return np.average(self.utils[opp_prof_name])

	def avg_opp_util_against(self, opp_prof_name):
		return np.average(self.opp_utils[opp_prof_name])

	def percent_agreement_against(self, opp_prof_name):
		agreements = list(self.agreements[opp_prof_name]).count(True)
		rounds = self.match_count_against(opp_prof_name)
		return agreements / rounds

class domain:
	def __init__(self, name):
		self.name = name
		self.profiles = {}

	def add_profile(self, profile_name):
		self.profiles[profile_name] = profile(profile_name)

	def profile_count(self):
		return len(self.profiles.keys())

class tournament_stats:
	def __init__(self, agents, domains):
		self.agents = agents
		self.domains = domains

	def match_count(self):
		return np.sum([x.match_count() for x in list(self.agents.values())]) / 2

	def agent_count(self):
		return len(self.agents)

	def domain_count(self):
		return len(self.domains)

	def profile_count(self):
		return np.sum(x.profile_count() for x in list(self.domains.values()))

	def rankings(self):
		stats = []
		for a in list(self.agents.values()):
			a_stats = {}
			a_stats["name"] = a.name
			a_stats["avg util"] = a.avg_self_util()
			stats.append(a_stats)
		ranking = sorted(stats, key= lambda i: i["avg util"], reverse=True)
		rank = 1
		for stats in ranking:
			stats["rank"] = rank
			rank += 1
		return ranking

	def rankings_against(self, opp_name):
		stats = []
		for a in list(self.agents.values()):
			if a.match_count_against(opp_name) != 0:
				a_stats = {}
				a_stats["name"] = a.name
				a_stats["avg util"] = a.avg_self_util_against(opp_name)
				stats.append(a_stats)
		ranking = sorted(stats, key= lambda i: i["avg util"], reverse=True)
		rank = 1
		for stats in ranking:
			stats["rank"] = rank
			rank += 1
		return ranking

	def profile_rankings(self):
		stats = []
		profiles = []
		for d in list(self.domains.values()):
			profiles.extend(list(d.profiles.values()))
		for p in profiles:
			p_stats = {}
			p_stats["name"] = p.name
			p_stats["avg util"] = p.avg_self_util()
			stats.append(p_stats)
		ranking = sorted(stats, key= lambda i: i["avg util"], reverse=True)
		rank = 1
		for stats in ranking:
			stats["rank"] = rank
			rank += 1
		return ranking

	def profile_rankings_against(self, opp_prof_name):
		stats = []
		profiles = []
		for d in list(self.domains.values()):
			profiles.extend(list(d.profiles.values()))
		for p in profiles:
			if p.match_count_against(opp_prof_name) != 0:
				p_stats = {}
				p_stats["name"] = p.name
				p_stats["avg util"] = p.avg_self_util_against(opp_prof_name)
				stats.append(p_stats)
		ranking = sorted(stats, key= lambda i: i["avg util"], reverse=True)
		rank = 1
		for stats in ranking:
			stats["rank"] = rank
			rank += 1
		return ranking

def read_summary_csv(filepath):
	with open(filepath) as csv_file:
		csv_reader = csv.DictReader(csv_file)
		round_count = 0
		agents = {}
		domains = {}
		for row in csv_reader:
			round_count += 1
			agent_a_name = str(row["agent A"])
			agent_b_name = str(row["agent B"])
			domain_name = str(row["profile A"].rsplit("/", 1)[0])
			profile_a_name = str(row["profile A"].rsplit("/", 1)[1])
			profile_b_name = str(row["profile B"].rsplit("/", 1)[1])
			util_a = float(row["utility A"])
			util_b = float(row["utility B"])
			agreement = row["agreement"] == "True"

			if agent_a_name not in agents.keys():
				agents[agent_a_name] = agent(agent_a_name)
			agent_a = agents[agent_a_name]
			if agent_b_name not in agents.keys():
				agents[agent_b_name] = agent(agent_b_name)
			agent_b = agents[agent_b_name]
			if domain_name not in domains:
				domains[domain_name] = domain(domain_name)
			domain_ab = domains[domain_name]
			profiles = domain_ab.profiles
			if profile_a_name not in profiles.keys():
				profiles[profile_a_name] = profile(profile_a_name)
			profile_a = profiles[profile_a_name]
			if profile_b_name not in profiles.keys():
				profiles[profile_b_name] = profile(profile_b_name)
			profile_b = profiles[profile_b_name]

			agent_a.add_match(agent_b_name, util_a, util_b, agreement)
			agent_b.add_match(agent_a_name, util_b, util_a, agreement)
			profile_a.add_match(profile_b_name, util_a, util_b, agreement)
			profile_b.add_match(profile_a_name, util_b, util_a, agreement)

		stats = tournament_stats(agents, domains)
	return stats

def write(filepath, data):
	with open(filepath, "r") as f:
		f.write(data)

def main():
	inpath = sys.argv[1]
	outpath = None if len(sys.argv) < 3 else sys.argv[2]

	stats = read_summary_csv(inpath)
	agents = stats.agents
	domains = stats.domains

	# Decide what to print below here

	for a in agents.values():
		print(a.name)
		print(a.avg_self_util())
		print(a.avg_opp_util())
		print(a.percent_agreement())
		print()

	print()
	for d in domains.values():
		for p in d.profiles.values():
			print(p.name)
			print(p.avg_self_util())
			print(p.avg_opp_util())
			print(p.percent_agreement())
			print()
		print()

	for stat in stats.rankings():
		print(stat)
	print()
	for prof_stat in stats.profile_rankings():
		print(prof_stat)
	print()
	print(stats.rankings_against(list(agents.keys())[0]))
	print(stats.profile_rankings_against(list(list(domains.values())[0].profiles.values())[0].name))
	if outpath is not None:
		write(outpath, stats.rankings())

if __name__ == "__main__":
	main()