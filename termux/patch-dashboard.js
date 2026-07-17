#!/usr/bin/env node
// ══════════════════════════════════════════════════════════════
//  Patch Dashboard — Thêm Android services + fixes
//  Chạy: node patch-dashboard.js
// ══════════════════════════════════════════════════════════════
const fs = require('fs');
const path = require('path');
const file = path.resolve(__dirname, '..', '..', 'Tirouter', 'dashboard.html');
let content = fs.readFileSync(file, 'utf8');

const originalLen = content.length;

// ─── 1. Thêm 4 service cards vào SERVICES array ─────────────
const servicesBlock = `
  {
    id: 'filetransfer',
    name: 'File Transfer',
    port: 5001,
    url: 'http://localhost:5001/health',
    endpointLabel: 'REST API',
    desc: '📁 LAN file transfer on Android Termux',
    group: 'android',
    check: async () => {
      try { const r = await fetch('http://localhost:5001/health', { signal: AbortSignal.timeout(2000) }); return r.ok; }
      catch { return false; }
    }
  },
  {
    id: 'voiceinput',
    name: 'Voice Input',
    port: 5002,
    url: 'http://localhost:5002/health',
    endpointLabel: 'Cohere Transcribe',
    desc: '🎤 Voice input + Cohere API',
    group: 'android',
    check: async () => {
      try { const r = await fetch('http://localhost:5002/health', { signal: AbortSignal.timeout(2000) }); return r.ok; }
      catch { return false; }
    }
  },
  {
    id: 'gemma4',
    name: 'Gemma 4',
    port: 11434,
    url: 'http://localhost:5000/v1/models',
    endpointLabel: 'phone/gemma-4',
    desc: '🤖 Google mobile LLM 12B',
    group: 'android',
    check: async () => {
      try {
        const r = await fetch('http://localhost:5000/v1/models', { signal: AbortSignal.timeout(3000) });
        if (!r.ok) return false;
        const d = await r.json();
        return d.data && d.data.some(m => m.id.includes('gemma'));
      } catch { return false; }
    }
  },
  {
    id: 'tirouterarm',
    name: 'TiRouter ARM64',
    port: 20128,
    url: 'http://localhost:20128/v1/models',
    endpointLabel: 'ARM64 on Termux',
    desc: '🚀 TiRouter on Android ARM64',
    group: 'android',
    check: async () => {
      try {
        const r = await fetch('http://localhost:20128/v1/models', { 
          headers: { 'Authorization': 'Bearer sk-orca-test' },
          signal: AbortSignal.timeout(3000)
        });
        return r.ok;
      } catch { return false; }
    }
  },
`;

// Insert before 'const ENDPOINTS'
const epMarker = 'const ENDPOINTS = [';
let idx = content.indexOf(epMarker);
content = content.slice(0, idx) + servicesBlock + '];\n\n' + content.slice(idx);

// ─── 2. Thêm endpoints ──────────────────────────────────────
const endpointsBlock = `  // ── Android Services ──
  { path: '/health', method: 'GET', desc: 'File Transfer health', service: 'File Transfer', port: 5001 },
  { path: '/files', method: 'GET', desc: 'List transferred files', service: 'File Transfer', port: 5001 },
  { path: '/upload', method: 'POST', desc: 'Upload file to phone', service: 'File Transfer', port: 5001 },
  { path: '/transcribe', method: 'POST', desc: 'Transcribe audio (Cohere)', service: 'Voice Input', port: 5002 },
  { path: '/record', method: 'POST', desc: 'Record mic + transcribe', service: 'Voice Input', port: 5002 },
  { path: '/languages', method: 'GET', desc: 'List supported languages', service: 'Voice Input', port: 5002 },
`;

// Insert before the last ]; in ENDPOINTS
const endOfEndpoints = content.lastIndexOf('];', content.indexOf('// ─── State'));
content = content.slice(0, endOfEndpoints) + '\n' + endpointsBlock + content.slice(endOfEndpoints);

