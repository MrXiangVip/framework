#!/usr/bin/env python3
"""Copy the existing Dwarves sword, preserving source and checksum."""
from pathlib import Path
import hashlib,json
app=Path(__file__).resolve().parents[1]
source=app.parents[2]/'external/extracted/characters/dwarves/models/extra models/Equipment/DWF_Weapon_sword_A.FBX'
output=app/'res/arena/models/sword.fbx'
output.write_bytes(source.read_bytes())
(app/'res/arena/models/sword-manifest.json').write_text(json.dumps(dict(source=str(source.relative_to(app.parents[2])),output=str(output.relative_to(app)),sha256=hashlib.sha256(output.read_bytes()).hexdigest()),indent=2)+'\n')
p=app/'required-apk-entries.txt';lines=p.read_text().splitlines()
for name in ['res/arena/models/sword.fbx','res/arena/models/sword-manifest.json']:
    if name not in lines:lines.append(name)
p.write_text('\n'.join(lines)+'\n')
