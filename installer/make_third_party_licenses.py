"""Generate THIRD-PARTY-LICENSES.md from the real dependency trees.

The project's own LICENSE (PolyForm Noncommercial) explicitly says bundled
third-party components keep their own terms and points here, so this file has to
exist and has to be accurate. It is generated, never hand-maintained.

Sources:
  - npm/pnpm production dependencies of the desktop app:
        pnpm licenses list --prod --json   (run in windows/)
  - Android/Gradle dependencies: read from android/app/build.gradle.kts, which
    declares them explicitly. Gradle has no built-in license reporter, so the
    SPDX ids come from a small table of the well-known AndroidX/Google/Square
    artifacts this project uses; anything unrecognised is emitted as UNKNOWN so
    it is visible rather than silently omitted.

Usage:
    python installer/make_third_party_licenses.py <repo_root>
"""

import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

# Android artifact prefix -> (license name, url). Every dependency declared in
# android/app/build.gradle.kts must match one of these or be reported UNKNOWN.
ANDROID_LICENSES = [
    ("androidx.", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("com.google.android.material", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("com.google.dagger", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("com.google.code.gson", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("com.google.devtools.ksp", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("com.squareup.okhttp3", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("com.squareup.retrofit2", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("org.jetbrains.kotlin", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("org.jetbrains.kotlinx", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("junit", "Eclipse Public License 1.0", "https://www.eclipse.org/legal/epl-v10.html"),
]

DEP_RE = re.compile(r'^\s*(?:implementation|api|ksp|testImplementation|androidTestImplementation|debugImplementation)\s*\(\s*"([^":]+):([^":]+)(?::([^"]+))?"\s*\)')


def npm_licenses(windows_dir: Path):
    """{license: [(name, versions)]} for production npm deps."""
    try:
        out = subprocess.run(
            ["npx", "pnpm", "licenses", "list", "--prod", "--json"],
            cwd=windows_dir, capture_output=True, text=True, shell=True, timeout=300,
        ).stdout
        start = out.find("{")
        data = json.loads(out[start:]) if start >= 0 else {}
    except Exception as exc:  # noqa: BLE001
        sys.stderr.write(f"[WARN] could not read npm licenses: {exc}\n")
        return {}

    result = defaultdict(list)
    for lic, pkgs in data.items():
        for pkg in pkgs:
            result[lic].append((pkg.get("name", "?"), ", ".join(pkg.get("versions", []))))
    for lic in result:
        result[lic].sort()
    return result


def android_deps(gradle_file: Path):
    """[(group:artifact, license, url)] for declared Android dependencies."""
    if not gradle_file.is_file():
        return []
    seen, rows = set(), []
    for line in gradle_file.read_text(encoding="utf-8").splitlines():
        m = DEP_RE.match(line)
        if not m:
            continue
        coord = f"{m.group(1)}:{m.group(2)}"
        if coord in seen:
            continue
        seen.add(coord)
        lic, url = "UNKNOWN — verify before distributing", ""
        for prefix, name, link in ANDROID_LICENSES:
            if coord.startswith(prefix):
                lic, url = name, link
                break
        rows.append((coord, lic, url))
    rows.sort()
    return rows


def main(argv) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    npm = npm_licenses(root / "windows")
    android = android_deps(root / "android" / "app" / "build.gradle.kts")

    npm_count = sum(len(v) for v in npm.values())
    out = [
        "# Third-party licenses",
        "",
        "Wellness Companion itself is licensed under the PolyForm Noncommercial",
        "License 1.0.0 (see [LICENSE](LICENSE)). The components listed below are",
        "redistributed with it under their own terms; nothing in this project's",
        "license restricts or overrides the rights those licenses grant.",
        "",
        "**This file is generated** by `installer/make_third_party_licenses.py`.",
        "Regenerate it whenever dependencies change; do not edit it by hand.",
        "",
        "## Bundled AI model",
        "",
        "The desktop app downloads and loads **Qwen3-4B-Instruct-2507** in GGUF form",
        "(`Qwen3-4B-Instruct-2507-Q4_K_M.gguf`, quantised by lmstudio-community).",
        "Qwen3 is released by Alibaba Cloud under the **Apache License 2.0**.",
        "The model is not stored in this repository; it is fetched at install time",
        "from <https://huggingface.co/lmstudio-community/Qwen3-4B-Instruct-2507-GGUF>.",
        "",
        "## Bundled font",
        "",
        "**Nunito** by Vernon Adams, Cyreal and Jacques Le Bailly, under the",
        "**SIL Open Font License 1.1** (<https://openfontlicense.org>). The latin and",
        "latin-ext subsets are vendored at",
        "`windows/src/renderer/src/assets/fonts/` so the app makes no network request",
        "for fonts at runtime.",
        "",
        "## Runtime components installed alongside the app",
        "",
        "The installer bundles the **Microsoft Visual C++ Redistributable**, which is",
        "redistributable under the Microsoft Visual Studio redistributable terms.",
        "",
        f"## Desktop app — npm dependencies ({npm_count} production packages)",
        "",
    ]

    if npm:
        for lic in sorted(npm):
            out += [f"### {lic}", "", "| package | version |", "|---|---|"]
            out += [f"| `{name}` | {vers} |" for name, vers in npm[lic]]
            out.append("")
    else:
        out += ["_Could not enumerate npm licenses; run "
                "`pnpm licenses list --prod` in `windows/`._", ""]

    out += [f"## Phone app — Gradle dependencies ({len(android)} declared artifacts)", ""]
    if android:
        out += ["| artifact | license |", "|---|---|"]
        for coord, lic, url in android:
            out.append(f"| `{coord}` | {f'[{lic}]({url})' if url else lic} |")
        out.append("")
        out += ["Transitive Android dependencies inherit the licenses of their parents;",
                "the table lists what the project declares directly.", ""]
    else:
        out += ["_Could not read `android/app/build.gradle.kts`._", ""]

    dest = root / "THIRD-PARTY-LICENSES.md"
    dest.write_text("\n".join(out), encoding="utf-8")
    unknown = sum(1 for _, lic, _ in android if lic.startswith("UNKNOWN"))
    print(f"  [OK] {dest} — {npm_count} npm packages, {len(android)} Android artifacts"
          + (f", {unknown} UNKNOWN (verify!)" if unknown else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
