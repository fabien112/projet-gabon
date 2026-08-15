<template>
  <div v-if="session.superAdmin" class="page">
    <header class="topbar">
      <div class="title-wrap">
        <span class="logo" aria-hidden="true">🔄</span>
        <h1>DataExpert – Configuration</h1>
      </div>
      <div class="top-actions">
        <RouterLink class="btn-outline" to="/">← Rapport</RouterLink>
        <button type="button" class="btn-outline" @click="doLogout">Déconnexion</button>
      </div>
    </header>

    <!-- Session DSS : bandeau dédié -->
    <section class="session-bar" :class="status.dssSessionActive ? 'on' : 'off'">
      <div class="session-left">
        <span class="session-dot" aria-hidden="true" />
        <div>
          <p class="session-title">Session DSS</p>
          <p class="session-state">
            {{ status.dssSessionActive ? 'Connectée et prête' : 'Inactive — reconnexion nécessaire' }}
          </p>
        </div>
      </div>
      <button
        type="button"
        class="btn-reconnect"
        :disabled="reconnecting || status.dssSessionActive"
        @click="doReconnect"
      >
        {{ reconnecting ? 'Reconnexion…' : status.dssSessionActive ? 'Connecté' : 'Reconnecter' }}
      </button>
    </section>

    <section
      v-if="status.catchUpNeeded || status.catchUpKind === 'OK'"
      class="catchup-bar"
      :class="status.catchUpNeeded ? 'warn' : 'ok'"
    >
      <div class="catchup-text">
        <p class="catchup-title">{{ status.catchUpNeeded ? 'Rattrapage recommandé' : 'Données à jour' }}</p>
        <p class="catchup-msg">{{ status.catchUpMessage }}</p>
        <p v-if="status.catchUpNeeded && status.catchUpFrom && status.catchUpTo" class="catchup-period">
          Période : {{ formatDate(status.catchUpFrom) }} → {{ formatDate(status.catchUpTo) }}
        </p>
      </div>
      <button
        v-if="status.catchUpNeeded && status.catchUpKind !== 'EMPTY'"
        type="button"
        class="btn-reconnect"
        :disabled="status.running"
        @click="startCatchUp"
      >
        {{ status.running ? 'Sync en cours…' : 'Rattraper maintenant' }}
      </button>
      <button
        v-else-if="status.catchUpKind === 'EMPTY'"
        type="button"
        class="btn-reconnect"
        @click="tab = 'sync'"
      >
        Aller à la synchronisation
      </button>
    </section>

    <nav class="tabs" aria-label="Sections configuration">
      <button
        type="button"
        class="tab"
        :class="{ active: tab === 'cameras' }"
        @click="tab = 'cameras'"
      >
        Caméras
      </button>
      <button
        type="button"
        class="tab"
        :class="{ active: tab === 'users' }"
        @click="tab = 'users'"
      >
        Utilisateurs
      </button>
      <button
        type="button"
        class="tab"
        :class="{ active: tab === 'sync' }"
        @click="tab = 'sync'"
      >
        Synchronisation
        <span v-if="status.running" class="tab-dot" aria-hidden="true" />
      </button>
    </nav>

    <section v-show="tab === 'cameras'" class="panel cameras-panel">
      <div class="cameras-head">
        <div>
          <h2>Caméras</h2>
          <p class="lead cameras-lead">
            Ajoutez une caméra (identifiant DSS). Elle apparaît ensuite dans les rapports,
            le poll automatique et la synchronisation, comme les autres.
          </p>
        </div>
        <button type="button" class="btn-outline" :disabled="discovering" @click="loadDiscover">
          {{ discovering ? 'Chargement DSS…' : 'Importer depuis DSS' }}
        </button>
      </div>

      <form class="cam-form" @submit.prevent="submitAdd">
        <label class="field">
          <span class="label">Nom</span>
          <input v-model="newCam.name" type="text" placeholder="Ex. Compteuse Akanda" required />
        </label>
        <label class="field">
          <span class="label">Identifiant canal DSS</span>
          <input v-model="newCam.channelId" type="text" placeholder="1000004$1$0$0" required />
        </label>
        <label class="field">
          <span class="label">Site (optionnel)</span>
          <input v-model="newCam.site" type="text" placeholder="Akanda" />
        </label>
        <button class="generate cam-add" type="submit" :disabled="addingCam">
          {{ addingCam ? 'Ajout…' : 'Ajouter la caméra' }}
        </button>
      </form>
      <p v-if="camError" class="error">{{ camError }}</p>
      <p v-if="camOk" class="ok-msg">{{ camOk }}</p>

      <div v-if="discovered.length" class="discover-list">
        <h3>Canaux DSS disponibles</h3>
        <ul>
          <li v-for="ch in discovered" :key="ch.channelId">
            <div class="discover-meta">
              <strong>{{ ch.name || ch.channelId }}</strong>
              <span class="channel-id">{{ ch.channelId }}</span>
              <span v-if="ch.deviceName" class="site-tag">{{ ch.deviceName }}</span>
            </div>
            <button
              type="button"
              class="btn-outline"
              :disabled="ch.alreadyAdded || addingCam"
              @click="addFromDiscover(ch)"
            >
              {{ ch.alreadyAdded ? 'Déjà ajoutée' : 'Ajouter' }}
            </button>
          </li>
        </ul>
      </div>

      <div class="cam-table-wrap">
        <table v-if="cameras.length" class="cam-table">
          <thead>
            <tr>
              <th>Nom</th>
              <th>Canal DSS</th>
              <th>Site</th>
              <th>Statut</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cam in cameras" :key="cam.id" :class="{ inactive: !cam.active }">
              <td>
                {{ cam.name }}
                <span v-if="cam.manual" class="badge-mini">manuelle</span>
              </td>
              <td class="mono">{{ cam.channelId }}</td>
              <td>{{ cam.site || '—' }}</td>
              <td>
                <span class="badge" :class="cam.active ? 'ok' : 'ko'">
                  {{ cam.active ? 'Active' : 'Inactive' }}
                </span>
              </td>
              <td class="cam-actions">
                <button type="button" class="btn-outline" @click="toggleActive(cam)">
                  {{ cam.active ? 'Désactiver' : 'Réactiver' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-cams">Aucune caméra pour le moment. Ajoutez-en une ou importez depuis DSS.</p>
      </div>
    </section>

    <section v-show="tab === 'users'" class="panel cameras-panel">
      <div class="cameras-head">
        <div>
          <h2>Utilisateurs</h2>
          <p class="lead cameras-lead">
            Créez, modifiez ou supprimez les comptes. Vous pouvez aussi réinitialiser
            l’identifiant et le mot de passe.
          </p>
        </div>
      </div>

      <form class="cam-form user-form" @submit.prevent="submitAddUser">
        <label class="field">
          <span class="label">Identifiant</span>
          <input v-model="newUser.username" type="text" placeholder="Ex. lea" required minlength="3" />
        </label>
        <label class="field">
          <span class="label">Mot de passe</span>
          <input v-model="newUser.password" type="password" placeholder="8 caractères min." required minlength="8" />
        </label>
        <button class="generate cam-add" type="submit" :disabled="addingUser">
          {{ addingUser ? 'Ajout…' : 'Ajouter l’utilisateur' }}
        </button>
      </form>
      <p v-if="userError" class="error">{{ userError }}</p>
      <p v-if="userOk" class="ok-msg">{{ userOk }}</p>

      <div class="cam-table-wrap">
        <table v-if="users.length" class="cam-table">
          <thead>
            <tr>
              <th>Identifiant</th>
              <th>Rôle</th>
              <th>Statut</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id" :class="{ inactive: !u.enabled }">
              <td>
                <template v-if="editId === u.id">
                  <input
                    v-model="editForm.username"
                    class="inline-input"
                    type="text"
                    minlength="3"
                    placeholder="Identifiant"
                  />
                </template>
                <template v-else>
                  {{ u.username }}
                  <span v-if="u.systemUser" class="badge-mini">système</span>
                </template>
              </td>
              <td>{{ u.superAdmin ? 'Superadmin' : 'Utilisateur' }}</td>
              <td>
                <span class="badge" :class="u.enabled ? 'ok' : 'ko'">
                  {{ u.enabled ? 'Actif' : 'Désactivé' }}
                </span>
              </td>
              <td class="cam-actions">
                <template v-if="editId === u.id">
                  <input
                    v-model="editForm.password"
                    class="inline-input"
                    type="password"
                    minlength="8"
                    placeholder="Nouveau mot de passe"
                  />
                  <button type="button" class="btn-outline" :disabled="savingUser" @click="saveEdit(u)">
                    {{ savingUser ? 'Enregistrement…' : 'Enregistrer' }}
                  </button>
                  <button type="button" class="btn-outline" @click="cancelEdit">Annuler</button>
                </template>
                <template v-else>
                  <button type="button" class="btn-outline" @click="startEdit(u)">Modifier</button>
                  <button
                    v-if="!u.superAdmin"
                    type="button"
                    class="btn-outline"
                    @click="toggleUser(u)"
                  >
                    {{ u.enabled ? 'Désactiver' : 'Réactiver' }}
                  </button>
                  <button
                    v-if="!u.superAdmin"
                    type="button"
                    class="btn-outline danger"
                    @click="removeUser(u)"
                  >
                    Supprimer
                  </button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="empty-cams">Aucun utilisateur pour le moment.</p>
      </div>
    </section>

    <div v-show="tab === 'sync'" class="layout">
      <!-- Action principale -->
      <section class="panel action-panel">
        <h2>Lancer une synchronisation</h2>
        <p class="lead">
          Choisissez la période (7 jours max pour la comparaison). Relancer la même période
          met à jour les données sans créer de doublons.
        </p>

        <div class="form-grid">
          <label class="field">
            <span class="label">Du</span>
            <input v-model="from" type="date" :max="to" :disabled="status.running" />
          </label>
          <label class="field">
            <span class="label">Au</span>
            <input v-model="to" type="date" :min="from" :max="today" :disabled="status.running" />
          </label>
        </div>

        <button
          class="generate"
          :disabled="status.running || !canStart"
          @click="startSync"
        >
          <span v-if="status.running" class="spinner" aria-hidden="true"></span>
          {{ status.running ? 'Synchronisation en cours…' : 'Lancer la synchronisation' }}
        </button>
        <button
          type="button"
          class="btn-outline compare-btn"
          :disabled="status.running || comparing || !canStart"
          @click="runCompare"
        >
          <span v-if="comparing" class="spinner dark" aria-hidden="true"></span>
          {{ comparing ? 'Comparaison DSS…' : 'Comparer DSS ↔ base' }}
        </button>

        <p v-if="formError" class="error">{{ formError }}</p>
        <p v-if="apiError" class="error">{{ apiError }}</p>
        <p v-if="compareError" class="error">{{ compareError }}</p>

        <div v-if="compareResult" class="compare-block" :class="compareResult.aligned ? 'ok' : 'ko'">
          <div class="progress-head">
            <h3>DSS ↔ base locale</h3>
            <span class="badge" :class="compareResult.aligned ? 'ok' : 'ko'">
              {{ compareResult.aligned ? 'Aligné' : 'Écart' }}
            </span>
          </div>
          <p class="progress-msg">{{ compareResult.message }}</p>
          <dl class="compare-facts">
            <div>
              <dt>Entrées DSS / base</dt>
              <dd>{{ compareResult.dssEntries }} / {{ compareResult.dbEntries }}</dd>
            </div>
            <div>
              <dt>Sorties DSS / base</dt>
              <dd>{{ compareResult.dssExits }} / {{ compareResult.dbExits }}</dd>
            </div>
            <div>
              <dt>Créneaux identiques</dt>
              <dd>{{ compareResult.matches }}</dd>
            </div>
            <div>
              <dt>Manquants en base</dt>
              <dd>{{ compareResult.missingInDb }}</dd>
            </div>
            <div>
              <dt>Valeurs différentes</dt>
              <dd>{{ compareResult.valueMismatches }}</dd>
            </div>
          </dl>
          <table v-if="compareResult.diffs?.length" class="cam-table compare-table">
            <thead>
              <tr>
                <th>Écart</th>
                <th>Canal</th>
                <th>Jour</th>
                <th>Heure</th>
                <th>DSS in/out</th>
                <th>Base in/out</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(d, i) in compareResult.diffs" :key="i">
                <td>{{ diffLabel(d.type) }}</td>
                <td class="mono">{{ d.channelId }}</td>
                <td>{{ formatDate(d.date) }}</td>
                <td>{{ d.hour }}</td>
                <td>{{ d.dssIn == null ? '—' : d.dssIn + ' / ' + d.dssOut }}</td>
                <td>{{ d.dbIn == null ? '—' : d.dbIn + ' / ' + d.dbOut }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Progression intégrée sous l'action -->
        <div
          v-if="status.running || status.phase === 'SUCCESS' || status.phase === 'FAILED'"
          class="progress-block"
        >
          <div class="progress-head">
            <h3>Progression</h3>
            <span class="badge" :class="statusClass(status.phase || status.lastJobStatus)">
              {{ status.phase || status.lastJobStatus || '—' }}
            </span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPct + '%' }"></div>
          </div>
          <div class="progress-meta">
            <span>{{ status.daysDone }} / {{ status.daysTotal }} jour(s)</span>
            <span v-if="status.currentDate">Jour : {{ formatDate(status.currentDate) }}</span>
            <span>{{ status.rowsUpserted }} ligne(s)</span>
          </div>
          <p v-if="status.message" class="progress-msg">{{ status.message }}</p>
        </div>
      </section>

      <!-- Infos secondaires -->
      <aside class="side-stack">
        <section class="panel side-panel poll-panel" :class="pollPanelClass">
          <div class="poll-head">
            <h2>Poll automatique</h2>
            <span class="poll-live" :class="{ on: status.pollInProgress || pollRecent }">
              <span class="poll-pulse" aria-hidden="true" />
              {{ pollLiveLabel }}
            </span>
          </div>
          <p class="poll-lead">
            Récupération automatique du jour en cours depuis DSS vers la base locale.
          </p>
          <dl class="facts">
            <div>
              <dt>État</dt>
              <dd :class="pollStatusClass">{{ pollStateLabel }}</dd>
            </div>
            <div>
              <dt>Dernier poll</dt>
              <dd>{{ formatDateTime(status.pollLastAt) }}</dd>
            </div>
            <div>
              <dt>Résultat</dt>
              <dd>{{ status.pollLastMessage || '—' }}</dd>
            </div>
            <div>
              <dt>Lignes / créneaux</dt>
              <dd>
                {{ status.pollLastRowsUpserted ?? 0 }} ligne(s)
                <template v-if="status.pollLastActiveSlots != null">
                  · {{ status.pollLastActiveSlots }} actif(s)
                </template>
              </dd>
            </div>
          </dl>
        </section>
      </aside>

      <section class="panel last-sync-panel">
        <h2>Dernière synchronisation</h2>
        <dl class="facts facts-2col">
          <div>
            <dt>Terminée le</dt>
            <dd>{{ formatDateTime(status.lastJobFinishedAt) }}</dd>
          </div>
          <div>
            <dt>Statut</dt>
            <dd :class="statusClass(status.lastJobStatus)">{{ status.lastJobStatus || '—' }}</dd>
          </div>
          <div>
            <dt>Période</dt>
            <dd>
              <template v-if="status.lastJobFrom && status.lastJobTo">
                {{ formatDate(status.lastJobFrom) }} → {{ formatDate(status.lastJobTo) }}
              </template>
              <template v-else>—</template>
            </dd>
          </div>
          <div>
            <dt>Dernier jour en base</dt>
            <dd>{{ status.lastCoveredDateInDb ? formatDate(status.lastCoveredDateInDb) : '—' }}</dd>
          </div>
        </dl>
        <p v-if="status.lastJobMessage" class="side-msg">{{ status.lastJobMessage }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { logoutApp } from '../api/auth'
import { clearSession, session } from '../auth/session'
import {
  addCamera,
  discoverDssCameras,
  fetchConfiguredCameras,
  updateCamera,
} from '../api/cameras'
import { fetchSyncStatus, reconnectDss, startHistorySync, compareDssDb } from '../api/sync'
import { createAppUser, deleteAppUser, fetchAppUsers, updateAppUser } from '../api/users'

const today = new Date().toISOString().slice(0, 10)
const router = useRouter()
const route = useRoute()
const monthAgo = new Date()
monthAgo.setDate(monthAgo.getDate() - 30)

const from = ref(monthAgo.toISOString().slice(0, 10))
const to = ref(today)
const formError = ref('')
const apiError = ref('')
const reconnecting = ref(false)
const tab = ref('cameras')
const cameras = ref([])
const discovered = ref([])
const discovering = ref(false)
const addingCam = ref(false)
const camError = ref('')
const camOk = ref('')
const comparing = ref(false)
const compareError = ref('')
const compareResult = ref(null)
const newCam = reactive({
  name: '',
  channelId: '',
  site: '',
})
const users = ref([])
const addingUser = ref(false)
const savingUser = ref(false)
const userError = ref('')
const userOk = ref('')
const newUser = reactive({
  username: '',
  password: '',
})
const editId = ref(null)
const editForm = reactive({
  username: '',
  password: '',
})

const status = reactive({
  running: false,
  phase: 'IDLE',
  fromDate: null,
  toDate: null,
  currentDate: null,
  daysTotal: 0,
  daysDone: 0,
  rowsUpserted: 0,
  message: '',
  lastJobFinishedAt: null,
  lastCoveredDateInDb: null,
  lastJobStatus: null,
  lastJobMessage: null,
  lastJobFrom: null,
  lastJobTo: null,
  dssSessionActive: false,
  pollEnabled: true,
  pollInProgress: false,
  pollSuspended: false,
  pollInterval: '60s',
  pollLastAt: null,
  pollLastStatus: null,
  pollLastMessage: null,
  pollLastRowsUpserted: 0,
  pollLastActiveSlots: 0,
  pollLastDate: null,
  catchUpNeeded: false,
  catchUpKind: 'OK',
  catchUpFrom: null,
  catchUpTo: null,
  catchUpMessage: '',
})

let timer = null
const nowTick = ref(Date.now())
let tickTimer = null

const canStart = computed(() => from.value && to.value && to.value >= from.value)

const progressPct = computed(() => {
  if (!status.daysTotal) return 0
  return Math.min(100, Math.round((status.daysDone / status.daysTotal) * 100))
})

const pollRecent = computed(() => {
  if (!status.pollLastAt) return false
  const age = nowTick.value - new Date(status.pollLastAt).getTime()
  return age >= 0 && age < 12_000
})

const pollLiveLabel = computed(() => {
  if (status.pollInProgress) return 'En cours…'
  if (status.pollSuspended) return 'Suspendu'
  if (!status.pollEnabled) return 'Désactivé'
  if (pollRecent.value) return 'Signalé'
  return 'En attente'
})

const pollStateLabel = computed(() => {
  if (status.pollInProgress) return 'Pull en cours'
  if (status.pollSuspended) return 'Suspendu (sync historique)'
  if (!status.pollEnabled) return 'Désactivé'
  if (status.pollLastStatus === 'OK') return 'OK'
  if (status.pollLastStatus === 'FAILED') return 'Échec'
  if (status.pollLastStatus === 'SKIPPED') return 'Ignoré'
  return status.pollLastStatus || 'En attente'
})

const pollStatusClass = computed(() => {
  if (status.pollInProgress) return 'run'
  if (status.pollLastStatus === 'OK') return 'ok'
  if (status.pollLastStatus === 'FAILED') return 'ko'
  if (status.pollSuspended || status.pollLastStatus === 'SKIPPED') return 'run'
  return ''
})

const pollPanelClass = computed(() => {
  if (status.pollInProgress || pollRecent.value) return 'live'
  if (status.pollLastStatus === 'FAILED') return 'fail'
  if (!status.pollEnabled || status.pollSuspended) return 'muted'
  return ''
})

function applyStatus(data) {
  Object.assign(status, data)
}

async function doLogout() {
  try {
    await logoutApp()
  } catch {
    // ignore
  }
  clearSession()
  router.replace('/login')
}

function formatDate(isoDate) {
  if (!isoDate) return '—'
  const [y, m, d] = String(isoDate).slice(0, 10).split('-')
  return `${d}/${m}/${y}`
}

function formatDateTime(iso) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('fr-FR')
  } catch {
    return String(iso)
  }
}

function statusClass(s) {
  if (s === 'SUCCESS') return 'ok'
  if (s === 'FAILED') return 'ko'
  if (s === 'RUNNING') return 'run'
  return ''
}

async function refresh() {
  try {
    applyStatus(await fetchSyncStatus())
    // Ne pas effacer une erreur de sync récente si refresh périodique
  } catch (e) {
    apiError.value = e?.response?.data?.message || e.message || 'Impossible de lire le statut sync'
  }
}

async function startCatchUp() {
  if (!status.catchUpFrom || !status.catchUpTo || status.running) return
  tab.value = 'sync'
  from.value = status.catchUpFrom
  to.value = status.catchUpTo
  await startSync()
}

async function startSync() {
  formError.value = ''
  apiError.value = ''
  compareError.value = ''
  if (!canStart.value) {
    formError.value = 'Période invalide'
    return
  }
  try {
    applyStatus(await startHistorySync({ from: from.value, to: to.value }))
  } catch (e) {
    apiError.value = e?.response?.data?.message || e.message || 'Échec démarrage sync'
  }
}

function diffLabel(type) {
  if (type === 'MISSING_IN_DB') return 'Manquant en base'
  if (type === 'VALUE') return 'Valeur différente'
  if (type === 'EXTRA_IN_DB') return 'En base seulement'
  return type
}

async function runCompare() {
  compareError.value = ''
  compareResult.value = null
  if (!canStart.value) {
    compareError.value = 'Période invalide'
    return
  }
  const fromDay = from.value
  const toDay = to.value
  const days = Math.round((new Date(toDay) - new Date(fromDay)) / 86400000) + 1
  if (days > 7) {
    compareError.value = 'La comparaison est limitée à 7 jours. Raccourcissez la période.'
    return
  }
  comparing.value = true
  try {
    compareResult.value = await compareDssDb({ from: fromDay, to: toDay })
  } catch (e) {
    compareError.value = e?.response?.data?.message || e.message || 'Échec de la comparaison DSS'
  } finally {
    comparing.value = false
  }
}

async function refreshCameras() {
  cameras.value = await fetchConfiguredCameras()
}

async function refreshUsers() {
  users.value = await fetchAppUsers()
}

async function submitAddUser() {
  userError.value = ''
  userOk.value = ''
  addingUser.value = true
  try {
    await createAppUser({
      username: newUser.username,
      password: newUser.password,
    })
    newUser.username = ''
    newUser.password = ''
    userOk.value = 'Utilisateur ajouté'
    await refreshUsers()
  } catch (e) {
    userError.value = e?.response?.data?.message || e.message || 'Impossible d’ajouter l’utilisateur'
  } finally {
    addingUser.value = false
  }
}

function startEdit(user) {
  editId.value = user.id
  editForm.username = user.username
  editForm.password = ''
  userError.value = ''
  userOk.value = ''
}

function cancelEdit() {
  editId.value = null
  editForm.username = ''
  editForm.password = ''
}

async function saveEdit(user) {
  userError.value = ''
  userOk.value = ''
  const payload = {}
  if (editForm.username && editForm.username !== user.username) {
    payload.username = editForm.username
  }
  if (editForm.password) {
    payload.password = editForm.password
  }
  if (!payload.username && !payload.password) {
    userError.value = 'Indiquez un nouvel identifiant ou un nouveau mot de passe'
    return
  }
  savingUser.value = true
  try {
    await updateAppUser(user.id, payload)
    userOk.value = payload.password
      ? 'Identifiant / mot de passe mis à jour'
      : 'Identifiant mis à jour'
    cancelEdit()
    await refreshUsers()
  } catch (e) {
    userError.value = e?.response?.data?.message || e.message || 'Impossible de modifier l’utilisateur'
  } finally {
    savingUser.value = false
  }
}

async function toggleUser(user) {
  userError.value = ''
  userOk.value = ''
  try {
    await updateAppUser(user.id, { enabled: !user.enabled })
    await refreshUsers()
  } catch (e) {
    userError.value = e?.response?.data?.message || e.message || 'Impossible de modifier l’utilisateur'
  }
}

async function removeUser(user) {
  if (!window.confirm(`Supprimer le compte « ${user.username} » ?`)) return
  userError.value = ''
  userOk.value = ''
  try {
    await deleteAppUser(user.id)
    if (editId.value === user.id) cancelEdit()
    userOk.value = 'Utilisateur supprimé'
    await refreshUsers()
  } catch (e) {
    userError.value = e?.response?.data?.message || e.message || 'Impossible de supprimer l’utilisateur'
  }
}

function resetNewCam() {
  newCam.name = ''
  newCam.channelId = ''
  newCam.site = ''
}

async function submitAdd() {
  camError.value = ''
  camOk.value = ''
  addingCam.value = true
  try {
    await addCamera({
      channelId: newCam.channelId,
      name: newCam.name,
      site: newCam.site,
    })
    resetNewCam()
    await refreshCameras()
    camOk.value = 'Caméra ajoutée. Elle est disponible dans les rapports. Lancez une sync pour l’historique.'
  } catch (e) {
    camError.value = e?.response?.data?.message || e.message || 'Impossible d’ajouter la caméra'
  } finally {
    addingCam.value = false
  }
}

async function addFromDiscover(ch) {
  camError.value = ''
  camOk.value = ''
  addingCam.value = true
  try {
    await addCamera({
      channelId: ch.channelId,
      name: ch.name || ch.channelId,
      site: '',
    })
    ch.alreadyAdded = true
    await refreshCameras()
    camOk.value = `« ${ch.name || ch.channelId} » ajoutée.`
  } catch (e) {
    camError.value = e?.response?.data?.message || e.message || 'Impossible d’ajouter la caméra'
  } finally {
    addingCam.value = false
  }
}

async function loadDiscover() {
  camError.value = ''
  discovering.value = true
  try {
    discovered.value = await discoverDssCameras()
  } catch (e) {
    camError.value = e?.response?.data?.message || e.message || 'Impossible de lire l’arbre DSS'
  } finally {
    discovering.value = false
  }
}

async function toggleActive(cam) {
  camError.value = ''
  camOk.value = ''
  try {
    await updateCamera(cam.id, { active: !cam.active })
    await refreshCameras()
  } catch (e) {
    camError.value = e?.response?.data?.message || e.message || 'Mise à jour impossible'
  }
}

async function doReconnect() {
  reconnecting.value = true
  apiError.value = ''
  try {
    const res = await reconnectDss()
    await refresh()
    if (!res?.dssSessionActive) {
      apiError.value = res?.message || 'Session DSS toujours inactive'
    }
  } catch (e) {
    apiError.value = e?.response?.data?.message || e.message || 'Échec reconnexion DSS'
    await refresh()
  } finally {
    reconnecting.value = false
  }
}

onMounted(async () => {
  if (!session.superAdmin) {
    router.replace('/')
    return
  }
  if (route.query.tab === 'sync') {
    tab.value = 'sync'
  } else if (route.query.tab === 'users') {
    tab.value = 'users'
  }
  await Promise.all([
    refresh(),
    refreshCameras().catch((e) => {
      camError.value = e?.response?.data?.message || e.message || 'Impossible de charger les caméras'
    }),
    refreshUsers().catch((e) => {
      userError.value = e?.response?.data?.message || e.message || 'Impossible de charger les utilisateurs'
    }),
  ])
  timer = setInterval(refresh, 2000)
  tickTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (tickTimer) clearInterval(tickTimer)
})
</script>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 20px 48px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--blue-soft, #dbeafe);
  display: grid;
  place-items: center;
  font-size: 1rem;
}

