from pathlib import Path

p = Path('.github/scripts/apply_v58_fixes.py')
s = p.read_text(encoding='utf-8')
start = s.find('# Auto-scroll speed picker may be opened after Display Options dismisses.')
end = s.find('# Font picker may also be opened after the main sheet dismisses.', start)
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
s = s[:start] + replacement + s[end:]
exec(compile(s, 'apply_v58_fixes.py', 'exec'), {'__name__': '__main__'})
