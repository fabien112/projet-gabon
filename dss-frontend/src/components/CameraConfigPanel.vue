<template>
  <div class="cam-layout">
    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>Caméras de comptage</h2>
          <p class="lead">
            Ces caméras apparaissent dans les rapports et sont alimentées par le poll
            automatique et la synchronisation historique.
          </p>
        </div>
        <span class="count-badge">{{ configured.length }} active(s)</span>
      </div>

      <p v-if="listError" class="error">{{ listError }}</p>

      <div v-if="configured.length === 0 && !loadingConfigured" class="empty">
        Aucune caméra configurée. Ajoutez-en depuis la liste DSS ci-dessous.
      </div>

      <ul v-else class="cam-list">
        <li v-for="cam in configured" :key="cam.id" class="cam-row">
          <div class="cam-info">
            <strong>{{ cam.name }}</strong>
            <span class="meta">
              <template v-if="cam.site">{{ cam.site }} · </template>
              {{ cam.channelId }}
            </span>
          </div>
          <button
            type="button"
            class="btn-danger"
            :disabled="removingId === cam.id"
            @click="remove(cam)"
          >
            {{ removingId === cam.id ? 'Retrait…' : 'Retirer' }}
          </button>
        </li>
      </ul>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>Ajouter depuis DSS</h2>
          <p class="lead">
            Canaux vidéo principaux disponibles sur le serveur. Une fois ajoutée,
            la caméra est comptée exactement comme les autres.
          </p>
        </div>
        <button
          type="button"
          class="btn-outline"
          :disabled="loadingAvailable || !dssReady"
          @click="loadAvailable"
        >
          {{ loadingAvailable ? 'Chargement…' : 'Rafraîchir DSS' }}
        </button>
      </div>

      <p v-if="!dssReady" class="warn">
        Session DSS inactive — cliquez sur Reconnecter ci-dessus.
      </p>
      <p v-if="availableError" class="error">{{ availableError }}</p>
      <p v-if="actionError" class="error">{{ actionError }}</p>
      <p v-if="actionOk" class="ok">{{ actionOk }}</p>

      <label class="search">
        <span class="label">Rechercher</span>
        <input v-model="query" type="search" placeholder="Nom, site ou identifiant…" />
      </label>

      <div v-if="loadingAvailable" class="empty">Chargement de l’arbre DSS…</div>
      <div v-else-if="filteredAvailable.length === 0" class="empty">
        {{ available.length === 0 ? 'Aucune caméra DSS listée.' : 'Aucun résultat.' }}
      </div>
      <ul v-else class="cam-list">
        <li v-for="cam in filteredAvailable" :key="cam.channelId" class="cam-row">
          <div class="cam-info">
            <strong>{{ cam.name || cam.channelId }}</strong>
            <span class="meta">
              <template v-if="cam.deviceName">{{ cam.deviceName }} · </template>
              {{ cam.channelId }}
            </span>
          </div>
          <span v-if="cam.alreadyAdded" class="added">Déjà ajoutée</span>
          <button
            v-else
            type="button"
            class="btn-add"
            :disabled="addingId === cam.channelId"
            @click="add(cam)"
          >
            {{ addingId === cam.channelId ? 'Ajout…' : 'Ajouter' }}
          </button>
        </li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import {
  addCamera,
  fetchAvailableCameras,
  fetchConfiguredCameras,
  removeCamera,
} from '../api/cameras'

const props = defineProps({
  dssReady: { type: Boolean, default: false },
})

const configured = ref([])
const available = ref([])
const query = ref('')
const loadingConfigured = ref(false)
const loadingAvailable = ref(false)
const listError = ref('')
const availableError = ref('')
const actionError = ref('')
const actionOk = ref('')
const addingId = ref(null)
const removingId = ref(null)

const filteredAvailable = computed(() => {
  const q = query.value.trim().toLowerCase()
  const rows = available.value
  if (!q) return rows
  return rows.filter((cam) => {
    const hay = [cam.name, cam.deviceName, cam.channelId]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return hay.includes(q)
  })
})