h1 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--ink, #0f172a);
}

.top-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.btn-outline {
  border: 1px solid var(--line, #e2e8f0);
  background: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  text-decoration: none;
  color: inherit;
  font-weight: 600;
  font-size: 0.85rem;
  cursor: pointer;
}

.btn-outline.danger {
  color: #b91c1c;
  border-color: #fecaca;
}

.inline-input {
  width: 100%;
  min-width: 140px;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 8px;
  padding: 8px 10px;
  font: inherit;
  margin-bottom: 6px;
}

.cam-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  align-items: center;
}

/* Session DSS */
.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  padding: 4px;
  background: #fff;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 12px;
}

.tab {
  flex: 1;
  border: 0;
  background: transparent;
  border-radius: 10px;
  padding: 10px 14px;
  font-weight: 700;
  font-size: 0.92rem;
  color: var(--muted, #64748b);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.tab.active {
  background: #0f172a;
  color: #fff;
}

.tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fbbf24;
  box-shadow: 0 0 0 0 rgba(251, 191, 36, 0.5);
  animation: pulse 1.4s ease-out infinite;
}

.catchup-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 14px;
  border: 1px solid var(--line, #e2e8f0);
  margin-bottom: 16px;
}

.catchup-bar.warn {
  background: #fff7ed;
  border-color: #fdba74;
}

