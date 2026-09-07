from pathlib import Path

p = Path('.github/scripts/run_v58_fixes2.py')
runner = p.read_text(encoding='utf-8')
base = Path('.github/scripts/apply_v58_fixes.py').read_text(encoding='utf-8')

# Apply the same direct auto-scroll patch rewrite performed by run_v58_fixes2.py.
start = base.find('# Auto-scroll speed picker may be opened after Display Options dismisses.')
end = base.find('# Font picker may also be opened after the main sheet dismisses.', start)
if start < 0 or end < 0:
    raise SystemExit('auto-scroll patch section markers missing')
replacement = r'''# Auto-scroll speed picker may be opened after Display Options dismisses.
s = rep(s,
''' + "'''" + r'''    private void showAutoScrollSpeedDialog() {
        LinearLayout box = new LinearLayout(this);''' + "'''" + r''',
''' + "'''" + r'''    private void showAutoScrollSpeedDialog() {
        beginAutoScrollInteraction();
        LinearLayout box = new LinearLayout(this);''' + "'''" + r''', 'speed dialog lock')
s = rep(s,
''' + "'''" + r'''        new AlertDialog.Builder(this)
                .setTitle("Auto scroll speed")''' + "'''" + r''',
''' + "'''" + r'''        AlertDialog speedDialog = new AlertDialog.Builder(this)
                .setTitle("Auto scroll speed")''' + "'''" + r''', 'speed dialog var')
s = rep(s,
''' + "'''" + r'''                .setPositiveButton("Done", (d, w) -> updateAutoScrollState())
                .show();''' + "'''" + r''',
''' + "'''" + r'''                .setPositiveButton("Done", (d, w) -> updateAutoScrollState())
                .create();
        speedDialog.setOnDismissListener(d -> endAutoScrollInteraction());
        speedDialog.show();''' + "'''" + r''', 'speed dialog unlock')

'''
base = base[:start] + replacement + base[end:]

# The second scroll CSS occurrence is split differently from the preload CSS.
# Replace its core expression instead of depending on the surrounding Java concatenation.
old_block = '''s = rep(s,\n        '\"padding:5vh \" + safeMargin + \"vw 12vh \" + safeMargin + \"vw !important;\" +',\n        '\"padding:5vh \" + adaptiveMargin + \"px 12vh \" + adaptiveMargin + \"px !important;\" +',\n        'reader scroll margin')\n'''
new_block = '''remaining_margin = 'safeMargin + \"vw 12vh \" + safeMargin + \"vw'\nif remaining_margin not in s:\n    raise SystemExit('remaining reader scroll margin core not found')\ns = s.replace(remaining_margin, 'adaptiveMargin + \"px 12vh \" + adaptiveMargin + \"px', 1)\n'''
if old_block not in base:
    raise SystemExit('reader scroll patch block not found')
base = base.replace(old_block, new_block, 1)

exec(compile(base, 'apply_v58_fixes.py', 'exec'), {'__name__': '__main__'})