async function loadConfigured() {
  loadingConfigured.value = true
  listError.value = ''
  try {
    configured.value = await fetchConfiguredCameras()
  } catch (e) {
    listError.value = e?.response?.data?.message || e.message || 'Impossible de lister les caméras'
  } finally {
    loadingConfigured.value = false
  }
}

async function loadAvailable() {
  if (!props.dssReady) {
    availableError.value = 'Session DSS inactive'
    return
  }
  loadingAvailable.value = true
  availableError.value = ''
  try {
    available.value = await fetchAvailableCameras()
  } catch (e) {
    availableError.value =
      e?.response?.data?.message || e.message || 'Impossible de lister les caméras DSS'
  } finally {
    loadingAvailable.value = false
  }
}

async function add(cam) {
  actionError.value = ''
  actionOk.value = ''
  addingId.value = cam.channelId
  try {
    await addCamera(cam.channelId)
    actionOk.value = `${cam.name || cam.channelId} ajoutée. Lancez une sync ou attendez le poll pour ses données.`
    await Promise.all([loadConfigured(), loadAvailable()])
  } catch (e) {
    actionError.value = e?.response?.data?.message || e.message || 'Échec de l’ajout'
  } finally {
    addingId.value = null
  }
}

async function remove(cam) {
  actionError.value = ''
  actionOk.value = ''
  removingId.value = cam.id
  try {
    await removeCamera(cam.id)
    actionOk.value = `${cam.name} retirée du comptage (l’historique local est conservé).`
    await Promise.all([loadConfigured(), props.dssReady ? loadAvailable() : Promise.resolve()])
  } catch (e) {
    actionError.value = e?.response?.data?.message || e.message || 'Échec du retrait'
  } finally {
    removingId.value = null
  }
}

watch(
  () => props.dssReady,
  (ready) => {
    if (ready && available.value.length === 0) {
      loadAvailable()
    }
  }
)

onMounted(async () => {
  await loadConfigured()
  if (props.dssReady) {
    await loadAvailable()
  }
})
</script>

<style scoped>
.cam-layout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  background: #fff;
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 14px;
  box-shadow: var(--shadow, 0 1px 2px rgba(15, 23, 42, 0.04));
  padding: 20px 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.panel h2 {
  margin: 0 0 6px;
  font-size: 1.05rem;
}

.lead {
  margin: 0;
  color: var(--muted, #64748b);
  font-size: 0.9rem;
  line-height: 1.45;
}

.count-badge {
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ccfbf1;
  color: #0f766e;
}

.search {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.label {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--muted, #64748b);
}

.search input {
  border: 1px solid var(--line, #e2e8f0);
  border-radius: 10px;
  padding: 10px 12px;
  min-height: 44px;
  font: inherit;
}

.cam-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cam-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  background: #f8fafc;
}

.cam-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cam-info strong {
  font-size: 0.92rem;
}

.meta {
  font-size: 0.75rem;
  color: var(--muted, #64748b);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-outline,
.btn-add,
.btn-danger {
  border-radius: 10px;
  padding: 8px 12px;
  font-weight: 700;
  font-size: 0.82rem;
  cursor: pointer;
  white-space: nowrap;
}

.btn-outline {
  border: 1px solid var(--line, #e2e8f0);
  background: #fff;
  color: inherit;
}

.btn-add {
  border: 0;
  background: var(--blue, #2563eb);
  color: #fff;
}

.btn-danger {
  border: 1px solid #fecaca;
  background: #fff;
  color: #b91c1c;
}

.btn-add:disabled,
.btn-danger:disabled,
.btn-outline:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.added {
  font-size: 0.78rem;
  font-weight: 700;
  color: #0f766e;
}

.empty,
.error,
.warn,
.ok {
  margin: 0 0 12px;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 0.9rem;
}

.empty {
  background: #f8fafc;
  color: var(--muted, #64748b);
}

.error {
  color: #b42318;
  background: #fee4e2;
}

.warn {
  color: #9a3412;
  background: #ffedd5;
}

.ok {
  color: #0f766e;
  background: #ccfbf1;
}

@media (max-width: 700px) {
  .panel-head,
  .cam-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
