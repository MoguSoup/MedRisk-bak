from pathlib import Path

from PIL import Image, ImageDraw

pages = sorted(Path(__file__).parent.glob("page-*.png"))
width, height = 240, 340
margin, label_height, cols = 20, 24, 5
rows = (len(pages) + cols - 1) // cols
sheet = Image.new("RGB", (cols * (width + margin) + margin, rows * (height + label_height + margin) + margin), "white")
draw = ImageDraw.Draw(sheet)

for i, page in enumerate(pages):
    image = Image.open(page).convert("RGB")
    image.thumbnail((width, height))
    x = margin + (i % cols) * (width + margin)
    y = margin + (i // cols) * (height + label_height + margin)
    sheet.paste(image, (x, y))
    draw.text((x, y + height + 2), page.stem, fill=(0, 0, 0))

sheet.save(Path(__file__).parent / "contact_sheet.png")
