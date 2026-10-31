"""诱饵方块贴图 v2：铺满 16x16 全像素不透明，8 个等级递进。

视觉语言参照 Spore 菌囊：灰褐偏暗的真菌肉块 + 碎颗粒 + 深色裂纹，
等级越高 → 整体压暗、裂纹/孔洞越多、骨白刺点转为病态绿、并出现发光的核心。
"""
import math
import os
import random
from PIL import Image

SIZE = 16

# 参照 Spore 菌囊取的灰褐色系（饱和度和亮度都比 v1 低）
RAMP = [
    (34, 26, 24),
    (56, 40, 35),
    (78, 55, 47),
    (100, 72, 60),
    (124, 92, 76),
    (148, 114, 94),
    (172, 140, 118),
]

BONE = (206, 196, 176)
GREEN = (150, 182, 92)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def value_noise(rng, cells):
    grid = [[rng.random() for _ in range(cells + 1)] for _ in range(cells + 1)]
    out = [[0.0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            fx = x / SIZE * cells
            fy = y / SIZE * cells
            x0, y0 = int(fx), int(fy)
            x0 = min(x0, cells - 1)
            y0 = min(y0, cells - 1)
            tx, ty = fx - x0, fy - y0
            sx = tx * tx * (3 - 2 * tx)
            sy = ty * ty * (3 - 2 * ty)
            v00 = grid[y0][x0]
            v10 = grid[y0][x0 + 1]
            v01 = grid[y0 + 1][x0]
            v11 = grid[y0 + 1][x0 + 1]
            a = v00 + (v10 - v00) * sx
            b = v01 + (v11 - v01) * sx
            out[y][x] = a + (b - a) * sy
    return out


def build(tier):
    rng = random.Random(7000 + tier)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    px = img.load()

    n_coarse = value_noise(rng, 4)
    n_fine = value_noise(rng, 8)
    accent = lerp(BONE, GREEN, (tier - 1) / 7.0)
    darkness = 0.92 - (tier - 1) * 0.055          # 高阶压暗

    # ---------- 底：铺满的肉块 ----------
    for y in range(SIZE):
        for x in range(SIZE):
            v = n_coarse[y][x] * 0.62 + n_fine[y][x] * 0.38
            # 左上轻微受光，让整块有体积感
            v += (1.0 - (x + y) / (2.0 * SIZE)) * 0.18
            v += rng.uniform(-0.055, 0.055)        # 细颗粒
            v = max(0.0, min(1.0, (v - 0.12) / 0.86 * darkness))
            idx = min(len(RAMP) - 1, max(0, int(v * len(RAMP))))
            px[x, y] = RAMP[idx] + (255,)

    # ---------- 裂纹：深色随机游走 ----------
    for _ in range(1 + tier // 2):
        x, y = rng.randint(0, SIZE - 1), rng.randint(0, SIZE - 1)
        for _ in range(rng.randint(5, 10)):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                px[x, y] = RAMP[0] + (255,)
                if x + 1 < SIZE and rng.random() < 0.4:
                    px[x + 1, y] = RAMP[0] + (255,)
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1, 1))

    # ---------- 孢子孔 / 刺点 ----------
    pores = 2 + tier
    placed = 0
    for _ in range(pores * 20):
        if placed >= pores:
            break
        x, y = rng.randint(0, SIZE - 1), rng.randint(0, SIZE - 1)
        if px[x, y] == RAMP[0] + (255,):
            continue
        px[x, y] = accent + (255,)
        placed += 1

    # ---------- 高阶：血管汇聚 + 发光核心 ----------
    if tier >= 4:
        cx, cy = rng.randint(5, 10), rng.randint(5, 10)
        veins = (tier - 3)
        for _ in range(veins):
            x, y = cx, cy
            for _ in range(rng.randint(4, 7)):
                if 0 <= x < SIZE and 0 <= y < SIZE:
                    px[x, y] = accent + (255,)
                ang = rng.uniform(0, math.tau)
                x += int(round(math.cos(ang) * 1.2))
                y += int(round(math.sin(ang) * 1.2))

        core = 2 if tier >= 6 else 1
        for oy in range(core):
            for ox in range(core):
                xx, yy = cx + ox, cy + oy
                if 0 <= xx < SIZE and 0 <= yy < SIZE:
                    px[xx, yy] = accent + (255,)

    return img


def main():
    out_dir = os.path.join(
        r"C:\Users\Administrator\Desktop\sporesrp\forge-1.20.1-47.4.10-mdk-SporeSRP",
        "src", "main", "resources", "assets", "sporesrp", "textures", "block",
    )
    os.makedirs(out_dir, exist_ok=True)

    tiles = []
    for tier in range(1, 9):
        im = build(tier)
        im.save(os.path.join(out_dir, f"decoy_{tier}.png"))
        tiles.append(im)

    scale, pad = 6, 4
    sheet = Image.new(
        "RGBA",
        (len(tiles) * (SIZE * scale + pad) + pad, SIZE * scale + pad * 2),
        (30, 30, 34, 255),
    )
    for i, t in enumerate(tiles):
        big = t.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
        sheet.paste(big, (pad + i * (SIZE * scale + pad), pad), big)
    preview = r"C:\Users\Administrator\Desktop\sporesrp\_decoy_preview.png"
    sheet.save(preview)

    # 顺带确认没有透明像素
    for i, t in enumerate(tiles, 1):
        a = t.getchannel("A").getdata()
        assert min(a) == 255, f"tier {i} has transparency"
    print("ok: 8 textures, fully opaque")
    print("preview:", preview)


if __name__ == "__main__":
    main()
