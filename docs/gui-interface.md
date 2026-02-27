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
- API Explorer for custom requests.
- Security hardening for demos:
  - Token show/hide and clear controls.
  - Safety mode confirmation prompts before launch/cancel/remove operations.

## Run locally

From repository root:

```bash
python3 -m http.server 8080
```

Open `http://localhost:8080/gui/`.

## Notes

- Configure OTA CE base URL in the UI (default: `http://ota.ce`).
- Add OTA CE hostnames in `/etc/hosts` when using Docker Compose.
- Device aliases are local browser metadata and do not modify server-side device identity.
- The GUI cannot execute OS-level copy commands directly (browser sandbox). Use the generated remote-copy commands in your terminal.
