"""Recreate exact signed APK bytes from CI artifacts; contains no private signing material."""
import base64, hashlib, json
from pathlib import Path
import bsdiff4
root = Path(__file__).parent
manifest = json.loads((root / 'manifest.json').read_text())
Path('signed-verification').mkdir(exist_ok=True)
for item in manifest:
    source = Path(item['source']).read_bytes()
    assert hashlib.sha256(source).hexdigest() == item['source_sha256'], 'Unexpected source artifact'
    signed = bsdiff4.patch(source, base64.b64decode((root / item['patch']).read_bytes()))
    assert hashlib.sha256(signed).hexdigest() == item['signed_sha256'], 'Signed APK bytes differ'
    Path('signed-verification', item['name'] + '.apk').write_bytes(signed)
    print(item['name'], item['signed_sha256'])
