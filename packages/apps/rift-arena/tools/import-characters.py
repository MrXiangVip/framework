#!/usr/bin/env python3
"""Import portable model, animation and texture assets from local Unity packages."""
import gzip
import hashlib
import io
import json
import tarfile
from pathlib import Path
import struct
import zlib

APP = Path(__file__).resolve().parents[1]
SOURCE = APP.parents[2] / 'external/unity/dwarves'
TARGET = APP / 'res/arena'
PACKS = {
    'dwarves': 'Toon RTS Units - Dwarves 1.0.unitypackage',
}
MODELS = {
    'DWF_SM_Heavy_Infantry_A.FBX': 'warrior',
    'DWF_SM_Archer_A.FBX': 'archer',
    'DWF_SM_Priest_A.FBX': 'mage',
    'DWF_SM_Light_Infantry_A.FBX': 'soldier',
}

def tga_png(data):
    """Decode true-color TGA (raw or RLE) without external dependencies."""
    ident, cmap, kind = data[:3]
    width, height = struct.unpack_from('<HH', data, 12)
    depth, flags = data[16:18]
    if cmap or kind not in (2, 10) or depth not in (24, 32):
        raise ValueError('Unsupported TGA format')
    step = depth // 8
    offset = 18 + ident
    pixels = []
    while len(pixels) < width * height:
        count, repeat = 1, False
        if kind == 10:
            packet = data[offset]
            offset += 1
            count, repeat = (packet & 127) + 1, bool(packet & 128)
        for i in range(1 if repeat else count):
            b, g, r = data[offset:offset+3]
            a = data[offset+3] if step == 4 else 255
            offset += step
            pixels.extend([bytes((r, g, b, a))] * (count if repeat else 1))
    rows = [b''.join(pixels[y*width:(y+1)*width]) for y in range(height)]
    if flags & 16:
        rows = [b''.join(row[x:x+4] for x in range(len(row)-4, -1, -4)) for row in rows]
    if not flags & 32:
        rows.reverse()
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b''.join(b'\0' + row for row in rows))) + chunk(b'IEND', b''))

def main():
    records = []
    for group, filename in PACKS.items():
        with tarfile.open(fileobj=io.BytesIO(gzip.decompress((SOURCE / filename).read_bytes())), mode='r:') as archive:
            members = {m.name: m for m in archive.getmembers()}
            for name, member in members.items():
                if not name.endswith('/pathname'):
                    continue
                original = archive.extractfile(member).read().decode().split('\n')[0].rstrip('\0')
                path = Path(original)
                if path.suffix.lower() not in ('.fbx', '.png', '.tga'):
                    continue
                root = 'Assets/Toon_RTS/Dwarves/'
                relative = path.relative_to(root)
                if '..' in relative.parts:
                    raise ValueError(original)
                output = APP.parents[2] / 'external/extracted/characters' / group / relative
                data = archive.extractfile(members[name.rsplit('/', 1)[0] + '/asset']).read()
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(data)
                records.append(dict(package=filename, source=original, output=str(output.relative_to(APP.parents[2])), sha256=hashlib.sha256(data).hexdigest()))
                if group == 'dwarves' and path.name in MODELS:
                    runtime = TARGET / 'models' / (MODELS[path.name] + '.fbx')
                    runtime.write_bytes(data)
                    records.append(dict(package=filename, source=original, output=str(runtime.relative_to(APP.parents[2])), sha256=hashlib.sha256(data).hexdigest()))
                if group == 'dwarves' and path.name in ('DWF_Units_Blue.tga', 'DWF_Units_Red.tga'):
                    runtime = TARGET / 'textures' / (path.stem + '.png')
                    runtime.write_bytes(tga_png(data))
                    records.append(dict(package=filename, source=original, output=str(runtime.relative_to(APP.parents[2])), sha256=hashlib.sha256(runtime.read_bytes()).hexdigest()))
    (APP.parents[2] / 'external/extracted/characters/manifest.json').write_text(json.dumps(records, ensure_ascii=False, indent=2) + '\n')
    print('Imported', len(records), 'assets; only four models and two textures are packaged at runtime.')

if __name__ == '__main__':
    main()
