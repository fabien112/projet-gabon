<template>
  <div class="ms" ref="root">
    <button
      type="button"
      class="ms-trigger"
      :class="{ open, invalid }"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="ms-trigger-text">{{ triggerLabel }}</span>
      <span class="ms-caret" aria-hidden="true">▾</span>
    </button>

    <div v-if="open" class="ms-panel" role="listbox" aria-multiselectable="true">
      <button
        type="button"
        class="ms-option all"
        :class="{ checked: allSelected }"
        @click="toggleAll"
      >
        <span class="ms-check" aria-hidden="true">{{ allSelected ? '✓' : '' }}</span>
        <span>Toutes</span>
      </button>
      <div class="ms-sep" />
      <button
        v-for="cam in options"
        :key="cam.channelId"
        type="button"
        class="ms-option"
        :class="{ checked: isSelected(cam.channelId), disabled: isDisabled(cam.channelId) }"
        :disabled="isDisabled(cam.channelId)"
        role="option"
        :aria-selected="isSelected(cam.channelId)"
        @click="toggle(cam.channelId)"
      >
        <span class="ms-check" aria-hidden="true">{{ isSelected(cam.channelId) ? '✓' : '' }}</span>
        <span class="ms-name" :title="cam.name">{{ cam.name }}</span>
        <span v-if="cam.site" class="ms-site">{{ cam.site }}</span>
      </button>
      <p v-if="options.length === 0" class="ms-empty">Aucune caméra</p>
      <p class="ms-footer">{{ modelValue.length }}/{{ max }} sélectionnée(s)</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  options: { type: Array, default: () => [] },
  modelValue: { type: Array, default: () => [] },
  max: { type: Number, default: 3 },
  invalid: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const root = ref(null)

const allSelected = computed(() => {
  const opts = props.options
  if (!opts.length) return false
  const selected = new Set(props.modelValue)
  return opts.every((c) => selected.has(c.channelId))
})

const triggerLabel = computed(() => {
  const n = props.modelValue.length
  if (n === 0) return 'Choisir une caméra…'
  if (allSelected.value) return `Toutes (${props.options.length})`
  if (n === 1) {
    const cam = props.options.find((c) => c.channelId === props.modelValue[0])
    return cam?.name || '1 caméra'
  }
  return `${n} caméras`
})

function isSelected(id) {
  return props.modelValue.includes(id)
}

function isDisabled(id) {
  return !isSelected(id) && props.modelValue.length >= props.max
}

function setValue(next) {
  emit('update:modelValue', next)
}

function toggle(id) {
  if (isSelected(id)) {
    setValue(props.modelValue.filter((x) => x !== id))
    return
  }
  if (props.modelValue.length >= props.max) return
  setValue([...props.modelValue, id])
}

function toggleAll() {
  if (allSelected.value) {
    setValue([])
    return
  }
  setValue(props.options.slice(0, props.max).map((c) => c.channelId))
}

function close() {
  open.value = false
}

function onDocPointer(e) {
  if (!open.value || !root.value) return
  if (!root.value.contains(e.target)) close()
}

onMounted(() => document.addEventListener('pointerdown', onDocPointer))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointer))
</script>

<style scoped>
.ms {
  position: relative;
  min-width: 0;
}

.ms-trigger {
  width: 100%;
  height: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  font-size: 0.88rem;
  color: var(--ink);
  text-align: left;
  box-sizing: border-box;
}

.ms-trigger:hover {
  border-color: #cbd5e1;
}

.ms-trigger.open {
  border-color: var(--blue);
  box-shadow: 0 0 0 3px var(--blue-soft);
}

.ms-trigger.invalid {
  border-color: #f97066;
}

.ms-trigger-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-caret {
  flex-shrink: 0;
  color: var(--muted);
  font-size: 0.75rem;
  transition: transform 0.15s ease;
}

.ms-trigger.open .ms-caret {
  transform: rotate(180deg);
}

.ms-panel {
  position: absolute;
  z-index: 40;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  min-width: 240px;
  max-height: 280px;
  overflow: auto;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  padding: 6px;
}

.ms-option {
  width: 100%;
  display: grid;
  grid-template-columns: 22px 1fr auto;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 9px 10px;
  cursor: pointer;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 500;
  color: var(--ink);
  text-align: left;
}

.ms-option:hover:not(.disabled) {
  background: #f1f5f9;
}

.ms-option.checked {
  background: var(--blue-soft);
  color: var(--blue);
  font-weight: 600;
}

.ms-option.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ms-option.all {
  font-weight: 700;
}

.ms-check {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1.5px solid #cbd5e1;
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  background: #fff;
  color: var(--blue);
}

.ms-option.checked .ms-check {
  border-color: var(--blue);
  background: #fff;
}

.ms-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-site {
  font-size: 0.72rem;
  color: var(--muted);
  font-weight: 600;
}

.ms-sep {
  height: 1px;
  background: var(--line);
  margin: 4px 6px;
}

.ms-empty,
.ms-footer {
  margin: 0;
  padding: 8px 10px 6px;
  font-size: 0.72rem;
  color: var(--muted);
  font-weight: 600;
}

.ms-footer {
  border-top: 1px solid var(--line);
  margin-top: 4px;
}
</style>
