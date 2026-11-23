#!/usr/bin/env python3
"""Generate layout, id, bitmap drawable and string resources (not Android ARSC)."""
import argparse
import base64
from pathlib import Path
import re
import xml.etree.ElementTree as ET

# Java keywords cannot be used as generated field or package names.
KEYWORDS = set(('abstract assert boolean break byte case catch char class const continue '
                'default do double else enum extends final finally float for goto if '
                'implements import instanceof int interface long native new package private '
                'protected public return short static strictfp super switch synchronized this '
                'throw throws transient try void volatile while true false null _').split())


def identifier(value):
    return re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*', value) and value not in KEYWORDS


def generate(app, output):
    package = ET.parse(app / 'AndroidManifest.xml').getroot().get('package', '')
    if not package or not all(identifier(part) for part in package.split('.')):
        raise ValueError('Invalid manifest package: ' + package)
    layouts = sorted((app / 'res' / 'layout').glob('*.xml'))
    if len(layouts) > 65536:
        raise ValueError('Too many layout resources')
    fields, index = [], []
    declared, referenced, string_references = set(), set(), set()
    for number, path in enumerate(layouts):
        if not identifier(path.stem):
            raise ValueError('Invalid layout resource name: ' + path.name)
        tree = ET.parse(path)  # Fail the build for malformed layout XML.
        for element in tree.iter():
            for value in element.attrib.values():
                if value.startswith('@string/'):
                    string_references.add(value[len('@string/'):])
                if value.startswith(('@+id/', '@id/')):
                    name = value.split('/', 1)[1]
                    if not identifier(name):
                        raise ValueError('Invalid id resource name: ' + value)
                    (declared if value.startswith('@+id/') else referenced).add(name)
        resource_id = 0x7f010000 + number
        fields.append('        public static final int %s = 0x%08x;' % (path.stem, resource_id))
        index.append('0x%08x=%s' % (resource_id, path.relative_to(app).as_posix()))
    if referenced - declared:
        raise ValueError('Undeclared id resources: ' + ', '.join(sorted(referenced - declared)))
    if len(declared) > 65536:
        raise ValueError('Too many id resources')
    id_fields = []
    for number, name in enumerate(sorted(declared)):
        resource_id = 0x7f020000 + number
        id_fields.append('        public static final int %s = 0x%08x;' % (name, resource_id))
        index.append('id.%s=0x%08x' % (name, resource_id))
    drawables = sorted(path for path in (app / 'res' / 'drawable').glob('*')
                       if path.is_file() and path.suffix.lower() in
                       ('.png', '.jpg', '.jpeg', '.gif', '.bmp'))
    if len(drawables) > 65536:
        raise ValueError('Too many drawable resources')
    drawable_fields, drawable_names = [], set()
    for number, path in enumerate(drawables):
        if not identifier(path.stem):
            raise ValueError('Invalid drawable resource name: ' + path.name)
        if path.stem in drawable_names:
            raise ValueError('Duplicate drawable resource name: ' + path.stem)
        drawable_names.add(path.stem)
        resource_id = 0x7f030000 + number
        drawable_fields.append('        public static final int %s = 0x%08x;' % (path.stem, resource_id))
        index.append('0x%08x=%s' % (resource_id, path.relative_to(app).as_posix()))
    strings = {}
    for path in sorted((app / 'res' / 'values').glob('*.xml')):
        root = ET.parse(path).getroot()
        if root.tag != 'resources':
            raise ValueError('Expected resources root: ' + str(path))
        for element in root:
            if element.tag != 'string':
                continue
            name = element.get('name', '')
            if not identifier(name) or name in strings:
                raise ValueError('Invalid or duplicate string resource name: ' + name)
            if len(element):
                raise ValueError('Styled strings are not supported: ' + name)
            value = element.text or ''
            if value.startswith('@string/'):
                raise ValueError('String aliases are not supported: ' + name)
            # Preserve XML text whitespace; support common Android backslash escapes.
            def unescape(match):
                token = match.group(1)
                if token.startswith('u') and len(token) == 5:
                    return chr(int(token[1:], 16))
                replacements = {'n': '\n', 't': '\t', 'r': '\r', '\\': '\\',
                                '"': '"', "'": "'", '@': '@', '?': '?'}
                if token not in replacements:
                    raise ValueError('Unsupported string escape: ' + match.group(0))
                return replacements[token]
            strings[name] = re.sub(r'\\(u[0-9a-fA-F]{4}|[\s\S])', unescape, value)
    if len(strings) > 65536:
        raise ValueError('Too many string resources')
    if string_references - strings.keys():
        raise ValueError('Undeclared string resources: ' + ', '.join(sorted(string_references - strings.keys())))
    string_fields = []
    for number, name in enumerate(sorted(strings)):
        resource_id = 0x7f040000 + number
        value = strings[name]
        encoded = base64.b64encode(value.encode('utf-8')).decode('ascii')
        string_fields.append('        public static final int %s = 0x%08x;' % (name, resource_id))
        index.append('0x%08x=%s|%s' % (resource_id, name, encoded))
    java = output / 'generated' / Path(*package.split('.')) / 'R.java'
    java.parent.mkdir(parents=True, exist_ok=True)
    java.write_text('// Generated; do not edit.\npackage %s;\n\n' % package
                    + 'public final class R {\n    private R() {}\n'
                    + '    public static final class layout {\n'
                    + '\n'.join(fields) + '\n    }\n'
                    + '    public static final class id {\n'
                    + '\n'.join(id_fields) + '\n    }\n'
                    + '    public static final class drawable {\n'
                    + '\n'.join(drawable_fields) + '\n    }\n'
                    + '    public static final class string {\n'
                    + '\n'.join(string_fields) + '\n    }\n}\n', encoding='utf-8')
    table = output / 'classes' / 'META-INF' / 'framework' / 'resources.properties'
    table.parent.mkdir(parents=True, exist_ok=True)
    table.write_text('\n'.join(index) + '\n', encoding='ascii')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    generate(args.app, args.output)
