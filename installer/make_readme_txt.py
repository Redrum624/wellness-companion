"""Generate a version-stamped plain-text README.txt from README.md.

Shipped alongside the installer in the output dir, so someone who downloads the
setup exe on its own still gets readable instructions without a Markdown viewer.
Generated at build time rather than hand-copied, so it can never drift from
README.md.

Flattens Markdown to readable plain text: headings underlined, code fences
stripped to indented text, links rendered as "text (url)", badge/image lines
dropped, table separator rows removed.

Usage:
    python make_readme_txt.py <README.md> <version> <output_dir> [app_name]
    -> writes "<output_dir>/<app_name> <version> README.txt"

No third-party deps (stdlib only).
"""

import re
import sys
from pathlib import Path

DEFAULT_APP_NAME = "Wellness Companion"


def _flatten_inline(text: str) -> str:
    """Strip inline Markdown markup from a single line of prose."""
    # Raw inline HTML tags (<img>, <a>, <br>, <sub>, ...) -> drop the tag, keep text.
    text = re.sub(r"<[^>]+>", "", text)
    # Images: ![alt](url) -> drop entirely (badges, screenshots).
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)

    # Links: [text](url) -> "text (url)"; bare [text]() -> "text".
    def _link(m):
        label, url = m.group(1), m.group(2).strip()
        return f"{label} ({url})" if url else label

    text = re.sub(r"\[([^\]]+)\]\(([^)]*)\)", _link, text)
    # Bold / italic: **x**, __x__, *x*, _x_ -> x.
    text = re.sub(r"\*\*([^*]+)\*\*", r"\1", text)
    text = re.sub(r"__([^_]+)__", r"\1", text)
    text = re.sub(r"(?<!\*)\*(?!\*)([^*\n]+)\*(?!\*)", r"\1", text)
    text = re.sub(r"(?<!\w)_([^_\n]+)_(?!\w)", r"\1", text)
    # Inline code: `code` -> code.
    text = re.sub(r"`([^`]+)`", r"\1", text)
    return text


def flatten_markdown(md: str) -> str:
    # Drop HTML comments outright, including multi-line ones. They are editor
    # notes (e.g. badges commented out until publish) and must not surface in
    # the shipped plain-text readme.
    md = re.sub(r"<!--.*?-->", "", md, flags=re.DOTALL)

    out_lines = []
    in_code = False
    skip_block = False
    for raw in md.splitlines():
        stripped = raw.strip()

        # Code fence toggle (```lang / ```): drop the fence line itself.
        if stripped.startswith("```") or stripped.startswith("~~~"):
            if not in_code:
                # Diagram sources (mermaid et al.) are meaningless as plain
                # text — replace the whole block with a pointer.
                lang = stripped.lstrip("`~").strip().lower()
                skip_block = lang in ("mermaid", "plantuml", "dot", "graphviz")
                if skip_block:
                    out_lines.append("    [diagram - see README.md in the project repository]")
            in_code = not in_code
            if not in_code:
                skip_block = False
            continue
        if in_code:
            if skip_block:
                continue
            # Keep code content verbatim, indented so it reads as a block.
            out_lines.append("    " + raw)
            continue

        # Drop pure badge/image lines.
        if re.fullmatch(r"!\[[^\]]*\]\([^)]*\)\s*", stripped):
            continue
        # Drop standalone raw-HTML tag lines (<p align="center">, </p>, <img/>, <br>).
        if re.fullmatch(r"</?[a-zA-Z][^>]*>\s*", stripped):
            continue
        # Drop table separator rows: | --- | :--: | ...
        if re.fullmatch(r"\|?[\s:|-]+\|?", stripped) and "-" in stripped and "|" in stripped:
            continue
        # Horizontal rule -> dashed divider.
        if re.fullmatch(r"(-{3,}|\*{3,}|_{3,})", stripped):
            out_lines.append("-" * 60)
            continue

        # Headings: # .. ###### -> text, with an underline for H1/H2.
        m = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if m:
            level = len(m.group(1))
            title = _flatten_inline(m.group(2)).strip()
            if level == 1:
                out_lines.append("")
                out_lines.append(title.upper())
                out_lines.append("=" * max(3, len(title)))
            elif level == 2:
                out_lines.append("")
                out_lines.append(title)
                out_lines.append("-" * max(3, len(title)))
            else:
                out_lines.append("")
                out_lines.append(title)
            continue

        # Blockquote marker.
        line = re.sub(r"^\s*>\s?", "", raw)
        out_lines.append(_flatten_inline(line).rstrip())

    # Collapse 3+ blank lines down to a single blank line.
    text = "\n".join(out_lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def main(argv) -> int:
    if len(argv) not in (4, 5):
        sys.stderr.write(
            "usage: make_readme_txt.py <README.md> <version> <output_dir> [app_name]\n"
        )
        return 2
    md_path, version, out_dir = Path(argv[1]), argv[2], Path(argv[3])
    app_name = argv[4] if len(argv) == 5 else DEFAULT_APP_NAME
    if not md_path.is_file():
        sys.stderr.write(f"[ERROR] README not found: {md_path}\n")
        return 1
    out_dir.mkdir(parents=True, exist_ok=True)

    md = md_path.read_text(encoding="utf-8")
    body = flatten_markdown(md)
    title = f"{app_name} {version}"
    header = f"{title}\n{'=' * len(title)}\n\n"
    out_path = out_dir / f"{app_name} {version} README.txt"
    # CRLF so it renders correctly in native Windows Notepad.
    out_path.write_text(header + body, encoding="utf-8", newline="\r\n")
    print(f"  [OK] README.txt written: {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
