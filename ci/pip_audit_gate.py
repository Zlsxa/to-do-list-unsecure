"""Gate de sécurité des dépendances : fait échouer le build si pip-audit trouve
une vulnérabilité de sévérité >= seuil (CRITICAL par défaut).

pip-audit ne donne pas la sévérité : on la récupère dans la base OSV
(avis GitHub "GHSA-..." qui portent une sévérité LOW/MODERATE/HIGH/CRITICAL).

Usage : python3 ci/pip_audit_gate.py pip-audit.json
Seuil : variable d'environnement PIP_AUDIT_FAIL_ON (LOW, MODERATE, HIGH, CRITICAL)
"""
import json
import os
import sys
import urllib.request

LEVELS = ["UNKNOWN", "LOW", "MODERATE", "HIGH", "CRITICAL"]
OSV_URL = "https://api.osv.dev/v1/vulns/"


class OsvUnreachable(Exception):
    pass


def osv_severity(vuln_ids):
    reached = False
    for vid in sorted(vuln_ids, key=lambda v: not v.startswith("GHSA-")):
        try:
            with urllib.request.urlopen(OSV_URL + vid, timeout=15) as resp:
                data = json.load(resp)
        except OSError:
            continue
        reached = True
        severity = (data.get("database_specific") or {}).get("severity")
        if severity:
            return severity.upper()
    if not reached:
        raise OsvUnreachable(", ".join(vuln_ids))
    return "UNKNOWN"


def main(report_path):
    threshold = os.environ.get("PIP_AUDIT_FAIL_ON", "CRITICAL").upper()
    with open(report_path, encoding="utf-8") as f:
        report = json.load(f)

    blocking = []
    seen = set()
    print(f"{'PAQUET':<12} {'VERSION':<10} {'VULN':<22} {'SÉVÉRITÉ':<9} CORRIGÉ EN")
    for dep in report.get("dependencies", []):
        for vuln in dep.get("vulns", []):
            ids = [vuln["id"], *vuln.get("aliases", [])]
            key = (dep["name"], frozenset(ids))
            if any(i in done for done in seen for i in ids):
                continue
            seen.add(key[1])
            try:
                severity = osv_severity(ids)
            except OsvUnreachable as exc:
                print(f"ÉCHEC GATE SCA : base OSV injoignable, sévérité invérifiable ({exc})")
                sys.exit(2)
            cves = [i for i in ids if i.startswith("CVE-")]
            label = cves[0] if cves else vuln["id"]
            fixes = ", ".join(vuln.get("fix_versions", [])) or "-"
            print(f"{dep['name']:<12} {dep['version']:<10} {label:<22} {severity:<9} {fixes}")
            if LEVELS.index(severity) >= LEVELS.index(threshold):
                blocking.append(f"{dep['name']} {dep['version']} : {label} ({severity}), corriger en {fixes}")

    if blocking:
        print(f"\nÉCHEC GATE SCA : {len(blocking)} vulnérabilité(s) de sévérité >= {threshold}")
        for line in blocking:
            print(" - " + line)
        sys.exit(1)
    print(f"\nGATE SCA OK : aucune vulnérabilité de sévérité >= {threshold}")


if __name__ == "__main__":
    main(sys.argv[1])
