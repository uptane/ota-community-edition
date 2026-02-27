# OTA CE GUI interface

This repository includes a browser GUI under `gui/` for interacting with OTA Community Edition APIs.

## Features

- Service health checks for core OTA CE services.
- Device and group management:
  - Load devices and groups from OTA API.
  - Save custom-name ↔ UUID mappings in browser local storage.
  - Create groups and add/remove devices from groups.
- Quick Update workflow:
  - Supports artifact source options: text mode, file picker, and drag/drop.
  - Runs preflight readiness checks before update creation.
  - Uploads target, creates MTU, update, campaign, and optionally launches campaign.
- Campaign Monitor:
  - Manual refresh or auto-poll.
  - Campaign summary + deliveries view.
  - Cancel campaign action.
- Remote Copy Helper:
  - Generates `scp` / `rsync` commands to transfer `ota-ce-gen/devices/:uuid` to a target host.
  - Generates the remote `aktualizr` run command.
  - Copy-to-clipboard support for faster handoff.
- Python E2E Flow Helper:
  - Generates a terminal command for `scripts/ota_e2e_flow.py` using Quick Update values.
  - Useful when you want server-side automation with richer debug logs.
- API Explorer for custom requests.
- Security hardening for demos:
  - Token show/hide and clear controls.
  - Safety mode confirmation prompts before launch/cancel/remove operations.

## Run locally

From repository root:

```bash
# recommended: serve GUI + reverse-proxy OTA API to avoid CORS
python3 scripts/gui-dev-server.py --port 8080 --target http://ota.ce
```

Open `http://localhost:8080/gui/`.

If `8080` is occupied, run on another port:

```bash
python3 scripts/gui-dev-server.py --port 8081 --target http://ota.ce
```

And open `http://localhost:8081/gui/`.

To check what is using 8080:

```bash
ss -ltnp | rg :8080
```

If you use plain static hosting (`python3 -m http.server`), cross-origin API calls may fail with `Failed to fetch` unless CORS is configured on your OTA host.

## Notes

- Configure OTA CE base URL in the UI. Leave it empty when using `scripts/gui-dev-server.py` proxy mode.
- Add OTA CE hostnames in `/etc/hosts` when using Docker Compose.
- Device aliases are local browser metadata and do not modify server-side device identity.
- The GUI cannot execute OS-level copy commands directly (browser sandbox). Use generated commands in your terminal.
- For CLI automation, run `python3 scripts/ota_e2e_flow.py --help`.
