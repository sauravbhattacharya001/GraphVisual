#!/usr/bin/env python3
"""Inject canonical, robots, description, and Open Graph tags into every
docs/*.html page that doesn't already have them.

Idempotent: re-running on the same tree adds nothing.

Strategy:
  - Skip 404.html (intentionally minimal).
  - Read <title> for og:title / fallback description.
  - Read first <h1> + first sentence of first <p> for a richer description
    when no <meta name="description"> is present.
  - Insert tags immediately after the existing <link rel="stylesheet"> line
    (or, failing that, before </head>).
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent
SITE = "https://sauravbhattacharya001.github.io/GraphVisual/"
SKIP = {"404.html"}

TITLE_RE = re.compile(r"<title>(.*?)</title>", re.IGNORECASE | re.DOTALL)
H1_RE = re.compile(r"<h1[^>]*>(.*?)</h1>", re.IGNORECASE | re.DOTALL)
P_RE = re.compile(r"<p[^>]*>(.*?)</p>", re.IGNORECASE | re.DOTALL)
TAG_STRIP_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")
STYLE_LINK_RE = re.compile(
    r'(\s*)<link\s+rel="stylesheet"\s+href="styles\.css"\s*/?>\s*\n',
    re.IGNORECASE,
)
HEAD_END_RE = re.compile(r"\s*</head>", re.IGNORECASE)


def _clean(s: str) -> str:
    return WS_RE.sub(" ", TAG_STRIP_RE.sub("", s)).strip()


def _truncate(text: str, limit: int = 160) -> str:
    text = text.strip()
    if len(text) <= limit:
        return text
    cut = text[: limit - 1]
    sp = cut.rfind(" ")
    if sp > limit * 0.6:
        cut = cut[:sp]
    return cut.rstrip(",.;:- ") + "\u2026"


def _attr_escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace('"', "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def derive_meta(html: str, filename: str) -> tuple[str, str, str]:
    """Return (page_title, og_title, description)."""
    title_match = TITLE_RE.search(html)
    raw_title = _clean(title_match.group(1)) if title_match else filename
    # og:title strips the " - GraphVisual" / " - GraphVisual Docs" suffix.
    og_title = re.sub(r"\s*[\u2013\u2014\-]\s*GraphVisual.*$", "", raw_title).strip() or raw_title

    desc_match = re.search(
        r'<meta\s+name="description"\s+content="([^"]+)"', html, re.IGNORECASE
    )
    if desc_match:
        description = desc_match.group(1).strip()
    else:
        h1 = H1_RE.search(html)
        h1_text = _clean(h1.group(1)) if h1 else og_title
        p_text = ""
        for p in P_RE.finditer(html):
            candidate = _clean(p.group(1))
            if len(candidate) >= 40:
                p_text = candidate
                break
        if p_text:
            description = f"{h1_text}: {p_text}" if h1_text and h1_text not in p_text else p_text
        else:
            description = f"{h1_text} - GraphVisual documentation."
    # Strip leading emoji / symbols so descriptions read cleanly.
    description = re.sub(r"^[^\w]+", "", description).strip()
    return raw_title, og_title, _truncate(description)


def inject(html: str, filename: str) -> tuple[str, bool]:
    raw_title, og_title, description = derive_meta(html, filename)
    canonical_url = SITE + filename

    new_tags: list[str] = []
    if 'name="description"' not in html.lower():
        new_tags.append(
            f'    <meta name="description" content="{_attr_escape(description)}">'
        )
    if 'rel="canonical"' not in html.lower():
        new_tags.append(f'    <link rel="canonical" href="{canonical_url}">')
    if 'name="robots"' not in html.lower():
        new_tags.append('    <meta name="robots" content="index, follow">')
    if "og:title" not in html.lower():
        new_tags.append(
            f'    <meta property="og:title" content="{_attr_escape(og_title)}">'
        )
    if "og:description" not in html.lower():
        new_tags.append(
            f'    <meta property="og:description" content="{_attr_escape(description)}">'
        )
    if "og:type" not in html.lower():
        new_tags.append('    <meta property="og:type" content="website">')
    if "og:url" not in html.lower():
        new_tags.append(f'    <meta property="og:url" content="{canonical_url}">')
    if "og:site_name" not in html.lower():
        new_tags.append('    <meta property="og:site_name" content="GraphVisual">')
    if "twitter:card" not in html.lower():
        new_tags.append(
            '    <meta name="twitter:card" content="summary_large_image">'
        )
    if "twitter:title" not in html.lower():
        new_tags.append(
            f'    <meta name="twitter:title" content="{_attr_escape(og_title)}">'
        )
    if "twitter:description" not in html.lower():
        new_tags.append(
            f'    <meta name="twitter:description" content="{_attr_escape(description)}">'
        )

    if not new_tags:
        return html, False

    block = "\n".join(new_tags) + "\n"

    style_match = STYLE_LINK_RE.search(html)
    if style_match:
        insert_at = style_match.start()
        return html[:insert_at] + "\n" + block + html[insert_at:].lstrip("\n"), True

    head_end_match = HEAD_END_RE.search(html)
    if head_end_match:
        insert_at = head_end_match.start()
        return html[:insert_at] + "\n" + block + html[insert_at:].lstrip("\n"), True

    # No <head> closing tag found; bail out without modification.
    return html, False


def main() -> int:
    docs = DOCS
    if not docs.exists():
        print(f"docs dir not found at {docs}", file=sys.stderr)
        return 1

    updated: list[str] = []
    for path in sorted(docs.glob("*.html")):
        if path.name in SKIP:
            continue
        original = path.read_text(encoding="utf-8")
        new_html, changed = inject(original, path.name)
        if changed:
            path.write_text(new_html, encoding="utf-8", newline="\n")
            updated.append(path.name)

    print(f"Updated {len(updated)} page(s).")
    for name in updated:
        print(f"  - {name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
