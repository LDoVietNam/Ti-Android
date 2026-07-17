#!/usr/bin/env python3
"""
Phone AI Service — On-Device LLM via Android Termux
====================================================
Exposes Ollama as an OpenAI-compatible REST API on port 5000.
Designed to run on Android via Termux.

Endpoints:
  GET  /health              — Device health (battery, memory, cpu, ollama status)
  GET  /v1/models           — List available Ollama models
  POST /v1/chat/completions — Chat completion (OpenAI-compatible)
  GET  /v1/models/{model}   — Get model info
  POST /api/chat            — Simple chat endpoint

Integration:
  - TiRouter: termux/host-tools/start-phone-ai.bat --connect <phone-ip>
  - Dashboard: live widget showing battery, latency, model
"""

import json
import os
import platform
import subprocess
import time
import urllib.request
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Optional

# ─── Configuration ────────────────────────────────────────────────
PORT = int(os.environ.get("PHONE_AI_PORT", "5000"))
OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
HOST = os.environ.get("PHONE_AI_HOST", "0.0.0.0")
START_TIME = time.time()

# ─── Helpers ──────────────────────────────────────────────────────

def get_battery_info() -> dict:
    """Get Android battery info from Termux:BatteryStatus or dumpsys."""
    try:
        output = subprocess.check_output(
            ["termux-battery-status"], timeout=3, text=True
        )
        data = json.loads(output)
        return {
            "percent": data.get("percentage", 0),
            "power_plugged": data.get("plugged", "") != "UNPLUGGED",
            "status": data.get("status", "unknown"),
            "temperature": data.get("temperature", 0),
        }
    except Exception:
        # Fallback: try dumpsys
        try:
            output = subprocess.check_output(
                ["dumpsys", "battery"], timeout=3, text=True
            )
            lines = output.split("\n")
            info = {}
            for line in lines:
                if ":" in line:
                    k, v = line.split(":", 1)
                    info[k.strip()] = v.strip()
            return {
                "percent": int(info.get("level", 0)) if info.get("level", "0").isdigit() else 0,
                "power_plugged": "AC" in info.get("AC powered", "") or "USB" in info.get("USB powered", ""),
                "status": info.get("status", "unknown"),
            }
        except Exception:
            return {"percent": 0, "power_plugged": False, "status": "unknown"}


def get_memory_info() -> dict:
    """Get memory usage from /proc/meminfo."""
    try:
        with open("/proc/meminfo", "r") as f:
            lines = f.readlines()
        mem_total = 0
        mem_available = 0
        for line in lines:
            if line.startswith("MemTotal:"):
                mem_total = int(line.split()[1])
            elif line.startswith("MemAvailable:"):
                mem_available = int(line.split()[1])
        if mem_total > 0:
            used_percent = round((1 - mem_available / mem_total) * 100, 1)
            return {
                "total_kb": mem_total,
                "available_kb": mem_available,
                "percent": used_percent,
            }
    except Exception:
        pass
    return {"percent": 0}


def get_cpu_info() -> dict:
    """Get CPU usage from /proc/stat."""
    try:
        with open("/proc/stat", "r") as f:
            line = f.readline()
        parts = line.split()
        if len(parts) >= 5:
            user = int(parts[1])
            nice = int(parts[2])
            system = int(parts[3])
            idle = int(parts[4])
            total = user + nice + system + idle
            # Simple: return idle percentage (first reading)
            return {"percent": round((1 - idle / total) * 100, 1) if total > 0 else 0}
    except Exception:
        pass
    return {"percent": 0}


def get_hostname() -> str:
    try:
        return platform.node() or "android-device"
    except Exception:
        return "android-device"


def get_platform() -> str:
    try:
        return f"Android ({platform.release()})"
    except Exception:
        return "Android"


def ollama_request(method: str, path: str, body: Optional[dict] = None, timeout: int = 30) -> Optional[dict]:
    """Make request to Ollama API."""
    url = f"{OLLAMA_URL}{path}"
    try:
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode())
    except Exception:
        return None


def get_ollama_models() -> list:
    """Get list of available Ollama models."""
    result = ollama_request("GET", "/api/tags")
    if result and "models" in result:
        return [
            {
                "id": m["name"],
                "object": "model",
                "created": m.get("modified_at", ""),
                "owned_by": "ollama",
            }
            for m in result["models"]
        ]
    return []


def check_ollama() -> bool:
    """Check if Ollama is running."""
    return ollama_request("GET", "/api/tags") is not None


def ollama_chat(model: str, messages: list, stream: bool = False) -> Optional[dict]:
    """Send chat request to Ollama."""
    body = {
        "model": model,
        "messages": messages,
        "stream": stream,
    }
    return ollama_request("POST", "/api/chat", body, timeout=60)


