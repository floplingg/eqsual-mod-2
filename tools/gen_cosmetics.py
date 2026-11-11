#!/usr/bin/env python3
"""
Eqsual Cosmetics asset generator.

Builds, from the definitions in this file:
  * src/main/resources/eqsual_cosmetics/cosmetics.json   (3D models: cuboid parts, UVs, animations, particles)
  * src/main/resources/assets/eqsual_cosmetics/textures/cosmetic/atlas.png   (model texture atlas)
  * src/main/resources/assets/eqsual_cosmetics/textures/gui/icons.png        (menu icons, rendered from the 3D models)

Model space (pixels, 16 px = 1 block):
  origin = centre of the top of the player's head
  +Y = up, +Z = towards the face (front), X = left/right
  The head itself spans x,z in [-4, 4] and y in [-8, 0]; the skin hat layer reaches +-4.5.

Run:  python3 tools/gen_cosmetics.py      (requires Pillow)
"""
import json, math, os, random, zlib
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ATLAS_SIZE = 128
TILE = 16

# ----------------------------------------------------------------------------------------------------------------------
# Texture tiles
# ----------------------------------------------------------------------------------------------------------------------
TILES = {}  # name -> (tx, ty) tile coords
_atlas = Image.new("RGBA", (ATLAS_SIZE, ATLAS_SIZE), (0, 0, 0, 0))


def _clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return tuple(_clamp(x * f) for x in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def tile(name):
    def deco(fn):
        idx = len(TILES)
        tx, ty = idx % (ATLAS_SIZE // TILE), idx // (ATLAS_SIZE // TILE)
        rnd = random.Random(zlib.crc32(name.encode()))
        img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
        fn(img, rnd)
        _atlas.paste(img, (tx * TILE, ty * TILE))
        TILES[name] = (tx, ty)
        return fn
    return deco


def noise_fill(img, rnd, base, var=0.12, alpha=255):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            f = 1 + rnd.uniform(-var, var)
            c = shade(base, f)
            px[x, y] = c[:3] + (alpha,)


def metal(img, rnd, base, hi, lo):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = (math.sin((x + y) * 0.55) + 1) / 2
            c = tuple(_clamp(lo[i] + (hi[i] - lo[i]) * (0.35 + 0.65 * t) * rnd.uniform(0.92, 1.05)) for i in range(3))
            px[x, y] = c + (255,)
    for _ in range(5):
        x, y = rnd.randrange(TILE), rnd.randrange(TILE)
        px[x, y] = (255, 255, 240, 255)


@tile("gold")
def _(img, r): metal(img, r, None, (255, 225, 90), (190, 120, 20))


@tile("gold_dark")
def _(img, r): metal(img, r, None, (215, 160, 40), (120, 70, 10))


@tile("red_velvet")
def _(img, r):
    noise_fill(img, r, (170, 18, 30), 0.15)
    d = ImageDraw.Draw(img)
    for y in range(0, TILE, 4):
        d.line([(0, y), (TILE, y)], fill=(130, 10, 22, 255))


@tile("black_velvet")
def _(img, r):
    noise_fill(img, r, (30, 26, 32), 0.25)
    d = ImageDraw.Draw(img)
    for x in range(0, TILE, 5):
        d.line([(x, 0), (x, TILE)], fill=(48, 40, 50, 255))


@tile("ruby")
def _(img, r):
    noise_fill(img, r, (220, 20, 50), 0.1)
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 7, 7], fill=(255, 120, 140, 255))
    d.point([(3, 3), (4, 3)], fill=(255, 230, 235, 255))


@tile("sapphire")
def _(img, r):
    noise_fill(img, r, (30, 80, 220), 0.1)
    ImageDraw.Draw(img).rectangle([2, 2, 6, 6], fill=(140, 180, 255, 255))


@tile("emerald")
def _(img, r):
    noise_fill(img, r, (20, 170, 80), 0.1)
    ImageDraw.Draw(img).rectangle([2, 2, 6, 6], fill=(140, 255, 180, 255))


@tile("white_fur")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            v = r.randint(215, 255)
            px[x, y] = (v, v, _clamp(v + 2), 255)


@tile("santa_red")
def _(img, r):
    noise_fill(img, r, (200, 25, 30), 0.12)
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            if (x * 3 + y * 5) % 11 == 0:
                px[x, y] = (170, 15, 22, 255)


@tile("antler")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = math.sin(y * 0.9 + x * 0.2) * 0.08
            px[x, y] = shade((150, 105, 60), 1 + t + r.uniform(-0.1, 0.1))


@tile("pumpkin")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            rib = 0.82 if x % 4 == 0 else (0.93 if x % 4 == 3 else 1.0)
            px[x, y] = shade((235, 130, 20), rib * r.uniform(0.94, 1.04))


@tile("pumpkin_face")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            rib = 0.82 if x % 4 == 0 else 1.0
            px[x, y] = shade((235, 130, 20), rib * r.uniform(0.94, 1.04))
    d = ImageDraw.Draw(img)
    hole = (40, 18, 5, 255)
    d.polygon([(3, 6), (6, 6), (4.5, 3)], fill=hole)
    d.polygon([(10, 6), (13, 6), (11.5, 3)], fill=hole)
    d.polygon([(7, 9), (9, 9), (8, 7)], fill=hole)
    d.rectangle([3, 11, 12, 13], fill=hole)
    d.rectangle([5, 11, 6, 11], fill=(235, 130, 20, 255))
    d.rectangle([9, 13, 10, 13], fill=(235, 130, 20, 255))


