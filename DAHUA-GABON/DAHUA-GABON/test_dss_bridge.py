"""
Test DSS Bridge v6 - Authentification correcte selon la documentation officielle

Sources : Dahua-HTTP-API-For-DSS-V8.7_EN + Dahua-Bridge-Development-and-Docking-Manual_V1.1.1

ARCHITECTURE D'AUTHENTIFICATION (deux systemes independants) :

[A] TOKEN BRIDGE (ECOS)
    POST /ecos/api/v1.1/account/authorize  (HMAC-SHA256 AK/SK)
    → token a passer dans X-Subject-Token pour les endpoints /ecos/api/v1.1/bridge/*
    → NE FONCTIONNE PAS pour /obms/ ou /brms/ (tokens non interchangeables)

[B] TOKEN DSS UTILISATEUR (BRMS) - login en 2 appels sur le MEME endpoint
    Appel 1 : POST /brms/api/v1.0/accounts/authorize  (juste username + clientType)
              → HTTP 401 + realm + randomKey (valide 10 secondes !)
    Appel 2 : POST /brms/api/v1.0/accounts/authorize  (avec signature calculee)
              → HTTP 200 + token DSS
    Formule signature (5 MD5) :
        temp1 = MD5(password)
        temp2 = MD5(userName + temp1)
        temp3 = MD5(temp2)
        temp4 = MD5(userName + ":" + realm + ":" + temp3)
        signature = MD5(temp4 + ":" + randomKey)
    → token a passer dans X-Subject-Token pour /obms/ /brms/ /ipms/ etc.
    → Keep-alive : PUT /brms/api/v1.0/accounts/keepalive toutes les 20s

[C] ATTENDANCE
    GET /obms/api/v1.0/attendance/swiping-card-report/page
    Header : X-Subject-Token: {token DSS utilisateur}
"""

import sys
import hmac
import hashlib
import time
import json
import requests
import urllib3
from datetime import datetime, timedelta

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ============================================================
DSS_IP       = "192.168.1.147"
DSS_PORT     = 443
ACCESS_KEY   = "TNJKFKEN7JDKKZDVMBMNNNSS"
SECRET_KEY   = "DDAQYKYT2FKLNGJLUBQDB6ZW76QTR7LR"
DSS_USERNAME = "system"
DSS_PASSWORD = "Doukiflorence@12345"
# ============================================================

BASE_URL = f"https://{DSS_IP}:{DSS_PORT}"
SESSION  = requests.Session()
SESSION.verify = False
SESSION.headers.update({"Content-Type": "application/json;charset=UTF-8"})


# ─────────────────────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────────────────────

def md5(text: str) -> str:
    return hashlib.md5(text.encode("utf-8")).hexdigest()

def _raw_post(url: str, payload: dict, headers: dict = None) -> requests.Response:
    h = {}
    if headers:
        h.update(headers)
    return SESSION.post(url, json=payload, headers=h, timeout=10)

def _raw_get(url: str, params: dict = None, headers: dict = None,
             timeout: int = 30) -> requests.Response:
    h = {}
    if headers:
        h.update(headers)
    return SESSION.get(url, params=params, headers=h, timeout=timeout)

def _raw_put(url: str, payload: dict = None, headers: dict = None) -> requests.Response:
    h = {}
    if headers:
        h.update(headers)
    return SESSION.put(url, json=payload or {}, headers=h, timeout=10)


# ─────────────────────────────────────────────────────────────
# A — Token Bridge (ECOS / HMAC-SHA256)
# ─────────────────────────────────────────────────────────────

def bridge_authorize() -> str | None:
    print("\n" + "="*62)
    print("ETAPE A : Token Bridge (AK/SK HMAC-SHA256)")
    print("="*62)

    timestamp = int(time.time())
    sig = hmac.new(
        SECRET_KEY.encode("utf-8"),
        str(timestamp).encode("utf-8"),
        hashlib.sha256
    ).hexdigest()

    payload = {"accessKey": ACCESS_KEY, "signature": sig, "timestamp": timestamp}
    print(f"  POST /ecos/api/v1.1/account/authorize")
    print(f"  Payload : {json.dumps(payload)}")

    r = _raw_post(f"{BASE_URL}/ecos/api/v1.1/account/authorize", payload)
    print(f"  HTTP {r.status_code}")
    try:
        data = r.json()
        print(f"  Body : {json.dumps(data, indent=4)}")
        if data.get("code") == 1000:
            token = data["data"]["token"]
            print(f"  [OK] Token Bridge : {token}")
            return token
    except Exception as e:
        print(f"  [ERREUR parse] {e} | raw: {r.text[:200]}")
    return None


# ─────────────────────────────────────────────────────────────
# B — Token DSS utilisateur (BRMS double-authorize)
# ─────────────────────────────────────────────────────────────

