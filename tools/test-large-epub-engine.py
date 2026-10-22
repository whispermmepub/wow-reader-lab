#!/usr/bin/env python3
"""Bounded host-Chromium stress harness for the exported reader engines.

This is deliberately a desktop/host test: it does not start Android, open an EPUB
ZIP in WebView, or measure a physical device.  Run the producer host unit tests
first so app/build/reader-engine-fixtures/page.js and scroll.js are current, then
pass --fixtures-current.  ``--baseline`` may be used to run against existing
fixtures while clearly marking the report as a baseline.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import tempfile
import time
import zipfile
from pathlib import Path
from typing import Callable

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_FIXTURES = ROOT / "app/build/reader-engine-fixtures"
BRIDGE = r"""(() => { window.readerEvents=[]; window.WoW=new Proxy({}, {get:(_,name)=>function(){ readerEvents.push({name:String(name),args:Array.from(arguments),at:performance.now()}); }}); return true; })()"""
TEXT = "Deterministic reader stress text with Burmese မင်္ဂလာပါ and typography. "
SVG = "<svg width='180' height='42' viewBox='0 0 180 42' aria-label='fixture'><rect width='178' height='40' fill='#ddd' stroke='#333'/><circle cx='21' cy='21' r='13' fill='#357'/><path d='M45 30 L70 8 L95 30' fill='none' stroke='#a24' stroke-width='4'/><text x='108' y='26' font-size='13'>fixture</text></svg>"


def chapter_html(target_bytes: int, variant: str) -> str:
    """Make deterministic HTML without retaining a large intermediate list."""
    prefix = "<!doctype html><html><head><meta charset='utf-8'><style>html,body{font-size:16px}body{line-height:1.8}img,svg{display:block;margin:8px 0;max-width:100%}.nest{border-left:1px solid #aaa;padding-left:5px}</style></head><body><h1>Stress fixture " + variant + "</h1>"
    suffix = "<p id='end'>END OF DETERMINISTIC FIXTURE</p></body></html>"
    out = [prefix]
    current = len(prefix) + len(suffix)
    i = 0
    while current < target_bytes:
        i += 1
        base = TEXT * (5 if variant != "media" else 2)
        if variant == "deep":
            inner = f"<div class='nest' data-depth='{i % 10}'>" + base + "</div>"
            for _ in range(7):
                inner = "<div class='nest'>" + inner + "</div>"
            piece = f"<section><p>{i}. {base}</p>{inner}</section>"
        elif variant == "media":
            piece = f"<figure>{SVG}<p>{i}. {base}</p></figure>"
        else:
            piece = f"<p>{i}. {base}<strong>bold</strong><em>italic</em></p>"
        out.append(piece)
        current += len(piece)
    out.append(suffix)
    return "".join(out)


def stream_epub_zip(path: Path, minimum_size: int, chapter_bytes: int = 1 << 20) -> int:
    """Create a valid stored EPUB using ZipFile.open and bounded chunks."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix="wow-epub-", delete=False) as tmp:
        chapter = Path(tmp.name)
    try:
        with chapter.open("wb") as f:
            block = ("ZIP_STORED synthetic EPUB content. " * 4096).encode()
            written = 0
            while written < chapter_bytes:
                n = min(len(block), chapter_bytes - written)
                f.write(block[:n])
                written += n
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as z:
            z.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
            z.writestr("META-INF/container.xml", '<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>')
            count = (minimum_size + chapter_bytes - 1) // chapter_bytes
            manifest = ''.join(f'<item id="c{i}" href="synthetic-{i:03d}.xhtml" media-type="application/xhtml+xml"/>' for i in range(1, count+1))
            spine = ''.join(f'<itemref idref="c{i}"/>' for i in range(1, count+1))
            z.writestr("OEBPS/content.opf", '<package version="2.0" unique-identifier="uid" xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">wow-stress</dc:identifier><dc:title>Host stress fixture</dc:title><dc:language>en</dc:language></metadata><manifest>'+manifest+'</manifest><spine>'+spine+'</spine></package>')
            remaining = minimum_size
            entry = 0
            while remaining > 0:
                entry += 1
                name = f"OEBPS/synthetic-{entry:03d}.xhtml"
                size = min(chapter_bytes, remaining)
                with z.open(name, "w", force_zip64=True) as dst, chapter.open("rb") as src:
                    dst.write(b'<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body><p>')
                    left = size
                    while left:
                        chunk = src.read(min(1024 * 1024, left))
                        if not chunk:
                            src.seek(0)
                            continue
                        dst.write(chunk)
                        left -= len(chunk)
                    dst.write(b'</p><p id="end">END OF CHAPTER</p></body></html>')
                remaining -= size
        return path.stat().st_size
    finally:
        chapter.unlink(missing_ok=True)


