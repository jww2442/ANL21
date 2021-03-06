import glob
import os
import json
from collections import defaultdict
import plotly.graph_objects as go


def plot_results():
    result_files = sorted(glob.glob("results/*negotiation.json"))
    summary = [
        "session,agent A,agent B,profile A,profile B,utility A,utility B,agreement"
    ]

    for result_file in result_files:
        with open(result_file, "r") as f:
            result = json.loads(f.read())["SAOPState"]

        utilities = defaultdict(
            lambda: defaultdict(lambda: {"x": [], "y": [], "bids": []})
        )
        accept = {"x": [], "y": [], "bids": []}
        for index, action in enumerate(result["actions"], 1):
            if "offer" in action:
                offer = action["offer"]
                actor = offer["actor"]
                for agent, util in offer["utilities"].items():
                    utilities[agent][actor]["x"].append(index)
                    utilities[agent][actor]["y"].append(util)
                    utilities[agent][actor]["bids"].append(offer["bid"]["issuevalues"])
            elif "accept" in action:
                offer = action["accept"]
                index -= 1
                for agent, util in offer["utilities"].items():
                    accept["x"].append(index)
                    accept["y"].append(util)
                    accept["bids"].append(offer["bid"]["issuevalues"])

        fig = go.Figure()
        fig.add_trace(
            go.Scatter(
                mode="markers",
                x=accept["x"],
                y=accept["y"],
                name="agreement",
                marker={"color": "green", "size": 15},
                hoverinfo="skip",
            )
        )

        color = {0: "red", 1: "blue"}
        for i, (agent, data) in enumerate(utilities.items()):
            for actor, utility in data.items():
                name = "_".join(agent.split("_")[-2:])
                text = []
                for bid, util in zip(utility["bids"], utility["y"]):
                    text.append(
                        "<br>".join(
                            [f"<b>utility: {util:.3f}</b><br>"]
                            + [f"{i}: {v}" for i, v in bid.items()]
                        )
                    )
                fig.add_trace(
                    go.Scatter(
                        mode="lines+markers" if agent == actor else "markers",
                        x=utilities[agent][actor]["x"],
                        y=utilities[agent][actor]["y"],
                        name=f"{name} offered"
                        if agent == actor
                        else f"{name} received",
                        legendgroup=agent,
                        marker={"color": color[i]},
                        hovertext=text,
                        hoverinfo="text",
                    )
                )

        fig.update_layout(
            # width=1000,
            height=800,
            legend={
                "yanchor": "bottom",
                "y": 1,
                "xanchor": "left",
                "x": 0,
            },
        )
        fig.update_xaxes(title_text="round", range=[0, index + 1], ticks="outside")
        fig.update_yaxes(title_text="utility", range=[0, 1], ticks="outside")
        fig.write_html(f"{os.path.splitext(result_file)[0]}.html")

        partyprofiles = result["partyprofiles"]
        agents = [v["party"]["partyref"].split(".")[-1] for v in partyprofiles.values()]
        profiles = [v["profile"].split(":")[-1] for v in partyprofiles.values()]
        summary.append(
            ",".join(
                [
                    os.path.splitext(os.path.basename(result_file))[0],
                    *agents,
                    *profiles,
                    *[str(x) for x in (accept["y"] if accept["y"] else [0.0, 0.0])],
                    "True" if accept["y"] else "False",
                ]
            )
        )
    with open("results/summary.csv", "w") as f:
        f.write("\n".join(summary))