.catchup-bar.ok {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.catchup-title {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: var(--muted, #64748b);
}

.catchup-msg {
  margin: 4px 0 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--ink, #0f172a);
}

.catchup-period {
  margin: 4px 0 0;
  font-size: 0.85rem;
  color: #9a3412;
  font-weight: 600;
}

.catchup-bar.ok .catchup-period {
  color: #0f766e;
}

.session-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 14px;
  border: 1px solid var(--line, #e2e8f0);
  margin-bottom: 16px;
}

.session-bar.on {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.session-bar.off {
  background: #fef2f2;
  border-color: #fecaca;
}

.session-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.session-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
}

.session-bar.on .session-dot {
  background: #0d9488;
  box-shadow: 0 0 0 4px rgba(13, 148, 136, 0.2);
}

.session-bar.off .session-dot {
  background: #dc2626;
  box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.15);
}

.session-title {
  margin: 0;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--muted, #64748b);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.session-state {
  margin: 2px 0 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--ink, #0f172a);
}

.btn-reconnect {
  border: 0;
  border-radius: 10px;
  padding: 10px 14px;
  font-weight: 700;
  font-size: 0.85rem;
  cursor: pointer;
  white-space: nowrap;
  background: #0f172a;
  color: #fff;
}

.session-bar.on .btn-reconnect {
  background: #0d9488;
  cursor: default;
  opacity: 0.85;
}

.btn-reconnect:disabled {
  opacity: 0.7;
  cursor: wait;
}

.session-bar.on .btn-reconnect:disabled {
  cursor: default;
}

.cameras-panel {
  margin-bottom: 16px;
}

.cameras-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.cameras-lead {
  margin-bottom: 16px;
  max-width: 70ch;
}

.cam-form {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 0.8fr auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 12px;
}

.user-form {
  grid-template-columns: 1fr 1fr auto;
}

.cam-add {
  width: auto;
  min-width: 180px;
  white-space: nowrap;
}

.ok-msg {
  margin: 0 0 12px;
  color: #0f766e;
  background: #ccfbf1;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.9rem;
}

.discover-list {
  margin: 8px 0 16px;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 12px;
}

.discover-list h3 {
  margin: 0 0 10px;
  font-size: 0.9rem;
}

.discover-list ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}

