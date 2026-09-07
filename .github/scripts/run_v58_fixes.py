from pathlib import Path

p = Path('.github/scripts/apply_v58_fixes.py')
s = p.read_text(encoding='utf-8')
old = "a, b, block = method_block(s, '    private void showAutoScrollSpeedDialog() {', '    private int autoScrollPixelsPerSecond() {')"
new = "a, b, block = method_block(s, '    private void showAutoScrollSpeedDialog() {', '    private int readerPanelBase() {')"
if old not in s:
    raise SystemExit('v58 auto-scroll patch marker not found')
s = s.replace(old, new, 1)
exec(compile(s, 'apply_v58_fixes.py', 'exec'), {'__name__': '__main__'})