@tile("pumpkin_glow")
def _(img, r):
    # Emissive overlay: only the carved holes are opaque.
    d = ImageDraw.Draw(img)
    glow = (255, 210, 60, 255)
    d.polygon([(3, 6), (6, 6), (4.5, 3)], fill=glow)
    d.polygon([(10, 6), (13, 6), (11.5, 3)], fill=glow)
    d.polygon([(7, 9), (9, 9), (8, 7)], fill=glow)
    d.rectangle([3, 11, 12, 13], fill=glow)
    d.rectangle([5, 11, 6, 11], fill=(0, 0, 0, 0))
    d.rectangle([9, 13, 10, 13], fill=(0, 0, 0, 0))


@tile("stem")
def _(img, r): noise_fill(img, r, (70, 110, 35), 0.15)


@tile("witch")
def _(img, r):
    noise_fill(img, r, (38, 24, 52), 0.2)
    px = img.load()
    for _ in range(6):
        px[r.randrange(TILE), r.randrange(TILE)] = (70, 45, 95, 255)


@tile("witch_band")
def _(img, r):
    noise_fill(img, r, (120, 40, 170), 0.1)
    ImageDraw.Draw(img).line([(0, 1), (TILE, 1)], fill=(170, 90, 220, 255))


@tile("devil")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = 1 - y / TILE * 0.35
            px[x, y] = shade((200, 20, 20), t * r.uniform(0.9, 1.08))


@tile("devil_dark")
def _(img, r): noise_fill(img, r, (95, 8, 12), 0.15)


@tile("bunny")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            v = r.randint(232, 252)
            px[x, y] = (v, v, v, 255)


@tile("bunny_pink")
def _(img, r): noise_fill(img, r, (250, 160, 190), 0.06)


@tile("leaf")
def _(img, r):
    noise_fill(img, r, (60, 150, 50), 0.18)
    d = ImageDraw.Draw(img)
    for i in range(0, TILE, 4):
        d.line([(i, 0), (i + 3, TILE)], fill=(40, 115, 35, 255))


@tile("petal_red")
def _(img, r): noise_fill(img, r, (235, 60, 90), 0.1)


@tile("petal_yellow")
def _(img, r): noise_fill(img, r, (255, 215, 60), 0.08)


@tile("petal_blue")
def _(img, r): noise_fill(img, r, (110, 150, 255), 0.08)


@tile("petal_pink")
def _(img, r): noise_fill(img, r, (255, 170, 215), 0.06)


@tile("petal_white")
def _(img, r): noise_fill(img, r, (250, 250, 245), 0.03)


@tile("halo")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = abs(y - 7.5) / 8
            px[x, y] = (_clamp(255), _clamp(240 - 40 * t), _clamp(150 - 90 * t), 255)


@tile("iron")
def _(img, r): metal(img, r, None, (210, 214, 222), (105, 110, 120))


@tile("ivory")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            band = 0.9 if y % 3 == 0 else 1.0
            px[x, y] = shade((240, 228, 200), band * r.uniform(0.95, 1.03))


@tile("leather")
def _(img, r):
    noise_fill(img, r, (110, 70, 40), 0.15)
    d = ImageDraw.Draw(img)
    for x in range(1, TILE, 4):
        d.point([(x, 2), (x, 13)], fill=(200, 170, 120, 255))


@tile("ice")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = (math.sin(x * 0.8 - y * 0.6) + 1) / 2
            px[x, y] = (_clamp(150 + 60 * t), _clamp(210 + 35 * t), 255, 205)


@tile("ice_crystal")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            v = 235 + r.randint(-10, 20)
            px[x, y] = (_clamp(v - 25), _clamp(v), 255, 230)
    d = ImageDraw.Draw(img)
    d.line([(0, 8), (16, 8)], fill=(255, 255, 255, 255))
    d.line([(8, 0), (8, 16)], fill=(255, 255, 255, 255))


@tile("fire_orange")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = y / TILE
            px[x, y] = (255, _clamp(70 + 120 * t + r.randint(-20, 20)), _clamp(10 + 30 * t), 255)


@tile("fire_yellow")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            t = y / TILE
            px[x, y] = (255, _clamp(190 + 60 * t), _clamp(60 + 120 * t + r.randint(-15, 15)), 255)


@tile("obsidian")
def _(img, r):
    noise_fill(img, r, (35, 18, 45), 0.3)
    px = img.load()
    for _ in range(7):
        px[r.randrange(TILE), r.randrange(TILE)] = (255, 120, 30, 255)


@tile("hat_black")
def _(img, r): noise_fill(img, r, (24, 24, 28), 0.15)


@tile("hat_band")
def _(img, r):
    noise_fill(img, r, (165, 22, 32), 0.08)
    ImageDraw.Draw(img).line([(0, 0), (TILE, 0)], fill=(210, 60, 70, 255))


