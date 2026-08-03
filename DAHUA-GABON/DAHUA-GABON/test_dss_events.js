/**
 * Test de découverte des endpoints d'événements DSS Pro V8.7
 * Usage : node test_dss_events.js
 */

const crypto = require('crypto');
const axios  = require('C:/Users/HP/Downloads/DAHUA-GABON/dahua-bridge/backend/node_modules/axios');
const https  = require('https');

const DSS_IP        = '192.168.1.147';
const DSS_PORT      = 443;
const ACCESS_KEY    = 'TNJKFKEN7JDKKZDVMBMNNNSS';
const SECRET_KEY    = 'DDAQYKYT2FKLNGJLUBQDB6ZW76QTR7LR';
const USERNAME      = 'system';
const PASSWORD      = 'Doukiflorence@12345';
const BASE_URL      = `https://${DSS_IP}:${DSS_PORT}`;

const client = axios.create({
  baseURL: BASE_URL,
  httpsAgent: new https.Agent({ rejectUnauthorized: false }),
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
  timeout: 10000,
});

function md5(str) {
  return crypto.createHash('md5').update(str, 'utf8').digest('hex');
}

function hmacSha256(secret, msg) {
  return crypto.createHmac('sha256', secret).update(msg).digest('hex');
}

function computeSignature(user, pwd, realm, rk) {
  const t1 = md5(pwd);
  const t2 = md5(user + t1);
  const t3 = md5(t2);
  const t4 = md5(`${user}:${realm}:${t3}`);
  return md5(`${t4}:${rk}`);
}

async function getBridgeToken() {
  const ts  = Math.floor(Date.now() / 1000);
  const sig = hmacSha256(SECRET_KEY, String(ts));
  const { data } = await client.post('/ecos/api/v1.1/account/authorize', {
    accessKey: ACCESS_KEY, signature: sig, timestamp: ts,
  });
  return data.data.token;
}

async function getUserToken() {
  let realm, randomKey;
  try {
    await client.post('/brms/api/v1.0/accounts/authorize', {
      userName: USERNAME, ipAddress: '', clientType: 'WINPC_V2',
    });
  } catch (err) {
    if (err.response?.status === 401) {
      realm     = err.response.data.realm;
      randomKey = err.response.data.randomKey;
    } else throw err;
  }
  const sig = computeSignature(USERNAME, PASSWORD, realm, randomKey);
  const { data } = await client.post('/brms/api/v1.0/accounts/authorize', {
    mac: '', deviceSN: '', signature: sig, userName: USERNAME,
    randomKey, publicKey: '', ipAddress: '', clientType: 'WINPC_V2',
    userType: '0', secretKey: '', secretVector: '', loginType: '1',
  });
  return data.token;
}

async function probe(method, url, token, body = null, extraHeaders = {}) {
  try {
    const cfg = { headers: { 'X-Subject-Token': token, ...extraHeaders }, timeout: 8000 };
    const res = method === 'POST'
      ? await client.post(url, body || {}, cfg)
      : await client.get(url, { ...cfg, params: body || undefined });
    return { status: res.status, data: JSON.stringify(res.data).slice(0, 400) };
  } catch (err) {
    if (err.response) {
      return { status: err.response.status, data: JSON.stringify(err.response.data).slice(0, 400) };
    }
    return { status: 'ERR', data: err.message };
  }
}

