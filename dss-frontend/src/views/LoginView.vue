<template>
  <div class="login-page">
    <div class="scene" aria-hidden="true">
      <img :src="scene" alt="" class="scene-img" />
      <div class="scene-shade" />
      <div class="scene-copy">
        <p class="eyebrow">DataExpert</p>
        <h2>Plateforme de rapports &amp; analyses</h2>
        <p>Collecte, traitement et restitution des flux de fréquentation.</p>
        <ul class="steps">
          <li>Collecte</li>
          <li>Traitement</li>
          <li>Stockage</li>
          <li>Analyse</li>
          <li>Décision</li>
        </ul>
      </div>
    </div>

    <main class="panel">
      <form class="card" @submit.prevent="submit">
        <div class="brand">
          <span class="mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <rect x="3" y="13" width="4" height="8" rx="1" fill="currentColor" />
              <rect x="10" y="8" width="4" height="13" rx="1" fill="currentColor" />
              <rect x="17" y="3" width="4" height="18" rx="1" fill="currentColor" />
            </svg>
          </span>
          <div>
            <h1>DataExpert</h1>
            <p>Plateforme de rapports &amp; analyses</p>
          </div>
        </div>

        <div class="welcome-block">
          <h2>Bienvenue</h2>
          <p>Connectez-vous pour accéder à vos rapports et tableaux de bord.</p>
        </div>

        <label>
          <span>Identifiant</span>
          <div class="field">
            <span class="icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <circle cx="12" cy="8" r="3.2" />
                <path d="M5 19.2c1.6-3.2 4.1-4.8 7-4.8s5.4 1.6 7 4.8" stroke-linecap="round" />
              </svg>
            </span>
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              required
              autofocus
              placeholder="Nom d'utilisateur"
            />
          </div>
        </label>

        <label>
          <span>Mot de passe</span>
          <div class="field">
            <span class="icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="5" y="11" width="14" height="9" rx="2" />
                <path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" />
              </svg>
            </span>
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              required
              placeholder="Mot de passe"
            />
            <button
              type="button"
              class="toggle"
              :aria-label="showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'"
              @click="showPassword = !showPassword"
            >
              <svg v-if="!showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7Z" />
                <circle cx="12" cy="12" r="2.5" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M4 5l16 14" stroke-linecap="round" />
                <path d="M10 10.2A3 3 0 0 0 12 15a3 3 0 0 0 2.8-1.9" />
                <path d="M6.5 8.2C4.4 9.7 3 12 3 12s3.5 7 9 7c1.6 0 3-.4 4.3-1" />
                <path d="M17.7 14.8C19.5 13.4 21 12 21 12s-3.5-7-9-7c-.7 0-1.4.1-2 .3" />
              </svg>
            </button>
          </div>
        </label>

        <label class="remember">
          <input v-model="remember" type="checkbox" />
          <span>Se souvenir de moi</span>
        </label>

        <p v-if="error" class="error">{{ error }}</p>

        <button type="submit" class="submit" :disabled="loading">
          <span v-if="loading" class="spinner" aria-hidden="true"></span>
          {{ loading ? 'Connexion…' : 'Se connecter' }}
        </button>

        <p class="secure">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <rect x="5" y="11" width="14" height="9" rx="2" />
            <path d="M8 11V8a4 4 0 0 1 8 0v3" stroke-linecap="round" />
          </svg>
          Sécurisé par session authentifiée
        </p>
      </form>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { loginApp } from '../api/auth'
import { applyMe } from '../auth/session'
import scene from '../assets/b110929a-7500-4f74-bf87-e928e4c811b8.png'

const REMEMBER_KEY = 'dss.remember.username'

const router = useRouter()
const username = ref(localStorage.getItem(REMEMBER_KEY) || '')
const password = ref('')
const remember = ref(!!localStorage.getItem(REMEMBER_KEY))
const loading = ref(false)
const error = ref('')
const showPassword = ref(false)

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const me = await loginApp({ username: username.value, password: password.value })
    if (remember.value) {
      localStorage.setItem(REMEMBER_KEY, username.value.trim())
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    applyMe(me)
    router.replace('/')
  } catch (e) {
    error.value = e?.response?.data?.message || 'Identifiant ou mot de passe incorrect'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  background: #050a19;
  overflow: hidden;
}

.scene {
  position: absolute;
  inset: 0;
}

.scene-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 28% center;
  display: block;
  transform: scale(1.04);
}

