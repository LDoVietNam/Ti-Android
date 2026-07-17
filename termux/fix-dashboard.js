#!/usr/bin/env node
/**
 * Fix dashboard.html:
 * 1. Extend renderEndpoints() to map Android service ports
 * 2. Add subtitle for Android services
 */
const fs = require('fs');
const path = 'Z:/01_PROJECTS/apps/Tirouter/dashboard.html';

let content = fs.readFileSync(path, 'utf8');

// Fix 1: Extend renderEndpoints port mapping
const oldMap = "const status = serviceStatus[ep.port === 20128 ? 'cliproxy' : ep.port === 1807 ? 'omniroute' : ''] ? 'up' : 'down';";
const newMap = "const status = serviceStatus[ep.port === 20128 ? 'cliproxy' : ep.port === 1807 ? 'omniroute' : ep.port === 5001 ? 'filetransfer' : ep.port === 5002 ? 'voiceinput' : ep.port === 5000 ? 'gemma4' : ep.port === 8080 ? 'freebuffapi' : ep.port === 1919 ? 'mcpbridge' : ''] ? 'up' : 'down';";
content = content.replace(oldMap, newMap);

// Fix 2: Update subtitle to mention Android
content = content.replace(
    "Unified AI Router — CLIProxyAPI + OmniRoute Integration",
    "Unified AI Router — CLIProxyAPI + OmniRoute + Android Node"
);

// Fix 3: Update integration section description
content = content.replace(
    "Cache layer gi\u1ea3m t\u1ea3i, MCP Bridge cho ph\u00e9p MCP clients k\u1ebft n\u1ed1i.",
    "Cache layer gi\u1ea3m t\u1ea3i, MCP Bridge cho ph\u00e9p MCP clients k\u1ebft n\u1ed1i. Android Node: File Transfer + Voice Input + Gemma 4 + TiRouter ARM64."
);

// Fix 4: Add new ENHANCED tab button for Android services
content = content.replace(
    "<button class=\"tab-btn\" onclick=\"switchTab(event, 'tabConfig')\">\u2699\ufe0f Config</button>",
    "<button class=\"tab-btn\" onclick=\"switchTab(event, 'tabConfig')\">\u2699\ufe0f Config</button>\n  <button class=\"tab-btn\" onclick=\"switchTab(event, 'tabAndroid')\">\U0001f4f1 Android</button>"
);

// Fix 5: Add Android tab content
const androidTab = `
<!-- Tab: Android Node -->
<div id="tabAndroid" class="tab-content">
  <div class="logs-panel" style="padding:20px;">
    <div style="margin-bottom:16px;">
      <span style="font-size:14px;font-weight:600;">\U0001f4f1 Android Node — Termux Services</span>
    </div>
    <div class="phone-widget" id="androidMetrics" style="grid-template-columns: repeat(4, 1fr);">
      <div class="phone-metric"><div class="value" id="androidFileCount">--</div><div class="label">Files Stored</div></div>
      <div class="phone-metric"><div class="value" id="androidStorage">--</div><div class="label">Free Space</div></div>
      <div class="phone-metric"><div class="value" id="androidVoiceLang">--</div><div class="label">Voice Lang</div></div>
      <div class="phone-metric"><div class="value" id="androidModelName">--</div><div class="label">Active Model</div></div>
    </div>
    <div style="margin-top:16px;padding:12px;background:var(--blue-bg);border-radius:8px;font-size:12px;color:var(--text-secondary);">
      <strong>\U0001f916 Android Node:</strong> Ollama (phone/ prefix) · File Transfer (:5001) · Voice Input (:5002) · TiRouter ARM64 (:20128)
    </div>
  </div>
</div>
`;

// Insert after config tab
// Find closing of config tab div
const configEnd = content.indexOf('<!-- Tab: Config Info -->');
const lastTabContent = content.lastIndexOf('</div>', content.indexOf('</div>', configEnd + 100));
const tabSectionEnd = content.indexOf('<script>', content.indexOf('<script>'));

// Insert android tab before the <script> section
const beforeScript = content.substring(0, tabSectionEnd);
const afterScript = content.substring(tabSectionEnd);
content = beforeScript + androidTab + '\n' + afterScript;

// Fix 6: Add Android metrics update to refreshAll
content = content.replace(
    "  renderEndpoints();\n  renderConfigInfo();\n  renderIntegration();\n  renderRouting();",
    "  renderEndpoints();\n  renderConfigInfo();\n  renderIntegration();\n  renderRouting();\n  updateAndroidMetrics();"
);

// Fix 7: Add updateAndroidMetrics function
const metricsFunc = `
// --- Android Metrics ---
async function updateAndroidMetrics() {
  // Get file count from file transfer
  try {
    const resp = await fetch('http://localhost:5001/files', { signal: AbortSignal.timeout(2000) });
    if (resp.ok) {
      const data = await resp.json();
      document.getElementById('androidFileCount').textContent = data.count || 0;
    }
  } catch {}
  // Get storage info
  try {
    const resp = await fetch('http://localhost:5001/health', { signal: AbortSignal.timeout(2000) });
    if (resp.ok) {
      const data = await resp.json();
      document.getElementById('androidStorage').textContent = (data.storage_free_gb || '--') + 'GB';
    }
  } catch {}
  // Get voice status
  document.getElementById('androidVoiceLang').textContent = serviceStatus.voiceinput === true ? 'vi/en' : '--';
  // Get active model
  document.getElementById('androidModelName').textContent = serviceStatus.gemma4 === true ? 'Gemma 4' : serviceStatus.phoneai === true ? 'llama3.2:1b' : '--';
}
`;

// Insert before the auto-refresh interval
content = content.replace(
    "// Auto-refresh every 10 seconds\nsetInterval(refreshAll, 10000);",
    metricsFunc + "\n// Auto-refresh every 10 seconds\nsetInterval(refreshAll, 10000);"
);

fs.writeFileSync(path, content);
console.log('Dashboard fixes applied successfully!');
console.log(`File size: ${content.length} chars`);