def _compute_signature(username: str, password: str, realm: str, random_key: str) -> str:
    """
    Formule DSS V8 (5 MD5 enchainees) extraite de la documentation officielle :
      temp1     = MD5(password)
      temp2     = MD5(userName + temp1)
      temp3     = MD5(temp2)
      temp4     = MD5(userName + ":" + realm + ":" + temp3)
      signature = MD5(temp4 + ":" + randomKey)
    """
    temp1 = md5(password)
    temp2 = md5(username + temp1)
    temp3 = md5(temp2)
    temp4 = md5(username + ":" + realm + ":" + temp3)
    signature = md5(temp4 + ":" + random_key)
    print(f"    temp1={temp1}")
    print(f"    temp2={temp2}")
    print(f"    temp3={temp3}")
    print(f"    temp4={temp4}")
    print(f"    signature={signature}")
    return signature


def dss_login(username: str = DSS_USERNAME,
              password: str = DSS_PASSWORD) -> str | None:
    """
    Login DSS en 2 appels sur POST /brms/api/v1.0/accounts/authorize.
    Appel 1 -> HTTP 401 + realm + randomKey (valide 10 secondes !)
    Appel 2 -> HTTP 200 + token
    """
    print("\n" + "="*62)
    print("ETAPE B : Login DSS utilisateur (double authorize)")
    print("="*62)

    url = f"{BASE_URL}/brms/api/v1.0/accounts/authorize"

    # ── Appel 1 : declencher le challenge ─────────────────────
    print(f"\n  [1] Challenge — POST {url}")
    payload1 = {
        "userName"  : username,
        "ipAddress" : "",
        "clientType": "WINPC_V2",
    }
    print(f"      Body : {json.dumps(payload1)}")

    r1 = _raw_post(url, payload1)
    print(f"      HTTP {r1.status_code}")
    print(f"      Body : {r1.text[:400]}")

    # La reponse au 1er appel est HTTP 401 selon la doc officielle
    # realm et randomKey sont a la RACINE du body (pas dans "data")
    try:
        body1 = r1.json()
    except Exception:
        print(f"  [ERREUR] Reponse non-JSON : {r1.text[:200]}")
        return None

    realm      = (body1.get("realm")
                  or body1.get("Realm")
                  or (body1.get("data") or {}).get("realm")
                  or "")
    random_key = (body1.get("randomKey")
                  or body1.get("random")
                  or body1.get("nonce")
                  or (body1.get("data") or {}).get("randomKey")
                  or (body1.get("data") or {}).get("random")
                  or "")

    if not realm or not random_key:
        print(f"  [ERREUR] realm ou randomKey absent. code={body1.get('code')} desc={body1.get('desc')}")
        print("  Piste : verifier username dans DSS Client > Gestion des comptes")
        return None

    print(f"\n      realm     = {realm}")
    print(f"      randomKey = {random_key}  (valide 10 secondes !)")

    # ── Calcul de la signature ────────────────────────────────
    print(f"\n  [SIG] Calcul signature (5x MD5) pour user={username}")
    signature = _compute_signature(username, password, realm, random_key)

    # ── Appel 2 : soumettre la signature ─────────────────────
    print(f"\n  [2] Authentification — POST {url}")
    payload2 = {
        "mac"        : "",
        "deviceSN"   : "",
        "signature"  : signature,
        "userName"   : username,
        "randomKey"  : random_key,
        "publicKey"  : "",
        "ipAddress"  : "",
        "clientType" : "WINPC_V2",
        "userType"   : "0",
        "secretKey"  : "",
        "secretVector": "",
        "loginType"  : "1",
    }
    print(f"      Body : {json.dumps(payload2)}")

    r2 = _raw_post(url, payload2)
    print(f"      HTTP {r2.status_code}")
    print(f"      Body : {r2.text[:400]}")

    try:
        body2 = r2.json()
    except Exception:
        print(f"  [ERREUR parse] {r2.text[:200]}")
        return None

    # Token peut etre a la racine OU dans "data" selon la version
    token = (body2.get("token")
             or (body2.get("data") or {}).get("token"))

    if token:
        print(f"\n  [OK] Token DSS : {token}")
        return token

    print(f"  [ECHEC] code={body2.get('code')} desc={body2.get('desc')}")
    return None


def dss_keepalive(token: str) -> None:
    """Keep-alive : PUT /brms/api/v1.0/accounts/keepalive (toutes les 20s)."""
    url = f"{BASE_URL}/brms/api/v1.0/accounts/keepalive"
    r = _raw_put(url,
                 payload={"token": token},
                 headers={"X-Subject-Token": token})
    try:
        data = r.json()
        if data.get("code") != 1000:
            print(f"  [keepalive] code={data.get('code')} desc={data.get('desc')}")
    except Exception:
        pass


# ─────────────────────────────────────────────────────────────
# C — Pointages via token DSS utilisateur
# ─────────────────────────────────────────────────────────────