@tile("knit")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            v = 1.0 if (x + (y // 2)) % 2 else 0.86
            px[x, y] = shade((40, 150, 160), v * r.uniform(0.95, 1.04))


@tile("knit_white")
def _(img, r):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            v = 1.0 if (x + (y // 2)) % 2 else 0.88
            px[x, y] = shade((240, 240, 240), v)


@tile("party")
def _(img, r):
    px = img.load()
    cols = [(255, 80, 160), (80, 200, 255), (255, 220, 60), (120, 230, 120)]
    for y in range(TILE):
        for x in range(TILE):
            px[x, y] = cols[((x + y) // 4) % 4] + (255,)


# ----------------------------------------------------------------------------------------------------------------------
# Model building helpers
# ----------------------------------------------------------------------------------------------------------------------
FACES = ["up", "down", "north", "south", "west", "east"]  # north = -Z (back), south = +Z (front)


def box(frm, to, tex, glow=False, translucent=False, rot=None, pivot=None, anim=None, faces=None, stretch=None):
    p = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "tex": tex}
    if glow: p["glow"] = True
    if translucent: p["translucent"] = True
    if rot: p["rot"] = [round(v, 3) for v in rot]
    if pivot is not None: p["pivot"] = [round(v, 3) for v in pivot]
    if anim: p["anim"] = anim
    if faces: p["faces"] = faces
    if stretch: p["stretch"] = stretch
    return p


def cbox(cx, cy, cz, sx, sy, sz, tex, **kw):
    """Box centred on (cx, cz) horizontally, with its base at cy."""
    return box([cx - sx / 2, cy, cz - sz / 2], [cx + sx / 2, cy + sy, cz + sz / 2], tex, **kw)


def ring(r, t, y0, y1, tex, **kw):
    """Square ring (4 boxes) around the head; r = outer half-size, t = thickness."""
    i = r - t
    return [
        box([-r, y0, i], [r, y1, r], tex, **kw),       # front
        box([-r, y0, -r], [r, y1, -i], tex, **kw),     # back
        box([-r, y0, -i], [-i, y1, i], tex, **kw),     # left
        box([i, y0, -i], [r, y1, i], tex, **kw),       # right
    ]


def around(r, positions, fn):
    """Places items along the 4 sides of a square ring of half-size r.
    positions: offsets along each side (-r..r). fn(x, z, yaw) -> list of parts."""
    out = []
    for side, (nx, nz, yaw) in enumerate([(0, 1, 0), (0, -1, 180), (-1, 0, -90), (1, 0, 90)]):
        for o in positions:
            if nx == 0:
                x, z = o, nz * r
            else:
                x, z = nx * r, o
            out += fn(x, z, yaw)
    return out


COSMETICS = []


def cosmetic(id, name, cats, slot, desc, parts, particles=None, tags=()):
    COSMETICS.append({
        "id": id, "name": name, "categories": cats, "slot": slot, "description": desc,
        "tags": list(tags), "particles": particles, "parts": parts,
    })


def flicker(speed, amp, phase):
    return {"type": "flicker", "speed": speed, "amp": amp, "phase": phase}


# --- Royal Crown (red & black) ----------------------------------------------------------------------------------------
def royal_crown():
    P = []
    P += ring(5.0, 1.0, -0.6, 2.4, "black_velvet")
    P += ring(5.15, 1.15, -0.8, -0.1, "gold")          # lower gold trim
    P += ring(5.15, 1.15, 2.2, 2.8, "gold")            # upper gold trim
    P.append(box([-4, 2.0, -4], [4, 3.6, 4], "red_velvet"))   # velvet cap
    P.append(box([-3, 3.6, -3], [3, 4.4, 3], "red_velvet"))
    P.append(box([-1.5, 4.4, -1.5], [1.5, 4.9, 1.5], "gold"))  # cap boss

    def spike(x, z, yaw):
        big = abs(x) < 0.01 and abs(z) > 0 or abs(z) < 0.01 and abs(x) > 0
        h = 3.6 if big else 2.4
        col = "red_velvet" if big else "black_velvet"
        out = [cbox(x, 2.8, z, 1.4, h, 1.4, col, rot=[0, 45, 0], pivot=[x, 2.8, z]),
               cbox(x, 2.8 + h, z, 1.1, 1.1, 1.1, "gold", rot=[0, 45, 0], pivot=[x, 2.8 + h, z])]
        return out
    P += around(4.55, [-4.0, 0.0, 4.0], spike)

    def gem(x, z, yaw):
        if yaw in (0, 180):
            return [box([x - 0.7, 0.3, z - 0.35], [x + 0.7, 1.7, z + 0.35], "ruby")]
        return [box([x - 0.35, 0.3, z - 0.7], [x + 0.35, 1.7, z + 0.7], "ruby")]
    P += around(5.05, [-2.2, 2.2], gem)
    P.append(box([-1.0, 0.1, 5.0], [1.0, 2.0, 5.6], "ruby", glow=True))   # big front gem
    # Orb and cross on top
    P.append(cbox(0, 4.9, 0, 1.6, 1.6, 1.6, "gold_dark"))
    P.append(cbox(0, 6.5, 0, 0.6, 2.0, 0.6, "gold"))
    P.append(cbox(0, 7.4, 0, 1.8, 0.6, 0.6, "gold"))
    return P


cosmetic("royal_crown", "Royal Crown", ["Crowns"], "head",
         "A red and black crown trimmed in gold, set with rubies.", royal_crown(),
         particles={"type": "end_rod", "chance": 0.03, "spread": 0.3, "y": 0.45, "vy": 0.005},
         tags=["red", "black", "king", "queen", "ruby"])


# --- Golden Crown ----------------------------------------------------------------------------------------------------
def golden_crown():
    P = ring(4.9, 0.9, -0.5, 2.0, "gold")
    P += ring(5.05, 1.05, -0.6, 0.0, "gold_dark")

    def spike(x, z, yaw):
        return [cbox(x, 2.0, z, 1.2, 2.2, 1.2, "gold", rot=[0, 45, 0], pivot=[x, 2.0, z]),
                cbox(x, 4.2, z, 0.8, 0.8, 0.8, "gold_dark")]
    P += around(4.45, [-4.0, -1.35, 1.35, 4.0], spike)

    gems = ["sapphire", "emerald", "ruby"]

    def gem(x, z, yaw):
        g = gems[int(abs(x * 7 + z * 3)) % 3]
        if yaw in (0, 180):
            return [box([x - 0.6, 0.2, z - 0.3], [x + 0.6, 1.4, z + 0.3], g)]
        return [box([x - 0.3, 0.2, z - 0.6], [x + 0.3, 1.4, z + 0.6], g)]
    P += around(4.95, [-2.6, 0.0, 2.6], gem)
    return P


cosmetic("golden_crown", "Golden Crown", ["Crowns"], "head", "Classic solid-gold crown with jewels.",
         golden_crown(), particles={"type": "wax_on", "chance": 0.04, "spread": 0.35, "y": 0.35, "vy": 0.0},
         tags=["gold", "king", "royal"])


# --- Fire Crown ------------------------------------------------------------------------------------------------------
def fire_crown():
    P = ring(4.9, 0.9, -0.5, 1.6, "obsidian")
    P += ring(5.05, 1.05, 1.4, 1.9, "gold_dark")
    i = 0

    def flame(x, z, yaw):
        nonlocal i
        i += 1
        h = 3.2 + (i % 3) * 0.9
        ph = i * 1.7
        return [cbox(x, 1.9, z, 1.6, h, 1.6, "fire_orange", glow=True, rot=[0, 45, 0], pivot=[x, 1.9, z],
                     anim=flicker(0.35, 0.28, ph)),
                cbox(x, 1.9, z, 0.9, h * 0.65, 0.9, "fire_yellow", glow=True, rot=[0, 45, 0], pivot=[x, 1.9, z],
                     anim=flicker(0.5, 0.3, ph + 1.0))]
    P += around(4.45, [-3.6, -1.2, 1.2, 3.6], flame)
    return P


cosmetic("fire_crown", "Fire Crown", ["Crowns", "Other"], "head", "A charred crown wreathed in living flame.",
         fire_crown(), particles={"type": "flame", "chance": 0.25, "spread": 0.32, "y": 0.42, "vy": 0.02},
         tags=["fire", "flame", "hot", "glow"])


# --- Snowflake Crown -------------------------------------------------------------------------------------------------
def snowflake_crown():
    P = ring(4.9, 0.9, -0.4, 1.4, "ice", translucent=True)
    spin = {"type": "spin", "speed": 1.2}

    def spike(x, z, yaw):
        big = (abs(x) < 0.01) != (abs(z) < 0.01)
        h = 4.2 if big else 2.6
        out = [cbox(x, 1.2, z, 1.0, h, 1.0, "ice_crystal", glow=True, rot=[0, 45, 0], pivot=[x, 1.2, z])]
        if big:  # snowflake arms
            out.append(cbox(x, 1.2 + h * 0.55, z, 2.6, 0.6, 0.6, "ice_crystal", glow=True,
                            rot=[0, yaw + 0.0, 0], pivot=[x, 1.2 + h * 0.55, z]))
        return out
    P += around(4.45, [-4.0, 0.0, 4.0], spike)
    # Floating snowflake orbiting slowly above the crown
    P.append(cbox(0, 5.8, 0, 3.4, 0.5, 0.5, "ice_crystal", glow=True, anim=spin, pivot=[0, 6, 0]))
    P.append(cbox(0, 5.8, 0, 0.5, 0.5, 3.4, "ice_crystal", glow=True, anim=spin, pivot=[0, 6, 0]))
    P.append(cbox(0, 5.8, 0, 2.4, 0.5, 0.5, "ice_crystal", glow=True, rot=[0, 45, 0], anim=spin, pivot=[0, 6, 0]))
    P.append(cbox(0, 5.8, 0, 0.5, 0.5, 2.4, "ice_crystal", glow=True, rot=[0, 45, 0], anim=spin, pivot=[0, 6, 0]))
    return P


cosmetic("snowflake_crown", "Snowflake Crown", ["Winter", "Crowns"], "head", "Glittering ice crown with a floating snowflake.",
         snowflake_crown(), particles={"type": "snowflake", "chance": 0.12, "spread": 0.4, "y": 0.5, "vy": -0.01},
         tags=["ice", "snow", "frozen", "glow"])


# --- Santa Hat -------------------------------------------------------------------------------------------------------
def santa_hat():
    P = ring(5.1, 1.1, -1.0, 1.0, "white_fur")
    P.append(box([-4.6, 1.0, -4.6], [4.6, 3.2, 4.6], "santa_red"))
    sway = {"type": "sway", "speed": 0.08, "amp": 6}
    P.append(box([-3.6, 3.2, -4.2], [3.6, 5.2, 3.0], "santa_red"))
    P.append(box([-2.6, 5.2, -4.6], [2.6, 6.8, 0.8], "santa_red", rot=[-12, 0, 0], pivot=[0, 5.2, -2]))
    P.append(box([-1.8, 5.8, -8.2], [1.8, 7.3, -3.6], "santa_red", rot=[-30, 0, 0], pivot=[0, 6.5, -4], anim=sway))
    P.append(box([-1.2, 5.0, -10.6], [1.2, 6.4, -7.6], "santa_red", rot=[-45, 0, 0], pivot=[0, 6.5, -4], anim=sway))
    P.append(cbox(0, 2.6, -11.0, 2.6, 2.6, 2.6, "white_fur", pivot=[0, 6.5, -4], anim=sway))
    return P


cosmetic("santa_hat", "Santa Hat", ["Christmas", "Hats"], "head", "Fluffy red hat with a swaying pompom.", santa_hat(),
         particles={"type": "snowflake", "chance": 0.04, "spread": 0.4, "y": 0.5, "vy": -0.01},
         tags=["christmas", "xmas", "santa", "red"])


# --- Reindeer Antlers -------------------------------------------------------------------------------------------------
def antlers():
    P = []
    for s in (-1, 1):
        P.append(box([s * 2.2 - 0.6, 0.0, -0.6], [s * 2.2 + 0.6, 4.5, 0.6], "antler", rot=[0, 0, -s * 18], pivot=[s * 2.2, 0, 0]))
        P.append(box([s * 3.6 - 0.5, 3.8, -0.5], [s * 3.6 + 0.5, 7.4, 0.5], "antler", rot=[0, 0, s * 8], pivot=[s * 3.6, 4, 0]))
        P.append(box([s * 3.0 - 0.45, 4.6, -0.45], [s * 3.0 + 0.45, 6.8, 0.45], "antler", rot=[25, 0, -s * 25], pivot=[s * 3.0, 4.6, 0]))
        P.append(box([s * 4.4 - 0.4, 5.6, -0.4], [s * 4.4 + 0.4, 7.4, 0.4], "antler", rot=[0, 0, -s * 40], pivot=[s * 4.2, 5.6, 0]))
        P.append(box([s * 2.6 - 0.4, 2.4, -0.4], [s * 2.6 + 0.4, 4.0, 0.4], "antler", rot=[0, 0, s * 35], pivot=[s * 2.4, 2.4, 0]))
        # little ears
        P.append(box([s * 4.0 - 0.3, -2.4, -1.4], [s * 4.0 + 0.3, -0.6, 0.6], "antler", rot=[0, 0, -s * 50], pivot=[s * 4.0, -1.5, 0]))
    return P


cosmetic("reindeer_antlers", "Reindeer Antlers", ["Christmas", "Winter"], "ears", "Branching antlers for the sleigh crew.",
         antlers(), tags=["reindeer", "rudolph", "christmas", "deer"])


# --- Pumpkin Head ----------------------------------------------------------------------------------------------------
def pumpkin():
    P = [box([-5, -8.8, -5], [5, 1.2, 5], "pumpkin", faces={"south": "pumpkin_face"}, stretch=["south"])]
    P.append(box([-5, -8.8, 5.02], [5, 1.2, 5.06], "pumpkin_glow", glow=True, translucent=True, stretch=["south", "north"]))
    P.append(cbox(0, 1.2, 0, 1.4, 2.2, 1.4, "stem", rot=[0, 0, 12], pivot=[0, 1.2, 0]))
    P.append(cbox(1.6, 2.7, 0, 2.4, 0.4, 1.4, "leaf", rot=[0, 20, -10], pivot=[0.8, 2.7, 0]))
    return P


cosmetic("pumpkin_head", "Pumpkin Head", ["Halloween"], "head", "A carved jack-o'-lantern with a glowing grin.", pumpkin(),
         particles={"type": "small_flame", "chance": 0.05, "spread": 0.2, "y": 0.4, "vy": 0.01},
         tags=["jack", "lantern", "spooky", "halloween", "glow"])


# --- Witch Hat -------------------------------------------------------------------------------------------------------
def witch_hat():
    P = [box([-8, 0.0, -8], [8, 0.7, 8], "witch")]
    P += ring(4.85, 0.85, 0.7, 2.1, "witch_band")
    P.append(box([-1.1, 0.8, 4.85], [1.1, 2.0, 5.15], "gold"))
    P.append(box([-4.0, 0.7, -4.0], [4.0, 3.6, 4.0], "witch"))
    P.append(box([-3.0, 3.6, -3.4], [3.0, 6.0, 2.6], "witch", rot=[-6, 0, 0], pivot=[0, 3.6, 0]))
    P.append(box([-2.0, 5.8, -3.6], [2.0, 8.4, 0.4], "witch", rot=[-14, 0, 0], pivot=[0, 5.8, -1]))
    P.append(box([-1.2, 8.0, -4.6], [1.2, 10.0, -2.2], "witch", rot=[-32, 0, 0], pivot=[0, 8.2, -2],
                 anim={"type": "sway", "speed": 0.06, "amp": 4}))
    P.append(box([-0.6, 9.4, -6.6], [0.6, 10.8, -4.4], "witch", rot=[-60, 0, 0], pivot=[0, 8.2, -2],
                 anim={"type": "sway", "speed": 0.06, "amp": 4}))
    return P


cosmetic("witch_hat", "Witch Hat", ["Halloween", "Hats"], "head", "Wide-brimmed pointed hat with a crooked tip.",
         witch_hat(), particles={"type": "witch", "chance": 0.05, "spread": 0.4, "y": 0.7, "vy": 0.0},
         tags=["witch", "spooky", "magic", "purple"])


# --- Devil Horns -----------------------------------------------------------------------------------------------------
def devil_horns():
    P = []
    for s in (-1, 1):
        x = s * 2.8
        P.append(box([x - 1.0, -0.4, -1.0], [x + 1.0, 1.6, 1.0], "devil_dark"))
        P.append(box([x - 0.8, 1.4, -0.8], [x + 0.8, 3.0, 0.8], "devil", rot=[0, 0, -s * 15], pivot=[x, 1.4, 0]))
        P.append(box([x + s * 0.4 - 0.6, 2.8, -0.6], [x + s * 0.4 + 0.6, 4.2, 0.6], "devil", rot=[0, 0, -s * 5], pivot=[x, 2.8, 0]))
        P.append(box([x + s * 0.4 - 0.4, 4.0, -0.4], [x + s * 0.4 + 0.4, 5.2, 0.4], "devil", rot=[0, 0, s * 20], pivot=[x + s * 0.4, 4.0, 0]))
        P.append(box([x - 0.2, 5.0, -0.2], [x + 0.2, 5.9, 0.2], "devil", glow=True, rot=[0, 0, s * 35], pivot=[x, 5.0, 0]))
    return P


cosmetic("devil_horns", "Devil Horns", ["Halloween"], "ears", "A pair of curved red horns with glowing tips.",
         devil_horns(), particles={"type": "crimson_spore", "chance": 0.08, "spread": 0.4, "y": 0.3, "vy": 0.0},
         tags=["devil", "demon", "red", "horns"])


# --- Bunny Ears ------------------------------------------------------------------------------------------------------
def bunny_ears():
    P = []
    for s in (-1, 1):
        x = s * 2.0
        tw = {"type": "twitch", "speed": 0.05, "amp": 14, "phase": 0 if s < 0 else 3.1}
        P.append(box([x - 1.1, -0.2, -0.6], [x + 1.1, 7.2, 0.6], "bunny", rot=[-8, 0, -s * 10], pivot=[x, 0, 0], anim=tw))
        P.append(box([x - 0.6, 0.6, 0.6], [x + 0.6, 6.4, 0.75], "bunny_pink", rot=[-8, 0, -s * 10], pivot=[x, 0, 0], anim=tw))
    return P


cosmetic("bunny_ears", "Bunny Ears", ["Easter"], "ears", "Soft twitching rabbit ears.", bunny_ears(),
         tags=["bunny", "rabbit", "easter", "cute"])


# --- Flower Crown ----------------------------------------------------------------------------------------------------
def flower_crown():
    P = ring(4.9, 0.8, -0.2, 0.8, "leaf")
    cols = ["petal_red", "petal_yellow", "petal_blue", "petal_pink", "petal_white"]
    n = 0

    def flower(x, z, yaw):
        nonlocal n
        n += 1
        c = cols[n % len(cols)]
        out = [cbox(x, 0.0, z, 1.8, 1.4, 1.8, c, rot=[0, 45, 0], pivot=[x, 0, z]),
               cbox(x, 1.2, z, 0.8, 0.5, 0.8, "petal_yellow" if c != "petal_yellow" else "petal_white")]
        return out
    P += around(4.55, [-3.6, -1.2, 1.2, 3.6], flower)

    def leaf(x, z, yaw):
        return [cbox(x, 0.2, z, 1.6, 0.4, 1.0, "leaf", rot=[0, yaw + 30, 15], pivot=[x, 0.2, z])]
    P += around(4.8, [-2.4, 0.0, 2.4], leaf)
    return P


cosmetic("flower_crown", "Flower Crown", ["Easter", "Crowns"], "head", "A woven ring of spring blossoms.", flower_crown(),
         particles={"type": "cherry_leaves", "chance": 0.03, "spread": 0.4, "y": 0.2, "vy": 0.0},
         tags=["flowers", "spring", "easter", "nature"])


# --- Halo ------------------------------------------------------------------------------------------------------------
def halo():
    P = []
    segs = 16
    R = 4.6
    for k in range(segs):
        a = 360.0 * k / segs
        rad = math.radians(a)
        x, z = math.sin(rad) * R, math.cos(rad) * R
        L = 2 * R * math.tan(math.pi / segs) + 0.15
        P.append(box([x - L / 2, 3.2, z - 0.45], [x + L / 2, 3.8, z + 0.45], "halo", glow=True,
                     rot=[0, a, 0], pivot=[x, 3.5, z],
                     anim={"type": "halo", "speed": 2.0, "bob": 0.5, "bobSpeed": 0.08}))
    return P


cosmetic("halo", "Halo", ["Other"], "halo", "A softly glowing, gently bobbing halo.", halo(),
         particles={"type": "end_rod", "chance": 0.03, "spread": 0.35, "y": 0.75, "vy": 0.0},
         tags=["angel", "glow", "holy", "heaven"])


# --- Viking Helmet ---------------------------------------------------------------------------------------------------
def viking():
    P = [box([-4.75, -3.6, -4.75], [4.75, 0.9, 4.75], "iron")]
    P.append(box([-3.8, 0.9, -3.8], [3.8, 2.3, 3.8], "iron"))
    P.append(box([-2.3, 2.3, -2.3], [2.3, 3.1, 2.3], "iron"))
    P += ring(5.0, 0.4, -3.9, -2.6, "leather")
    P.append(box([-0.35, 0.9, -4.1], [0.35, 3.3, 4.1], "gold_dark"))  # crest
    P.append(box([-0.6, -6.0, 4.75], [0.6, -2.6, 5.35], "iron"))      # nose guard
    for s in (-1, 1):
        x = s * 4.75
        P.append(box([x - 1.0 if s > 0 else x - 1.2, -2.2, -1.0], [x + 1.2 if s > 0 else x + 1.0, -0.2, 1.0], "ivory",
                     rot=[0, 0, -s * 10], pivot=[x, -1.2, 0]))
        P.append(box([x + s * 1.8 - 0.8, -1.4, -0.8], [x + s * 1.8 + 0.8, 1.4, 0.8], "ivory", rot=[0, 0, -s * 30], pivot=[x + s * 1.2, -1.2, 0]))
        P.append(box([x + s * 2.6 - 0.6, 1.0, -0.6], [x + s * 2.6 + 0.6, 3.4, 0.6], "ivory", rot=[0, 0, -s * 10], pivot=[x + s * 2.6, 1.0, 0]))
        P.append(box([x + s * 2.4 - 0.4, 3.2, -0.4], [x + s * 2.4 + 0.4, 4.8, 0.4], "ivory", rot=[0, 0, s * 18], pivot=[x + s * 2.4, 3.2, 0]))
    return P


cosmetic("viking_helmet", "Viking Helmet", ["Hats", "Other"], "head", "Iron helm with ivory horns and a leather band.",
         viking(), tags=["viking", "norse", "warrior", "helmet", "horns"])


# --- Top Hat ---------------------------------------------------------------------------------------------------------
def top_hat():
    P = [box([-6.6, 0.0, -6.6], [6.6, 0.6, 6.6], "hat_black")]
    P.append(box([-4.2, 0.6, -4.2], [4.2, 8.2, 4.2], "hat_black"))
    P += ring(4.35, 0.15, 0.6, 2.0, "hat_band")
    P.append(box([-4.35, 8.2, -4.35], [4.35, 8.6, 4.35], "hat_black"))
    return P


cosmetic("top_hat", "Top Hat", ["Hats"], "head", "A tall, dapper top hat with a red band.", top_hat(),
         tags=["gentleman", "fancy", "black"])


# --- Winter Beanie ---------------------------------------------------------------------------------------------------
def beanie():
    P = [box([-4.75, -3.6, -4.75], [4.75, 1.4, 4.75], "knit")]
    P += ring(5.1, 0.35, -4.3, -2.2, "knit_white")
    P.append(box([-3.6, 1.4, -3.6], [3.6, 2.4, 3.6], "knit"))
    P.append(cbox(0, 2.4, 0, 3.0, 2.8, 3.0, "white_fur", anim={"type": "bob", "speed": 0.12, "amp": 0.25}))
    return P


cosmetic("winter_beanie", "Winter Beanie", ["Winter", "Hats"], "head", "Cozy knitted beanie with a pompom.", beanie(),
         particles={"type": "snowflake", "chance": 0.03, "spread": 0.4, "y": 0.4, "vy": -0.01},
         tags=["beanie", "toque", "knit", "cold", "snow"])


# --- Party Hat -------------------------------------------------------------------------------------------------------
def party_hat():
    P = []
    layers = [(4.2, 0.0, 2.0), (3.4, 2.0, 2.0), (2.6, 4.0, 2.0), (1.8, 6.0, 2.0), (1.0, 8.0, 1.6)]
    for half, y, h in layers:
        P.append(box([-half, y, -half], [half, y + h, half], "party", rot=[0, 0, 8], pivot=[0, 0, 0]))
    P.append(cbox(1.3, 9.4, 0, 2.0, 2.0, 2.0, "petal_yellow", glow=True, rot=[0, 0, 8], pivot=[0, 0, 0],
                  anim={"type": "bob", "speed": 0.2, "amp": 0.3}))
    return P


cosmetic("party_hat", "Party Hat", ["Other", "Hats"], "head", "A striped cone hat for celebrations.", party_hat(),
         particles={"type": "happy_villager", "chance": 0.03, "spread": 0.35, "y": 0.8, "vy": 0.0},
         tags=["party", "birthday", "celebrate", "cone"])


# ----------------------------------------------------------------------------------------------------------------------
# UV assignment
# ----------------------------------------------------------------------------------------------------------------------
def face_dims(p, face):
    (x0, y0, z0), (x1, y1, z1) = p["from"], p["to"]
    w, h, d = abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)
    return {"up": (w, d), "down": (w, d), "north": (w, h), "south": (w, h), "west": (d, h), "east": (d, h)}[face]


def assign_uvs():
    for c in COSMETICS:
        for pi, p in enumerate(c["parts"]):
            uv = {}
            for fi, face in enumerate(FACES):
                tex = (p.get("faces") or {}).get(face, p["tex"])
                tx, ty = TILES[tex]
                fw, fh = face_dims(p, face)
                if face in (p.get("stretch") or []):
                    sw, sh, ox, oy = TILE, TILE, 0, 0
                else:
                    sw = max(1.0, min(TILE, round(fw * 1.0, 2)))
                    sh = max(1.0, min(TILE, round(fh * 1.0, 2)))
                    rnd = random.Random(pi * 31 + fi * 7 + len(c["id"]))
                    ox = rnd.randint(0, int(TILE - sw))
                    oy = rnd.randint(0, int(TILE - sh))
                u0, v0 = tx * TILE + ox, ty * TILE + oy
                uv[face] = [round(u0, 3), round(v0, 3), round(u0 + sw, 3), round(v0 + sh, 3)]
            p["uv"] = uv
            p.pop("faces", None)
            p.pop("stretch", None)


# ----------------------------------------------------------------------------------------------------------------------
# Icon renderer (isometric painter's algorithm over textured faces)
# ----------------------------------------------------------------------------------------------------------------------
def rot_matrix(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    # R = Rz * Ry * Rx  (same order as the Java renderer)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    def mul(a, b): return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(Rz, mul(Ry, Rx))


def apply(m, v): return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


FACE_CORNERS = {
    # corners as (x, y, z) selectors of from(0)/to(1), ordered: top-left, top-right, bottom-right, bottom-left (as seen from outside)
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "east":  [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "west":  [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "up":    [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down":  [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
FACE_LIGHT = {"up": 1.0, "south": 0.85, "east": 0.72, "west": 0.72, "north": 0.6, "down": 0.5}


def part_corner(p, sel):
    f, t = p["from"], p["to"]
    v = [t[i] if sel[i] else f[i] for i in range(3)]
    if p.get("rot"):
        piv = p.get("pivot", [0, 0, 0])
        m = rot_matrix(*p["rot"])
        v = [a + b for a, b in zip(apply(m, [v[i] - piv[i] for i in range(3)]), piv)]
    return v


def render_icon(c, size=32, ss=4):
    view = rot_matrix(24, 0, 0)
    yaw = rot_matrix(0, -32, 0)
    polys = []
    atlas = _atlas.load()
    for p in c["parts"]:
        for face in FACES:
            corners = [apply(view, apply(yaw, part_corner(p, s))) for s in FACE_CORNERS[face]]
            u0, v0, u1, v1 = p["uv"][face]
            n = 6
            for gy in range(n):
                for gx in range(n):
                    def lerp(a, b, t): return [a[i] + (b[i] - a[i]) * t for i in range(3)]
                    def pt(sx, sy):
                        top = lerp(corners[0], corners[1], sx)
                        bot = lerp(corners[3], corners[2], sx)
                        return lerp(top, bot, sy)
                    q = [pt(gx / n, gy / n), pt((gx + 1) / n, gy / n), pt((gx + 1) / n, (gy + 1) / n), pt(gx / n, (gy + 1) / n)]
                    tu = int(u0 + (u1 - u0) * (gx + 0.5) / n)
                    tv = int(v0 + (v1 - v0) * (gy + 0.5) / n)
                    col = atlas[min(ATLAS_SIZE - 1, tu), min(ATLAS_SIZE - 1, tv)]
                    if col[3] < 40:
                        continue
                    lit = 1.0 if p.get("glow") else FACE_LIGHT[face]
                    depth = sum(v[2] for v in q) / 4
                    polys.append((depth, q, shade(col, lit)[:3] + (255,)))
    if not polys:
        return Image.new("RGBA", (size, size))
    xs = [v[0] for _, q, _ in polys for v in q]
    ys = [v[1] for _, q, _ in polys for v in q]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    span = max(maxx - minx, maxy - miny)
    S = size * ss
    scale = (S * 0.86) / span
    cxp, cyp = (minx + maxx) / 2, (miny + maxy) / 2
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    polys.sort(key=lambda t: t[0])  # far (-z) first; +z is towards the viewer
    for _, q, col in polys:
        pts = [((v[0] - cxp) * scale + S / 2, -(v[1] - cyp) * scale + S / 2) for v in q]
        d.polygon(pts, fill=col)
    img = img.resize((size, size), Image.LANCZOS)
    # soft outline/shadow for readability on dark UI
    alpha = img.split()[3]
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow.putalpha(alpha.filter(ImageFilter.MaxFilter(3)).point(lambda a: min(150, a)))
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.alpha_composite(shadow)
    out.alpha_composite(img)
    return out


def main():
    assign_uvs()
    tex_dir = os.path.join(RES, "assets", "eqsual_cosmetics", "textures")
    os.makedirs(os.path.join(tex_dir, "cosmetic"), exist_ok=True)
    os.makedirs(os.path.join(tex_dir, "gui"), exist_ok=True)
    _atlas.save(os.path.join(tex_dir, "cosmetic", "atlas.png"))

    icons = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for i, c in enumerate(COSMETICS):
        icons.paste(render_icon(c), ((i % 8) * 32, (i // 8) * 32))
        c["icon"] = i
    icons.save(os.path.join(tex_dir, "gui", "icons.png"))

    data_dir = os.path.join(RES, "eqsual_cosmetics")
    os.makedirs(data_dir, exist_ok=True)
    with open(os.path.join(data_dir, "cosmetics.json"), "w") as f:
        json.dump({"atlasSize": ATLAS_SIZE, "categories": ["Christmas", "Halloween", "Easter", "Winter", "Crowns", "Hats", "Other"],
                   "cosmetics": COSMETICS}, f, separators=(",", ":"))
    print(f"{len(COSMETICS)} cosmetics, {sum(len(c['parts']) for c in COSMETICS)} parts, {len(TILES)} tiles")


if __name__ == "__main__":
    main()
