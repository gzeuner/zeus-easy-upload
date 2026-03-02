#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import re
import json
import urllib.request
import urllib.error
from datetime import datetime

ROADMAP_PATH = "V2_ROADMAP.md"
CREATED_PATH = "CREATED_ISSUES.md"

TOKEN = os.environ.get("GITHUB_TOKEN", "").strip()
REPO_SLUG = os.environ.get("GITHUB_REPOSITORY", "").strip()  # owner/repo
API_BASE = os.environ.get("GITHUB_API_URL", "https://api.github.com").strip()

if not TOKEN:
    raise SystemExit("ERROR: GITHUB_TOKEN missing")
if not REPO_SLUG or "/" not in REPO_SLUG:
    raise SystemExit("ERROR: GITHUB_REPOSITORY missing or invalid (expected owner/repo)")

OWNER, REPO = REPO_SLUG.split("/", 1)

def api_request(method: str, path: str, data=None):
    url = f"{API_BASE}{path}"
    headers = {
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "zeus-easy-upload-issue-workflow",
    }
    payload = None
    if data is not None:
        payload = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            parsed = json.loads(body) if body else None
        except Exception:
            parsed = body
        return e.code, parsed

def ensure_label(name: str, color: str, description: str):
    status, resp = api_request(
        "POST",
        f"/repos/{OWNER}/{REPO}/labels",
        {"name": name, "color": color, "description": description},
    )
    if status in (200, 201):
        print(f"Label created: {name}")
    elif status == 422:
        print(f"Label exists: {name}")
    else:
        raise SystemExit(f"ERROR: label create failed ({status}) {name}: {resp}")

def parse_roadmap():
    if not os.path.exists(ROADMAP_PATH):
        raise SystemExit(f"ERROR: {ROADMAP_PATH} not found")

    text = open(ROADMAP_PATH, "r", encoding="utf-8").read()
    text = text.replace("\r\n", "\n").replace("\r", "\n")

    pattern = re.compile(
        r"TITLE:\s*\n(?P<title>.+?)\n\s*\n"
        r"LABELS:\s*\n(?P<labels>.+?)\n\s*\n"
        r"BODY:\s*\n(?P<body>.*?)(?=\nTITLE:\s*\n|\Z)",
        re.DOTALL,
    )

    issues = []
    for m in pattern.finditer(text):
        title = m.group("title").strip()
        labels = [x.strip() for x in m.group("labels").split(",") if x.strip()]
        body = m.group("body").strip()
        issues.append({"title": title, "labels": labels, "body": body})

    if not issues:
        raise SystemExit("ERROR: No issue blocks parsed. Check V2_ROADMAP.md format.")
    return issues

def create_issue(title: str, body: str, labels):
    payload = {"title": title, "body": body}
    if labels:
        payload["labels"] = labels
    status, resp = api_request("POST", f"/repos/{OWNER}/{REPO}/issues", payload)
    if status not in (200, 201):
        raise SystemExit(f"ERROR: issue create failed ({status}): {resp}")
    return resp

def main():
    # Fail fast to avoid duplicates
    if os.path.exists(CREATED_PATH) and os.path.getsize(CREATED_PATH) > 50:
        raise SystemExit(
            f"ERROR: {CREATED_PATH} already exists and is non-empty. "
            "Refusing to create duplicate issues."
        )

    labels_to_create = [
        ("epic", "6f42c1", "Epic"),
        ("enhancement", "84b6eb", "Enhancement"),
        ("tech-debt", "d4c5f9", "Tech debt / internal improvement"),
        ("bug", "d73a4a", "Bug"),
        ("api", "0052cc", "API"),
        ("ui", "1d76db", "UI"),
        ("db2", "5319e7", "DB2/400 / IBM i"),
        ("import", "0e8a16", "Import pipeline"),
        ("priority:P1", "ff0000", "High priority"),
        ("priority:P2", "ffa500", "Medium priority"),
        ("priority:P3", "00aa00", "Lower priority"),
    ]

    print("== Ensuring labels ==")
    for name, color, desc in labels_to_create:
        ensure_label(name, color, desc)

    print("== Parsing roadmap ==")
    issues = parse_roadmap()
    print(f"Parsed {len(issues)} blocks")

    # Epic first (label contains 'epic')
    epic = None
    rest = []
    for it in issues:
        if any(l.lower() == "epic" for l in it["labels"]) and epic is None:
            epic = it
        else:
            rest.append(it)
    if epic is None:
        raise SystemExit("ERROR: No epic found (needs label 'epic' in LABELS).")

    print("== Creating epic ==")
    epic_resp = create_issue(epic["title"], epic["body"], epic["labels"])
    epic_number = epic_resp["number"]
    epic_url = epic_resp["html_url"]
    print(f"Epic created: #{epic_number} {epic_url}")

    created = [{"number": epic_number, "title": epic["title"], "url": epic_url, "labels": epic["labels"]}]

    print("== Creating remaining issues ==")
    for it in rest:
        body = it["body"].replace("#<EPIC>", f"#{epic_number}")
        if "Part of:" in body:
            body = body.replace("Part of: #<EPIC>", f"Part of: #{epic_number}")
        resp = create_issue(it["title"], body, it["labels"])
        created.append({"number": resp["number"], "title": it["title"], "url": resp["html_url"], "labels": it["labels"]})
        print(f"Created: #{resp['number']} {resp['html_url']}")

    print("== Writing CREATED_ISSUES.md ==")
    now = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%SZ")
    lines = []
    lines.append(f"# Created Issues (V2)\n")
    lines.append(f"- Repository: `{OWNER}/{REPO}`")
    lines.append(f"- Created at (UTC): {now}\n")
    lines.append(f"## Epic\n- #{epic_number} {epic_url}\n")
    lines.append("## Issues\n")
    for c in created[1:]:
        lines.append(f"- #{c['number']} {c['url']} — {c['title']}")
    lines.append("")

    with open(CREATED_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print("Done.")

if __name__ == "__main__":
    main()