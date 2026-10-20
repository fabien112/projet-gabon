<template>
  <div class="login-page">
    <div class="scene" aria-hidden="true">
      <img :src="scene" alt="" class="scene-img" />
      <div class="scene-shade" />
    </div>

    <main class="panel">
      <form class="card" @submit.prevent="submit">
        <AppBrand size="lg" subtitle="Rapport de fréquentation" />

        <label>
          Identifiant
          <input
            v-model="username"
            type="text"
            autocomplete="username"
            required
            autofocus
          />
        </label>

        <label>
          Mot de passe
          <div class="field">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              autocomplete="current-password"
              required
            />
            <button
              type="button"
              class="toggle"
              :aria-label="showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? 'Masquer' : 'Afficher' }}
            </button>
          </div>
        </label>

        <label class="remember">
          <input v-model="remember" type="checkbox" />
          Se souvenir de moi
        </label>

        <p v-if="error" class="error">{{ error }}</p>

        <button type="submit" class="submit" :disabled="loading">
          {{ loading ? 'Connexion…' : 'Se connecter' }}
        </button>
      </form>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { loginApp } from '../api/auth'
import { applyMe } from '../auth/session'
import AppBrand from '../components/AppBrand.vue'
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
  background: #1a2332;
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
}

.scene-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, transparent 48%, rgba(18, 24, 34, 0.55) 70%, #121822 100%);
}

.panel {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 32px 48px;
}

.card {
  width: min(100%, 380px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 28px 26px 24px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.28);
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: #334155;
}

input[type='text'],
input[type='password'] {
  width: 100%;
  border: 1px solid #c9d0d8;
  background: #fff;
  color: #1a2332;
  border-radius: 6px;
  padding: 10px 12px;
  outline: none;
}

input:focus {
  border-color: #3b5bdb;
}

.field {
  position: relative;
}

.field input {
  padding-right: 78px;
}

.toggle {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  border: 0;
  background: transparent;
  color: #3b5bdb;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  padding: 4px 6px;
}

.remember {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #5c6778;
}

.remember input {
  accent-color: #3b5bdb;
}

.submit {
  margin-top: 4px;
  border: 0;
  border-radius: 6px;
  padding: 11px 14px;
  background: #1a2332;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.submit:hover:not(:disabled) {
  background: #243044;
}

.submit:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: #9b1c1c;
  background: #fde8e8;
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 0.86rem;
}

@media (max-width: 920px) {
  .scene-shade {
    background: linear-gradient(180deg, transparent 0%, #121822 72%);
  }

  .scene-img {
    object-position: center 30%;
  }

  .panel {
    align-items: flex-end;
    justify-content: center;
    padding: 20px 16px 24px;
  }

  .card {
    width: 100%;
  }
}
</style>
