#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Enhanced OTA CE end-to-end flow (device->group->target->mtu->update->campaign)."""

import argparse
import hashlib
import json
import os
import time
from typing import Any, Dict, Optional, Tuple

import requests

DEFAULT_DEVICE_REGISTRY = os.getenv("DEVICEREG_URL", "http://deviceregistry.ota.ce/api/v1")
DEFAULT_REPO = os.getenv("REPO_URL", "http://reposerver.ota.ce/api/v1")
DEFAULT_DIRECTOR = os.getenv("DIRECTOR_URL", "http://director.ota.ce/api/v1")
DEFAULT_CAMPAIGNER = os.getenv("CAMPAIGNER_URL", "http://campaigner.ota.ce/api/v2")
DEFAULT_NAMESPACE = os.getenv("ATS_NAMESPACE", "default")
DEFAULT_HWID = os.getenv("HWID", "ota-ce-device")


def _headers(json_body: bool = True) -> Dict[str, str]:
    h = {"Accept": "application/json"}
    if json_body:
        h["Content-Type"] = "application/json"
    if DEFAULT_NAMESPACE:
        h["x-ats-namespace"] = DEFAULT_NAMESPACE
    return h


def _raise(resp: requests.Response, ctx: str):
    try:
        resp.raise_for_status()
    except requests.HTTPError as exc:
        try:
            body = resp.json()
        except Exception:
            body = resp.text
        raise SystemExit(f"{ctx} failed: HTTP {resp.status_code} - {body}") from exc


def _extract_id(payload: Any) -> str:
    if isinstance(payload, str):
        return payload.strip().strip('"').strip("'")
    if isinstance(payload, dict):
        for k in ("id", "uuid", "group", "update", "campaign"):
            if isinstance(payload.get(k), str):
                return payload[k]
    raise SystemExit(f"Could not extract id from response: {payload}")


def _sha256_len(path: str) -> Tuple[str, int]:
    h = hashlib.sha256()
    total = 0
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
            total += len(chunk)
    return h.hexdigest(), total


def fetch_device(devreg: str, device_id: str, fallback_hwid: Optional[str]) -> Dict[str, Any]:
    r = requests.get(f"{devreg}/devices/{device_id}", headers=_headers(json_body=False))
    _raise(r, "Fetch device")
    data = r.json()
    hwid = data.get("hardwareId") or data.get("hardware_id") or fallback_hwid
    if not hwid:
        raise SystemExit("Device has no hardwareId and no fallback hwid provided")
    data["hardwareId"] = hwid
    return data


def ensure_group(devreg: str, group_id: Optional[str], group_name: Optional[str]) -> str:
    if group_id:
        r = requests.get(f"{devreg}/device_groups/{group_id}", headers=_headers(json_body=False))
        if r.status_code == 200:
            return group_id
    if not group_name:
        raise SystemExit("Provide --group-id or --group-name")
    payload = {"name": group_name, "groupType": "static", "expression": None}
    r = requests.post(f"{devreg}/device_groups", headers=_headers(), json=payload)
    _raise(r, "Create group")
    gid = _extract_id(r.json())
    time.sleep(1)
    return gid


def add_device_to_group(devreg: str, group_id: str, device_id: str):
    r = requests.post(
        f"{devreg}/device_groups/{group_id}/devices/{device_id}",
        headers=_headers(json_body=False),
    )
    if r.status_code in (200, 201, 204, 409):
        return
    _raise(r, "Add device to group")


def upload_target(repo: str, pkg: str, ver: str, hwid: str, file_path: str) -> Tuple[str, str, int]:
    target_name = f"{pkg}_{ver}"
    file_hash, file_len = _sha256_len(file_path)
    with open(file_path, "rb") as fp:
        files = {"file": (os.path.basename(file_path), fp, "application/octet-stream")}
        r = requests.put(
            f"{repo}/user_repo/targets/{target_name}?name={pkg}&version={ver}&hardwareIds={hwid}",
            headers=_headers(json_body=False),
            files=files,
        )
    _raise(r, "Upload target")
    return target_name, file_hash, file_len


def create_mtu(director: str, hwid: str, target: str, digest: str, size: int) -> str:
    payload = {
        "targets": {
            hwid: {
                "to": {"target": target, "checksum": {"method": "sha256", "hash": digest}, "targetLength": size},
                "targetFormat": "BINARY",
                "generateDiff": False,
            }
        }
    }
    r = requests.post(f"{director}/multi_target_updates", headers=_headers(), json=payload)
    _raise(r, "Create MTU")
    return _extract_id(r.json())


def create_update(campaigner: str, mtu_id: str, name: str) -> str:
    payload = {"updateSource": {"id": mtu_id, "sourceType": "multi_target"}, "name": name, "description": name}
    r = requests.post(f"{campaigner}/updates", headers=_headers(), json=payload)
    _raise(r, "Create update")
    return _extract_id(r.json())


def create_campaign(campaigner: str, campaign_name: str, update_id: str, group_id: str) -> str:
    payload = {"name": campaign_name, "update": update_id, "groups": [group_id], "approvalNeeded": False}
    r = requests.post(f"{campaigner}/campaigns", headers=_headers(), json=payload)
    _raise(r, "Create campaign")
    return _extract_id(r.json())


def launch_campaign(campaigner: str, campaign_id: str):
    r = requests.post(f"{campaigner}/campaigns/{campaign_id}/launch", headers=_headers(json_body=False))
    _raise(r, "Launch campaign")


def parse_args():
    p = argparse.ArgumentParser(description="OTA CE E2E updater")
    p.add_argument("--device-id", required=True)
    p.add_argument("--group-id")
    p.add_argument("--group-name")
    p.add_argument("--pkg-name", default="mypkg")
    p.add_argument("--pkg-version", default="0.0.2")
    p.add_argument("--file", required=True)
    p.add_argument("--update-name", default="gui-upd")
    p.add_argument("--campaign-name", default="gui-campaign")
    p.add_argument("--campaigner-version", choices=["v1", "v2"], default="v2")
    p.add_argument("--hwid", default=DEFAULT_HWID)
    p.add_argument("--devicereg", default=DEFAULT_DEVICE_REGISTRY)
    p.add_argument("--repo", default=DEFAULT_REPO)
    p.add_argument("--director", default=DEFAULT_DIRECTOR)
    p.add_argument("--campaigner", default=DEFAULT_CAMPAIGNER)
    return p.parse_args()


def main():
    args = parse_args()
    campaigner = args.campaigner.replace("/api/v1", "/api/v2") if args.campaigner_version == "v2" else args.campaigner.replace("/api/v2", "/api/v1")

    device = fetch_device(args.devicereg, args.device_id, args.hwid)
    hwid = device["hardwareId"]
    group_id = ensure_group(args.devicereg, args.group_id, args.group_name)
    add_device_to_group(args.devicereg, group_id, args.device_id)

    target, digest, size = upload_target(args.repo, args.pkg_name, args.pkg_version, hwid, args.file)
    mtu_id = create_mtu(args.director, hwid, target, digest, size)
    upd_id = create_update(campaigner, mtu_id, args.update_name)
    camp_id = create_campaign(campaigner, args.campaign_name, upd_id, group_id)
    launch_campaign(campaigner, camp_id)

    print(json.dumps({
        "deviceId": args.device_id,
        "hardwareId": hwid,
        "groupId": group_id,
        "target": target,
        "mtuId": mtu_id,
        "updateId": upd_id,
        "campaignId": camp_id,
    }, indent=2))


if __name__ == "__main__":
    main()
