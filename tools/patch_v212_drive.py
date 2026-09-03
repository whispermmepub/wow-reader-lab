from pathlib import Path
p = Path('app/src/main/java/com/whisper/wowreader/GoogleDriveSync.java')
s = p.read_text(encoding='utf-8')
old = '''        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media&fields=id", "PATCH", token);
        c.setRequestProperty("Content-Type", "application/zip");
'''
new = '''        HttpURLConnection c = open("https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media&fields=id", "POST", token);
        c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        c.setRequestProperty("Content-Type", "application/zip");
'''
if old not in s:
    raise SystemExit('Drive update anchor missing')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
print('Patched Drive update method override')
