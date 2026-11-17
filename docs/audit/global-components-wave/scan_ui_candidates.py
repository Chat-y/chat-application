def kotlin_mask(s,strings=True):
 out=list(s);i=0;n=len(s)
 def blank(a,b):
  for k in range(a,b):
   if s[k]!='\n':out[k]=' '
 while i<n:
  a=i
  if s.startswith('//',i):
   j=s.find('\n',i);i=n if j<0 else j;blank(a,i)
  elif s.startswith('/*',i):
   depth=1;i+=2
   while i<n and depth:
    if s.startswith('/*',i):depth+=1;i+=2
    elif s.startswith('*/',i):depth-=1;i+=2
    else:i+=1
   blank(a,i)
  elif s.startswith('"""',i):
   j=s.find('"""',i+3);i=n if j<0 else j+3
   if strings:blank(a,i)
  elif s[i] in '\"\'':
   q=s[i];i+=1
   while i<n:
    if s[i]=='\\':i+=2
    elif s[i]==q:i+=1;break
    else:i+=1
   if strings:blank(a,min(i,n))
  else:i+=1
 return ''.join(out)

from pathlib import Path
import re,csv,io
import sys
root=Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve();ui=root/'app/src/main/kotlin/com/example/chat/ui';out=root/'docs/audit/global-components-wave';out.mkdir(parents=True,exist_ok=True)
strip_comments=lambda s:kotlin_mask(s,False)
sources={p:strip_comments(p.read_text()) for p in ui.rglob('*.kt')}
def tsv(name,head,rows):
 with (out/name).open('w', newline='') as f:
  w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(head);w.writerows(rows)
components=[]
for p,s in sources.items():
 if p.parent!=ui/'kit' and not p.is_relative_to(ui/'core/components'):continue
 for m in re.finditer(r'@Composable\s+(?:(?:private|internal|public)\s+)?fun\s+(?:[\w<>?]+\.)?(\w+)\s*\(',s):
  symbol=m[1];line=s.count('\n',0,m.start())+1
  consumers=[str(q.relative_to(root)) for q,t in sources.items() if q!=p and re.search(r'\b'+re.escape(symbol)+r'\s*\(',t)]
  components.append((str(p.relative_to(root)),line,symbol,'lexical only; includes private helpers and overloads',len(consumers),' | '.join(sorted(consumers)),'Pending real app images'))
tsv('shared-composables.tsv',['source','line','symbol','scope','consumer_file_count','consumer_files','image_status'],components)
controls=[];aliases=[];shadows=[];colors=[]
for p,s in sources.items():
 rel=str(p.relative_to(root))
 for m in re.finditer(r'\b(Button|TextButton|OutlinedButton|FilledTonalButton|ElevatedButton|OutlinedTextField|TextField|Card|ElevatedCard|OutlinedCard)\s*\(',s):
  alias=re.search(r'^import (com\.example\.chat\.ui\.kit\.\w+) as '+re.escape(m[1])+r'\s*$',s,re.M)
  qualified=m.start()>0 and s[m.start()-1]=='.'
  if alias and not qualified:
   aliases.append((rel,s.count('\n',0,m.start())+1,m[1],alias[1],'already shared alias, not a stock-control defect'))
  else:
   controls.append((rel,s.count('\n',0,m.start())+1,m[1],'lexical candidate; verify resolver and intentional exception before migration'))
 for m in re.finditer(r'\.shadow\s*\(',s):
  start=m.end();depth=1;i=start
  while i<len(s) and depth:
   depth += (s[i]=='(')-(s[i]==')');i+=1
  args=s[start:i-1]
  if 'ambientColor' not in args or 'spotColor' not in args:shadows.append((rel,s.count('\n',0,m.start())+1,'default/incomplete explicit shadow colors',' '.join(args.split())))
 for m in re.finditer(r'\bColor\(\s*0x[0-9A-Fa-f]+[Ll]?\s*\)|\bColor\.(?:Black|White)\b',s):
  colors.append((rel,s.count('\n',0,m.start())+1,m[0],'literal candidate; token definitions, mask colors and previews need individual classification'))
tsv('already-shared-aliases.tsv',['source','line','call','binding','status'],aliases)
tsv('remaining-stock-controls.tsv',['source','line','call','status'],controls)
tsv('remaining-default-shadows.tsv',['source','line','status','arguments'],shadows)
tsv('remaining-color-candidates.tsv',['source','line','expression','status'],colors)
print('components',len(components),'files',len({x[0] for x in components}),'stock calls',len(controls),'stock files',len({x[0] for x in controls}),'default shadows',len(shadows),'color candidates',len(colors))
