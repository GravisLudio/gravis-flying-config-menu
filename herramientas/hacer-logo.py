"""Logo for Create: Gravis's Flying Config Menu: a grey button with brass goggles, like the in-game goggles button.
Our own pixel art drawn here from shapes (Create's goggles texture is All Rights Reserved, so it is not used).
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

# Strap behind the lenses.
for x in range(2, N - 2):
    px[x, 15] = hexc("#6A4A2C")
    px[x, 16] = hexc("#4A321C")
    px[x, 17] = hexc("#33220F")

RIM_LIGHT, RIM_MID, RIM_DARK, OUTLINE = hexc("#F4D06A"), hexc("#C8932E"), hexc("#7E561A"), hexc("#2E1E0C")
GLASS_LIGHT, GLASS_MID, GLASS_DARK, GLINT = hexc("#9BE6F0"), hexc("#4DB4CB"), hexc("#25657E"), hexc("#F2FDFF")


def lens(cx, cy):
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            # Light comes from the top left: -1 there, +1 at the bottom right.
            shade = (dx + dy) / (math.hypot(dx, dy) * math.sqrt(2)) if d > 0 else 0
            if d <= 3.7:
                if shade < -0.35 and d > 1.2:
                    c = GLASS_LIGHT
                elif shade > 0.35 and d > 1.2:
                    c = GLASS_DARK
                else:
                    c = GLASS_MID
                px[x, y] = c
            elif d <= 5.4:
                px[x, y] = RIM_LIGHT if shade < -0.3 else RIM_DARK if shade > 0.45 else RIM_MID
            elif d <= 6.3:
                px[x, y] = OUTLINE
    # A small glint on the glass.
    gx, gy = int(cx - 2), int(cy - 2)
    px[gx, gy] = GLINT
    px[gx + 1, gy] = GLINT
    px[gx, gy + 1] = GLINT


lens(9.5, 16.0)
lens(22.5, 16.0)

# Bridge between the lenses.
px[15, 14] = RIM_LIGHT
px[16, 14] = RIM_LIGHT
px[15, 15] = RIM_DARK
px[16, 15] = RIM_DARK

out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
img.save(out / "icon_32.png")
img.resize((512, 512), Image.NEAREST).save(out / "icon_512.png")
img.resize((128, 128), Image.NEAREST).save(out / "icon_128.png")
print("ok")
