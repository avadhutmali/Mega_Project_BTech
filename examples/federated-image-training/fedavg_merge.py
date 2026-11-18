"""Weighted-average local PyTorch state dicts and print a compact run summary."""

import argparse
import json
from pathlib import Path

import torch


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--workers", type=int, required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    updates = [torch.load(f"update-{rank}.pt", map_location="cpu", weights_only=True)
               for rank in range(args.workers)]
    counts = [int(item["num_samples"]) for item in updates]
    total = sum(counts)
    if total <= 0:
        raise RuntimeError("No training examples were reported by workers.")

    merged = {}
    for key in updates[0]["state_dict"]:
        value = updates[0]["state_dict"][key]
        if torch.is_floating_point(value):
            merged[key] = sum(item["state_dict"][key] * count for item, count in zip(updates, counts)) / total
        else:
            merged[key] = value

    torch.save({"state_dict": merged, "classes": updates[0]["classes"], "num_samples": total,
                "strategy": "FedAvg"}, args.output)
    baseline_seconds = max(float(item["local_seconds"]) for item in updates)
    summary = {
        "strategy": "FedAvg",
        "workers": [{"rank": item["rank"], "samples": count, "loss": item["loss"],
                      "accuracy": item["accuracy"], "localSeconds": item["local_seconds"]}
                     for item, count in zip(updates, counts)],
        "totalSamples": total,
        "weightedLoss": sum(float(item["loss"]) * count for item, count in zip(updates, counts)) / total,
        "weightedAccuracy": sum(float(item["accuracy"]) * count for item, count in zip(updates, counts)) / total,
        "modelFile": Path(args.output).name,
        "parallelComputeSeconds": baseline_seconds,
    }
    Path("fedavg_report.json").write_text(json.dumps(summary, sort_keys=True))
    print("FEDAVG_REPORT " + json.dumps(summary, sort_keys=True), flush=True)


if __name__ == "__main__":
    main()
