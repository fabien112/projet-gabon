/**
 * Test WebSocket + ECOS token via query param
 */
const crypto  = require('crypto');
const axios   = require('C:/Users/HP/Downloads/DAHUA-GABON/dahua-bridge/backend/node_modules/axios');
const https   = require('https');
const http    = require('http');

const DSS_IP     = '192.168.1.147';
const DSS_PORT   = 443;
const ACCESS_KEY = 'TNJKFKEN7JDKKZDVMBMNNNSS';
const SECRET_KEY = 'DDAQYKYT2FKLNGJLUBQDB6ZW76QTR7LR';
const USERNAME   = 'system';
const PASSWORD   = 'Doukiflorence@12345';

const client = axios.create({
  baseURL: `https://${DSS_IP}:${DSS_PORT}`,
  httpsAgent: new https.Agent({ rejectUnauthorized: false }),
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
  timeout: 10000,
});

function md5(s)  { return crypto.createHash('md5').update(s,'utf8').digest('hex'); }
function hmac(k,m){ return crypto.createHmac('sha256',k).update(m).digest('hex'); }

async function getTokens() {
  const ts  = Math.floor(Date.now()/1000);
  const {data: b} = await client.post('/ecos/api/v1.1/account/authorize',
    { accessKey: ACCESS_KEY, signature: hmac(SECRET_KEY, String(ts)), timestamp: ts });
  const bridgeToken = b.data.token;

  let realm, rk;
  try { await client.post('/brms/api/v1.0/accounts/authorize',
    { userName: USERNAME, ipAddress: '', clientType: 'WINPC_V2' }); }
  catch(e) { if(e.response?.status===401){ realm=e.response.data.realm; rk=e.response.data.randomKey; } else throw e; }
  const t1=md5(PASSWORD), t2=md5(USERNAME+t1), t3=md5(t2), t4=md5(`${USERNAME}:${realm}:${t3}`);
  const sig=md5(`${t4}:${rk}`);
  const {data: u} = await client.post('/brms/api/v1.0/accounts/authorize',
    { mac:'', deviceSN:'', signature:sig, userName:USERNAME, randomKey:rk,
      publicKey:'', ipAddress:'', clientType:'WINPC_V2', userType:'0',
      secretKey:'', secretVector:'', loginType:'1' });
  return { bridgeToken, userToken: u.token };
}

async function probe(url, token) {
  try {
    const {data, status} = await client.get(url, {
      headers: { 'X-Subject-Token': token },
      params:  { token },
      timeout: 5000,
    });
    return `[${status}] ${JSON.stringify(data).slice(0,300)}`;
  } catch(e) {
    if(e.response) return `[${e.response.status}] ${JSON.stringify(e.response.data).slice(0,300)}`;
    return `ERR: ${e.message}`;
  }
}

async function probePost(url, token, body) {
  try {
    const {data, status} = await client.post(url, { ...body, token }, {
      headers: { 'X-Subject-Token': token },
      params:  { token },
      timeout: 5000,
    });
    return `[${status}] ${JSON.stringify(data).slice(0,300)}`;
  } catch(e) {
    if(e.response) return `[${e.response.status}] ${JSON.stringify(e.response.data).slice(0,300)}`;
    return `ERR: ${e.message}`;
  }
}

// Test WebSocket via HTTP upgrade (juste le handshake)
function testWs(path) {
  return new Promise(resolve => {
    const key = crypto.randomBytes(16).toString('base64');
    const req = https.request({
      hostname: DSS_IP, port: DSS_PORT,
      path, method: 'GET',
      rejectUnauthorized: false,
      headers: {
        'Connection': 'Upgrade', 'Upgrade': 'websocket',
        'Sec-WebSocket-Version': '13', 'Sec-WebSocket-Key': key,
      },
      timeout: 5000,
    }, res => resolve(`HTTP ${res.statusCode}`));
    req.on('upgrade', (res) => { resolve(`WS UPGRADE OK (${res.statusCode})`); req.destroy(); });
    req.on('error',  (e)   => resolve(`ERR: ${e.message}`));
    req.on('timeout',()    => { req.destroy(); resolve('TIMEOUT'); });
    req.end();
  });
}

async function main() {
  const { bridgeToken, userToken } = await getTokens();
  console.log('Tokens OK\n');

  console.log('=== ECOS avec token en query param ===');
  const ecosEndpoints = [
    ['/ecos/api/v1.1/event/subscribe',    bridgeToken, {}],
    ['/ecos/api/v1.1/alarm/subscribe',    bridgeToken, {}],
    ['/ecos/api/v1.1/longPoll',           bridgeToken, {}],
    ['/ecos/api/v1.1/message/subscribe',  bridgeToken, { messageTypes: ['ACCESS_CTL'] }],
    ['/ecos/api/v1.1/subscription/create',bridgeToken, { eventTypes: ['ACCESS_CTL'] }],
    // Avec userToken sur ECOS
    ['/ecos/api/v1.1/event/subscribe',    userToken,   {}],
    ['/ecos/api/v1.1/longPoll',           userToken,   {}],
  ];
  for (const [url, tok, body] of ecosEndpoints) {
    const label = tok === bridgeToken ? 'BRIDGE' : 'USER  ';
    const r = await probePost(url, tok, body);
    console.log(`[${label}] POST ${url}\n         → ${r}\n`);
  }

  console.log('\n=== Test WebSocket ===');
  const wsPaths = [
    '/ws', '/ecos/ws', '/brms/ws', '/obms/ws',
    '/ecos/api/v1.1/ws', '/websocket',
    `/ws?token=${bridgeToken}`,
    `/ecos/ws?token=${bridgeToken}`,
  ];
  for (const p of wsPaths) {
    const r = await testWs(p);
    console.log(`WS ${p.slice(0,50).padEnd(50)} → ${r}`);
  }
}

main().catch(console.error);
