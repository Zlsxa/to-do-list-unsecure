"""Convertit la section [packages] d'un Pipfile en requirements.txt (pip / pip-audit).

Usage : python3 ci/pipfile2req.py Pipfile requirements.txt
"""
import sys
import tomllib


def main(pipfile, output):
    with open(pipfile, "rb") as f:
        packages = tomllib.load(f).get("packages", {})
    lines = []
    for name, spec in packages.items():
        if isinstance(spec, dict):
            spec = spec.get("version", "*")
        lines.append(name if spec in ("*", "") else f"{name}{spec}")
    with open(output, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