def format_chat_response(model: str, ollama_response: dict) -> dict:
    """Format Ollama response to OpenAI-compatible format."""
    message = ollama_response.get("message", {})
    return {
        "id": f"chatcmpl-{int(time.time())}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": model,
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": message.get("role", "assistant"),
                    "content": message.get("content", ""),
                },
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": ollama_response.get("prompt_eval_count", 0),
            "completion_tokens": ollama_response.get("eval_count", 0),
            "total_tokens": ollama_response.get("prompt_eval_count", 0)
                         + ollama_response.get("eval_count", 0),
        },
    }


# ─── HTTP Handler ─────────────────────────────────────────────────

class PhoneAIHandler(BaseHTTPRequestHandler):
    """HTTP request handler for Phone AI service."""

    def _send_json(self, data: dict, status: int = 200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def _read_body(self) -> Optional[dict]:
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length > 0:
            body = self.rfile.read(content_length)
            try:
                return json.loads(body.decode())
            except json.JSONDecodeError:
                return None
        return None

    def do_OPTIONS(self):
        self._send_json({})

    def do_GET(self):
        path = self.path.rstrip("/")

        if path == "/health":
            ollama_ok = check_ollama()
            battery = get_battery_info()
            memory = get_memory_info()
            cpu = get_cpu_info()
            uptime = int(time.time() - START_TIME)

            self._send_json({
                "status": "ok",
                "service": "phone-ai",
                "version": "1.0.0",
                "uptime_seconds": uptime,
                "ollama_connected": ollama_ok,
                "battery": battery,
                "memory_percent": memory.get("percent", 0),
                "cpu_percent": cpu.get("percent", 0),
                "hostname": get_hostname(),
                "platform": get_platform(),
                "port": PORT,
            })

        elif path == "/v1/models":
            models = get_ollama_models()
            if not models:
                # Fallback: default models
                models = [
                    {"id": "llama3.2:1b", "object": "model", "owned_by": "ollama"},
                    {"id": "llama3.2:3b", "object": "model", "owned_by": "ollama"},
                    {"id": "phi3:mini", "object": "model", "owned_by": "ollama"},
                    {"id": "qwen2.5:1.5b", "object": "model", "owned_by": "ollama"},
                    {"id": "gemma2:2b", "object": "model", "owned_by": "ollama"},
                ]
            self._send_json({
                "object": "list",
                "data": models,
            })

        elif path.startswith("/v1/models/"):
            model_name = path[len("/v1/models/"):]
            models = get_ollama_models()
            found = [m for m in models if m["id"] == model_name]
            if found:
                self._send_json(found[0])
            else:
                self._send_json({
                    "id": model_name,
                    "object": "model",
                    "owned_by": "ollama",
                })

        else:
            self._send_json({"error": "Not found", "path": path}, 404)

    def do_POST(self):
        path = self.path.rstrip("/")
        body = self._read_body()

        if path == "/v1/chat/completions" or path == "/api/chat":
            if not body:
                self._send_json({"error": "Invalid request body"}, 400)
                return

            model = body.get("model", "llama3.2:1b")
            messages = body.get("messages", [])
            stream = body.get("stream", False)

            # If model has phone/ prefix, strip it
            if "/" in model:
                model = model.split("/")[-1]

            if not messages:
                self._send_json({"error": "No messages provided"}, 400)
                return

            # Check Ollama
            if not check_ollama():
                self._send_json({
                    "error": "Ollama not running",
                    "message": "Start Ollama with: ollama serve",
                }, 503)
                return

            # For /api/chat, convert simple format
            if path == "/api/chat":
                prompt = body.get("prompt", "")
                messages = [{"role": "user", "content": prompt}]

            result = ollama_chat(model, messages, stream)
            if result:
                response = format_chat_response(model, result)
                self._send_json(response)
            else:
                self._send_json({
                    "error": "ollama_request_failed",
                    "message": f"Failed to get response from Ollama model: {model}",
                }, 502)

        else:
            self._send_json({"error": "Not found", "path": path}, 404)

    def log_message(self, format, *args):
        """Suppress default logging; use structured format."""
        print(f"[PhoneAI] {args[0]}" if args else "")


# ─── Main ─────────────────────────────────────────────────────────

def main():
    server = HTTPServer((HOST, PORT), PhoneAIHandler)
    print(f"╔═══════════════════════════════════════════╗")
    print(f"║   Phone AI Service                       ║")
    print(f"║   On-Device LLM via Android Termux       ║")
    print(f"╚═══════════════════════════════════════════╝")
    print(f"")
    print(f"  📡 Listening on http://{HOST}:{PORT}")
    print(f"  🔗 Ollama URL: {OLLAMA_URL}")
    print(f"")
    print(f"  Endpoints:")
    print(f"    GET  /health              — Device health")
    print(f"    GET  /v1/models           — List models")
    print(f"    POST /v1/chat/completions — Chat (OpenAI format)")
    print(f"    POST /api/chat            — Chat (simple format)")
    print(f"")
    print(f"  Integration:")
    print("    TiRouter: termux/host-tools/start-phone-ai.bat --connect <this-ip>")
    print(f"")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[PhoneAI] Shutting down...")
        server.server_close()


if __name__ == "__main__":
    main()
