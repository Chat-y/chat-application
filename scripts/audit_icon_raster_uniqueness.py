import json,xml.etree.ElementTree as ET,collections,hashlib,io
from pathlib import Path
import cairosvg
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable';NS='{http://schemas.android.com/apk/res/android}';maps=json.load(open(Path(__file__).resolve().parents[1]/'docs/audit/row65-effective-resource-maps.json'));cache={}
def svg(res):
 root=ET.parse(ROOT/(res+'.xml')).getroot()
 def node(e):
  typ=e.tag.rsplit('}',1)[-1];a={k.removeprefix(NS):v for k,v in e.attrib.items()}
  if typ=='path':
   def paint(kind):
    v=a.get(kind+'Color','#00000000');alpha=(int(v[1:3],16)/255 if v.startswith('#') and len(v)==9 else 1)*float(a.get(kind+'Alpha','1'));return ('none',1) if alpha==0 else ('#1b2733',alpha)
   f,fa=paint('fill');s,sa=paint('stroke');return '<path d="'+a['pathData']+'" fill="'+f+'" fill-opacity="'+str(fa)+'" stroke="'+s+'" stroke-opacity="'+str(sa)+'" stroke-width="'+a.get('strokeWidth','0')+'" stroke-linecap="'+a.get('strokeLineCap','butt')+'" stroke-linejoin="'+a.get('strokeLineJoin','miter')+'" fill-rule="'+('evenodd' if a.get('fillType')=='evenOdd' else 'nonzero')+'"/>'
  if typ=='group':
   tr='translate('+a.get('translateX','0')+' '+a.get('translateY','0')+') translate('+a.get('pivotX','0')+' '+a.get('pivotY','0')+') rotate('+a.get('rotation','0')+') scale('+a.get('scaleX','1')+' '+a.get('scaleY','1')+') translate('+str(-float(a.get('pivotX','0')))+' '+str(-float(a.get('pivotY','0')))+')';return '<g transform="'+tr+'">'+''.join(node(c) for c in e)+'</g>'
  return ''.join(node(c) for c in e)
 return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 '+root.get(NS+'viewportWidth','24')+' '+root.get(NS+'viewportHeight','24')+'">'+node(root)+'</svg>'
result={}
for theme,roles in maps.items():
 groups=collections.defaultdict(list)
 for id,res in roles.items():
  if res not in cache:
   raw=cairosvg.svg2png(bytestring=svg(res).encode(),output_width=48,output_height=48);im=Image.open(io.BytesIO(raw)).convert('RGBA');cache[res]=hashlib.sha256(im.tobytes()).hexdigest()
  groups[cache[res]].append(id)
 result[theme]={'mapped_ids':len(roles),'identical_48px_groups':[v for v in groups.values() if len(v)>1]};print(theme,result[theme]['identical_48px_groups'])
(Path(__file__).resolve().parents[1]/'docs/audit/row65-raster-source-audit.json').write_text(json.dumps(result,indent=2)+'\n')
