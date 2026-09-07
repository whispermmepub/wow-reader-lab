from pathlib import Path

p = Path('app/src/main/java/com/whisper/wowreader/MyanmarSpellingStore.java')
s = p.read_text(encoding='utf-8')
old = '''    private static int distance(String a, String b, int cap) {
        int[] x = a.codePoints().toArray();
        int[] y = b.codePoints().toArray();'''
new = '''    private static int distance(String a, String b, int cap) {
        int[] x = toCodePoints(a);
        int[] y = toCodePoints(b);'''
if old not in s:
    raise SystemExit('missing Myanmar spelling distance marker')
s = s.replace(old, new, 1)
marker = '''    private static String normalize(String s) {'''
helper = '''    private static int[] toCodePoints(String value) {
        if (value == null || value.isEmpty()) return new int[0];
        int count = value.codePointCount(0, value.length());
        int[] out = new int[count];
        int at = 0;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            out[at++] = cp;
            i += Character.charCount(cp);
        }
        return out;
    }

''' + marker
if marker not in s:
    raise SystemExit('missing Myanmar spelling normalize marker')
s = s.replace(marker, helper, 1)
p.write_text(s, encoding='utf-8')
print('Myanmar spelling API 23 patch applied')
