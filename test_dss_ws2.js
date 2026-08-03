/**
 * Test connexion WebSocket DSS Pro — /brms/ws et /obms/ws
 */
const crypto = require('crypto');
const tls    = require('tls');
const axios  = require('C:/Users/HP/Downloads/DAHUA-GABON/dahua-bridge/backend/node_modules/axios');
const https  = require('https');

const DSS_IP  = '192.168.1.147';
const DSS_PORT = 443;

function md5(s)  { return crypto.createHash('md5').update(s,'utf8').digest('hex'); }
function hmac(k,m){ return crypto.createHmac('sha256',k).update(m).digest('hex'); }

const client = axios.create({
  baseURL: `https://${DSS_IP}:${DSS_PORT}`,
  httpsAgent: new https.Agent({ rejectUnauthorized: false }),
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
  timeout: 10000,
});

async function getTokens() {
  const ts = Math.floor(Date.now()/1000);
  const {data:b} = await client.post('/ecos/api/v1.1/account/authorize',
    { accessKey:'TNJKFKEN7JDKKZDVMBMNNNSS', signature:hmac('DDAQYKYT2FKLNGJLUBQDB6ZW76QTR7LR',String(ts)), timestamp:ts });
  const bridgeToken = b.data.token;

  const USERNAME='system', PASSWORD='Doukiflorence@12345';
  let realm,rk;
  try { await client.post('/brms/api/v1.0/accounts/authorize',
    { userName:USERNAME, ipAddress:'', clientType:'WINPC_V2' }); }
  catch(e) { if(e.response?.status===401){realm=e.response.data.realm;rk=e.response.data.randomKey;}else throw e; }
  const t1=md5(PASSWORD),t2=md5(USERNAME+t1),t3=md5(t2),t4=md5(`${USERNAME}:${realm}:${t3}`);
  const {data:u} = await client.post('/brms/api/v1.0/accounts/authorize',
    { mac:'',deviceSN:'',signature:md5(`${t4}:${rk}`),userName:USERNAME,randomKey:rk,
      publicKey:'',ipAddress:'',clientType:'WINPC_V2',userType:'0',secretKey:'',secretVector:'',loginType:'1' });
  return { bridgeToken, userToken: u.token };
}

function wsConnect(path, token, label) {
  return new Promise(resolve => {
    const wsKey = crypto.randomBytes(16).toString('base64');
    const fullPath = token ? `${path}?token=${encodeURIComponent(token)}` : path;

    const socket = tls.connect({ host: DSS_IP, port: DSS_PORT, rejectUnauthorized: false }, () => {
      const handshake = [
        `GET ${fullPath} HTTP/1.1`,
        `Host: ${DSS_IP}:${DSS_PORT}`,
        'Upgrade: websocket',
        'Connection: Upgrade',
        'Sec-WebSocket-Version: 13',
        `Sec-WebSocket-Key: ${wsKey}`,
        'Sec-WebSocket-Protocol: chat',
        '',
        '',
      ].join('\r\n');
      socket.write(handshake);
    });

    let buf = '';
    const t = setTimeout(() => { socket.destroy(); resolve(`${label}: TIMEOUT`); }, 5000);

    socket.on('data', chunk => {
      buf += chunk.toString('utf8', 0, Math.min(chunk.length, 500));
      const firstLine = buf.split('\r\n')[0];

      if (buf.includes('101')) {
        clearTimeout(t);
        // Connexion WS établie — envoyer un message et attendre la réponse
        // Frame WS : opcode=1 (text), masqué
        const msg = JSON.stringify({ action: 'subscribe', eventTypes: ['ACCESS_CTL'] });
        const msgBuf = Buffer.from(msg);
        const mask = crypto.randomBytes(4);
        const frame = Buffer.alloc(6 + msgBuf.length);
        frame[0] = 0x81;
        frame[1] = 0x80 | msgBuf.length;
        mask.copy(frame, 2);
        for (let i = 0; i < msgBuf.length; i++) frame[6+i] = msgBuf[i] ^ mask[i%4];
        socket.write(frame);

        setTimeout(() => {
          socket.destroy();
          resolve(`${label}: WS OK ✅ (101 Switching) — réponse: ${buf.slice(0,400)}`);
        }, 2000);
      } else if (buf.includes('\r\n\r\n') || buf.length > 200) {
        clearTimeout(t);
        socket.destroy();
        resolve(`${label}: HTTP ${firstLine} — ${buf.slice(0, 300)}`);
      }
    });

    socket.on('error', e => { clearTimeout(t); resolve(`${label}: ERR ${e.message}`); });
  });
}

async function main() {
  const { bridgeToken, userToken } = await getTokens();
  console.log('Tokens OK\n');

  const tests = [
    ['/brms/ws',           userToken,   'brms/ws + userToken'],
    ['/brms/ws',           bridgeToken, 'brms/ws + bridgeToken'],
    ['/brms/ws',           null,        'brms/ws sans token'],
    ['/obms/ws',           userToken,   'obms/ws + userToken'],
    ['/ecos/api/v1.1/ws',  bridgeToken, 'ecos/ws + bridgeToken'],
    ['/ecos/api/v1.1/ws',  userToken,   'ecos/ws + userToken'],
  ];

  for (const [path, token, label] of tests) {
    const r = await wsConnect(path, token, label);
    console.log(r);
    console.log();
  }
}

main().catch(console.error);