.scene-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(5, 10, 25, 0.28) 0%, rgba(5, 10, 25, 0.18) 42%, rgba(5, 10, 25, 0.72) 72%, #050a19 100%),
    linear-gradient(180deg, rgba(5, 10, 25, 0.2) 0%, rgba(5, 10, 25, 0.55) 100%);
}

.scene-copy {
  position: absolute;
  left: 48px;
  right: 46%;
  bottom: 48px;
  color: #fff;
  text-shadow: 0 10px 28px rgba(0, 0, 0, 0.45);
  max-width: 560px;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #67e8f9;
}

.scene-copy h2 {
  margin: 0 0 10px;
  font-size: clamp(1.7rem, 3vw, 2.5rem);
  font-weight: 700;
  letter-spacing: -0.03em;
}

.scene-copy p {
  margin: 0 0 18px;
  max-width: 42ch;
  color: rgba(255, 255, 255, 0.82);
  line-height: 1.5;
}

.steps {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
}

.steps li {
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #e0f2fe;
  background: rgba(8, 47, 73, 0.55);
  border: 1px solid rgba(103, 232, 249, 0.28);
  backdrop-filter: blur(10px);
}

.panel {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 40px 56px;
}

.card {
  width: min(100%, 420px);
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 32px 28px 22px;
  border-radius: 22px;
  background: rgba(8, 16, 36, 0.78);
  border: 1px solid rgba(125, 211, 252, 0.18);
  box-shadow:
    0 24px 80px rgba(0, 0, 0, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(18px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #67e8f9;
  background: linear-gradient(180deg, rgba(34, 211, 238, 0.2), rgba(37, 99, 235, 0.18));
  border: 1px solid rgba(103, 232, 249, 0.35);
}

.mark svg {
  width: 22px;
  height: 22px;
}

.brand h1 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #f8fafc;
}

.brand p {
  margin: 3px 0 0;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7dd3fc;
}

.welcome-block h2 {
  margin: 6px 0 6px;
  font-size: 1.7rem;
  color: #fff;
  letter-spacing: -0.03em;
}

.welcome-block p {
  margin: 0;
  color: #94a3b8;
  font-size: 0.92rem;
  line-height: 1.45;
}

label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 0.78rem;
  font-weight: 600;
  color: #cbd5e1;
}

.field {
  position: relative;
}

.icon,
.toggle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  color: #67e8f9;
  display: grid;
  place-items: center;
}

.icon {
  left: 12px;
  width: 18px;
  height: 18px;
  pointer-events: none;
}

.icon svg,
.toggle svg {
  width: 18px;
  height: 18px;
}

input[type='text'],
input[type='password'] {
  width: 100%;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(15, 23, 42, 0.72);
  color: #f8fafc;
  border-radius: 12px;
  padding: 13px 44px 13px 42px;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

input::placeholder {
  color: #64748b;
}

input:focus {
  border-color: #22d3ee;
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.18);
}

.toggle {
  right: 8px;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 6px;
}

.remember {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-weight: 500;
}

.remember input {
  accent-color: #22d3ee;
}

.submit {
  margin-top: 4px;
  border: 0;
  border-radius: 12px;
  padding: 14px 16px;
  min-height: 48px;
  background: linear-gradient(135deg, #38bdf8, #2563eb);
  color: #fff;
  font-weight: 800;
  cursor: pointer;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.38);
}

.submit:hover:not(:disabled) {
  filter: brightness(1.06);
}

.submit:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: #fda4af;
  background: rgba(127, 29, 29, 0.35);
  border: 1px solid rgba(248, 113, 113, 0.35);
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.88rem;
}

.secure {
  margin: 2px 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #64748b;
  font-size: 0.75rem;
}

.secure svg {
  width: 14px;
  height: 14px;
  color: #67e8f9;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 920px) {
  .scene-copy {
    left: 20px;
    right: 20px;
    bottom: auto;
    top: 18px;
    max-width: none;
  }

  .scene-copy h2,
  .scene-copy p,
  .steps {
    display: none;
  }

  .scene-img {
    object-position: center 30%;
  }

  .scene-shade {
    background:
      linear-gradient(180deg, rgba(5, 10, 25, 0.15) 0%, #050a19 78%);
  }

  .panel {
    align-items: flex-end;
    justify-content: center;
    padding: 24px 16px 28px;
  }

  .card {
    width: 100%;
  }
}
</style>
