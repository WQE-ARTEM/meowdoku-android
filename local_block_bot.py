import subprocess
import time
import io
import colorsys
import itertools
import os

BOARD_TOP_RATIO = 0.20
BOARD_MARGIN_RATIO = 0.06
TRAY_Y_RATIO = 0.78
DRAG_OFFSET_Y = 200
SWIPE_DURATION_MS = 280
MOVE_DELAY_SEC = 0.6
TMP_IMG = "/data/local/tmp/bb_screen.png"

def detect_mode():
    try:
        res = subprocess.run(["adb", "devices"], capture_output=True, text=True)
        if "\tdevice" in res.stdout:
            return "adb"
    except FileNotFoundError:
        pass
    return "root"

MODE = detect_mode()

def run_shell(cmd: str):
    if MODE == "adb":
        return subprocess.check_output(["adb", "exec-out", cmd])
    else:
        # Для root используем сохранение во временный файл для надежности
        subprocess.run(["su", "-c", cmd], check=True)

def capture_screen():
    if MODE == "adb":
        raw_png = run_shell("screencap -p")
    else:
        # Сохраняем во временную папку и вытаскиваем
        subprocess.run(["su", "-c", f"screencap -p {TMP_IMG}"], check=True)
        raw_png = subprocess.check_output(["su", "-c", f"cat {TMP_IMG}"])
    
    from PIL import Image
    return Image.open(io.BytesIO(raw_png)).convert("RGB")

def swipe(x1, y1, x2, y2):
    cmd = f"input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {SWIPE_DURATION_MS}"
    if MODE == "adb":
        subprocess.run(["adb", "shell", cmd])
    else:
        subprocess.run(["su", "-c", cmd])

def is_block_pixel(rgb):
    r, g, b = rgb[0] / 255.0, rgb[1] / 255.0, rgb[2] / 255.0
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    return v > 0.46 and s > 0.32

def parse_screen(img):
    w, h = img.size
    px = img.load()

    board_left = w * BOARD_MARGIN_RATIO
    board_width = w * (1.0 - 2.0 * BOARD_MARGIN_RATIO)
    cell_size = board_width / 8.0
    board_top = h * BOARD_TOP_RATIO

    board_mask = 0
    for r in range(8):
        for c in range(8):
            cx = int(board_left + (c + 0.5) * cell_size)
            cy = int(board_top + (r + 0.5) * cell_size)
            if is_block_pixel(px[cx, cy]):
                board_mask |= (1 << (r * 8 + c))

    tray_cell = cell_size * 0.52
    tray_y = h * TRAY_Y_RATIO
    slot_xs = [w * 0.21, w * 0.50, w * 0.79]
    pieces = []

    for idx, sx in enumerate(slot_xs):
        raw = []
        for dr in range(-2, 3):
            for dc in range(-2, 3):
                tx = int(sx + dc * tray_cell)
                ty = int(tray_y + dr * tray_cell)
                if 0 <= tx < w and 0 <= ty < h and is_block_pixel(px[tx, ty]):
                    raw.append((dr, dc))
        if raw:
            min_r = min(p[0] for p in raw)
            min_c = min(p[1] for p in raw)
            max_r = max(p[0] for p in raw)
            max_c = max(p[1] for p in raw)
            norm = [(p[0] - min_r, p[1] - min_c) for p in raw]
            center_x = sx + ((min_c + max_c) / 2.0) * tray_cell
            center_y = tray_y + ((min_r + max_r) / 2.0) * tray_cell
            pieces.append({
                "slot": idx,
                "cells": norm,
                "h": max_r - min_r + 1,
                "w": max_c - min_c + 1,
                "origin": (center_x, center_y)
            })

    return board_mask, pieces, board_left, board_top, cell_size

ROW_MASKS = [0xFF << (r * 8) for r in range(8)]
COL_MASKS = [sum(1 << (r * 8 + c) for r in range(8)) for c in range(8)]

def place_and_clear(board, mask):
    placed = board | mask
    clear_mask = 0
    lines = 0
    for rm in ROW_MASKS:
        if (placed & rm) == rm:
            clear_mask |= rm
            lines += 1
    for cm in COL_MASKS:
        if (placed & cm) == cm:
            clear_mask |= cm
            lines += 1
    return placed & (~clear_mask), lines

def evaluate(board, cleared):
    occupied = bin(board).count("1")
    holes = 0
    for r in range(8):
        for c in range(8):
            if not (board & (1 << (r * 8 + c))):
                up = (r == 0) or bool(board & (1 << ((r - 1) * 8 + c)))
                dn = (r == 7) or bool(board & (1 << ((r + 1) * 8 + c)))
                lt = (c == 0) or bool(board & (1 << (r * 8 + c - 1)))
                rt = (c == 7) or bool(board & (1 << (r * 8 + c + 1)))
                if up and dn and lt and rt:
                    holes += 1
    return cleared * 140.0 - occupied * 2.5 - holes * 25.0

def solve_dfs(board, order, idx=0, cleared=0, moves=()):
    if idx == len(order):
        return evaluate(board, cleared), moves
    piece = order[idx]
    best_score = -1e9
    best_moves = None

    for r in range(9 - piece["h"]):
        for c in range(9 - piece["w"]):
            mask = 0
            for dr, dc in piece["cells"]:
                mask |= (1 << ((r + dr) * 8 + (c + dc)))
            if (board & mask) == 0:
                nxt_b, cl = place_and_clear(board, mask)
                sc, mv = solve_dfs(nxt_b, order, idx + 1, cleared + cl, moves + ((piece, r, c),))
                if mv is not None and sc > best_score:
                    best_score = sc
                    best_moves = mv
    return best_score, best_moves

def find_best_sequence(board, pieces):
    best_sc = -1e9
    best_seq = None
    for perm in itertools.permutations(pieces):
        sc, seq = solve_dfs(board, perm)
        if seq is not None and sc > best_sc:
            best_sc = sc
            best_seq = seq
    return best_seq

def main():
    print(f"[*] Автономный локальный бот запущен (режим: {MODE.upper()})")
    print("[*] Откройте игру Block Blast. Старт через 5 секунд...")
    time.sleep(5)

    step = 1
    while True:
        try:
            img = capture_screen()
        except Exception as e:
            print(f"[!] Ошибка скриншота: {e}")
            time.sleep(1.0)
            continue

        board, pieces, b_left, b_top, cell_sz = parse_screen(img)
        
        if not pieces:
            print("[!] Фигуры не найдены в лотке, ждем...")
            time.sleep(1.0)
            continue

        seq = find_best_sequence(board, pieces)
        if not seq:
            print("[X] Ходов больше нет (Game Over).")
            break

        piece, target_r, target_c = seq[0]
        start_x, start_y = piece["origin"]
        
        end_x = b_left + (target_c + piece["w"] / 2.0) * cell_sz
        end_y = b_top + (target_r + piece["h"] / 2.0) * cell_sz + DRAG_OFFSET_Y

        print(f"[Ход #{step}] Слот {piece['slot']+1} -> в позицию ({target_r}, {target_c})")
        swipe(start_x, start_y, end_x, end_y)
        
        step += 1
        time.sleep(MOVE_DELAY_SEC)

if __name__ == "__main__":
    main()