// ─── 3. Integration table rows ──────────────────────────────
content = content.replace(
  "    { name: 'Phone AI (:5000)', role: 'On-Device LLM', type: 'edge', dep: 'self' },\n    { name: 'Smart Cache'",
  "    { name: 'Phone AI (:5000)', role: 'On-Device LLM', type: 'edge', dep: 'self' },\n    { name: 'File Transfer (:5001)', role: 'LAN File Service', type: 'android', dep: 'phoneai' },\n    { name: 'Voice Input (:5002)', role: 'Speech-to-Text', type: 'android', dep: 'phoneai' },\n    { name: 'Gemma 4 (12B)', role: 'Mobile LLM', type: 'android', dep: 'ollama' },\n    { name: 'TiRouter ARM64', role: 'Router on Phone', type: 'android', dep: 'phoneai' },\n    { name: 'Smart Cache'"
);

// ─── 4. Service matcher ─────────────────────────────────────
content = content.replace(
  "      : c.name.toLowerCase().includes('phone') ? 'phoneai'\n      : 'smartcache'",
  "      : c.name.toLowerCase().includes('file transfer') ? 'filetransfer'\n      : c.name.toLowerCase().includes('voice input') ? 'voiceinput'\n      : c.name.toLowerCase().includes('gemma') ? 'gemma4'\n      : c.name.toLowerCase().includes('tirouter arm') ? 'tirouterarm'\n      : c.name.toLowerCase().includes('phone') ? 'phoneai'\n      : 'smartcache'"
);

// ─── 5. Config entries ──────────────────────────────────────
content = content.replace(
  "    { label: 'Phone AI', value: serviceStatus.phoneai === true ? '● Connected' : serviceStatus.phoneai === false ? '○ Offline' : '⟳ Checking' },\n    { label: 'OrcaRouter'",
  "    { label: 'Phone AI', value: serviceStatus.phoneai === true ? '● Connected' : serviceStatus.phoneai === false ? '○ Offline' : '⟳ Checking' },\n    { label: 'File Transfer', value: serviceStatus.filetransfer === true ? '● Running (:5001)' : serviceStatus.filetransfer === false ? '○ Offline' : '⟳ Checking' },\n    { label: 'Voice Input', value: serviceStatus.voiceinput === true ? '● Running (:5002)' : serviceStatus.voiceinput === false ? '○ Offline' : '⟳ Checking' },\n    { label: 'Gemma 4', value: serviceStatus.gemma4 === true ? '● Loaded' : serviceStatus.gemma4 === false ? '○ Not installed' : '⟳ Checking' },\n    { label: 'TiRouter ARM64', value: serviceStatus.tirouterarm === true ? '● Running' : serviceStatus.tirouterarm === false ? '○ Offline' : '⟳ Checking' },\n    { label: 'OrcaRouter'"
);

// ─── 6. Extend renderEndpoints port mapping ─────────────────
content = content.replace(
  "const status = serviceStatus[ep.port === 20128 ? 'cliproxy' : ep.port === 1807 ? 'omniroute' : ''] ? 'up' : 'down';",
  "const status = serviceStatus[ep.port === 20128 ? 'cliproxy' : ep.port === 1807 ? 'omniroute' : ep.port === 5001 ? 'filetransfer' : ep.port === 5002 ? 'voiceinput' : ep.port === 5000 ? 'gemma4' : ep.port === 8080 ? 'freebuffapi' : ''] ? 'up' : 'down';"
);

// ─── 7. Update subtitle ─────────────────────────────────────
content = content.replace(
  "Unified AI Router — CLIProxyAPI + OmniRoute Integration",
  "Unified AI Router — CLIProxyAPI + OmniRoute + Android Node"
);