.discover-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #e2e8f0;
}

.discover-list li:last-child {
  border-bottom: 0;
}

.discover-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.channel-id,
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.8rem;
  color: #64748b;
}

.site-tag {
  font-size: 0.78rem;
  color: #0d9488;
  font-weight: 600;
}

.cam-table-wrap {
  overflow-x: auto;
}

.cam-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.cam-table th {
  text-align: left;
  font-size: 0.75rem;
  color: #64748b;
  padding: 8px 10px;
  border-bottom: 1px solid #e2e8f0;
}

.cam-table td {
  padding: 10px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.cam-table tr.inactive td {
  opacity: 0.55;
}

.badge-mini {
  margin-left: 6px;
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  background: #e0f2fe;
  color: #0369a1;
  border-radius: 999px;
  padding: 2px 7px;
}

.empty-cams {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 0.9rem;
}

/* Layout 2 colonnes */
.layout {
  display: grid;
  grid-template-columns: 1.4fr 0.9fr;
  gap: 16px;
  align-items: start;
}

.panel {
  background: #fff;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 14px;
  box-shadow: var(--shadow, 0 1px 2px rgba(15, 23, 42, 0.04));
  padding: 20px 22px;
}

.panel h2 {
  margin: 0 0 8px;
  font-size: 1.05rem;
  color: var(--ink, #0f172a);
}

.lead {
  margin: 0 0 18px;
  color: var(--muted, #64748b);
  font-size: 0.92rem;
  line-height: 1.45;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--muted, #64748b);
}

.field input {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 10px 12px;
  min-height: 44px;
  font: inherit;
  background: #fff;
}

.generate {
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: var(--blue, #2563eb);
  color: #fff;
  font-weight: 700;
  padding: 12px 16px;
  min-height: 46px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.generate:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.compare-btn {
  width: 100%;
  margin-top: 10px;
  min-height: 44px;
  justify-content: center;
}

.compare-block {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--line, #e2e8f0);
}

.compare-block.ok .progress-msg {
  color: #0f766e;
}

.compare-block.ko .progress-msg {
  color: #b91c1c;
}

.compare-facts {
  margin: 14px 0 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.compare-facts > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.compare-facts dt {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--muted, #64748b);
}

.compare-facts dd {
  margin: 0;
  font-weight: 700;
  font-size: 0.95rem;
}

.compare-table {
  margin-top: 14px;
}

.error {
  margin: 12px 0 0;
  color: #b42318;
  background: #fee4e2;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.9rem;
}

.progress-block {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--line, #e2e8f0);
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.progress-head h3 {
  margin: 0;
  font-size: 0.95rem;
}

.badge {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}

.badge.ok {
  background: #ccfbf1;
  color: #0f766e;
}

.badge.ko {
  background: #fee2e2;
  color: #b91c1c;
}

.badge.run {
  background: #ffedd5;
  color: #c2410c;
}

.progress-bar {
  height: 10px;
  background: #e2e8f0;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-fill {
  height: 100%;
  background: #0d9488;
  transition: width 0.3s ease;
}

.progress-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  font-size: 0.84rem;
  color: var(--muted, #64748b);
  font-weight: 600;
}

.progress-msg {
  margin: 10px 0 0;
  font-size: 0.9rem;
  color: var(--ink, #0f172a);
}

.side-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-panel h2 {
  margin-bottom: 14px;
}

.poll-panel .poll-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.poll-panel h2 {
  margin-bottom: 0;
}

.poll-lead {
  margin: 0 0 14px;
  color: var(--muted, #64748b);
  font-size: 0.88rem;
  line-height: 1.4;
}

.poll-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  font-weight: 700;
  color: #64748b;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 999px;
}

.poll-live.on {
  color: #0f766e;
  background: #ccfbf1;
}

.poll-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
}

.poll-live.on .poll-pulse {
  background: #0d9488;
  box-shadow: 0 0 0 0 rgba(13, 148, 136, 0.55);
  animation: pulse 1.4s ease-out infinite;
}

.poll-panel.live {
  border-color: #99f6e4;
  background: #f0fdfa;
}

.poll-panel.fail {
  border-color: #fecaca;
  background: #fff7f7;
}

.poll-panel.muted {
  opacity: 0.92;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(13, 148, 136, 0.45); }
  70% { box-shadow: 0 0 0 8px rgba(13, 148, 136, 0); }
  100% { box-shadow: 0 0 0 0 rgba(13, 148, 136, 0); }
}

.last-sync-panel {
  grid-column: 1 / -1;
}

.last-sync-panel h2 {
  margin-bottom: 14px;
}

.facts {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.facts-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
}

.facts > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.facts > div:last-of-type {
  border-bottom: 0;
  padding-bottom: 0;
}

.facts-2col > div {
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.facts-2col > div:nth-last-child(-n + 2) {
  border-bottom: 0;
  padding-bottom: 0;
}

.facts dt {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--muted, #64748b);
}

.facts dd {
  margin: 0;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--ink, #0f172a);
}

.facts dd.ok { color: #0d9488; }
.facts dd.ko { color: #dc2626; }
.facts dd.run { color: #d97706; }

.side-msg {
  margin: 14px 0 0;
  padding: 10px 12px;
  background: #f8fafc;
  border-radius: 10px;
  font-size: 0.85rem;
  color: #475569;
  line-height: 1.4;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  display: inline-block;
}

.spinner.dark {
  border-color: rgba(15, 23, 42, 0.2);
  border-top-color: #0f172a;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 860px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .facts-2col {
    grid-template-columns: 1fr;
  }

  .facts-2col > div:nth-last-child(-n + 2) {
    border-bottom: 1px solid #f1f5f9;
    padding-bottom: 10px;
  }

  .facts-2col > div:last-of-type {
    border-bottom: 0;
    padding-bottom: 0;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .session-bar,
  .catchup-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .cameras-head {
    flex-direction: column;
    align-items: stretch;
  }

  .form-grid,
  .cam-form,
  .user-form {
    grid-template-columns: 1fr;
  }
}
</style>
