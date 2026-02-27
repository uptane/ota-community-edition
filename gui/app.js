const services = [
  { name: "director", path: "/director/health/version" },
  { name: "treehub", path: "/treehub/health/version" },
  { name: "deviceregistry", path: "/deviceregistry/health/version" },
  { name: "campaigner", path: "/campaigner/health/version" },
  { name: "reposerver", path: "/reposerver/health/version" },
  { name: "keyserver", path: "/keyserver/health/version" }
];

const aliasStorageKey = "ota-ce-device-aliases";
let droppedFile = null;
let campaignTimer = null;

const el = {
  baseUrl: document.getElementById("baseUrl"),
  token: document.getElementById("token"),
  toggleToken: document.getElementById("toggleToken"),
  clearToken: document.getElementById("clearToken"),
  safeMode: document.getElementById("safeMode"),

  method: document.getElementById("method"),
  path: document.getElementById("path"),
  body: document.getElementById("body"),
  response: document.getElementById("response"),

  healthResults: document.getElementById("healthResults"),

  deviceUuid: document.getElementById("deviceUuid"),
  deviceAlias: document.getElementById("deviceAlias"),
  deviceHardwareId: document.getElementById("deviceHardwareId"),
  saveDeviceAlias: document.getElementById("saveDeviceAlias"),
  refreshDevices: document.getElementById("refreshDevices"),
  deviceNotice: document.getElementById("deviceNotice"),
  aliasList: document.getElementById("aliasList"),
  deviceList: document.getElementById("deviceList"),

  groupName: document.getElementById("groupName"),
  groupId: document.getElementById("groupId"),
  createGroup: document.getElementById("createGroup"),
  refreshGroups: document.getElementById("refreshGroups"),
  addDeviceToGroup: document.getElementById("addDeviceToGroup"),
  removeDeviceFromGroup: document.getElementById("removeDeviceFromGroup"),
  groupList: document.getElementById("groupList"),

  updateDeviceUuid: document.getElementById("updateDeviceUuid"),
  updateGroupId: document.getElementById("updateGroupId"),
  updatePkgName: document.getElementById("updatePkgName"),
  updatePkgVersion: document.getElementById("updatePkgVersion"),
  updateName: document.getElementById("updateName"),
  campaignName: document.getElementById("campaignName"),
  updateFileName: document.getElementById("updateFileName"),
  updateFileContent: document.getElementById("updateFileContent"),
  updateFilePicker: document.getElementById("updateFilePicker"),
  campaignerVersion: document.getElementById("campaignerVersion"),
  launchCampaign: document.getElementById("launchCampaign"),
  runPreflight: document.getElementById("runPreflight"),
  preflightOutput: document.getElementById("preflightOutput"),
  createUpdate: document.getElementById("createUpdateFromText"),
  updateFlowOutput: document.getElementById("updateFlowOutput"),

  remoteDeviceUuid: document.getElementById("remoteDeviceUuid"),
  remoteLocalRoot: document.getElementById("remoteLocalRoot"),
  remoteUser: document.getElementById("remoteUser"),
  remoteHost: document.getElementById("remoteHost"),
  remotePath: document.getElementById("remotePath"),
  remoteMethod: document.getElementById("remoteMethod"),
  generateRemoteCommands: document.getElementById("generateRemoteCommands"),
  copyRemoteCommands: document.getElementById("copyRemoteCommands"),
  remoteCommandOutput: document.getElementById("remoteCommandOutput"),

  fillPythonCmd: document.getElementById("fillPythonCmd"),
  copyPythonCmd: document.getElementById("copyPythonCmd"),
  pythonCmdOutput: document.getElementById("pythonCmdOutput"),

  textSourcePanel: document.getElementById("textSourcePanel"),
  fileSourcePanel: document.getElementById("fileSourcePanel"),
  dropSourcePanel: document.getElementById("dropSourcePanel"),
  dropZone: document.getElementById("dropZone"),
  dropFileName: document.getElementById("dropFileName"),

  monitorCampaignId: document.getElementById("monitorCampaignId"),
  monitorInterval: document.getElementById("monitorInterval"),
  refreshCampaign: document.getElementById("refreshCampaign"),
  cancelCampaign: document.getElementById("cancelCampaign"),
  campaignSummary: document.getElementById("campaignSummary"),
  deliveryList: document.getElementById("deliveryList"),

  checkHealth: document.getElementById("checkHealth"),
  sendRequest: document.getElementById("sendRequest")
};

