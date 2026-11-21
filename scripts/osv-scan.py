#!/usr/bin/env python3
"""CVE scan of every locked dependency against the public OSV.dev database (SUP-06).

Same check as scripts/osv-scan.ps1 for macOS/Linux without PowerShell: reads all Gradle lockfiles, sends
only public library coordinates (group:name + version) to https://api.osv.dev and prints known
vulnerabilities. Exit code 1 when a HIGH or CRITICAL vulnerability is in shipped code (blocker), 0 otherwise.
Complements `./gradlew dependencyCheckAggregate`, which needs an NVD API key (D-16).

Usage: python3 scripts/osv-scan.py
"""

import json
import re
import sys
import urllib.request
from pathlib import Path

OSV_BATCH_URL = "https://api.osv.dev/v1/querybatch"
OSV_VULN_URL = "https://api.osv.dev/v1/vulns/"
ECOSYSTEM = "Maven"
BATCH_SIZE = 500
BLOCKING_SEVERITIES = {"HIGH", "CRITICAL"}
TIMEOUT_S = 60
# Configurations whose dependencies end up inside the APK.
SHIPPED_CONFIG = re.compile(r"(^|,)(releaseRuntimeClasspath|runtimeClasspath)(,|$)")


def request(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"} if data else {}
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=TIMEOUT_S) as r:
        return json.load(r)


def main():
    root = Path(__file__).resolve().parents[1]
    lockfiles = [p for p in root.rglob("*gradle.lockfile") if "/build/" not in str(p)]
    configs = {}
    for lockfile in lockfiles:
        for line in lockfile.read_text().splitlines():
            if not line or line.startswith("#") or line.startswith("empty="):
                continue
            coordinate, used_in = line.split("=", 1)
            configs[coordinate] = configs.get(coordinate, "") + "," + used_in
    coordinates = sorted(configs)
    print(f"Scanning {len(coordinates)} unique dependencies from {len(lockfiles)} lockfiles...")

    findings = []
    for offset in range(0, len(coordinates), BATCH_SIZE):
        batch = coordinates[offset:offset + BATCH_SIZE]
        queries = []
        for coordinate in batch:
            group, name, version = coordinate.split(":")[:3]
            queries.append({"package": {"ecosystem": ECOSYSTEM, "name": f"{group}:{name}"}, "version": version})
        results = request(OSV_BATCH_URL, {"queries": queries})["results"]
        for coordinate, result in zip(batch, results):
            for vuln in result.get("vulns") or []:
                detail = request(OSV_VULN_URL + vuln["id"])
                severity = (detail.get("database_specific") or {}).get("severity") or "UNKNOWN"
                findings.append({
                    "dependency": coordinate,
                    "scope": "SHIPPED" if SHIPPED_CONFIG.search(configs[coordinate].lstrip(",")) else "TOOLING",
                    "id": vuln["id"],
                    "severity": severity.upper(),
                    "summary": (detail.get("summary") or "").strip(),
                })

    if not findings:
        print("OSV scan: no known vulnerabilities.")
        return 0
    findings.sort(key=lambda f: (f["scope"], f["severity"], f["dependency"]))
    width = max(len(f["dependency"]) for f in findings)
    print(f"\n{'Dependency':<{width}} Scope   Id                  Severity Summary")
    for f in findings:
        print(f"{f['dependency']:<{width}} {f['scope']:<7} {f['id']:<19} {f['severity']:<8} {f['summary'][:100]}")
    high = [f for f in findings if f["severity"] in BLOCKING_SEVERITIES]
    blocking = [f for f in high if f["scope"] == "SHIPPED"]
    if blocking:
        print(f"OSV scan: {len(blocking)} HIGH/CRITICAL finding(s) in shipped code - blocker (SUP-06).")
        return 1
    if high:
        print(f"OSV scan: {len(high)} HIGH/CRITICAL finding(s) in build tooling only (not in the APK) - "
              "update tools at the Phase Gate (SUP-10).")
    print(f"OSV scan: {len(findings)} finding(s) total, none HIGH/CRITICAL in shipped code - "
          "evaluate and record (SUP-06).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
