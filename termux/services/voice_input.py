#!/usr/bin/env python3
"""
Ti-Android Voice Input Service
- Record audio via Termux mic
- Transcribe via Cohere API (cloud) or offline fallback
- Return transcribed text for TiRouter / Phone AI
"""
import os, sys, json, base64, tempfile, subprocess
from datetime import datetime

import uvicorn
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import httpx

# ─── Config ──────────────────────────────────────────────
COHERE_API_KEY = os.environ.get("COHERE_API_KEY", "")
COHERE_TRANSCRIBE_URL = "https://api.cohere.com/v1/transcribe"
SAMPLE_RATE = int(os.environ.get("TI_AUDIO_SAMPLE_RATE", "16000"))
SUPPORTED_LANGS = ["vi", "en", "ar", "zh", "ja", "ko", "th"]

# ─── App ──────────────────────────────────────────────────
app = FastAPI(title="Ti-Android Voice Input", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# ─── Routes ───────────────────────────────────────────────

@app.get("/health")
async def health():
    """Service health + available features"""
    has_key = bool(COHERE_API_KEY)
    # Check if termux-microphone-record is available
    has_mic = subprocess.run(
        ["which", "termux-microphone-record"], 
        capture_output=True
    ).returncode == 0
    
    return {
        "status": "ok",
        "service": "voice-input",
        "version": "1.0.0",
        "provider": "cohere" if has_key else "offline",
        "features": {
            "transcribe": has_key,
            "record": has_mic,
            "languages": SUPPORTED_LANGS
        }
    }

@app.get("/languages")
async def list_languages():
    """List supported languages"""
    names = {
        "vi": "Tiếng Việt",
        "en": "English",
        "ar": "العربية",
        "zh": "中文",
        "ja": "日本語",
        "ko": "한국어",
        "th": "ไทย"
    }
    return {
        "languages": [
            {"code": lang, "name": names.get(lang, lang)}
            for lang in SUPPORTED_LANGS
        ]
    }

@app.post("/transcribe")
async def transcribe(
    audio: UploadFile = File(None),
    audio_base64: str = Form(None),
    language: str = Form("vi"),
    response_format: str = Form("json")
):
    """
    Transcribe audio to text.
    
    Accepts:
    - audio: uploaded WAV/MP3/M4A file
    - audio_base64: base64-encoded audio
    
    Returns transcribed text.
    """
    if not audio and not audio_base64:
        raise HTTPException(400, "Provide either audio file or audio_base64")
    
    if language not in SUPPORTED_LANGS:
        raise HTTPException(400, f"Unsupported language: {language}. Use: {SUPPORTED_LANGS}")

    # Check credentials
    if not COHERE_API_KEY:
        return {
            "status": "offline",
            "text": "",
            "note": "Configure COHERE_API_KEY for transcription",
            "language": language,
            "provider": "cohere"
        }

    # Save to temp file
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        if audio:
            content = await audio.read()
        else:
            content = base64.b64decode(audio_base64)
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # Call Cohere Transcribe API
        async with httpx.AsyncClient(timeout=60) as client:
            with open(tmp_path, "rb") as f:
                files = {"audio": (os.path.basename(tmp_path), f, "audio/wav")}
                data = {
                    "language": language,
                    "response_format": response_format
                }
                resp = await client.post(
                    COHERE_TRANSCRIBE_URL,
                    headers={"Authorization": f"Bearer {COHERE_API_KEY}"},
                    files=files,
                    data=data
                )
            
            if resp.status_code != 200:
                raise HTTPException(502, f"Cohere API error: {resp.text}")
            
            result = resp.json()
        
        # Calculate audio duration (approx)
        audio_duration = len(content) / (SAMPLE_RATE * 2)  # 16-bit mono
        
        return {
            "status": "ok",
            "text": result.get("text", ""),
            "confidence": result.get("confidence", 0.0),
            "language": language,
            "provider": "cohere",
            "audio_duration_seconds": round(audio_duration, 1),
            "processing_time_ms": result.get("processing_time_ms", 0)
        }
        
    except httpx.TimeoutException:
        raise HTTPException(504, "Cohere API timeout")
    except Exception as e:
        raise HTTPException(500, f"Transcription failed: {e}")
    finally:
        os.unlink(tmp_path)


@app.post("/record-and-transcribe")
async def record_and_transcribe(
    duration: int = Form(5),
    language: str = Form("vi")
):
    """
    Record audio from Android mic and transcribe.
    Requires Termux:API package.
    """
    # Check mic availability
    if subprocess.run(["which", "termux-microphone-record"], 
                      capture_output=True).returncode != 0:
        raise HTTPException(400, 
            "termux-microphone-record not found. Install: pkg install termux-api")
    
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_path = tmp.name
    
    try:
        # Record via Termux API
        result = subprocess.run([
            "termux-microphone-record",
            "-l", str(duration),
            "-f", tmp_path,
            "-e", "aac",
            "-r", str(SAMPLE_RATE)
        ], capture_output=True, timeout=duration + 10)
        
        if result.returncode != 0:
            raise HTTPException(500, f"Recording failed: {result.stderr.decode()}")
        
        # Now transcribe
        if not COHERE_API_KEY:
            return {
                "status": "recorded",
                "file": tmp_path,
                "duration": duration,
                "note": "COHERE_API_KEY needed for transcription"
            }
        
        async with httpx.AsyncClient(timeout=60) as client:
            with open(tmp_path, "rb") as f:
                resp = await client.post(
                    COHERE_TRANSCRIBE_URL,
                    headers={"Authorization": f"Bearer {COHERE_API_KEY}"},
                    files={"audio": (os.path.basename(tmp_path), f, "audio/wav")},
                    data={"language": language}
                )
            resp.raise_for_status()
            result = resp.json()
        
        return {
            "status": "ok",
            "text": result.get("text", ""),
            "confidence": result.get("confidence", 0.0),
            "duration": duration,
            "language": language,
            "provider": "cohere"
        }
        
    except subprocess.TimeoutExpired:
        raise HTTPException(504, "Recording timeout")
    except Exception as e:
        raise HTTPException(500, f"Record+transcribe failed: {e}")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


@app.get("/mic-test")
async def mic_test(duration: int = 3):
    """
    Quick mic test — record and return audio level.
    No transcription, just test if mic works.
    """
    if subprocess.run(["which", "termux-microphone-record"],
                      capture_output=True).returncode != 0:
        return {"available": False, "note": "Install termux-api: pkg install termux-api"}
    
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        tmp_path = tmp.name
    
    try:
        subprocess.run([
            "termux-microphone-record",
            "-l", str(duration),
            "-f", tmp_path
        ], capture_output=True, timeout=duration + 5)
        
        file_size = os.path.getsize(tmp_path) if os.path.exists(tmp_path) else 0
        
        return {
            "available": True,
            "recorded": file_size > 1000,
            "file_size": file_size,
            "duration": duration,
            "note": "Mic working!" if file_size > 1000 else "No audio captured"
        }
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


# ─── Main ─────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5002
    host = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
    
    if COHERE_API_KEY:
        print(f"🎤 Ti-Android Voice Input Service (Cohere)")
    else:
        print(f"🎤 Ti-Android Voice Input Service (OFFLINE)")
        print(f"   Set COHERE_API_KEY for transcription")
    
    print(f"   Listen: {host}:{port}")
    print(f"   Languages: {', '.join(SUPPORTED_LANGS)}")
    uvicorn.run(app, host=host, port=port, log_level="info")
