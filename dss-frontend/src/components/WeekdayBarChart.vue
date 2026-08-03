<template>
  <Bar v-if="chartData" :data="chartData" :options="options" />
</template>

<script setup>
import { computed } from 'vue'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'
import { Bar } from 'vue-chartjs'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

const props = defineProps({
  rows: { type: Array, default: () => [] },
})

const chartData = computed(() => ({
  labels: props.rows.map((r) => r.weekday),
  datasets: [
    {
      label: 'Entrées',
      data: props.rows.map((r) => r.entries),
      backgroundColor: '#0d9488',
      borderRadius: 4,
      maxBarThickness: 28,
    },
    {
      label: 'Sorties',
      data: props.rows.map((r) => r.exits),
      backgroundColor: '#7c3aed',
      borderRadius: 4,
      maxBarThickness: 28,
    },
  ],
}))

const options = {
  responsive: true,
  maintainAspectRatio: true,
  plugins: {
    legend: { position: 'top', align: 'end' },
  },
  scales: {
    x: { grid: { display: false } },
    y: { beginAtZero: true, grid: { color: '#eef2f7' } },
  },
}
</script>