def wait_ready(page, mode: str, timeout: int = 10000) -> None:
    callback = "onPageReady" if mode == "page" else "onScrollReady"
    page.wait_for_function("name => readerEvents.some(e => e.name === name)", arg=callback, timeout=timeout)
    failures = page.evaluate("readerEvents.filter(e => e.name === 'pageEngineFailed')")
    if failures:
        raise AssertionError(failures)


def settle(page, timeout: int = 5000) -> None:
    page.wait_for_function("() => !window.__wowPageEngine.locked", timeout=timeout)


def exercise_page(page, script: str) -> dict:
    start = time.monotonic(); page.evaluate(script); wait_ready(page, "page")
    ready_ms = round((time.monotonic() - start) * 1000)
    st = page.evaluate("() => ({count:__wowPageEngine.count, locked:__wowPageEngine.locked, generation:__wowPageEngine.generation})")
    if not st["count"] or st["locked"]: raise AssertionError(st)
    turns = 0
    # Rapid bursts are intentionally issued without sleeps; every accepted turn is
    # followed by a bounded settle so a callback cannot leave the engine locked.
    for direction in (1, -1, 1, -1):
        for _ in range(3):
            result = page.evaluate("d => __wowPageEngine.turn(d)", direction)
            if result == "page": turns += 1
            settle(page)
    count = st["count"]
    # Sample a bounded sequential burst, then explicitly visit the final page.
    # Testing every page in a multi-thousand-page host fixture adds IPC time,
    # not coverage of Android memory or EPUB completeness.
    for _ in range(min(256, max(1, count + 1))):
        result = page.evaluate("__wowPageEngine.turn(1)")
        if result == "chapter":
            break
        settle(page)
    if result != "chapter":
        page.evaluate("__wowPageEngine.page=__wowPageEngine.count-1;__wowPageEngine.apply(false)")
        assert page.evaluate("__wowPageEngine.turn(1)") == "chapter"
    end_state = page.evaluate("() => ({page:__wowPageEngine.page,count:__wowPageEngine.count,locked:__wowPageEngine.locked,requests:readerEvents.filter(e=>e.name==='requestChapter').length,completes:readerEvents.filter(e=>e.name==='onPageTurnComplete').length})")
    if not end_state["locked"] is True:  # boundary request intentionally locks for host handoff
        raise AssertionError(end_state)
    page.evaluate("__wowPageEngine.locked=false")
    page.evaluate("__wowPageEngine.page=__wowPageEngine.count-1;__wowPageEngine.apply(false)")
    result = page.evaluate("__wowPageEngine.turn(-1)")
    settle(page)
    if result != "page" or page.evaluate("__wowPageEngine.locked"):
        raise AssertionError("backwards turn did not unlock")
    return {"ready_ms": ready_ms, "pages": count, "accepted_turns": turns, "final": page.evaluate("() => ({page:__wowPageEngine.page,locked:__wowPageEngine.locked})")}


