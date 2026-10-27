"""CPU DDP image-classification example for IdleGrid.

Bundle this file with data/<class-name>/*.jpg and submit:
  train.py --epochs 2 --batch-size 32

IdleGrid starts one torchrun node on each selected Agent. DistributedSampler
assigns each rank a different sample subset, and DDP averages gradients.
Rank zero writes model.pt in the bundle directory; IdleGrid uploads it when done.
"""

import argparse
from datetime import timedelta
from pathlib import Path

import torch
import torch.distributed as dist
from torch import nn
from torch.nn.parallel import DistributedDataParallel as DDP
from torch.utils.data import DataLoader, DistributedSampler
from torchvision import datasets, transforms


class SmallImageNet(nn.Module):
    def __init__(self, classes: int):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(3, 32, 3, padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(32, 64, 3, padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.AdaptiveAvgPool2d((1, 1)), nn.Flatten(),
        )
        self.classifier = nn.Linear(64, classes)

    def forward(self, images):
        return self.classifier(self.features(images))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=2)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--lr", type=float, default=0.01)
    args = parser.parse_args()

    dist.init_process_group(backend="gloo", timeout=timedelta(minutes=2))
    rank, world = dist.get_rank(), dist.get_world_size()
    torch.manual_seed(1234)

    root = Path(__file__).parent / "data"
    dataset = datasets.ImageFolder(root, transform=transforms.Compose([
        transforms.Resize((64, 64)), transforms.ToTensor(),
    ]))
    sampler = DistributedSampler(dataset, num_replicas=world, rank=rank, shuffle=True)
    loader = DataLoader(dataset, batch_size=args.batch_size, sampler=sampler, num_workers=0)

    model = DDP(SmallImageNet(len(dataset.classes)))
    optimizer = torch.optim.SGD(model.parameters(), lr=args.lr, momentum=0.9)
    loss_fn = nn.CrossEntropyLoss()

    for epoch in range(args.epochs):
        sampler.set_epoch(epoch)
        model.train()
        total_loss = 0.0
        correct = 0
        seen = 0
        for images, labels in loader:
            optimizer.zero_grad(set_to_none=True)
            logits = model(images)
            loss = loss_fn(logits, labels)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            correct += (logits.argmax(dim=1) == labels).sum().item()
            seen += labels.size(0)
        print(f"rank={rank} epoch={epoch + 1}/{args.epochs} loss={total_loss / max(1, len(loader)):.4f} accuracy={correct / max(1, seen):.3f}", flush=True)

    if rank == 0:
        torch.save({"state_dict": model.module.state_dict(), "classes": dataset.classes}, root.parent / "model.pt")
        print("rank 0 saved model.pt", flush=True)
    dist.destroy_process_group()


if __name__ == "__main__":
    main()
