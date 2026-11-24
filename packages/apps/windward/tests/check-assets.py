#!/usr/bin/env python3
"""Verify provenance records against exactly the bytes shipped in the application."""
import hashlib
import json
from pathlib import Path
import zipfile
ROOT = Path(__file__).resolve().parents[4]
with zipfile.ZipFile(ROOT/'out/system/app/windward/windward.apk') as apk:
    manifest = json.loads(apk.read('res/world/asset-manifest.json'))
    packages = set()
    for record in manifest['assets']:
        assert hashlib.sha256(apk.read(record['output'])).hexdigest() == record['sha256'], record['output']
        packages.add(record['package'])
    assert len(packages) == 2
    actual = {p for p in apk.namelist() if p.startswith('res/world/') and not p.endswith('/') and not p.endswith('asset-manifest.json')}
    assert actual == {r['output'] for r in manifest['assets']}, 'Untracked or missing runtime assets'
print('PASS:', len(manifest['assets']), 'APK assets match SHA-256 provenance from two source packages')
