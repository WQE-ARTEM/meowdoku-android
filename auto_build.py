import os, subprocess, time
from pathlib import Path

print("\n=== ШАГ 1: Авторизация в GitHub ===")
auth_check = subprocess.run(["gh", "auth", "status"], capture_output=True)
if auth_check.returncode != 0:
    print("Сейчас откроется вход в GitHub:")
    print("1. Нажмите Enter (выбор GitHub.com)")
    print("2. Нажмите Enter (выбор HTTPS)")
    print("3. Нажмите Enter (согласие Y)")
    print("4. Нажмите Enter (Login with a web browser)")
    print("5. Скопируйте 8-значный код, нажмите Enter и вставьте его в открывшемся браузере!\n")
    subprocess.run(["gh", "auth", "login"], check=True)
