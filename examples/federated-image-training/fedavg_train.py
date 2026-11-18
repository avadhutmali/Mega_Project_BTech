"""One local training step for IdleGrid's coordinator-mediated FedAvg demo."""

import argparse
import random
import time
from pathlib import Path

import torch
from torch import nn
from torch.utils.data import DataLoader, Subset
from torchvision import datasets, transforms


class TinyImageClassifier(nn.Module):
    def __init__(self, classes: int):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(3, 12, kernel_size=3, padding=1), nn.ReLU(),
            nn.AdaptiveAvgPool2d((1, 1)), nn.Flatten(), nn.Linear(12, classes),
        )

    def forward(self, images):
        return self.net(images)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rank", type=int, required=True)
    parser.add_argument("--world-size", type=int, required=True)
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--checkpoint", required=True)
    args = parser.parse_args()

    random.seed(2026)
    torch.manual_seed(2026)  # identical initial model on every PC
    torch.set_num_threads(1)
    root = Path(__file__).parent / "data"
    dataset = datasets.ImageFolder(root, transform=transforms.Compose([
        transforms.Resize((32, 32)), transforms.ToTensor(),
    ]))
    indices = list(range(args.rank, len(dataset), args.world_size))
    if not indices:
        raise RuntimeError("This worker received no examples; add more images to data/.")
    loader = DataLoader(Subset(dataset, indices), batch_size=8, shuffle=True)

    model = TinyImageClassifier(len(dataset.classes))
    optimizer = torch.optim.SGD(model.parameters(), lr=0.04)
    loss_fn = nn.CrossEntropyLoss()
    started = time.monotonic()
    last_loss, accuracy = 0.0, 0.0
    for epoch in range(args.epochs):
        model.train()
        loss_sum = 0.0
        correct = seen = 0
        for images, labels in loader:
            optimizer.zero_grad(set_to_none=True)
            logits = model(images)
            loss = loss_fn(logits, labels)
            loss.backward()
            optimizer.step()
            loss_sum += loss.item() * labels.size(0)
            correct += (logits.argmax(dim=1) == labels).sum().item()
            seen += labels.size(0)
        last_loss = loss_sum / max(1, seen)
        accuracy = correct / max(1, seen)
        print(f"rank={args.rank} epoch={epoch + 1}/{args.epochs} samples={seen} loss={last_loss:.4f} accuracy={accuracy:.3f}", flush=True)

    elapsed = time.monotonic() - started
    torch.save({
        "state_dict": model.state_dict(),
        "classes": dataset.classes,
        "num_samples": len(indices),
        "loss": last_loss,
        "accuracy": accuracy,
        "local_seconds": elapsed,
        "rank": args.rank,
    }, args.checkpoint)
    print(f"rank={args.rank} update_saved={args.checkpoint} local_seconds={elapsed:.3f}", flush=True)


if __name__ == "__main__":
    main()
