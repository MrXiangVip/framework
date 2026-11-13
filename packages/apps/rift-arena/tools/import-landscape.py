#!/usr/bin/env python3
"""Copy selected original landscape assets from the local complete forest library."""
import hashlib
import json
from pathlib import Path

APP = Path(__file__).resolve().parents[1]
LIBRARY = APP.parents[2] / 'external/extracted/meadow-forest/res/forest/complete'
BASE = 'Assets/Synty/PolygonNatureBiomes/PNB_Meadow_Forest/'
SELECTION = {
    'models/watchpillar.fbx': 'Models/SM_Prop_Stonewall_Pillar_01.fbx',
    'models/runestone.fbx': 'Models/SM_Prop_StoneRunes_01.fbx',
    'models/well.fbx': 'Models/SM_Prop_Well_01.fbx',
    'models/bridge.fbx': 'Models/SM_Prop_Bridge_01.fbx',
    'models/cliff.fbx': 'Models/SM_Env_Rock_Cliff_01.fbx',
    'models/rockpile.fbx': 'Models/SM_Env_Rock_Pile_01.fbx',
    'textures/ground-grass.png': 'Terrain/Grass_Texture_01.png',
    'textures/ground-dirt.png': 'Terrain/Dirt_Texture_01.png',
    'textures/ground-stone.png': 'Terrain/Footpath_Tiles_Texture_01.png',
    'textures/ground-gravel.png': 'Terrain/Gravel_Texture_01.png',
}

def main():
    catalog = {path: guid for guid, present, path in
               (line.rstrip('\n').split('\t', 2) for line in (LIBRARY / 'catalog.tsv').open()) if present == '1'}
    target = APP / 'res/arena'
    manifest_path = target / 'asset-manifest.json'
    manifest = json.loads(manifest_path.read_text())
    outputs = set(SELECTION)
    manifest['assets'] = [entry for entry in manifest['assets'] if entry['output'] not in outputs]
    for output, source in SELECTION.items():
        source = BASE + source
        data = (LIBRARY / catalog[source] / 'asset').read_bytes()
        destination = target / output
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        manifest['assets'].append(dict(source=source, output=output, sha256=hashlib.sha256(data).hexdigest()))
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    required = APP / 'required-apk-entries.txt'
    lines = required.read_text().splitlines()
    for output in SELECTION:
        entry = 'res/arena/' + output
        if entry not in lines:
            lines.append(entry)
    required.write_text('\n'.join(lines) + '\n')
    print('Imported', len(SELECTION), 'landscape assets')

if __name__ == '__main__':
    main()
