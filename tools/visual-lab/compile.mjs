import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {validate,androidVector,kotlinTokens,styles} from './renderer.mjs';
export const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
export async function compile(config,check=false) {
  validate(config);
  const outputs = new Map([[path.join(root,'app/src/main/java/com/kingzcheung/xime/ui/theme/MaterialRecipes.kt'),kotlinTokens(config)]]);
  for(const s of styles) outputs.set(path.join(root,`app/src/main/res/drawable/cyime_mark_${s}.xml`),androidVector(config,s));
  for(const [file,content] of outputs){if(check){if(await readFile(file,'utf8')!==content)throw Error(`过期生成文件：${file}`);}else await writeFile(file,content,'utf8');}
  return [...outputs.keys()];
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const config=JSON.parse(await readFile(new URL('./design.json',import.meta.url),'utf8'));
  const files=await compile(config,process.argv.includes('--check'));
  console.log(`${process.argv.includes('--check')?'Verified':'Generated'} ${files.length} shared resources`);
}