// ─── 8. Integration description ─────────────────────────────
content = content.replace(
  "Cache layer giảm tải, MCP Bridge cho phép MCP clients kết nối.",
  "Cache layer giảm tải, MCP Bridge cho phép MCP clients kết nối. Android Node: File Transfer, Voice Input, Gemma 4, TiRouter ARM64."
);

// ─── 9. Android tab button ──────────────────────────────────
content = content.replace(
  'onclick="switchTab(event, \'tabConfig\')">⚙️ Config</button>',
  'onclick="switchTab(event, \'tabConfig\')">⚙️ Config</button>\n  <button class="tab-btn" onclick="switchTab(event, \'tabAndroid\')">📱 Android</button>'
);

// ─── 10. Android tab content ────────────────────────────────
const androidTab = `
<!-- Tab: Android Node -->
<div id="tabAndroid" class="tab-content">
  <div class="logs-panel" style="padding:20px;">
    <div style="margin-bottom:16px;">
      <span style="font-size:14px;font-weight:600;">📱 Android Node — Termux Services</span>
    </div>
    <div class="phone-widget" id="androidMetrics" style="grid-template-columns: repeat(4, 1fr);">
      <div class="phone-metric"><div class="value" id="androidFileCount">--</div><div class="label">Files</div></div>
      <div class="phone-metric"><div class="value" id="androidStorage">--</div><div class="label">Free Space</div></div>
      <div class="phone-metric"><div class="value" id="androidVoiceLang">--</div><div class="label">Voice</div></div>
      <div class="phone-metric"><div class="value" id="androidModelName">--</div><div class="label">Model</div></div>
    </div>
    <div style="margin-top:16px;padding:12px;background:var(--blue-bg);border-radius:8px;font-size:12px;color:var(--text-secondary);">
      <strong>🤖 Android Node:</strong> Ollama (phone/) · File Transfer (:5001) · Voice Input (:5002) · TiRouter ARM64 (:20128)
    </div>
  </div>
</div>
`;

const scriptStart = content.indexOf('\n<script>');
content = content.slice(0, scriptStart) + '\n' + androidTab + '\n' + content.slice(scriptStart);

// ─── 11. Metrics update function ────────────────────────────
const metricsFunc = `
// ─── Android Metrics ────────────────────────────────────────
async function updateAndroidMetrics() {
  try {
    const r = await fetch('http://localhost:5001/files', { signal: AbortSignal.timeout(2000) });
    if (r.ok) { const d = await r.json(); document.getElementById('androidFileCount').textContent = d.count || 0; }
  } catch {}
  try {
    const r = await fetch('http://localhost:5001/health', { signal: AbortSignal.timeout(2000) });
    if (r.ok) { const d = await r.json(); document.getElementById('androidStorage').textContent = (d.storage_free_gb || '--') + 'GB'; }
  } catch {}
  document.getElementById('androidVoiceLang').textContent = serviceStatus.voiceinput === true ? '✅ Cohere' : '--';
  document.getElementById('androidModelName').textContent = serviceStatus.gemma4 === true ? 'Gemma 4' : serviceStatus.phoneai === true ? 'llama3.2:1b' : '--';
}
`;

content = content.replace(
  '// Auto-refresh every 10 seconds\nsetInterval(refreshAll, 10000);',
  metricsFunc + '\n// Auto-refresh every 10 seconds\nsetInterval(refreshAll, 10000);'
);

// ─── 12. Add updateAndroidMetrics to refreshAll ────────────
content = content.replace(
  '  renderEndpoints();\n  renderConfigInfo();\n  renderIntegration();\n  renderRouting();',
  '  renderEndpoints();\n  renderConfigInfo();\n  renderIntegration();\n  renderRouting();\n  updateAndroidMetrics();'
);

// ─── Write result ──────────────────────────────────────────
fs.writeFileSync(file, content);
console.log(`✅ Dashboard patched: ${originalLen} → ${content.length} chars (+${content.length - originalLen})`);
