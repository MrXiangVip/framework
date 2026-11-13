#!/usr/bin/env python3
"""Package only the existing Dwarves clips used by the runtime."""
from pathlib import Path
import hashlib, json
APP=Path(__file__).resolve().parents[1]
GROUPS={'infantry':('Infantry','infantry'),'archer':('Archer','archer'),'mage':('Mage','mage')}
ACTIONS={'idle':'01_idle','run':'03_run','attack':'07_attack_A','hurt':'09_take_damage','death':'10_death_A'}
def main():
    root=APP/'res/arena';entries=[]
    for group,(folder,prefix) in GROUPS.items():
        actions=dict(ACTIONS)
        if group=='archer':actions['retreat']='06_combat_walk'
        if group=='infantry':
            actions['charge']='04_charge'
            actions['attack2']='08_attack_B'
            actions['combat-idle']='05_combat_idle'
            actions['combat-walk']='06_combat_walk'
        for action,suffix in actions.items():
            if group=='archer' and action=='attack':suffix='07_attack'
            source=APP.parents[2]/'external/extracted/characters/dwarves/animation'/folder/('DWF_'+prefix+'_'+suffix+'.FBX')
            output='animations/'+group+'-'+action+'.fbx'
            data=source.read_bytes();target=root/output;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            entries.append(dict(source=str(source.relative_to(APP.parents[2])),output=output,sha256=hashlib.sha256(data).hexdigest()))
    (root/'animations/manifest.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n')
    p=APP/'required-apk-entries.txt';lines=p.read_text().splitlines()
    for name in [e['output'] for e in entries]+['animations/manifest.json']:
        name='res/arena/'+name
        if name not in lines:lines.append(name)
    p.write_text('\n'.join(lines)+'\n');print('Imported',len(entries),'animation clips')
if __name__=='__main__':main()
