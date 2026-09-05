from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
for p in root.rglob('*.xml'): ET.parse(p)
print('XML:',len(list(root.rglob('*.xml'))),'parsed')
# Lexical delimiter check, intentionally not described as Kotlin compilation.
def check(p):
 s=p.read_text(); stack=[]; i=0; n=len(s)
 while i<n:
  if s.startswith('//',i):
   k=s.find('\n',i);i=n if k<0 else k;continue
  if s.startswith('/*',i):
   depth=1;i+=2
   while i<n and depth:
    if s.startswith('/*',i):depth+=1;i+=2
    elif s.startswith('*/',i):depth-=1;i+=2
    else:i+=1
   assert depth==0,(p,'comment');continue
  if s.startswith('"""',i):
   k=s.find('"""',i+3);assert k>=0,(p,'raw string');i=k+3;continue
  if s[i] in ['"',"'"]:
   q=s[i];i+=1
   while i<n:
    if s[i]=='\\':i+=2;continue
    if s[i]==q:break
    i+=1
   assert i<n,(p,'string');i+=1;continue
  if s[i] in '([{':stack.append((s[i],s.count('\n',0,i)+1))
  elif s[i] in ')]}':
   assert stack and stack[-1][0]=='([{'[')]}'.index(s[i])],(p,'delimiter',s.count('\n',0,i)+1,stack[-1:] )
   stack.pop()
  i+=1
 assert not stack,(p,stack[-5:])
for p in list(root.rglob('*.kt'))+list(root.rglob('*.kts')):check(p)
print('Kotlin/KTS lexical delimiter check:',len(list(root.rglob('*.kt')))+len(list(root.rglob('*.kts'))),'files')
for p in root.rglob('*.kt'):
 imports=re.findall(r'^import (.*)$',p.read_text(), re.M)
 assert len(imports)==len(set(imports)),(p,'duplicate imports')
print('No duplicate explicit imports')
assert 'versionCode = 6' in (root/'app/build.gradle.kts').read_text()
assert 'versionName = "0.6.0"' in (root/'app/build.gradle.kts').read_text()
print('Version 0.6.0 / 6 verified')
