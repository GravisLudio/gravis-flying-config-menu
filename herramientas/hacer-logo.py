"""Logo for Create: Gravis's Flying Config Menu: a grey button with goggles, like the in-game goggles button.
Our own pixel art drawn here from shapes: a leather strap arching over two oval brass-rimmed lenses, the same kind of
goggles the button shows. Create's goggles texture is All Rights Reserved, so no pixel of it is used.
Drawn at 32x32 and scaled with nearest neighbour so the pixels stay sharp.
Usage: python herramientas/hacer-logo.py <output folder>  (then copy icon_512.png to docs/ and icon_128.png to
src/main/resources/assets/gravis_flying_config_menu/icon.png)."""
import math
import pathlib
import sys
from PIL import Image

N = 32
img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# Button: black outline, light top/left, dark bottom/right, grey face.
for y in range(N):
    for x in range(N):
        if x == 0 or y == 0 or x == N - 1 or y == N - 1:
            c = "#000000"
        elif y <= 2 or x == 1:
            c = "#ABABAB"
        elif y >= N - 3 or x == N - 2:
            c = "#505050"
        else:
            c = "#727272"
        px[x, y] = hexc(c)

LEATHER_LIGHT, LEATHER, LEATHER_DARK = hexc("#94603A"), hexc("#6E4024"), hexc("#3F2210")
OUTLINE = hexc("#34190B")
RIM_LIGHT, RIM, RIM_DARK = hexc("#F8E38C"), hexc("#E2B95A"), hexc("#B47A36")
GLASS_DARK, GLASS, GLASS_LIGHT, GLINT = hexc("#356A7B"), hexc("#4C8D9F"), hexc("#73AABA"), hexc("#D8F2F7")
BRIDGE = hexc("#5C2C1C")


def inside(x, y, cx, cy, rx, ry):
    dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
    return dx * dx + dy * dy <= 1.0


# Strap: the upper part of an elliptical band behind the lenses, lighter on its top edge.
SCX, SCY = 16.0, 19.0
for y in range(N):
    for x in range(N):
        if y + 0.5 > 19.5:
            continue
        outer = inside(x, y, SCX, SCY, 12.2, 12.0)
        inner = inside(x, y, SCX, SCY, 9.0, 8.8)
        if outer and not inner:
            edge_out = not inside(x, y, SCX, SCY, 11.2, 11.0)
            edge_in = inside(x, y, SCX, SCY, 9.9, 9.7)
            px[x, y] = LEATHER_DARK if edge_out else LEATHER_LIGHT if edge_in else LEATHER


def lens(cx, cy):
    rx, ry = 6.2, 4.6
    for y in range(N):
        for x in range(N):
            if not inside(x, y, cx, cy, rx, ry):
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if not inside(x, y, cx, cy, rx - 1.0, ry - 1.0):
                px[x, y] = OUTLINE
            elif not inside(x, y, cx, cy, rx - 2.4, ry - 2.3):
                # Brass rim, lit from the top left.
                t = (dx / rx + dy / ry)
                px[x, y] = RIM_LIGHT if t < -0.45 else RIM_DARK if t > 0.55 else RIM
            else:
                px[x, y] = GLASS_DARK if dy < -0.6 else GLASS_LIGHT if dy > 0.6 and dx > 0 else GLASS
    px[int(cx - 2), int(cy - 1)] = GLINT


lens(9.6, 20.0)
lens(22.4, 20.0)

# Leather bridge between the lenses.
for x in (15, 16):
    for y in (18, 19, 20):
        px[x, y] = BRIDGE
px[15, 18] = LEATHER
px[16, 18] = LEATHER

out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
img.save(out / "icon_32.png")
img.resize((512, 512), Image.NEAREST).save(out / "icon_512.png")
img.resize((128, 128), Image.NEAREST).save(out / "icon_128.png")
print("ok")
