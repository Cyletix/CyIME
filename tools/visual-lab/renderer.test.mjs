import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {validate,iconSvg,sceneSvg,optics,kotlinTokens,styles,levels,themePalette,toolbarMarkSvg,androidVector,launcherBareVector} from './renderer.mjs';
import {compile} from './compile.mjs';
const c=JSON.parse(await readFile(new URL('./design.json',import.meta.url),'utf8'));
const resources=JSON.parse(await readFile(new URL('./android-resources.json',import.meta.url),'utf8'));
const toolbarVector=await readFile(new URL('../../app/src/main/res/drawable/cyime_toolbar_mark.xml',import.meta.url),'utf8');
const hash=value=>createHash('sha256').update(JSON.stringify(value)).digest('hex');
const iconPaletteHashes={
 neon:'da94ee7a9cb3b34fdd059289d9b997a6b12269f518ed7ddae183dce6e3682b8b',
 glass:'7ad67fade389f640e0c6ea14269cced8d1bca821b6e40ebaa5ce49254f72baaa',
 facet:'589be382eb36a5852dbb391ade7ca9d25006f4b9fc74588eaaf1e0e58823c599',
 frost:'e9c3733d5e1bee91b35b1261b95cd47fb4761300948dc992f4c24a6be108eab2'
};
test('Android outputs match current shared source',async()=>{await compile(c,true)});
test('launcher resources keep upgrade aliases and select framed or trimmed artwork',async()=>{
 const manifest=await readFile(new URL('../../app/src/main/AndroidManifest.xml',import.meta.url),'utf8');
 const aliases=[...manifest.matchAll(/<activity-alias\b[\s\S]*?<\/activity-alias>/g)].map(m=>m[0]);
 assert.equal(aliases.length,10);
 assert.equal(aliases.filter(s=>s.includes('android:enabled="true"')).length,1);
 for(const style of ['original',...styles]) for(const bare of [false,true]) {
  const name='com.kingzcheung.xime.launcher.Icon'+style[0].toUpperCase()+style.slice(1)+(bare?'Bare':'');
  const alias=aliases.find(s=>s.includes(`android:name="${name}"`));
  assert.ok(alias,name);
  const asset=style==='original'?'facet':style;
  assert.ok(alias.includes(bare?`@drawable/cyime_launcher_bare_${asset}`:`@mipmap/cyime_launcher_${asset}`));
  assert.ok(alias.includes('android:targetActivity="com.kingzcheung.xime.MainActivity"'));
  if(!bare) {
   const xml=await readFile(new URL(`../../app/src/main/res/mipmap-anydpi-v26/cyime_launcher_${asset}.xml`,import.meta.url),'utf8');
   assert.match(xml,/<adaptive-icon/);
   assert.ok(xml.includes(`@drawable/cyime_mark_${asset}`));
   assert.match(xml,/<background android:drawable="@drawable\/cyime_launcher_background_(dark|light)"/);
  }
 }
});
test('bare launcher trims margins while preserving artwork and keyboard canvas',()=>{
 for(const style of styles) {
  const original=androidVector(c,style), bare=launcherBareVector(c,style);
  assert.match(original,/viewportWidth="1024"/);
  assert.match(bare,/viewportWidth="672"/);
  assert.match(bare,/translateX="-176" android:translateY="-160"/);
  assert.deepEqual([...bare.matchAll(/<path[\s\S]*?<\/path>/g)].map(m=>m[0]),
    [...original.matchAll(/<path[\s\S]*?<\/path>/g)].map(m=>m[0]));
 }
});
test('four distinct material recipes scale by level without regaining an inner rim',()=>{
 validate(c);
 const fields=['shade','tint','top','topReach','border','depth','facet','matte','glow'];
 assert.deepEqual(Object.keys(c.styles.neon.material),fields);
 assert.equal(new Set(styles.map(style=>JSON.stringify(c.styles[style].material))).size,4);
 for(const style of styles)for(const level of levels){
  const p=optics(c,style,level), m=c.styles[style].material;
  assert.equal(p.topReach,m.topReach);
  for(const field of fields.filter(field=>field!=='topReach'&&field!=='glow'))assert.ok(Math.abs(p[field]-m[field]*c.levels[level])<.000001,`${style} ${level} ${field}`);
  assert.equal(p.glow,style==='neon'&&level==='FLOATING'?Number((m.glow*c.levels[level]).toFixed(5)):0);
  assert.equal('innerRim' in p,false);
  assert.ok(p.border<=.45&&p.top<=.25);
 }
 assert.ok(c.styles.neon.material.shade>c.styles.glass.material.shade);
 assert.ok(c.styles.glass.material.top>c.styles.facet.material.top);
 assert.ok(c.styles.facet.material.facet>0);
 assert.ok(c.styles.frost.material.matte>0);
 const missing=structuredClone(c);delete missing.styles.glass.material.topReach;
 assert.throws(()=>validate(missing),/material/);
 const extraGlow=structuredClone(c);extraGlow.styles.frost.material.glow=.1;
 assert.throws(()=>validate(extraGlow),/霓光/);
});
test('launcher icon keeps seven reference segments and every calibrated palette',()=>{
 assert.equal(hash(c.icon.segments),'31ef7f7680a359273e503f1bd89509e04465a7947eac398d625f4f1d830857e4');
 for(const style of styles){
  assert.equal(hash(c.styles[style].icon),iconPaletteHashes[style]);
  const svg=iconSvg(c,style);
  assert.equal((svg.match(/<path /g)||[]).length,7);
  assert.equal((svg.match(/<linearGradient /g)||[]).length,7);
  assert.doesNotMatch(svg,/<rect|<image|stroke=|filter=|transform=/);
 }
});
test('keyboard uses the single-color 4-V vector at 22 px in its 32 px area',()=>{
 const pathData=toolbarVector.match(/android:pathData="([^"]+)"/)?.[1];
 assert.ok(pathData);
 const mark=toolbarMarkSvg('#42639C');
 assert.ok(mark.includes(`d="${pathData}"`));
 assert.match(mark,/x="18" y="17" width="22" height="22" viewBox="0 0 24 24"/);
 assert.equal((mark.match(/<path /g)||[]).length,1);
 assert.doesNotMatch(mark,/linearGradient|segment-|fill="url/);
 for(const mode of ['light','dark']){
  const t=themePalette(resources,'soft_blue',mode,'keyboard');
  const scene=sceneSvg(c,'facet','keyboard',mode,resources,'soft_blue');
  assert.ok(scene.includes(toolbarMarkSvg(t.toolbarInk)));
  assert.doesNotMatch(scene,/segment-0/);
 }
 assert.ok(sceneSvg(c,'facet','icon','dark',resources,'soft_blue').includes(c.styles.facet.icon.purple));
});
test('material SVG layers distinguish all four styles without an inner rim',()=>{
 const scenes=Object.fromEntries(styles.map(style=>[style,sceneSvg(c,style,'keyboard','dark',resources,'soft_blue')]));
 assert.equal(new Set(Object.values(scenes)).size,4);
 assert.match(scenes.neon,/fill="#000000" fill-opacity="0\.11"/);
 assert.match(scenes.neon,/stroke="#B4CAFA" stroke-opacity="0\.36"/);
 assert.match(scenes.glass,/id="h\d+"/);
 assert.match(scenes.facet,/M 358\.24 4 L 436 4 L 436 17\.44 Z/);
 assert.match(scenes.frost,/fill="#FFFFFF" fill-opacity="0\.12"/);
 for(const scene of Object.values(scenes))assert.doesNotMatch(scene,/innerRim/);
 const original=sceneSvg(c,'original','keyboard','dark',resources,'soft_blue');
 assert.doesNotMatch(original,/clipPath id="c|linearGradient id="h|linearGradient id="d/);
 assert.throws(()=>sceneSvg(c,'original','icon','dark',resources,'soft_blue'),/只预览/);
});
test('JSON property order cannot alter Android parameter mapping',()=>{
 const reverse=structuredClone(c);
 reverse.levels=Object.fromEntries(Object.entries(c.levels).reverse());
 for(const style of styles)reverse.styles[style].material=Object.fromEntries(Object.entries(c.styles[style].material).reverse());
 assert.equal(kotlinTokens(c),kotlinTokens(reverse));
 assert.doesNotMatch(kotlinTokens(c),/surfaceGain|lightGain|innerRim|highlight/);
});
test('twenty comparison scenes and original surfaces render with valid dimensions',()=>{
 for(const style of [...styles,'original'])for(const [scene,mode] of style==='original'?[['keyboard','dark'],['keyboard','light'],['settings','dark'],['settings','light']]:[['icon','dark'],['keyboard','dark'],['keyboard','light'],['settings','dark'],['settings','light']]){
  const svg=sceneSvg(c,style,scene,mode,resources,'soft_blue');
  assert.match(svg,/viewBox="0 0 \d+ \d+"/);
  assert.doesNotMatch(svg,/NaN|undefined/);
 }
});

test('all preview themes use Android palette and original function icons',()=>{
 for(const t of resources.themes)for(const mode of ['light','dark'])for(const style of [...styles,'original']){
  const svg=sceneSvg(c,style,'keyboard',mode,resources,t.id), p=themePalette(resources,t.id,mode,'keyboard');
  for(const color of [p.bg,p.key,p.fn,p.ink,p.fnInk,p.toolbarInk])assert.ok(svg.includes(color),`${t.id} ${color}`);
  for(const id of ['schema','emoji','edit','clipboard','handwriting_lookup','voice','hide','delete','reset','language','enter']){
   assert.ok(svg.includes(`data-icon="${id}"`));assert.ok(svg.includes(resources.icons[id]));
  }
  assert.doesNotMatch(svg,/[⌨☻✥▤〰♩⌄⌫↻◎↵]/);
 }
 assert.throws(()=>sceneSvg(c,'glass','keyboard','dark',resources,'missing'),/请选择/);
 const missing=structuredClone(resources);delete missing.icons.voice;
 assert.throws(()=>sceneSvg(c,'glass','keyboard','dark',missing,'soft_blue'),/缺少原有图标/);
});