function joinUrl(baseUrl, path) {
  const b = baseUrl.replace(/\/+$/, "");
  const p = path.startsWith("/") ? path : `/${path}`;
  return `${b}${p}`;
}

function authHeaders() {
  const h = {};
  const token = el.token.value.trim();
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}

function jsonHeaders() {
  return { ...authHeaders(), "Content-Type": "application/json", Accept: "application/json" };
}

function pretty(obj) {
  if (typeof obj === "string") return obj;
  try { return JSON.stringify(obj, null, 2); } catch { return String(obj); }
}


function asFriendlyError(error) {
  const msg = error?.message || String(error);
  if (msg.includes("Failed to fetch")) {
    return `${msg} (possible CORS/network issue: try empty Base URL with scripts/gui-dev-server.py)`;
  }
  return msg;
}

function addResult(container, kind, text) {
  const row = document.createElement("div");
  row.className = `result ${kind}`;
  row.textContent = text;
  container.appendChild(row);
}

async function request(method, path, body) {
  const url = joinUrl(el.baseUrl.value.trim(), path);
  const options = { method, headers: jsonHeaders() };
  if (body && method !== "GET") options.body = body;

  const started = performance.now();
  const res = await fetch(url, options);
  const text = await res.text();
  const duration = Math.round(performance.now() - started);

  let parsed = text;
  try { parsed = JSON.parse(text); } catch { /* noop */ }

  return { status: res.status, ok: res.ok, duration, url, body: parsed };
}

async function requestJson(method, path, payload) {
  const res = await fetch(joinUrl(el.baseUrl.value.trim(), path), {
    method,
    headers: jsonHeaders(),
    body: payload ? JSON.stringify(payload) : undefined
  });
  const text = await res.text();
  let parsed = text;
  try { parsed = JSON.parse(text); } catch { /* noop */ }
  if (!res.ok) throw new Error(`HTTP ${res.status} ${path}\n${pretty(parsed)}`);
  return parsed;
}

function confirmAction(msg) {
  if (!el.safeMode.checked) return true;
  return window.confirm(msg);
}

