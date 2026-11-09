import os
import io
import json
import base64
from http.server import BaseHTTPRequestHandler, HTTPServer
from PIL import Image
from google import genai
from google.genai import types

# Инициализация клиента Gemini
API_KEY = os.environ.get("GEMINI_API_KEY", "")
if not API_KEY:
    print("⚠️ ОШИБКА: Не задан GEMINI_API_KEY!")
    print("Выполните: export GEMINI_API_KEY='ваш_ключ'")
    exit(1)

client = genai.Client(api_key=API_KEY)
MEMORY_FILE = "game_memory.json"

def load_memory():
    if os.path.exists(MEMORY_FILE):
        with open(MEMORY_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}

def save_memory(mem):
    with open(MEMORY_FILE, "w", encoding="utf-8") as f:
        json.dump(mem, f, ensure_ascii=False, indent=2)

memory = load_memory()
last_state = {"game": None, "last_action": None}

SYSTEM_PROMPT = """
Ты — Универсальный Игровой ИИ-Агент (General Game Playing AI) со сверхчеловеческим уровнем игры.
Твоя задача — смотреть на скриншот мобильной игры, анализировать состояние, учитывать опыт прошлых ходов и выдавать ИДЕАЛЬНУЮ последовательность действий.

ПРАВИЛА РАБОТЫ:
1. Определи название игры на скриншоте. Если игра новая или правила требуют уточнения — используй Google Search, чтобы найти правила и выигрышную стратегию.
2. Посмотри на результат своего прошлого действия (сравни с тем, что планировал). Если прошлый ход привел к ошибке или не сработал — запиши урок в поле `learned_lesson`.
3. Рассчитай точные координаты для победы. Координаты X и Y всегда указывай в нормализованном виде от 0.0 до 1.0 (где 0.0, 0.0 — левый верхний угол экрана, а 1.0, 1.0 — правый нижний угол).
4. Верни ответ СТРОГО в формате чистого JSON (без markdown-оберток ```json):
{
  "game_name": "Название игры",
  "status_short": "Краткая мысль для экрана (до 45 символов)",
  "learned_lesson": "Новое правило или поправка к стратегии (если понял что-то важное)",
  "actions": [
    {"type": "tap", "x": 0.5, "y": 0.5, "delay_ms": 150},
    {"type": "double_tap", "x": 0.3, "y": 0.6, "delay_ms": 200},
    {"type": "swipe", "x1": 0.2, "y1": 0.5, "x2": 0.8, "y2": 0.5, "duration_ms": 300, "delay_ms": 250}
  ]
}
"""

class AgentHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_len = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_len)
        data = json.loads(body.decode('utf-8'))

        img_bytes = base64.b64decode(data["image"])
        width = data.get("width", 1080)
        height = data.get("height", 2400)
        img = Image.open(io.BytesIO(img_bytes))

        # Собираем накопленный опыт по текущей игре
        known_games_context = json.dumps(memory, ensure_ascii=False)
        user_prompt = (
            f"Разрешение экрана: {width}x{height}.\n"
            f"Накопленная база знаний по играм: {known_games_context}\n"
            f"Прошлое действие: {last_state['last_action']}\n"
            "Проанализируй экран, найди лучшее решение и верни JSON с точными координатами (0.0..1.0)."
        )

        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=[img, user_prompt],
                config=types.GenerateContentConfig(
                    system_instruction=SYSTEM_PROMPT,
                    temperature=0.1,
                    tools=[types.Tool(google_search=types.GoogleSearch())]
                )
            )

            raw_text = response.text.strip()
            if raw_text.startswith("```"):
                raw_text = raw_text.split("\n", 1)[1].rsplit("```", 1)[0].strip()

            result = json.loads(raw_text)
            game_name = result.get("game_name", "Unknown")
            lesson = result.get("learned_lesson", "")

            if game_name and lesson:
                memory.setdefault(game_name, [])
                if lesson not in memory[game_name]:
                    memory[game_name].append(lesson)
                    # Храним последние 15 самых важных уроков по игре
                    memory[game_name] = memory[game_name][-15:]
                    save_memory(memory)

            # Переводим нормализованные координаты (0.0..1.0) в точные пиксели экрана
            pixel_actions = []
            for act in result.get("actions", []):
                t = act.get("type", "tap")
                if t in ("tap", "double_tap"):
                    pixel_actions.append({
                        "type": t,
                        "x": int(float(act.get("x", 0.5)) * width),
                        "y": int(float(act.get("y", 0.5)) * height),
                        "delay_ms": int(act.get("delay_ms", 150))
                    })
                elif t == "swipe":
                    pixel_actions.append({
                        "type": "swipe",
                        "x1": int(float(act.get("x1", 0.5)) * width),
                        "y1": int(float(act.get("y1", 0.5)) * height),
                        "x2": int(float(act.get("x2", 0.5)) * width),
                        "y2": int(float(act.get("y2", 0.5)) * height),
                        "duration_ms": int(act.get("duration_ms", 300)),
                        "delay_ms": int(act.get("delay_ms", 250))
                    })

            last_state["game"] = game_name
            last_state["last_action"] = result.get("status_short", "")

            reply = {
                "status": f"[{game_name}] {result.get('status_short', 'Ход выполнен')}",
                "actions": pixel_actions
            }
            print(f"✅ {reply['status']} | Действий: {len(pixel_actions)}")

        except Exception as e:
            print(f"❌ Ошибка анализа: {e}")
            reply = {"status": f"Ошибка ИИ: {str(e)[:35]}", "actions": []}

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(reply, ensure_ascii=False).encode("utf-8"))

    def log_message(self, format, *args):
        return

if __name__ == "__main__":
    print("🧠 Универсальный ИИ-Мозг запущен на [http://127.0.0.1:8765](http://127.0.0.1:8765)")
    print("Ожидание скриншотов от Android-агента...")
    HTTPServer(("127.0.0.1", 8765), AgentHandler).serve_forever()
