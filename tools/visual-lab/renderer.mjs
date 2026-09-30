// Geometry and optical recipes consumed by both this preview and compile.mjs.
export const levels = ['BASE', 'RAISED', 'FLOATING'];
export const styles = ['neon', 'glass', 'facet', 'frost'];
const iconColors = ['highlight', 'light', 'midLight', 'leftEdge', 'bottomStart', 'bottomMid', 'bottomEdge', 'purple', 'purpleEdge', 'fold', 'foldTop', 'foldLower', 'foldLight', 'shadow'];
const iconSegments = ['upper-fold', 'left-fold', 'lower-fold', 'top-face', 'left-face', 'bottom-face', 'right-face'];
const materialFields = ['shade', 'tint', 'top', 'topReach', 'border', 'depth', 'facet', 'matte', 'glow'];
const opacityFields = materialFields.filter(field => field !== 'topReach');
export function validate(c) {
  if (c.version !== 1) throw Error('version 必须为 1');
  for (const level of levels) {
    const intensity = c.levels?.[level];
    if (typeof intensity !== 'number' || !Number.isFinite(intensity) || intensity <= 0 || intensity > 2) throw Error(`${level} 强度必须在 0–2`);
  }
  for (const name of styles) {
    const s = c.styles?.[name];
    if (!s || !materialFields.every(field => {
      const value = s.material?.[field];
      return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 1 && (field === 'topReach' || levels.every(level => value * c.levels[level] <= 1));
    })) throw Error(`${name}.material 需要完整的 0–1 材质参数`);
    if (name !== 'neon' && s.material.glow !== 0) throw Error('只有霓光允许悬浮辉光');
    if (!iconColors.every(key=>/^#[a-f\d]{6}$/i.test(s.icon?.[key]||''))) throw Error(`${name}.icon 需要完整的命名 #RRGGBB 色值`);
  }
  const segments=c.icon?.segments;
  if (!Array.isArray(segments) || segments.map(s=>s.name).join('|')!==iconSegments.join('|')) throw Error('图标需要按顺序定义三条折带和四个亮面');
  for (const segment of segments) {
    if (!/^[MLQZCQHVSTAZmlqzcqhvstaz\d\s.,+\-]+$/.test(segment.path||'')) throw Error(`${segment.name}.path 必须为 SVG 路径`);
    if (![segment.from,segment.to].every(p=>Array.isArray(p)&&p.length===2&&p.every(n=>Number.isFinite(n)&&n>=0&&n<=1024))) throw Error(`${segment.name} 渐变坐标无效`);
    if (!Array.isArray(segment.stops)||segment.stops.length<2||segment.stops[0][0]!==0||segment.stops.at(-1)[0]!==1||segment.stops.some(([at,color],i)=>!Number.isFinite(at)||at<0||at>1||!iconColors.includes(color)||(i>0&&at<=segment.stops[i-1][0]))) throw Error(`${segment.name} 渐变色阶无效`);
  }
  return c;
}
export const esc = s => String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
export function mix(a,b,t) {
  const c=[1,3,5].map(i=>Math.round(parseInt(a.slice(i,i+2),16)*(1-t)+parseInt(b.slice(i,i+2),16)*t));
  return '#'+c.map(n=>n.toString(16).padStart(2,'0')).join('');
}
export function optics(c,style,level) {
  const intensity=c.levels[level], material=c.styles[style].material;
  const scaled=Object.fromEntries(opacityFields.map(field=>[field,Number((material[field]*intensity).toFixed(5))]));
  return {...scaled,topReach:material.topReach,glow:style==='neon'&&level==='FLOATING'?scaled.glow:0};
}
function rgbToHsl(hex) {
  const [r,g,b]=[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255);
  const max=Math.max(r,g,b), min=Math.min(r,g,b), d=max-min, l=(max+min)/2;
  if(d===0) return [0,0,l];
  const s=d/(1-Math.abs(2*l-1));
  let h=max===r?((g-b)/d)%6:max===g?(b-r)/d+2:(r-g)/d+4;
  return [((h*60)+360)%360,s,l];
}
function hslToRgb(h,s,l) {
  const chroma=(1-Math.abs(2*l-1))*s, x=chroma*(1-Math.abs((h/60)%2-1)), m=l-chroma/2;
  const [r,g,b]=h<60?[chroma,x,0]:h<120?[x,chroma,0]:h<180?[0,chroma,x]:h<240?[0,x,chroma]:h<300?[x,0,chroma]:[chroma,0,x];
  return '#'+[r,g,b].map(v=>Math.round((v+m)*255).toString(16).padStart(2,'0')).join('').toUpperCase();
}
export function themeIconColor(source,accent) {
  const [,sourceS,sourceL]=rgbToHsl(source), [accentH,accentS,accentL]=rgbToHsl(accent);
  return hslToRgb(accentH,Math.min(1,sourceS*accentS/.65),Math.max(0,Math.min(1,sourceL+(accentL-.6)*.35)));
}
export function iconSvg(c,style='facet',themeAccent=null) {
  const palette=c.styles[style].icon;
  const gradients=c.icon.segments.map((s,i)=>`<linearGradient id="segment-${i}" gradientUnits="userSpaceOnUse" x1="${s.from[0]}" y1="${s.from[1]}" x2="${s.to[0]}" y2="${s.to[1]}">${s.stops.map(([at,color])=>`<stop offset="${at}" stop-color="${themeAccent?themeIconColor(palette[color],themeAccent):palette[color]}"/>`).join('')}</linearGradient>`).join('');
  const paths=c.icon.segments.map((s,i)=>`<path d="${esc(s.path)}" fill="url(#segment-${i})"/>`).join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024"><defs>${gradients}</defs>${paths}</svg>`;
}
export function themePalette(resources,themeId,mode,scene) {
  const theme=resources?.themes.find(t=>t.id===themeId);
  if(!theme?.[mode]?.[scene]) throw Error('请选择已从 Android 导出的主题');
  return theme[mode][scene];
}
export function originalIcon(resources,id,x,y,size,color) {
  const png=resources?.icons[id];
  if(!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(png||'')) throw Error(`缺少原有图标：${id}`);
  const mask=`icon-${id}-${x}-${y}`;
  return `<defs><mask id="${mask}" maskUnits="userSpaceOnUse" x="${x}" y="${y}" width="${size}" height="${size}" style="mask-type:alpha"><image x="${x}" y="${y}" width="${size}" height="${size}" href="${png}"/></mask></defs><rect data-icon="${id}" x="${x}" y="${y}" width="${size}" height="${size}" fill="${color}" mask="url(#${mask})"/>`;
}
export function toolbarMarkSvg(color,x=18,y=17) {
  return `<svg data-toolbar-mark="true" x="${x}" y="${y}" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="${esc(color)}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M8.2,5.2 L12,8.8 L15.8,5.2 M18.8,8.2 L15.2,12 L18.8,15.8 M15.8,18.8 L12,15.2 L8.2,18.8 M5.2,15.8 L8.8,12 L5.2,8.2"/></svg>`;
}
const svgNum=n=>Number(n.toFixed(4));
function cap(c,style,level,x,y,w,h,r,base,t,id,label='',font=24) {
  if (style==='original') return `<rect x="${x+.5}" y="${y+.5}" width="${w-1}" height="${h-1}" rx="${r}" fill="${base}"/>${label?`<text x="${x+w/2}" y="${y+h/2}" text-anchor="middle" dominant-baseline="central" font-size="${font}" fill="${t.ink}">${esc(label)}</text>`:''}`;
  const p=optics(c,style,level), dark=t.dark, shadow=mix(t.accent,'#000000',dark?.75:.3);
  const rect=`x="${x+.5}" y="${y+.5}" width="${w-1}" height="${h-1}" rx="${r}"`;
  // Same ordered layers and alpha rules as CyMaterial.kt, inside the theme surface.
  return `<defs><clipPath id="c${id}"><rect ${rect}/></clipPath>${p.top?`<linearGradient id="h${id}" gradientUnits="userSpaceOnUse" x1="${x}" y1="${y}" x2="${x}" y2="${svgNum(y+h*p.topReach)}"><stop stop-color="${dark?'#FFFFFF':t.accent}" stop-opacity="${p.top*(dark?1:.55)}"/><stop offset="1" stop-color="${dark?'#FFFFFF':t.accent}" stop-opacity="0"/></linearGradient>`:''}${p.depth?`<linearGradient id="d${id}" gradientUnits="userSpaceOnUse" x1="${x}" y1="${svgNum(y+h*.6)}" x2="${x}" y2="${y+h}"><stop stop-color="${shadow}" stop-opacity="0"/><stop offset="1" stop-color="${shadow}" stop-opacity="${p.depth}"/></linearGradient>`:''}</defs>
  <rect ${rect} fill="${base}"/><g clip-path="url(#c${id})">${p.shade?`<rect ${rect} fill="#000000" fill-opacity="${p.shade*(dark?1:.35)}"/>`:''}${p.tint?`<rect ${rect} fill="${t.accent}" fill-opacity="${p.tint*(dark?1:.7)}"/>`:''}${p.matte?`<rect ${rect} fill="${dark?'#FFFFFF':'#000000'}" fill-opacity="${p.matte*(dark?1:.35)}"/>`:''}${p.top?`<rect x="${x}" y="${y}" width="${w}" height="${svgNum(h*p.topReach)}" fill="url(#h${id})"/>`:''}${p.depth?`<rect x="${x}" y="${svgNum(y+h*.6)}" width="${w}" height="${svgNum(h*.4)}" fill="url(#d${id})"/>`:''}
  ${p.facet?`<path d="M ${svgNum(x+w*.82)} ${y} L ${x+w} ${y} L ${x+w} ${svgNum(y+h*.28)} Z" fill="${t.accent}" fill-opacity="${p.facet*(dark?1:.7)}"/>`:''}
  ${p.glow?`<rect ${rect} fill="none" stroke="${t.accent}" stroke-opacity="${p.glow}" stroke-width="3"/>`:''}
  <rect ${rect} fill="none" stroke="${style==='neon'?t.accent:dark?'#FFFFFF':t.accent}" stroke-opacity="${p.border}" stroke-width="1"/></g>
  ${label?`<text x="${x+w/2}" y="${y+h/2}" text-anchor="middle" dominant-baseline="central" font-size="${font}" fill="${t.ink}">${esc(label)}</text>`:''}`;
}
export function sceneSvg(c,style,scene='keyboard',theme='dark',resources,themeId) {
  if (style==='original' && scene==='icon') throw Error('原有外观只预览键盘和设置卡片');
  if (scene==='icon') return iconSvg(c,style);
  const t=themePalette(resources,themeId,theme,scene), width=440, height=scene==='settings'?400:360;
  let body=`<rect width="440" height="${height}" fill="${t.bg}"/>`, seq=0;
  const key=(level,x,y,w,h,r,base,label='',font=24,ink=t.ink)=>cap(c,style,level,x,y,w,h,r,base,{...t,ink},String(seq++),label,font);
  const icon=(id,x,y,size,color=t.accent)=>originalIcon(resources,id,x,y,size,color);
  if(scene==='settings') {
    body+=`<text x="22" y="42" fill="${t.ink}" font-size="25">CyIME 设置</text><text x="22" y="84" fill="${t.accent}" font-size="13">外观与交互</text>`;
    body+=key('RAISED',16,101,408,244,18,t.card);
    ['主题与定制','按键效果','布局与显示'].forEach((s,i)=>{
      body+=`<text x="76" y="${139+i*76}" fill="${t.ink}" font-size="19">${s}</text><text x="76" y="${163+i*76}" fill="${t.muted}" font-size="13">${['自定义外观和样式','按键音效和振动反馈','候选词显示、键盘布局等'][i]}</text>`;
      body+=icon('next',379,131+i*76,24,t.muted);
      body+=key('BASE',29,123+i*76,34,34,10,t.key);
      body+=icon(['palette','effects','layout'][i],35,129+i*76,22);
    });
  } else {
    body+=key('FLOATING',4,4,432,48,18,t.bg);
    body+=toolbarMarkSvg(t.toolbarInk);
    ['schema','emoji','edit','clipboard','handwriting_lookup','voice','hide'].forEach((id,i)=>{body+=icon(id,71+i*53,17,22,t.toolbarInk);});
    body+=key('BASE',8,62,52,214,11,t.key);
    ['，','。','？','！'].forEach((s,i)=>{body+=`<text x="34" y="${93+i*52}" text-anchor="middle" fill="${t.ink}" font-size="20">${s}</text>`;});
    ['分词','ABC','DEF','GHI','JKL','MNO','PQRS','TUV','WXYZ'].forEach((s,i)=>{body+=key('BASE',68+i%3*91,62+Math.floor(i/3)*73,84,68,11,t.key,s,22);});
    ['delete','reset','0'].forEach((s,i)=>{
      body+=key(i===2?'BASE':'RAISED',346,62+i*73,86,68,11,i===2?t.key:t.fn,i===2?s:'',24);
      if(i<2) body+=icon(s,377,84+i*73,24,t.fnInk);
    });
    [[8,58,'!@#'],[73,60,'123'],[140,174,'拼音'],[321,48,'language'],[376,56,'enter']].forEach(([x,w,s],i)=>{
      body+=key(i===2?'BASE':'RAISED',x,287,w,65,11,i===2?t.key:i===4?t.enter:t.fn,i<3?s:'',i===2?18:22,i===2?t.ink:t.fnInk);
      if(i>=3) body+=icon(s,x+(w-24)/2,307,24,t.fnInk);
    });
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" font-family="system-ui, sans-serif">${body}</svg>`;
}
export function androidVector(c,style) {
  const palette=c.styles[style].icon;
  const paths=c.icon.segments.map(s=>`<path android:pathData="${esc(s.path)}"><aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="${s.from[0]}" android:startY="${s.from[1]}" android:endX="${s.to[0]}" android:endY="${s.to[1]}">${s.stops.map(([at,color])=>`<item android:offset="${at}" android:color="${palette[color]}"/>`).join('')}</gradient></aapt:attr></path>`).join('');
  return `<!-- Generated by tools/visual-lab/compile.mjs; edit design.json instead. -->\n<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="1024" android:viewportHeight="1024">${paths}</vector>\n`;
}
export function kotlinTokens(c) {
  const f=n=>Number(n).toFixed(5).replace(/0+$/,'').replace(/\.$/,'.0')+'f';
  return `// Generated by tools/visual-lab/compile.mjs; edit design.json instead.\npackage com.kingzcheung.xime.ui.theme\n\nenum class MaterialLevel { BASE, RAISED, FLOATING }\ndata class SurfaceOptics(${materialFields.map(field=>`val ${field}: Float`).join(', ')})\n\ninternal object MaterialRecipes {\n    fun resolve(style: VisualStyle, level: MaterialLevel): SurfaceOptics {\n        val material = when (style) {\n${styles.map(name=>`            VisualStyle.${name.toUpperCase()} -> SurfaceOptics(${materialFields.map(field=>f(c.styles[name].material[field])).join(', ')})`).join('\n')}\n            VisualStyle.ORIGINAL -> SurfaceOptics(${materialFields.map(()=>f(0)).join(', ')})\n        }\n        val intensity = when (level) {\n${levels.map(name=>`            MaterialLevel.${name} -> ${f(c.levels[name])}`).join('\n')}\n        }\n        return material.copy(\n${opacityFields.map(field=>`            ${field} = ${field==='glow'?'if (style == VisualStyle.NEON && level == MaterialLevel.FLOATING) material.glow * intensity else 0f':`material.${field} * intensity`}`).join(',\n')}\n        )\n    }\n}\n`;
}
