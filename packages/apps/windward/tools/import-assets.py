#!/usr/bin/env python3
"""Select portable Meadow Forest/Fantasy assets; never modify the source packages."""
from pathlib import Path
import gzip
import hashlib
import json
import struct
import tarfile
import zlib

APP = Path(__file__).resolve().parents[1]
ROOT = APP.parents[2]
FOREST = 'POLYGON Meadow Forest - Nature Biomes - Low Poly 3D Art by Synty v1.10.3.unitypackage'
FANTASY = 'Anime Fantasy Pack 2 1.0.unitypackage'
BASE = 'Assets/Synty/PolygonNatureBiomes/PNB_Meadow_Forest/'
MODELS = {
    'tree': 'SM_Env_Tree_Meadow_01.fbx', 'birch': 'SM_Env_Tree_Birch_01.fbx',
    'rock': 'SM_Env_Rock_01.fbx', 'roundrock': 'SM_Env_Rock_Round_01.fbx',
    'grass': 'SM_Env_Grass_Med_Clump_01.fbx', 'flowers': 'SM_Env_Wildflowers_Patch_01.fbx',
    'mushrooms': 'SM_Prop_Mushroom_Group_03.fbx', 'tent': 'SM_Prop_Camp_Tent_01.fbx',
    'runestone': 'SM_Prop_StoneRunes_01.fbx',
}
TEXTURES = {
    'palette': 'Textures/PolygonNatureBiomes_Meadow_Texture_01.png',
    'leaves': 'Textures/Plants/leafPatch_04.tga', 'birch-leaves': 'Textures/Plants/leafPatch_01.tga',
    'branches': 'Textures/Plants/Branches_01.tga', 'grass-blades': 'Textures/Plants/Grass_Mid_01.tga',
    'flowers': 'Textures/Plants/WildFlowers_01.tga',
    'ground-grass': 'Terrain/Grass_Texture_01.png', 'ground-dirt': 'Terrain/Dirt_Texture_01.png',
}

def tga_png(data):
    ident, cmap, kind = data[:3]
    width, height = struct.unpack_from('<HH', data, 12)
    depth, flags = data[16:18]
    if cmap or kind not in (2, 10) or depth not in (24, 32):
        raise ValueError('Unsupported TGA format')
    step, offset = depth // 8, 18 + ident
    size = width * height * step
    if kind == 2:
        decoded = data[offset:offset+size]
    else:
        decoded = bytearray()
        while len(decoded) < size:
            packet = data[offset]; offset += 1
            count = (packet & 127) + 1
            if packet & 128:
                decoded.extend(data[offset:offset+step] * count); offset += step
            else:
                decoded.extend(data[offset:offset+step*count]); offset += step*count
    if len(decoded) != size:
        raise ValueError('Invalid TGA pixel count')
    rgba = bytearray(width * height * 4)
    rgba[0::4] = decoded[2::step]; rgba[1::4] = decoded[1::step]; rgba[2::4] = decoded[0::step]
    rgba[3::4] = decoded[3::step] if step == 4 else bytes([255]) * (width * height)
    rows = [bytes(rgba[y*width*4:(y+1)*width*4]) for y in range(height)]
    if flags & 16:
        rows = [b''.join(row[x:x+4] for x in range(len(row)-4, -1, -4)) for row in rows]
    if not flags & 32:
        rows.reverse()
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b''.join(b'\0'+r for r in rows))) + chunk(b'IEND', b'')

def main():
    selections = {BASE+'Models/'+v: 'models/'+k+'.fbx' for k,v in MODELS.items()}
    selections.update({BASE+v: 'textures/'+k+'.png' for k,v in TEXTURES.items()})
    guy = 'Assets/AnimeFantasyPack2/AnimeGuySwordsman/'
    fantasy = {guy+'Mesh/SK_Guy_Swordsman.fbx': 'models/traveller.fbx'}
    fantasy.update({guy+'Texture/'+name: 'textures/'+out+'.png' for name,out in {
        'T_Body_Albedo.tga':'traveller-body', 'T_Clothes_Albedo.tga':'traveller-clothes',
        'T_Hair_Albedo.tga':'traveller-hair',
    }.items()})
    records = []
    def save(package, source, output, data):
        converted = tga_png(data) if source.lower().endswith('.tga') else data
        target = APP/'res/world'/output
        target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(converted)
        records.append(dict(package=str(package.relative_to(ROOT)),source=source,output='res/world/'+output,
                            source_sha256=hashlib.sha256(data).hexdigest(),sha256=hashlib.sha256(converted).hexdigest()))

    # The supplied Meadow archive fails gzip CRC. Its existing extracted library has
    # per-file hashes; validate those instead of silently ignoring archive integrity.
    library = ROOT/'external/extracted/meadow-forest/res/forest/complete'
    catalog = {r[2]:r[0] for r in (line.split('\t',2) for line in (library/'catalog.tsv').read_text().splitlines()) if len(r)==3 and r[1]=='1'}
    hashes = {f['output']:f['sha256'] for f in json.loads((library/'manifest.json').read_text())['files']}
    for source, output in sorted(selections.items()):
        relative = catalog[source]+'/asset'
        data = (library/relative).read_bytes()
        if hashlib.sha256(data).hexdigest() != hashes[relative]:
            raise ValueError('Extracted forest asset checksum mismatch: '+source)
        save(ROOT/'external/unity/synty'/FOREST,source,output,data)

    package = ROOT/'external/unity/anime-fantasy'/FANTASY
    # Two sequential passes avoid repeated seeks through the large compressed archive.
    selected = {}
    with gzip.open(package, 'rb') as stream, tarfile.open(fileobj=stream, mode='r|') as archive:
        for member in archive:
            if not member.name.endswith('/pathname'): continue
            source = archive.extractfile(member).read().decode('utf-8').split('\n')[0].rstrip('\0\r')
            if source in fantasy:
                selected[member.name.rsplit('/',1)[0]+'/asset'] = source
    missing = set(fantasy)-set(selected.values())
    if missing: raise ValueError('Missing Fantasy assets: '+repr(sorted(missing)))
    found = set()
    with gzip.open(package, 'rb') as stream, tarfile.open(fileobj=stream, mode='r|') as archive:
        for member in archive:
            source = selected.get(member.name)
            if source is None: continue
            save(package,source,fantasy[source],archive.extractfile(member).read())
            found.add(source)
    if found != set(fantasy): raise ValueError('Missing selected Fantasy data')
    manifest = APP/'res/world/asset-manifest.json'
    manifest.write_text(json.dumps({'forest_input':'external/extracted/meadow-forest/res/forest/complete', 'assets':sorted(records,key=lambda r:r['output'])},ensure_ascii=False,indent=2)+'\n')
    required = APP/'required-apk-entries.txt'
    lines = [s for s in required.read_text().splitlines() if not s.startswith('res/world/')]
    lines += ['res/world/asset-manifest.json'] + sorted(r['output'] for r in records)
    required.write_text('\n'.join(dict.fromkeys(lines))+'\n')
    print('Imported',len(records),'assets from two original packages;',sum((APP/r['output']).stat().st_size for r in records),'runtime bytes')

if __name__ == '__main__': main()
