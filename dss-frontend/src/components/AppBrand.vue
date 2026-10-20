<template>
  <div class="app-brand" :class="size">
    <p class="word">
      <span class="gab">GAB</span><span class="track">TRACK</span>
    </p>
    <p v-if="subtitle" class="sub">{{ subtitle }}</p>
  </div>
</template>

<script setup>
defineProps({
  subtitle: { type: String, default: '' },
  size: { type: String, default: 'md' },
})
</script>

<style scoped>
.app-brand {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.word {
  margin: 0;
  line-height: 1;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.gab {
  color: #64748b;
  font-weight: 600;
}

.track {
  color: #0f172a;
  letter-spacing: 0.08em;
}

.sub {
  margin: 0;
  color: #64748b;
  font-weight: 500;
}

.md .word {
  font-size: 1.28rem;
}

.md .sub {
  font-size: 0.78rem;
}

.lg .word {
  font-size: 1.7rem;
}

.lg .sub {
  font-size: 0.9rem;
  margin-top: 4px;
}

.sm .word {
  font-size: 1.12rem;
}

.sm .sub {
  font-size: 0.72rem;
}
</style>
