import subprocess, time, io, colorsys, itertools

BOARD_TOP_RATIO, BOARD_MARGIN_RATIO, TRAY_Y_RATIO = 0.20, 0.06, 0.78
DRAG_OFFSET_Y, SWIPE_DURATION_MS, MOVE_DELAY_SEC = 200, 280, 0.6

def setup_adb():
    print("--------------------------------------------------")
    print("📱 НАСТРОЙКА ПОДКЛЮЧЕНИЯ ADB")
    print("1. Откройте Настройки -> Для разработчиков -> Отладка по Wi-Fi")
    print("2. Посмотрите текущий порт (пятизначное число после двоеточия, например: 40665)")
    print("--------------------------------------------------")
    port = input("Введите этот порт в Termux и нажмите Enter: ").strip()
    
    subprocess.run(["adb", "kill-server"])
    subprocess.run(["adb", "start-server"])
    res = subprocess.run(["adb", "connect", f"127.0.0.1:{port}"], capture_output=True, text=True)
    print(res.stdout)
    if "connected" in res.stdout or "already connected" in res.stdout:
        print("✅ Успешно подключено!")
        return True
    else:
        print("❌ Не удалось подключиться. Проверьте правильность порта.")
        return False

def capture_screen():
    raw = subprocess.check_output(["adb", "exec-out", "screencap -p"])
    from PIL import Image
    return Image.open(io.BytesIO(raw)).convert("RGB")

def swipe(x1, y1, x2, y2):
    cmd = f"input swipe {int(x1)} {int(y1)} {int(x2)} {int(y2)} {SWIPE_DURATION_MS}"
    subprocess.run(["adb", "shell", cmd])

def is_block_pixel(rgb):
    r, g, b = rgb[0]/255.0, rgb[1]/255.0, rgb[2]/255.0
    _, s, v = colorsys.rgb_to_hsv(r, g, b)
    return v > 0.46 and s > 0.32

def parse_screen(img):
    w, h = img.size; px = img.load()
    b_left = w * BOARD_MARGIN_RATIO; cell_sz = (w * (1.0 - 2.0 * BOARD_MARGIN_RATIO)) / 8.0; b_top = h * BOARD_TOP_RATIO
    board = 0
    for r in range(8):
        for c in range(8):
            if is_block_pixel(px[int(b_left + (c + 0.5) * cell_sz), int(b_top + (r + 0.5) * cell_sz)]):
                board |= (1 << (r * 8 + c))
    tray_cell = cell_sz * 0.52; tray_y = h * TRAY_Y_RATIO
    pieces = []
    for idx, sx in enumerate([w * 0.21, w * 0.50, w * 0.79]):
        raw = []
        for dr in range(-2, 3):
            for dc in range(-2, 3):
                tx, ty = int(sx + dc * tray_cell), int(tray_y + dr * tray_cell)
                if 0 <= tx < w and 0 <= ty < h and is_block_pixel(px[tx, ty]): raw.append((dr, dc))
        if raw:
            min_r, min_c = min(p[0] for p in raw), min(p[1] for p in raw)
            max_r, max_c = max(p[0] for p in raw), max(p[1] for p in raw)
            pieces.append({"slot": idx, "cells": [(p[0]-min_r, p[1]-min_c) for p in raw], "h": max_r-min_r+1, "w": max_c-min_c+1, "origin": (sx + ((min_c+max_c)/2.0)*tray_cell, tray_y + ((min_r+max_r)/2.0)*tray_cell)})
    return board, pieces, b_left, b_top, cell_sz

ROW_MASKS = [0xFF << (r * 8) for r in range(8)]
COL_MASKS = [sum(1 << (r * 8 + c) for r in range(8)) for c in range(8)]

def place_and_clear(board, mask):
    placed = board | mask; clear = 0; lines = 0
    for rm in ROW_MASKS:
        if (placed & rm) == rm: clear |= rm; lines += 1
    for cm in COL_MASKS:
        if (placed & cm) == cm: clear |= cm; lines += 1
    return placed & (~clear), lines

def evaluate(board, cleared):
    occupied = bin(board).count("1"); holes = 0
    for r in range(8):
        for c in range(8):
            if not (board & (1 << (r * 8 + c))):
                up, dn = (r == 0) or bool(board & (1 << ((r-1)*8+c))), (r == 7) or bool(board & (1 << ((r+1)*8+c)))
                lt, rt = (c == 0) or bool(board & (1 << (r*8+c-1))), (c == 7) or bool(board & (1 << (r*8+c+1)))
                if up and dn and lt and rt: holes += 1
    return cleared * 140.0 - occupied * 2.5 - holes * 25.0

def solve_dfs(board, order, idx=0, cleared=0, moves=()):
    if idx == len(order): return evaluate(board, cleared), moves
    piece = order[idx]; best_sc, best_mv = -1e9, None
    for r in range(9 - piece["h"]):
        for c in range(9 - piece["w"]):
            mask = sum(1 << ((r + dr) * 8 + (c + dc)) for dr, dc in piece["cells"])
            if (board & mask) == 0:
                nxt_b, cl = place_and_clear(board, mask)
                sc, mv = solve_dfs(nxt_b, order, idx + 1, cleared + cl, moves + ((piece, r, c),))
                if mv and sc > best_sc: best_sc, best_mv = sc, mv
    return best_sc, best_mv

if __name__ == "__main__":
    while not setup_adb():
        print("Попробуйте еще раз.\n")
    
    print("\n🤖 БОТ ГОТОВ К РАБОТЕ!")
    print("Откройте игру Block Blast прямо сейчас. Старт через 4 секунды...")
    time.sleep(4)

    step = 1
    while True:
        try:
            img = capture_screen()
            board, pieces, b_left, b_top, cell_sz = parse_screen(img)
            if not pieces: 
                time.sleep(1.0)
                continue
            best_seq = max((solve_dfs(board, p) for p in itertools.permutations(pieces)), key=lambda x: x[0], default=(None, None))[1]
            if not best_seq: 
                print("❌ Ходов больше нет (Game Over)")
                break
            piece, tr, tc = best_seq[0]
            print(f"👉 Ход #{step}: Слот {piece['slot']+1} -> в ряд {tr}, колонку {tc}")
            swipe(piece["origin"][0], piece["origin"][1], b_left + (tc + piece["w"]/2.0)*cell_sz, b_top + (tr + piece["h"]/2.0)*cell_sz + DRAG_OFFSET_Y)
            step += 1
            time.sleep(MOVE_DELAY_SEC)
        except Exception as e:
            print(f"⚠️ Ошибка связи с экраном: {e}")
            time.sleep(1.5)
