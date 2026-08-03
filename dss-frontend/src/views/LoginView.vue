<template>
  <div class="login-page">
    <form class="card" @submit.prevent="submit">
      <div class="brand">
        <span class="logo" aria-hidden="true">👥</span>
        <h1>People Counting</h1>
        <p>Connexion à l’application</p>
      </div>

      <label>
        <span>Identifiant</span>
        <input v-model="username" type="text" autocomplete="username" required autofocus />
      </label>
      <label>
        <span>Mot de passe</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" :disabled="loading">
        <span v-if="loading" class="spinner" aria-hidden="true"></span>
        {{ loading ? 'Connexion…' : 'Se connecter' }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { loginApp } from '../api/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await loginApp({ username: username.value, password: password.value })
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
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 20%, rgba(13, 148, 136, 0.18), transparent 40%),
    radial-gradient(circle at 80% 0%, rgba(15, 23, 42, 0.08), transparent 35%),
    #f1f5f9;
}
.card {
  width: min(100%, 400px);
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
}
.brand {
  text-align: center;
  margin-bottom: 8px;
}
.brand .logo {
  font-size: 1.8rem;
}
.brand h1 {
  margin: 8px 0 4px;
  font-size: 1.35rem;
}
.brand p {
  margin: 0;
  color: #64748b;
  font-size: 0.92rem;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.85rem;
  color: #475569;
}
input {
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 1rem;
}
button {
  margin-top: 6px;
  border: none;
  border-radius: 10px;
  padding: 12px 14px;
  background: #0d9488;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.error {
  margin: 0;
  color: #dc2626;
  font-size: 0.9rem;
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
</style>