def exercise_scroll(page, script: str) -> dict:
    start = time.monotonic(); page.evaluate(script); wait_ready(page, "scroll")
    ready_ms = round((time.monotonic() - start) * 1000)
    height = page.evaluate("Math.max(0,document.documentElement.scrollHeight-innerHeight)")
    for ratio in (0.0, 1.0, 0.1, 0.9, 0.0, 1.0):
        page.evaluate("r => scrollTo(0, Math.round(Math.max(0,document.documentElement.scrollHeight-innerHeight)*r))", ratio)
        page.wait_for_timeout(20)
    page.evaluate("scrollTo(0,document.documentElement.scrollHeight)")
    page.wait_for_timeout(130)
    page.evaluate("scrollTo(0,0)")
    page.wait_for_timeout(130)
    state = page.evaluate("() => ({scrollY, height:Math.max(0,document.documentElement.scrollHeight-innerHeight),locked:!!__wowPageEngine.locked, events:readerEvents.filter(e=>e.name==='onScroll').length})")
    if state["locked"] or state["events"] < 1: raise AssertionError(state)
    return {"ready_ms": ready_ms, "scroll_height": height, "final": state}


def run(args) -> dict:
    fixtures = args.fixtures
    if not fixtures.exists(): raise FileNotFoundError(fixtures)
    scripts = {m: (fixtures / f"{m}.js").read_text() for m in ("page", "scroll")}
    status = "baseline-existing-fixtures" if args.baseline else "fixtures-current-confirmed"
    cases = []
    with tempfile.TemporaryDirectory(prefix="wow-large-epub-") as td:
        temp = Path(td)
        zip_cases = []
        for minimum, label in ((12 << 20, "synthetic-zip-12MiB"), (32 << 20, "synthetic-zip-32MiB")):
            p = temp / f"{label}.epub"; started = time.monotonic(); size = stream_epub_zip(p, minimum)
            zip_cases.append({"case": label, "bytes": size, "elapsed_ms": round((time.monotonic()-started)*1000), "zip_stored": True, "opened_in_android": False})
        with sync_playwright() as pw:
            browser = pw.chromium.launch(executable_path=args.chromium, headless=True, args=["--no-sandbox"])
            context = browser.new_context(viewport={"width": 400, "height": 800})
            context.route("**/*", lambda route: route.abort())
            for name, target, variant in (("~1MiB", 1<<20, "normal"), ("~3MiB", 3<<20, "deep"), ("media-modest", 512<<10, "media")):
                html = chapter_html(target, variant)
                digest = hashlib.sha256(html.encode()).hexdigest()
                for mode in ("page", "scroll"):
                    page = context.new_page(); errors=[]; page.on("pageerror", lambda e: errors.append(str(e)))
                    page.set_content(html); page.evaluate(BRIDGE)
                    started=time.monotonic()
                    result = exercise_page(page, scripts[mode]) if mode == "page" else exercise_scroll(page, scripts[mode])
                    elapsed = round((time.monotonic()-started)*1000)
                    if errors: raise AssertionError(errors)
                    cases.append({"case": f"{name}-{mode}", "html_bytes": len(html.encode()), "sha256": digest, "elapsed_ms": elapsed, "pass": True, **result})
                    page.close()
            browser.close()
    return {"fixture_status": status, "physicalDeviceTested": False, "androidZipOpened": False, "zip_cases": zip_cases, "cases": cases, "limitations": ["host Chromium layout/callback stress only", "synthetic ZIP EPUBs are size/storage tests and are not opened on Android", "no book/file hard cap is introduced"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, default=DEFAULT_FIXTURES)
    parser.add_argument("--chromium", default="/usr/bin/chromium")
    parser.add_argument("--json", type=Path, help="write the JSON report here")
    parser.add_argument("--fixtures-current", action="store_true", help="confirm generated fixtures were freshly produced by host tests")
    parser.add_argument("--baseline", action="store_true", help="run existing fixtures and label output baseline")
    args = parser.parse_args()
    if not args.fixtures_current and not args.baseline:
        parser.error("refusing to run until generated fixtures are current; use --fixtures-current, or explicitly use --baseline")
    report = run(args)
    output = json.dumps(report, indent=2)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True); args.json.write_text(output + "\n")
    print(output)
