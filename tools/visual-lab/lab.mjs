import {validate,sceneSvg,styles} from './renderer.mjs';
const $=id=>document.getElementById(id), code=$('code');
let config, initial, jsonDraft, svgDraft, resources, currentSvg='', rev=0, timer, gallerySource='';
const status=(s,error=false)=>{$('status').textContent=s;$('status').classList.toggle('error',error)};
const svgUrl=s=>'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(s);
function safeSvg(source) {
  const doc=new DOMParser().parseFromString(source,'image/svg+xml');
  if(doc.querySelector('parsererror') || doc.documentElement.localName!=='svg') throw Error('SVG 语法不完整，请检查标签和属性');
  // Image context already disables script; reject active/external features before export as well.
  for(const el of doc.querySelectorAll('*')) {
    if(['script','foreignObject','animate','set','animateTransform'].includes(el.localName)) throw Error('只预览静态 SVG，不接受脚本或嵌入网页');
    for(const a of el.attributes) if(/^on/i.test(a.name)||(/href$/.test(a.name)&&!a.value.startsWith('#')&&!(el.localName==='image'&&/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(a.value)))||/url\(\s*["']?(?!#)/i.test(a.value)) throw Error('SVG 不允许事件或外部资源');
  }
  if(/@import|@font-face/i.test(source)) throw Error('SVG 不允许导入外部样式');
  return new XMLSerializer().serializeToString(doc);
}
function draw() {
  const start=performance.now();rev++;$('lines').textContent=Array.from({length:code.value.split('\n').length},(_,i)=>i+1).join('\n');
  try {
    if($('mode').value==='json') {
      config=validate(JSON.parse(code.value));jsonDraft=code.value;
      const original=$('style').value==='original';
      $('scene').querySelector('option[value="icon"]').disabled=original;
      if(original&&$('scene').value==='icon')$('scene').value='keyboard';
      currentSvg=sceneSvg(config,$('style').value,$('scene').value,$('theme').value,resources,$('palette').value);
      const source=code.value+'|'+$('palette').value;
      if(gallerySource!==source){gallery();gallerySource=source;}
      for(const id of ['style','scene','theme','palette']) localStorage.setItem('cyime-lab-'+id,$(id).value);
    }
    else {svgDraft=code.value;currentSvg=safeSvg(code.value);}
    $('render').src=svgUrl(currentSvg);$('timing').textContent=`#${rev} · ${(performance.now()-start).toFixed(1)} ms`;
    $('dimensions').textContent=$('mode').value==='svg'?'自由 SVG · 原始比例':$('scene').value==='icon'?'1024 × 1024 · 图标原色':'共享材质参数 · 布局示意；左上角使用随主题着色的线稿';
    status('已更新 · 输入后自动预览');$('compile').disabled=$('mode').value!=='json';$('save').disabled=false;$('svg').disabled=false;$('png').disabled=false;
  } catch(e) {status(e.message+' · 右侧保留上次有效预览',true);$('compile').disabled=true;$('svg').disabled=true;$('png').disabled=true;}
}
function gallery() {
  const fragment=document.createDocumentFragment();
  for(const s of ['original',...styles]) for(const [scene,theme,label] of s==='original'?[['keyboard','dark','深色键盘'],['keyboard','light','浅色键盘'],['settings','dark','深色卡片'],['settings','light','浅色卡片']]:[['icon','dark','透明图标'],['keyboard','dark','深色键盘'],['keyboard','light','浅色键盘'],['settings','dark','深色卡片'],['settings','light','浅色卡片']]) {
    const styleLabel=s==='original'?'原有外观':config.styles[s].label;
    const b=document.createElement('button');b.className='sample';b.setAttribute('aria-label',`${styleLabel} ${label}`);
    const image=new Image();image.src=svgUrl(sceneSvg(config,s,scene,theme,resources,$('palette').value));image.alt=`${s} ${scene} ${theme}`;if(scene==='icon')image.className='checker';
    const title=document.createElement('span');title.textContent=`${styleLabel} / ${label}`;b.append(image,title);b.onclick=()=>{$('style').value=s;$('scene').value=scene;$('theme').value=theme;if($('mode').value==='svg'){svgDraft=code.value;$('mode').value='json';code.value=jsonDraft;}draw();$('canvas').scrollIntoView({behavior:'smooth',block:'center'});};fragment.append(b);
  }
  $('gallery').replaceChildren(fragment);
}
function download(data,type,name){const a=document.createElement('a');const url=URL.createObjectURL(new Blob([data],{type}));a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
code.oninput=()=>{clearTimeout(timer);timer=setTimeout(draw,100)};code.onscroll=()=>{$('lines').scrollTop=code.scrollTop};
code.onkeydown=e=>{if(e.key==='Tab'){e.preventDefault();code.setRangeText('  ',code.selectionStart,code.selectionEnd,'end');code.dispatchEvent(new Event('input'));}};
for(const id of ['style','scene','theme','palette']) $(id).onchange=draw;
$('backdrop').onchange=()=>{$('canvas').className=$('backdrop').value};
$('mode').onchange=()=>{if($('mode').value==='svg'){jsonDraft=code.value;code.value=svgDraft||currentSvg.replace(/></g,'>\n<');}else{svgDraft=code.value;code.value=jsonDraft;}draw();};
$('reset').onclick=()=>{$('mode').value='json';code.value=initial;draw()};
$('import').onclick=()=>$('file').click();$('file').onchange=async()=>{const f=$('file').files[0];if(!f)return;$('mode').value=f.name.endsWith('.svg')?'svg':'json';code.value=await f.text();draw();};
$('save').onclick=()=>download(code.value,'text/plain',`cyime-design.${$('mode').value}`);
$('svg').onclick=()=>download(currentSvg,'image/svg+xml',`cyime-${$('style').value}-${$('scene').value}.svg`);
$('png').onclick=async()=>{try{const img=new Image();img.src=svgUrl(currentSvg);await img.decode();const canvas=document.createElement('canvas');canvas.width=img.naturalWidth||1024;canvas.height=img.naturalHeight||1024;canvas.getContext('2d').drawImage(img,0,0);canvas.toBlob(blob=>{if(blob)download(blob,'image/png','cyime-preview.png');},'image/png');}catch(e){status(e.message,true);}};
$('compile').onclick=async()=>{try{validate(JSON.parse(code.value));const r=await fetch('/compile',{method:'POST',headers:{'Content-Type':'application/json'},body:code.value});const result=await r.json();if(!r.ok)throw Error(result.error);initial=code.value;status('已保存并生成 Android 参数与矢量图标；重新打包后生效');}catch(e){status(e.message,true);}};
try {
  const [source,assets]=await Promise.all([fetch('design.json'),fetch('android-resources.json')]);
  if(!source.ok||!assets.ok)throw Error('缺少 Android 原有图标与配色导出，请运行尺寸验收生成资源');
  initial=await source.text();resources=await assets.json();
  for(const t of resources.themes){const option=new Option(t.name,t.id);$('palette').add(option);}
  $('palette').value=resources.themes.some(t=>t.id===resources.selectedTheme)?resources.selectedTheme:resources.themes[0].id;
  for(const id of ['style','scene','theme','palette']){const saved=localStorage.getItem('cyime-lab-'+id);if([...$(id).options].some(o=>o.value===saved))$(id).value=saved;}
  $('resource-source').textContent=resources.source+' · 配色和功能图标来自输入法；设备动态配色为本次导出快照';
  code.value=jsonDraft=initial;draw();
  for(const name of ['visual-frost-dark','visual-frost-light']){const img=new Image();img.src=`/captures/${name}.png`;img.alt=`Android ${name}`;img.onerror=()=>img.remove();$('captures').append(img);}
}catch(e){status('无法加载配置：'+e.message,true)}
