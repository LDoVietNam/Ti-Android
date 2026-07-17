#!/usr/bin/env python3
"""
Ti-Android LAN File Transfer Service
- HTTP REST for file upload/download/list
- Auto-discovery via LAN broadcast
- For TiRouter + Phone AI integration
"""
import os, sys, json, hashlib, mimetypes
from pathlib import Path
from datetime import datetime

import uvicorn
from fastapi import FastAPI, UploadFile, File, HTTPException, Depends
from fastapi.responses import FileResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import aiofiles

# ─── Config ──────────────────────────────────────────────
STORAGE_DIR = os.environ.get("TI_FILE_STORAGE", 
    os.path.expanduser("~/ti-android-node/data/files"))
MAX_FILE_SIZE = int(os.environ.get("TI_MAX_FILE_SIZE_MB", "100")) * 1024 * 1024

os.makedirs(STORAGE_DIR, exist_ok=True)

# ─── App ──────────────────────────────────────────────────
app = FastAPI(title="Ti-Android File Transfer", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

# ─── Helpers ──────────────────────────────────────────────
def get_free_space() -> float:
    try:
        s = os.statvfs(STORAGE_DIR)
        return round(s.f_bavail * s.f_frsize / (1024**3), 1)
    except:
        return 0.0

def md5_file(path: str) -> str:
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()[:16]

# ─── API Routes ───────────────────────────────────────────

@app.get("/health")
async def health():
    """Service health + disk info"""
    return {
        "status": "ok",
        "service": "file-transfer",
        "version": "1.0.0",
        "storage": {
            "dir": STORAGE_DIR,
            "free_gb": get_free_space(),
            "max_file_mb": MAX_FILE_SIZE // (1024*1024)
        }
    }

@app.get("/files")
async def list_files():
    """List all transferred files"""
    files = []
    for f in sorted(Path(STORAGE_DIR).iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if f.is_file():
            files.append({
                "name": f.name,
                "size": f.stat().st_size,
                "size_str": fmt_size(f.stat().st_size),
                "type": mimetypes.guess_type(f.name)[0] or "application/octet-stream",
                "modified": datetime.fromtimestamp(f.stat().st_mtime).isoformat(),
                "hash": md5_file(str(f))
            })
    return {
        "count": len(files),
        "total_size": sum(f.stat().st_size for f in Path(STORAGE_DIR).iterdir() if f.is_file()),
        "files": files
    }

@app.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    """Upload a file"""
    safe_name = os.path.basename(file.filename or "unknown")
    path = os.path.join(STORAGE_DIR, safe_name)
    
    size = 0
    async with aiofiles.open(path, "wb") as f:
        while chunk := await file.read(8192):
            size += len(chunk)
            if size > MAX_FILE_SIZE:
                os.remove(path)
                raise HTTPException(413, f"File too large (max {MAX_FILE_SIZE // (1024*1024)}MB)")
            await f.write(chunk)
    
    return {
        "status": "ok",
        "filename": safe_name,
        "size": size,
        "size_str": fmt_size(size),
        "hash": md5_file(path)
    }

@app.get("/download/{filename:path}")
async def download_file(filename: str):
    """Download a file"""
    path = os.path.join(STORAGE_DIR, os.path.basename(filename))
    if not os.path.exists(path):
        raise HTTPException(404, "File not found")
    return FileResponse(path, filename=os.path.basename(path))

@app.delete("/files/{filename:path}")
async def delete_file(filename: str):
    """Delete a file"""
    path = os.path.join(STORAGE_DIR, os.path.basename(filename))
    if os.path.exists(path):
        os.remove(path)
        return {"status": "deleted", "filename": filename}
    raise HTTPException(404, "File not found")

@app.post("/receive")
async def receive_from(url: str, filename: str = None):
    """Pull file from URL (Phone downloads from PC)"""
    import httpx
    if not filename:
        filename = url.split("/")[-1]
    path = os.path.join(STORAGE_DIR, filename)
    
    async with httpx.AsyncClient(timeout=120) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        async with aiofiles.open(path, "wb") as f:
            await f.write(resp.content)
    
    return {
        "status": "ok",
        "filename": filename,
        "size": len(resp.content),
        "hash": md5_file(path),
        "source": url
    }

def fmt_size(bytes: int) -> str:
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes < 1024:
            return f"{bytes:.1f}{unit}"
        bytes /= 1024
    return f"{bytes:.1f}TB"

# ─── Main ─────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5001
    host = sys.argv[2] if len(sys.argv) > 2 else "0.0.0.0"
    print(f"📁 Ti-Android File Transfer Service")
    print(f"   Listen: {host}:{port}")
    print(f"   Storage: {STORAGE_DIR}")
    print(f"   Max file: {MAX_FILE_SIZE // (1024*1024)}MB")
    uvicorn.run(app, host=host, port=port, log_level="info")
