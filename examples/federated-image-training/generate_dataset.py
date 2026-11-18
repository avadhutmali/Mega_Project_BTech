"""Create the deterministic synthetic shape dataset used by the demo bundle."""

import random
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).parent / "data"
CLASSES = ("circle", "square", "triangle")
SAMPLES_PER_CLASS = 300


def main():
    rng = random.Random(2026)
    for class_name in CLASSES:
        folder = ROOT / class_name
        folder.mkdir(parents=True, exist_ok=True)
        for old in folder.glob("*.png"):
            old.unlink()
        for index in range(SAMPLES_PER_CLASS):
            image = Image.new("RGB", (64, 64), (245, 245, 245))
            draw = ImageDraw.Draw(image)
            size = rng.randint(24, 42)
            center_x = rng.randint(27, 37)
            center_y = rng.randint(27, 37)
            left, top = center_x - size // 2, center_y - size // 2
            right, bottom = left + size, top + size
            color = (rng.randint(25, 80), rng.randint(65, 145), rng.randint(145, 235))
            if class_name == "circle":
                draw.ellipse((left, top, right, bottom), fill=color)
            elif class_name == "square":
                draw.rectangle((left, top, right, bottom), fill=color)
            else:
                draw.polygon(((center_x, top), (right, bottom), (left, bottom)), fill=color)
            image.save(folder / f"{class_name}_{index:04d}.png", optimize=True)


if __name__ == "__main__":
    main()
