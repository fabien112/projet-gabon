/**
 * Authentification DSS Pro V8.7
 *
 * Deux tokens distincts :
 *   - bridgeToken : HMAC-SHA256 AK/SK → /ecos/api/v1.1/account/authorize
 *   - userToken   : double-authorize  → /brms/api/v1.0/accounts/authorize
 *
 * Le userToken expire toutes les 30 min — on le renouvelle automatiquement.
 * Keep-alive toutes les 25s via PUT /brms/api/v1.0/accounts/keepalive
 */

const crypto = require('crypto');
const axios  = require('axios');
const https  = require('https');
const config = require('../config');
const logger = require('./logger');

const BASE_URL = `https://${config.dss.ip}:${config.dss.port}`;

// Agent HTTPS sans vérification de certificat (cert auto-signé DSS)
const httpsAgent = new https.Agent({ rejectUnauthorized: false });

const client = axios.create({
  baseURL: BASE_URL,
  httpsAgent,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
  timeout: 15000,
});

// ── État interne des tokens ──────────────────────────────────
let _bridgeToken   = null;
let _bridgeExpires = 0;
let _userToken     = null;
let _userExpires   = 0;
let _keepAliveTimer = null;

// ── Helpers crypto ───────────────────────────────────────────
function md5(str) {
  return crypto.createHash('md5').update(str, 'utf8').digest('hex');
}

function hmacSha256(secret, message) {
  return crypto.createHmac('sha256', secret).update(message).digest('hex');
}

/**
 * Formule signature DSS V8 — 5 MD5 enchaînés (doc officielle)
 */
function computeSignature(username, password, realm, randomKey) {
  const t1 = md5(password);
  const t2 = md5(username + t1);
  const t3 = md5(t2);
  const t4 = md5(`${username}:${realm}:${t3}`);
  return md5(`${t4}:${randomKey}`);
}

// ── Token Bridge (ECOS) ──────────────────────────────────────
async function getBridgeToken() {
  if (_bridgeToken && Date.now() < _bridgeExpires) return _bridgeToken;

  const timestamp = Math.floor(Date.now() / 1000);
  const signature = hmacSha256(config.dss.secretKey, String(timestamp));

  const { data } = await client.post('/ecos/api/v1.1/account/authorize', {
    accessKey: config.dss.accessKey,
    signature,
    timestamp,
  });

  if (data.code !== 1000) throw new Error(`Bridge auth failed: ${data.desc}`);

  _bridgeToken   = data.data.token;
  _bridgeExpires = Date.now() + (parseInt(data.data.duration, 10) - 60) * 1000;
  logger.info('Bridge token renewed');
  return _bridgeToken;
}

// ── Token DSS utilisateur (BRMS double-authorize) ────────────
async function getUserToken() {
  if (_userToken && Date.now() < _userExpires) return _userToken;

  const { username, password } = config.dss;

  // Appel 1 : challenge (réponse HTTP 401 + realm + randomKey)
  let realm, randomKey;
  try {
    await client.post('/brms/api/v1.0/accounts/authorize', {
      userName:   username,
      ipAddress:  '',
      clientType: 'WINPC_V2',
    });
  } catch (err) {
    // axios lève une erreur sur HTTP 401 — on récupère le body
    if (err.response && err.response.status === 401) {
      realm     = err.response.data.realm;
      randomKey = err.response.data.randomKey;
    } else {
      throw err;
    }
  }

  if (!realm || !randomKey) throw new Error('DSS challenge: realm/randomKey manquants');

  // Appel 2 : signature
  const signature = computeSignature(username, password, realm, randomKey);

  const { data } = await client.post('/brms/api/v1.0/accounts/authorize', {
    mac: '', deviceSN: '',
    signature,
    userName:     username,
    randomKey,
    publicKey:    '',
    ipAddress:    '',
    clientType:   'WINPC_V2',
    userType:     '0',
    secretKey:    '',
    secretVector: '',
    loginType:    '1',
  });

  if (!data.token) throw new Error(`DSS login failed: ${JSON.stringify(data)}`);

  _userToken   = data.token;
  // durée retournée en minutes (ex: 30)
  const durationMs = ((data.duration || 30) * 60 - 60) * 1000;
  _userExpires = Date.now() + durationMs;

  _startKeepAlive();
  logger.info('DSS user token renewed');
  return _userToken;
}

// ── Keep-alive ───────────────────────────────────────────────
function _startKeepAlive() {
  if (_keepAliveTimer) clearInterval(_keepAliveTimer);
  _keepAliveTimer = setInterval(async () => {
    if (!_userToken) return;
    try {
      await client.put('/brms/api/v1.0/accounts/keepalive',
        { token: _userToken },
        { headers: { 'X-Subject-Token': _userToken } }
      );
    } catch {
      _userToken = null; // force re-login au prochain appel
    }
  }, 25 * 1000);
}

// ── Helpers exposés ──────────────────────────────────────────
async function userHeaders() {
  const token = await getUserToken();
  return { 'X-Subject-Token': token };
}

async function bridgeHeaders() {
  const token = await getBridgeToken();
  return { 'X-Subject-Token': token };
}

module.exports = { client, getBridgeToken, getUserToken, userHeaders, bridgeHeaders };