def get_attendance(user_token: str) -> None:
    print("\n" + "="*62)
    print("ETAPE C : Pointages (30 derniers jours)")
    print("="*62)

    headers = {"X-Subject-Token": user_token}
    now = datetime.now()
    params = {
        "startTime": (now - timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S"),
        "endTime"  : now.strftime("%Y-%m-%d %H:%M:%S"),
        "deptId"   : "001",
        "eventType": "0",
        "page"     : "1",
        "pageSize" : "20",
    }

    endpoints = [
        f"{BASE_URL}/obms/api/v1.0/attendance/swiping-card-report/page",
        f"{BASE_URL}/obms/api/v1.0/attendance/record-info-report/page",
        f"{BASE_URL}/obms/api/v1.0/attendance/abnormal-report/page",
    ]

    for ep in endpoints:
        label = ep.split('/obms/')[-1] if '/obms/' in ep else ep
        print(f"\n  {label}")
        try:
            r = _raw_get(ep, params=params, headers=headers, timeout=30)
            print(f"  HTTP {r.status_code}")
        except Exception as e:
            print(f"  [TIMEOUT/ERREUR] {e}")
            continue
        try:
            data = r.json()
            code = data.get("code")
            print(f"  code={code}  desc={data.get('desc', '')}")
            if code == 1000:
                records = data.get("data", {}).get("pageData", [])
                print(f"  [OK] {len(records)} enregistrement(s)")
                for rec in records[:10]:
                    print(f"    {rec.get('name','?'):20s} | "
                          f"{rec.get('swipeTime', rec.get('time','?'))} | "
                          f"{rec.get('eventName','?')}")
                return
        except Exception as e:
            print(f"  [parse error] {e} | {r.text[:100]}")

    print("\n  [INFO] Aucun endpoint d'attendance n'a repondu code=1000.")
    print("  Piste : verifier que le module Attendance est active dans DSS Pro.")
    print("  Piste : le deptId '001' est peut-etre invalide — essayer sans filtre deptId.")


# ─────────────────────────────────────────────────────────────
# D — Verification accessibilite token Bridge sur ECOS
# ─────────────────────────────────────────────────────────────

def test_bridge_endpoints(bridge_token: str) -> None:
    """Teste les vrais endpoints Bridge avec X-Subject-Token (doc page 8)."""
    print("\n" + "="*62)
    print("ETAPE D : Test endpoints Bridge (X-Subject-Token)")
    print("="*62)

    headers = {"X-Subject-Token": bridge_token}

    # Heartbeat Bridge (endpoint le plus simple pour valider le token)
    r = _raw_post(f"{BASE_URL}/ecos/api/v1.1/account/heartbeat",
                  payload={}, headers=headers)
    print(f"  heartbeat : HTTP {r.status_code} | {r.text[:100]}")

    # Config Bridge
    r = _raw_get(f"{BASE_URL}/ecos/api/v1.1/bridge/1/config",
                 headers=headers)
    print(f"  bridge/1/config : HTTP {r.status_code} | {r.text[:200]}")


# ─────────────────────────────────────────────────────────────
# Point d'entree
# ─────────────────────────────────────────────────────────────

def main():
    print("+" + "="*60 + "+")
    print("|  TEST DSS BRIDGE v6 - Authentification conforme doc     |")
    print("+" + "="*60 + "+")
    print(f"  Serveur  : {BASE_URL}")
    print(f"  Username : {DSS_USERNAME}")
    print(f"  Date     : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

    # A) Token Bridge (ECOS)
    bridge_token = bridge_authorize()
    if not bridge_token:
        print("\n[STOP] Token Bridge impossible — verifier AK/SK.")
        return

    # Validation token Bridge sur endpoint heartbeat
    test_bridge_endpoints(bridge_token)

    # B) Token DSS utilisateur (BRMS)
    user_token = dss_login()

    if not user_token:
        print("\n" + "="*62)
        print("[DIAGNOSTIC] Login DSS utilisateur echoue.")
        print("="*62)
        print("""
  L'endpoint POST /brms/api/v1.0/accounts/authorize retourne 7000
  ou ne renvoie pas de realm/randomKey pour le username fourni.

  ACTIONS REQUISES SUR LE SERVEUR DSS PRO :
  ─────────────────────────────────────────
  1. Ouvrir DSS Client sur le serveur 192.168.1.147
  2. Aller dans : Systeme > Gestion des comptes (ou User Management)
  3. Verifier que le compte '{username}' existe et n'est pas bloque
  4. Verifier / reinitialiser le mot de passe
  5. Ou creer un nouveau compte avec le role "Operator" ou "Admin"
  6. Mettre a jour DSS_USERNAME et DSS_PASSWORD dans ce script
  7. Relancer le script

  ALTERNATIVE — tester directement l'interface web :
  → Ouvrir https://192.168.1.147 dans Chrome sur le serveur
  → Se connecter et noter le username/password qui fonctionne
        """.format(username=DSS_USERNAME))
        return

    # C) Pointages
    get_attendance(user_token)

    print("\n" + "="*62)
    print("[RESUME FINAL]")
    print(f"  Bridge Token (ECOS) : {bridge_token}")
    print(f"  User Token   (BRMS) : {user_token}")
    print("="*62)


if __name__ == "__main__":
    main()
