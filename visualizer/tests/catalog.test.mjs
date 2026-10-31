import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {loadCatalog, normalizeState, defaultState, selectGames, summarize, opponents} from '../catalog.js';

const upload = (agent=10,label='v03',league='bronze') => ({agent,label,league,upload:1,games:`docs/data/${agent}.json`});
const game = (id, seat, ranks, scores, pseudo='rival', rating=15) => ({gameId:id,seat,ranks,scores,frames:21,opponent:{pseudo,agentId:90,ratingAtFetch:rating}});
const manifest = (agent,games) => ({agentId:agent,rawDirectory:`replays/arena/v03/bronze/upload-01/games`,games});
const fixture = (registry,files) => async path => ({ok:path==='/docs/data/arena-uploads.json'||path in files,status:404,json:async()=>path==='/docs/data/arena-uploads.json'?registry:files[path]});

test('ranks and seats decide outcome even for equal HP and removed opponent',async()=>{
  const catalog=await loadCatalog(fixture({uploads:[upload()]},{'/docs/data/10.json':manifest(10,[game(1,1,[1,0],[50,50]),game(2,0,[0,1],[99,-1]),game(3,0,[0,0],[10,10])])}));
  assert.deepEqual(catalog.games.map(g=>g.result),['win','win','draw']);
  assert.equal(catalog.games[1].opponentRemoved,true);
  assert.deepEqual(summarize(catalog.games),{games:3,wins:2,losses:0,draws:1,winRate:2/3,removed:1,scoreTies:2});
});

test('league/version filters separate legacy and foreign leagues; invalid upload resets',async()=>{
  const registry={uploads:[upload(),upload(11,'v02'),upload(12,'v03','silver')],legacyUploads:[upload(13,'legacy')]};
  const files=Object.fromEntries([10,11,12,13].map(id=>[`/docs/data/${id}.json`,manifest(id,[game(id,0,[0,1],[100,0])])]));
  const catalog=await loadCatalog(fixture(registry,files));
  assert.equal(defaultState(catalog).league,'silver');
  assert.deepEqual(selectGames(catalog,{league:'bronze',version:'all'}).map(g=>g.agent),[11,10]);
  assert.deepEqual(selectGames(catalog,{league:'bronze',version:'legacy'}).map(g=>g.agent),[13]);
  assert.equal(normalizeState(catalog,{league:'bronze',version:'v03',upload:'11'}).upload,'all');
});

test('exact opponent drilldown does not include substring neighbours; missing rating stays null',async()=>{
  const catalog=await loadCatalog(fixture({uploads:[upload()]},{'/docs/data/10.json':manifest(10,[game(1,0,[1,0],[0,100],'bob',null),game(2,1,[0,1],[100,0],'bobby',30),game(3,1,[1,0],[0,100],'bob',20)])}));
  const selected=selectGames(catalog,{opponent:'bob',opponentExact:'bob'});
  assert.equal(selected.length,2);assert.equal(selected[1].opponent.ratingAtFetch,null);
  assert.deepEqual(opponents(catalog.games,2).map(g=>[g.name,g.games,g.losses,g.wins]),[['bob',2,1,1]]);
});

test('failed manifests and wrong-agent data are reported, without fabricated zero games',async()=>{
  const catalog=await loadCatalog(fixture({uploads:[upload(),upload(11,'v02')]},{'/docs/data/10.json':manifest(999,[])}));
  assert.equal(catalog.errors.length,2);assert.equal(catalog.games.length,0);
});

test('numeric game IDs and local paths reject malformed manifest rows',async()=>{
  const bad=manifest(10,[game(1,0,[0,1],[10,0])]);bad.rawDirectory='https://example.com/';
  const catalog=await loadCatalog(fixture({uploads:[upload()]},{'/docs/data/10.json':bad}));
  assert.equal(catalog.errors.length,1);
});

test('real saved registry loads every manifest game and keeps known v03 results',async()=>{
  const root=new URL('../../',import.meta.url);
  const read=async path=>JSON.parse(await readFile(fileURLToPath(new URL('.'+path,root)),'utf8'));
  const catalog=await loadCatalog(async path=>({ok:true,json:async()=>read(path)}));
  assert.equal(catalog.errors.length,0);
  const registry=await read('/docs/data/arena-uploads.json');
  const count=async uploads=>(await Promise.all(uploads.map(async u=>(await read('/'+u.games)).games.length))).reduce((a,b)=>a+b,0);
  assert.equal(catalog.games.length,await count([...registry.uploads,...registry.legacyUploads]));
  assert.equal(selectGames(catalog,{league:'bronze',version:'all'}).length,await count(registry.uploads.filter(u=>u.league==='bronze')));
  for(const [agent,wins] of [[6827580,37],[6828013,39]]){
    const rows=selectGames(catalog,{league:'bronze',version:'v03',upload:String(agent)});
    assert.equal(rows.length,60);assert.equal(summarize(rows).wins,wins);
  }
});
