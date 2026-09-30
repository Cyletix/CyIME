import http from 'node:http';
import {readFile,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {compile,root} from './compile.mjs';
const dir=path.dirname(fileURLToPath(import.meta.url)), port=Number(process.env.CYIME_VISUAL_PORT||4319);
const mime={'.html':'text/html; charset=utf-8','.mjs':'text/javascript','.css':'text/css','.json':'application/json','.png':'image/png'};
const files=new Set(['index.html','lab.css','lab.mjs','renderer.mjs','design.json','android-resources.json']);
http.createServer(async(req,res)=>{
  try {
    if(req.headers.host!==`127.0.0.1:${port}`&&req.headers.host!==`localhost:${port}`){res.writeHead(403).end();return;}
    const url=new URL(req.url,`http://127.0.0.1:${port}`);
    if(req.method==='POST'&&url.pathname==='/compile') {
      if(![`http://127.0.0.1:${port}`,`http://localhost:${port}`].includes(req.headers.origin)){res.writeHead(403).end();return;}
      let body='';for await(const chunk of req){body+=chunk;if(body.length>200000)throw Error('代码超过 200 KB');}
      const config=JSON.parse(body), outputs=await compile(config);await writeFile(path.join(dir,'design.json'),JSON.stringify(config,null,2)+'\n');
      res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify({files:outputs.length}));return;
    }
    if(req.method!=='GET'){res.writeHead(405).end();return;}
    const name=url.pathname==='/'?'index.html':url.pathname.slice(1);
    const capture=/^captures\/(visual-(neon|glass|facet|frost)-(dark|light))\.png$/.exec(name);
    if(!files.has(name)&&!capture){res.writeHead(404).end();return;}
    const target=capture?path.join(root,'.codex-artifacts',`${capture[1]}.png`):path.join(dir,name);
    res.writeHead(200,{'Content-Type':mime[path.extname(target)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}).end(await readFile(target));
  }catch(e){if(!res.headersSent)res.writeHead(400,{'Content-Type':'application/json'});res.end(JSON.stringify({error:e.message}));}
}).listen(port,'127.0.0.1',()=>console.log(`CyIME Visual Lab: http://127.0.0.1:${port}`));