function getAliases() {
  try {
    const parsed = JSON.parse(localStorage.getItem(aliasStorageKey) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

function setAliases(aliases) { localStorage.setItem(aliasStorageKey, JSON.stringify(aliases)); }
function findAlias(uuid) { return getAliases().find((x) => x.uuid === uuid) || null; }

function renderAliasList() {
  const aliases = getAliases();
  el.aliasList.innerHTML = "";
  if (!aliases.length) return addResult(el.aliasList, "warn", "No custom mappings yet.");

  aliases.forEach((item) => {
    const row = document.createElement("div");
    row.className = "result ok";
    row.textContent = `${item.alias} | ${item.uuid} | HWID: ${item.hardwareId || "-"}`;

    const removeBtn = document.createElement("button");
    removeBtn.textContent = "Remove";
    removeBtn.className = "secondary";
    removeBtn.addEventListener("click", () => {
      setAliases(getAliases().filter((x) => x.uuid !== item.uuid));
      renderAliasList();
    });

    row.appendChild(document.createElement("br"));
    row.appendChild(removeBtn);
    el.aliasList.appendChild(row);
  });
}

function normalizeList(payload) {
  if (Array.isArray(payload)) return payload;
  if (payload && Array.isArray(payload.values)) return payload.values;
  if (payload && Array.isArray(payload.items)) return payload.items;
  return [];
}

function extractId(payload) {
  if (typeof payload === "string") return payload.replace(/^"|"$/g, "").trim();
  if (payload && typeof payload === "object") {
    if (typeof payload.id === "string") return payload.id;
    if (typeof payload.uuid === "string") return payload.uuid;
  }
  throw new Error(`Unable to extract id from response: ${pretty(payload)}`);
}

function readArtifactSourceMode() {
  const checked = document.querySelector('input[name="artifactSource"]:checked');
  return checked ? checked.value : "text";
}

function applySourceMode() {
  const mode = readArtifactSourceMode();
  el.textSourcePanel.classList.toggle("hidden", mode !== "text");
  el.fileSourcePanel.classList.toggle("hidden", mode !== "file");
  el.dropSourcePanel.classList.toggle("hidden", mode !== "drop");
}

async function checkAllHealth() {
  el.healthResults.innerHTML = "";
  for (const service of services) {
    addResult(el.healthResults, "warn", `${service.name}: checking...`);
    const row = el.healthResults.lastChild;
    try {
      const result = await request("GET", service.path);
      row.className = `result ${result.ok ? "ok" : "error"}`;
      row.textContent = `${service.name}: ${result.status} (${result.duration}ms)`;
    } catch (e) {
      row.className = "result error";
      row.textContent = `${service.name}: ${asFriendlyError(e)}`;
    }
  }
}

function saveDeviceAlias() {
  const uuid = el.deviceUuid.value.trim();
  const alias = el.deviceAlias.value.trim();
  if (!uuid || !alias) {
    el.deviceNotice.textContent = "Device UUID and custom name are required.";
    return;
  }
  const hardwareId = el.deviceHardwareId.value.trim();
  const aliases = getAliases();
  const idx = aliases.findIndex((x) => x.uuid === uuid);
  const next = { uuid, alias, hardwareId };
  if (idx >= 0) aliases[idx] = next;
  else aliases.unshift(next);
  setAliases(aliases);
  el.updateDeviceUuid.value = uuid;
  el.deviceNotice.textContent = `Saved ${alias} ↔ ${uuid}`;
  renderAliasList();
}

async function refreshDevices() {
  el.deviceList.innerHTML = "";
  try {
    const result = await request("GET", "/api/v1/devices?limit=200");
    if (!result.ok) return addResult(el.deviceList, "error", `API ${result.status}: unable to list devices`);

    const items = normalizeList(result.body);
    if (!items.length) return addResult(el.deviceList, "warn", "No devices returned.");

    items.forEach((item) => {
      const uuid = item.id || item.uuid || item.deviceId || "unknown";
      const alias = findAlias(uuid);
      const row = document.createElement("div");
      row.className = "result ok";
      row.textContent = `${alias?.alias || item.deviceName || item.name || "(no name)"} | UUID: ${uuid} | HWID: ${item.hardwareId || item.deviceType || "unknown"}`;

      const useBtn = document.createElement("button");
      useBtn.textContent = "Use";
      useBtn.className = "secondary";
      useBtn.addEventListener("click", () => {
        el.deviceUuid.value = uuid;
        el.updateDeviceUuid.value = uuid;
        el.deviceAlias.value = alias?.alias || item.deviceName || item.name || "";
        el.deviceHardwareId.value = item.hardwareId || "ota-ce-device";
      });

      row.appendChild(document.createElement("br"));
      row.appendChild(useBtn);
      el.deviceList.appendChild(row);
    });
    el.deviceNotice.textContent = `Loaded ${items.length} devices.`;
  } catch (e) {
    addResult(el.deviceList, "error", `Device refresh failed: ${asFriendlyError(e)}`);
  }
}

async function createGroup() {
  const groupName = el.groupName.value.trim();
  if (!groupName) {
    el.deviceNotice.textContent = "Group name is required to create a group.";
    return;
  }
  try {
    const payload = { name: groupName, groupType: "static", expression: null };
    const resp = await requestJson("POST", "/api/v1/device_groups", payload);
    const id = extractId(resp);
    el.groupId.value = id;
    el.updateGroupId.value = id;
    el.deviceNotice.textContent = `Created group ${groupName}: ${id}`;
    await refreshGroups();
  } catch (e) {
    el.deviceNotice.textContent = `Create group failed: ${asFriendlyError(e)}`;
  }
}

async function refreshGroups() {
  el.groupList.innerHTML = "";
  try {
    const result = await request("GET", "/api/v1/device_groups?limit=200");
    if (!result.ok) return addResult(el.groupList, "error", `API ${result.status}: unable to list groups`);
    const groups = normalizeList(result.body);
    if (!groups.length) return addResult(el.groupList, "warn", "No groups returned.");

    groups.forEach((g) => {
      const gid = g.id || g.uuid || "unknown";
      const row = document.createElement("div");
      row.className = "result ok";
      row.textContent = `${g.name || "(unnamed)"} | UUID: ${gid} | type: ${g.groupType || "?"}`;

      const useBtn = document.createElement("button");
      useBtn.className = "secondary";
      useBtn.textContent = "Use";
      useBtn.addEventListener("click", () => {
        el.groupId.value = gid;
        el.updateGroupId.value = gid;
      });
      row.appendChild(document.createElement("br"));
      row.appendChild(useBtn);
      el.groupList.appendChild(row);
    });
  } catch (e) {
    addResult(el.groupList, "error", `Group refresh failed: ${asFriendlyError(e)}`);
  }
}

async function addDeviceToGroup() {
  const groupId = el.groupId.value.trim();
  const deviceId = el.deviceUuid.value.trim() || el.updateDeviceUuid.value.trim();
  if (!groupId || !deviceId) return (el.deviceNotice.textContent = "Group UUID and Device UUID are required.");

  try {
    await requestJson("POST", `/api/v1/device_groups/${encodeURIComponent(groupId)}/devices/${encodeURIComponent(deviceId)}`);
    el.deviceNotice.textContent = `Added ${deviceId} to group ${groupId}`;
  } catch (e) {
    el.deviceNotice.textContent = `Add to group failed: ${asFriendlyError(e)}`;
  }
}

async function removeDeviceFromGroup() {
  const groupId = el.groupId.value.trim();
  const deviceId = el.deviceUuid.value.trim() || el.updateDeviceUuid.value.trim();
  if (!groupId || !deviceId) return (el.deviceNotice.textContent = "Group UUID and Device UUID are required.");
  if (!confirmAction(`Remove device ${deviceId} from group ${groupId}?`)) return;

  try {
    await requestJson("DELETE", `/api/v1/device_groups/${encodeURIComponent(groupId)}/devices/${encodeURIComponent(deviceId)}`);
    el.deviceNotice.textContent = `Removed ${deviceId} from group ${groupId}`;
  } catch (e) {
    el.deviceNotice.textContent = `Remove from group failed: ${asFriendlyError(e)}`;
  }
}

async function digestSha256Hex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function resolveArtifact() {
  const mode = readArtifactSourceMode();
  const fileName = el.updateFileName.value.trim();
  if (!fileName) throw new Error("File name is required.");

  if (mode === "text") {
    const content = el.updateFileContent.value;
    if (!content.length) throw new Error("File contents cannot be empty in text mode.");
    const bytes = new TextEncoder().encode(content);
    return { fileName, size: bytes.length, hash: await digestSha256Hex(bytes), blob: new Blob([content], { type: "text/plain" }) };
  }

  const sourceFile = mode === "file" ? el.updateFilePicker.files[0] : droppedFile;
  if (!sourceFile) throw new Error(`No file selected for ${mode} mode.`);

  const ab = await sourceFile.arrayBuffer();
  const bytes = new Uint8Array(ab);
  return {
    fileName: sourceFile.name,
    size: sourceFile.size,
    hash: await digestSha256Hex(bytes),
    blob: sourceFile
  };
}

function appendOut(target, text) { target.textContent += `${text}\n`; }

async function runPreflightChecks() {
  el.preflightOutput.textContent = "Running preflight checks...\n";

  const base = el.baseUrl.value.trim();
  const deviceId = el.updateDeviceUuid.value.trim();
  const groupId = el.updateGroupId.value.trim();
  const packageName = el.updatePkgName.value.trim();
  const packageVersion = el.updatePkgVersion.value.trim();

  if (!base.startsWith("http")) appendOut(el.preflightOutput, "❌ Base URL must start with http/https");
  if (!deviceId) appendOut(el.preflightOutput, "❌ Device UUID is missing");
  if (!groupId) appendOut(el.preflightOutput, "❌ Group UUID is missing");
  if (!packageName || !packageVersion) appendOut(el.preflightOutput, "❌ Package name/version missing");

  try {
    await resolveArtifact();
    appendOut(el.preflightOutput, "✅ Artifact source is valid");
  } catch (e) {
    appendOut(el.preflightOutput, `❌ Artifact invalid: ${e.message}`);
  }

  for (const s of services) {
    try {
      const r = await request("GET", s.path);
      appendOut(el.preflightOutput, `${r.ok ? "✅" : "❌"} ${s.name} health: ${r.status}`);
    } catch (e) {
      appendOut(el.preflightOutput, `❌ ${s.name} health failed: ${asFriendlyError(e)}`);
    }
  }

  try {
    await requestJson("GET", `/api/v1/devices/${encodeURIComponent(deviceId)}`);
    appendOut(el.preflightOutput, "✅ Device exists");
  } catch (e) {
    appendOut(el.preflightOutput, `❌ Device check failed: ${asFriendlyError(e)}`);
  }

  try {
    await requestJson("GET", `/api/v1/device_groups/${encodeURIComponent(groupId)}`);
    appendOut(el.preflightOutput, "✅ Group exists");
  } catch (e) {
    appendOut(el.preflightOutput, `❌ Group check failed: ${asFriendlyError(e)}`);
  }

  try {
    const list = await requestJson("GET", `/api/v1/device_groups/${encodeURIComponent(groupId)}/devices?limit=200`);
    const entries = normalizeList(list);
    const present = entries.some((d) => (d.id || d.uuid || d.deviceId || d) === deviceId);
    appendOut(el.preflightOutput, `${present ? "✅" : "⚠️"} Device ${present ? "is" : "is not"} in group`);
  } catch (e) {
    appendOut(el.preflightOutput, `⚠️ Membership check failed: ${asFriendlyError(e)}`);
  }

  appendOut(el.preflightOutput, "Preflight done.");
}

async function createUpdateFlow() {
  const deviceId = el.updateDeviceUuid.value.trim();
  const groupId = el.updateGroupId.value.trim();
  const packageName = el.updatePkgName.value.trim();
  const packageVersion = el.updatePkgVersion.value.trim();
  const updateName = el.updateName.value.trim();
  const campaignName = el.campaignName.value.trim();
  const campaignApiBase = el.campaignerVersion.value === "v1" ? "/api/v1" : "/api/v2";

  if (!deviceId || !groupId || !packageName || !packageVersion || !updateName || !campaignName) {
    el.updateFlowOutput.textContent = "Missing required fields.";
    return;
  }

  el.updateFlowOutput.textContent = "Starting update flow...\n";

  try {
    const artifact = await resolveArtifact();
    appendOut(el.updateFlowOutput, `1) Artifact ready: ${artifact.fileName} (${artifact.size} bytes)`);

    const device = await requestJson("GET", `/api/v1/devices/${encodeURIComponent(deviceId)}`);
    const hardwareId = device.hardwareId || device.deviceType || "ota-ce-device";
    appendOut(el.updateFlowOutput, `2) Device hardwareId: ${hardwareId}`);

    const targetName = `${packageName}_${packageVersion}`;
    const uploadPath = `/api/v1/user_repo/targets/${encodeURIComponent(targetName)}?name=${encodeURIComponent(packageName)}&version=${encodeURIComponent(packageVersion)}&hardwareIds=${encodeURIComponent(hardwareId)}`;

    const form = new FormData();
    form.append("file", artifact.blob, artifact.fileName);
    const uploadRes = await fetch(joinUrl(el.baseUrl.value.trim(), uploadPath), {
      method: "PUT",
      headers: { ...authHeaders(), Accept: "application/json" },
      body: form
    });
    const uploadTxt = await uploadRes.text();
    if (!uploadRes.ok) throw new Error(`Target upload failed: HTTP ${uploadRes.status}\n${uploadTxt}`);
    appendOut(el.updateFlowOutput, `3) Target uploaded: ${targetName}`);

    const mtu = await requestJson("POST", "/api/v1/multi_target_updates", {
      targets: {
        [hardwareId]: {
          to: { target: targetName, checksum: { method: "sha256", hash: artifact.hash }, targetLength: artifact.size },
          targetFormat: "BINARY",
          generateDiff: false
        }
      }
    });
    const mtuId = extractId(mtu);
    appendOut(el.updateFlowOutput, `4) MTU created: ${mtuId}`);

    const upd = await requestJson("POST", `${campaignApiBase}/updates`, {
      updateSource: { id: mtuId, sourceType: "multi_target" },
      name: updateName,
      description: `Created from GUI (${artifact.fileName})`
    });
    const updateId = extractId(upd);
    appendOut(el.updateFlowOutput, `5) Update created: ${updateId}`);

    const camp = await requestJson("POST", `${campaignApiBase}/campaigns`, {
      name: campaignName,
      update: updateId,
      groups: [groupId],
      approvalNeeded: false
    });
    const campaignId = extractId(camp);
    appendOut(el.updateFlowOutput, `6) Campaign created: ${campaignId}`);
    el.monitorCampaignId.value = campaignId;
    el.remoteDeviceUuid.value = deviceId;

    if (el.launchCampaign.checked) {
      if (!confirmAction(`Launch campaign ${campaignId}?`)) {
        appendOut(el.updateFlowOutput, "7) Launch skipped by user.");
      } else {
        await requestJson("POST", `${campaignApiBase}/campaigns/${encodeURIComponent(campaignId)}/launch`);
        appendOut(el.updateFlowOutput, "7) Campaign launched.");
      }
    }

    appendOut(el.updateFlowOutput, "Done ✅");
  } catch (e) {
    appendOut(el.updateFlowOutput, `Failed ❌\n${asFriendlyError(e)}`);
  }
}

async function refreshCampaignMonitor() {
  const campaignId = el.monitorCampaignId.value.trim();
  const api = el.campaignerVersion.value === "v1" ? "/api/v1" : "/api/v2";
  if (!campaignId) return;

  el.campaignSummary.innerHTML = "";
  el.deliveryList.innerHTML = "";
  try {
    const campaign = await requestJson("GET", `${api}/campaigns/${encodeURIComponent(campaignId)}`);
    addResult(el.campaignSummary, "ok", `Status: ${campaign.status || "unknown"}`);
    addResult(el.campaignSummary, "ok", `Name: ${campaign.name || "-"}`);

    try {
      const deliveries = await requestJson("GET", `${api}/campaigns/${encodeURIComponent(campaignId)}/deliveries`);
      const items = normalizeList(deliveries);
      if (!items.length) addResult(el.deliveryList, "warn", "No deliveries returned.");
      items.forEach((d) => {
        addResult(el.deliveryList, "ok", `Device: ${d.deviceId || d.device || "?"} | Status: ${d.status || "?"} | ${d.reason || d.message || ""}`);
      });
    } catch (e) {
      addResult(el.deliveryList, "warn", `Deliveries endpoint unavailable or failed: ${e.message}`);
    }
  } catch (e) {
    addResult(el.campaignSummary, "error", `Campaign fetch failed: ${asFriendlyError(e)}`);
  }
}

function configureCampaignPolling() {
  if (campaignTimer) clearInterval(campaignTimer);
  campaignTimer = null;

  const sec = Number(el.monitorInterval.value || "0");
  if (sec > 0) campaignTimer = setInterval(refreshCampaignMonitor, sec * 1000);
}

async function cancelCampaign() {
  const campaignId = el.monitorCampaignId.value.trim();
  if (!campaignId) return;
  if (!confirmAction(`Cancel campaign ${campaignId}?`)) return;

  const api = el.campaignerVersion.value === "v1" ? "/api/v1" : "/api/v2";
  try {
    await requestJson("POST", `${api}/campaigns/${encodeURIComponent(campaignId)}/cancel`);
    await refreshCampaignMonitor();
  } catch (e) {
    el.campaignSummary.innerHTML = "";
    addResult(el.campaignSummary, "error", `Cancel failed: ${asFriendlyError(e)}`);
  }
}

async function sendManualRequest() {
  const method = el.method.value;
  const path = el.path.value.trim();
  const rawBody = el.body.value.trim();
  let body = undefined;

  if (rawBody) {
    try { JSON.parse(rawBody); body = rawBody; }
    catch { el.response.textContent = "Invalid JSON body."; return; }
  }

  el.response.textContent = "Loading...";
  try {
    const result = await request(method, path, body);
    el.response.textContent = `[${result.status}] ${result.url} (${result.duration}ms)\n\n${pretty(result.body)}`;
  } catch (error) {
    el.response.textContent = `Request error: ${asFriendlyError(error)}`;
  }
}


function shellQuote(s) {
  return `'${String(s).replace(/'/g, `'\''`)}'`;
}

function generateRemoteCopyCommands() {
  const deviceId = (el.remoteDeviceUuid.value.trim() || el.updateDeviceUuid.value.trim() || el.deviceUuid.value.trim());
  const localRoot = el.remoteLocalRoot.value.trim() || "./ota-ce-gen/devices";
  const user = el.remoteUser.value.trim();
  const host = el.remoteHost.value.trim();
  const remotePath = el.remotePath.value.trim();
  const method = el.remoteMethod.value;

  if (!deviceId || !user || !host || !remotePath) {
    el.remoteCommandOutput.textContent = "Missing fields. Device UUID, remote user, host, and remote path are required.";
    return;
  }

  const localDir = `${localRoot.replace(/\/+$/, "")}/${deviceId}`;
  const remote = `${user}@${host}:${remotePath}`;

  const scpCmd = `scp -r ${shellQuote(localDir)} ${shellQuote(remote)}`;
  const rsyncCmd = `rsync -avz --progress ${shellQuote(localDir)}/ ${shellQuote(remote)}/${deviceId}/`;
  const cdPath = `${remotePath.replace(/\/+$/, "")}/${deviceId}`;
  const runAktualizr = `ssh ${shellQuote(user + "@" + host)} "cd ${cdPath} && sudo aktualizr --run-mode=once --config=config.toml --loglevel=2"`;

  const selected = method === "rsync" ? rsyncCmd : scpCmd;

  el.remoteCommandOutput.textContent = [
    `# 1) Copy generated device folder to remote target`,
    selected,
    ``,
    `# 2) On remote target run aktualizr manually`,
    runAktualizr
  ].join("\n");
}

async function copyRemoteCommandsToClipboard() {
  const text = el.remoteCommandOutput.textContent.trim();
  if (!text || text === "Remote copy commands will appear here.") {
    generateRemoteCopyCommands();
  }
  try {
    await navigator.clipboard.writeText(el.remoteCommandOutput.textContent);
    addResult(el.campaignSummary, "ok", "Remote copy commands copied to clipboard.");
  } catch (e) {
    addResult(el.campaignSummary, "warn", `Clipboard copy failed: ${e.message}`);
  }
}


function generatePythonFlowCommand() {
  const deviceId = (el.updateDeviceUuid.value || el.deviceUuid.value || "").trim();
  const groupId = (el.updateGroupId.value || el.groupId.value || "").trim();
  const pkgName = el.updatePkgName.value.trim() || "mypkg";
  const pkgVersion = el.updatePkgVersion.value.trim() || "0.0.2";
  const updateName = el.updateName.value.trim() || "gui-update";
  const campaignName = el.campaignName.value.trim() || "gui-campaign";
  const campaignerVersion = el.campaignerVersion.value;
  const filePath = "~/path/to/update.bin";

  if (!deviceId) {
    el.pythonCmdOutput.textContent = "Device UUID is required to generate python command.";
    return;
  }

  const parts = [
    "python3 scripts/ota_e2e_flow.py",
    `  --device-id ${shellQuote(deviceId)}`,
    groupId ? `  --group-id ${shellQuote(groupId)}` : `  --group-name ${shellQuote("gui-group")}`,
    `  --pkg-name ${shellQuote(pkgName)}`,
    `  --pkg-version ${shellQuote(pkgVersion)}`,
    `  --file ${shellQuote(filePath)}`,
    `  --update-name ${shellQuote(updateName)}`,
    `  --campaign-name ${shellQuote(campaignName)}`,
    `  --campaigner-version ${shellQuote(campaignerVersion)}`,
  ];

  el.pythonCmdOutput.textContent = parts.join(" \
");
}

async function copyPythonCommandToClipboard() {
  const text = el.pythonCmdOutput.textContent.trim();
  if (!text || text === "Python command will appear here.") {
    generatePythonFlowCommand();
  }
  try {
    await navigator.clipboard.writeText(el.pythonCmdOutput.textContent);
    addResult(el.campaignSummary, "ok", "Python flow command copied to clipboard.");
  } catch (e) {
    addResult(el.campaignSummary, "warn", `Clipboard copy failed: ${asFriendlyError(e)}`);
  }
}

function setupDropZone() {
  const dz = el.dropZone;
  ["dragenter", "dragover"].forEach((ev) => dz.addEventListener(ev, (e) => {
    e.preventDefault();
    dz.classList.add("active");
  }));
  ["dragleave", "drop"].forEach((ev) => dz.addEventListener(ev, (e) => {
    e.preventDefault();
    dz.classList.remove("active");
  }));
  dz.addEventListener("drop", (e) => {
    const file = e.dataTransfer.files[0];
    droppedFile = file || null;
    el.dropFileName.textContent = droppedFile ? `Dropped: ${droppedFile.name}` : "No file selected.";
    if (droppedFile) el.updateFileName.value = droppedFile.name;
  });
}

function init() {
  el.toggleToken.addEventListener("click", () => {
    el.token.type = el.token.type === "password" ? "text" : "password";
    el.toggleToken.textContent = el.token.type === "password" ? "Show token" : "Hide token";
  });
  el.clearToken.addEventListener("click", () => {
    if (!confirmAction("Clear bearer token from this page?")) return;
    el.token.value = "";
  });

  document.querySelectorAll('input[name="artifactSource"]').forEach((r) => r.addEventListener("change", applySourceMode));
  el.updateFilePicker.addEventListener("change", () => {
    const f = el.updateFilePicker.files[0];
    if (f) el.updateFileName.value = f.name;
  });

  setupDropZone();

  el.checkHealth.addEventListener("click", checkAllHealth);
  el.saveDeviceAlias.addEventListener("click", saveDeviceAlias);
  el.refreshDevices.addEventListener("click", refreshDevices);
  el.createGroup.addEventListener("click", createGroup);
  el.refreshGroups.addEventListener("click", refreshGroups);
  el.addDeviceToGroup.addEventListener("click", addDeviceToGroup);
  el.removeDeviceFromGroup.addEventListener("click", removeDeviceFromGroup);

  el.runPreflight.addEventListener("click", runPreflightChecks);
  el.createUpdate.addEventListener("click", createUpdateFlow);

  el.generateRemoteCommands.addEventListener("click", generateRemoteCopyCommands);
  el.copyRemoteCommands.addEventListener("click", copyRemoteCommandsToClipboard);
  el.fillPythonCmd.addEventListener("click", generatePythonFlowCommand);
  el.copyPythonCmd.addEventListener("click", copyPythonCommandToClipboard);

  el.refreshCampaign.addEventListener("click", refreshCampaignMonitor);
  el.cancelCampaign.addEventListener("click", cancelCampaign);
  el.monitorInterval.addEventListener("change", configureCampaignPolling);

  el.sendRequest.addEventListener("click", sendManualRequest);
  document.querySelectorAll(".preset").forEach((button) => button.addEventListener("click", () => {
    el.method.value = button.dataset.method;
    el.path.value = button.dataset.path;
  }));

  applySourceMode();
  renderAliasList();
  refreshDevices();
  refreshGroups();
  el.remoteDeviceUuid.value = el.updateDeviceUuid.value.trim();
  configureCampaignPolling();
}

init();
