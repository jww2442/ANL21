import json
import subprocess

from scripts.utility_space import UtilitySpace


class Session:
    exec_command = [
        "java",
        "-cp",
        "scripts/simplerunner-1.6.0-jar-with-dependencies.jar:parties/*",
        "geniusweb.simplerunner.NegoRunner",
        "settings.json",
    ]

    def __init__(self, session_data):
        self.mode = next(iter(session_data.keys()))
        self.profiles = []
        session_data = next(iter(session_data.values()))

        participants = []
        self.tags = []
        for party in session_data["parties"]:
            self.profiles.append(party["profile"])
            participants.append(
                {
                    "TeamInfo": {
                        "parties": [
                            {
                                "party": {
                                    "partyref": f"classpath:{party['party']}",
                                    "parameters": party["parameters"]
                                    if "parameters" in party
                                    else {},
                                },
                                "profile": party["profile"],
                            }
                        ]
                    }
                }
            )
            self.tags.append(party["parameters"]["tag"] if "tag" in party["parameters"]
                        else "") 

        self.settings = {
            "LearnSettings"
            if self.mode == "learn"
            else "SAOPSettings": {
                "participants": participants,
                "deadline": {
                    "deadlinetime": {"durationms": session_data["deadline"] * 1000}
                },
            }
        }

    def execute(self):
        with open("settings.json", "w") as f:
            f.write(json.dumps(self.settings))

        self.process = subprocess.Popen(
            self.exec_command, stderr=subprocess.PIPE, universal_newlines=True
        )
        try:
            _, self.stderr = self.process.communicate(
                timeout=self.settings["LearnSettings" if self.mode == "learn" else "SAOPSettings"]["deadline"]["deadlinetime"]["durationms"] / 1000 * 1.2 + 5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            with open("results/myReport.txt", "a") as test_report, open("settings.json", "r") as report_settings:
                test_report.writelines(["Begin Settings\n" + report_settings.read() + "\nEnd settings\n"])
            _, self.stderr = self.process.communicate()

    def post_process(self, id):
        with open("results.json") as f:
            results = json.load(f)

        if self.mode == "negotiation":
            self.add_utilities_to_results(results)

        with open(f"results/{id+1:04d}_{self.mode}.json", "w") as f:
            f.write(json.dumps(results, indent=2))

    def add_utilities_to_results(self, results):
        results = results["SAOPState"]
        tags_map = {}
        for i, name in enumerate(results["connections"]):
            tags_map[name] = self.tags[i] or ""
        results["tags"] = tags_map

        if not results["actions"]:
            print(
                f"\nWARNING: Session has failed (error can also be found in result json-file):\n{self.stderr}\n"
            )
            results["error"] = self.stderr
        else:
            utility_spaces = {
                k: UtilitySpace(v["profile"])
                for k, v in results["partyprofiles"].items()
            }
            for k, v in results["partyprofiles"].items():
                results["partyprofiles"][k]["party"]["partyref"] += tags_map[k]
            for action in results["actions"]:
                if "offer" in action:
                    offer = action["offer"]
                elif "accept" in action:
                    offer = action["accept"]
                else:
                    continue

                offer["actor"] += tags_map[offer["actor"]]
                bid = offer["bid"]["issuevalues"]
                offer["utilities"] = {
                    (k + tags_map[k]): v.get_utility(bid) for k, v in utility_spaces.items()
                }