async function main() {
  console.log('=== Obtention des tokens ===');
  const bridgeToken = await getBridgeToken();
  const userToken   = await getUserToken();
  console.log('Bridge token :', bridgeToken.slice(0, 20) + '...');
  console.log('User token   :', userToken.slice(0, 20) + '...\n');

  // Endpoints à sonder avec le userToken (BRMS/OBMS)
  const userEndpoints = [
    ['GET',  '/brms/api/v1.0/event/subscribe'],
    ['POST', '/brms/api/v1.0/event/subscribe',      { eventTypes: ['ACCESS_CTL'] }],
    ['POST', '/brms/api/v1.0/event/subscribe',      { eventTypes: [1] }],
    ['GET',  '/brms/api/v1.0/alarm/subscribe'],
    ['POST', '/brms/api/v1.0/alarm/subscribe',      { alarmType: ['ACCESS_CTL'] }],
    ['GET',  '/obms/api/v1.0/event/subscribe'],
    ['POST', '/obms/api/v1.0/event/subscribe',      { eventTypes: ['ACCESS_CTL'] }],
    ['GET',  '/obms/api/v1.1/event/subscribe'],
    ['POST', '/obms/api/v1.1/event/subscribe',      { eventTypes: ['ACCESS_CTL'] }],
    ['GET',  '/obms/api/v1.0/alarm/subscribe'],
    ['POST', '/obms/api/v1.0/alarm/subscribe',      {}],
    ['GET',  '/brms/api/v1.0/event/types'],
    ['GET',  '/brms/api/v1.0/alarm/types'],
    ['GET',  '/obms/api/v1.0/event/types'],
  ];

  // ECOS avec token aussi dans le body
  const ecosWithBody = [
    ['POST', '/ecos/api/v1.1/event/subscribe',         { token: bridgeToken, eventTypes: ['ACCESS_CTL'] }],
    ['POST', '/ecos/api/v1.1/alarm/subscribe',         { token: bridgeToken }],
    ['POST', '/ecos/api/v1.1/message/subscribe',       { token: bridgeToken, messageTypes: ['ACCESS_CTL'] }],
    ['GET',  '/ecos/api/v1.1/event/types',             null],
    ['GET',  '/ecos/api/v1.1/subscription/list',       null],
    // Autres namespaces possibles
    ['GET',  '/evo/api/v1.0/event/subscribe',          null],
    ['POST', '/evo/api/v1.0/event/subscribe',          { eventTypes: ['ACCESS_CTL'] }],
    ['GET',  '/acs/api/v1.0/event/subscribe',          null],
    ['POST', '/acs/api/v1.0/event/subscribe',          { eventTypes: ['ACCESS_CTL'] }],
    ['GET',  '/ecos/api/v1.1/bridge/event/subscribe',  null],
    ['POST', '/ecos/api/v1.1/bridge/event/subscribe',  { token: bridgeToken }],
    // Long-poll Dahua classique
    ['POST', '/ecos/api/v1.1/longPoll',                { token: bridgeToken }],
    ['POST', '/brms/api/v1.0/longPoll',                {}],
    ['POST', '/obms/api/v1.0/real-time/subscribe',     {}],
    ['POST', '/obms/api/v1.1/real-time/subscribe',     {}],
    // Alarm center DSS
    ['GET',  '/ams/api/v1.0/alarm/subscribe',          null],
    ['POST', '/ams/api/v1.0/alarm/subscribe',          {}],
  ];

  console.log('=== Test endpoints avec USER TOKEN ===');
  for (const [method, url, body] of userEndpoints) {
    const r = await probe(method, url, userToken, body);
    const flag = (r.status === 200 || r.status === 201) ? '✅' : (r.status === 404 ? '❌' : '⚠️ ');
    console.log(`${flag} ${method.padEnd(4)} ${url.padEnd(50)} [${r.status}] ${r.data}`);
  }

  console.log('\n=== Test endpoints ECOS (bridge token + body token) et autres namespaces ===');
  for (const [method, url, body] of ecosWithBody) {
    const tok = url.startsWith('/ecos') ? bridgeToken : userToken;
    const r = await probe(method, url, tok, body);
    const flag = r.data.includes('"code":1000') ? '✅' : (r.data.includes('1010') || r.data.includes('404') ? '❌' : '⚠️ ');
    console.log(`${flag} ${method.padEnd(4)} ${url.padEnd(50)} [${r.status}] ${r.data}`);
  }
}

main().catch(console.error);
