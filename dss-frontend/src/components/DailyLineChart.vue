<template>
  <Line v-if="chartData" :data="chartData" :options="options" />
</template>

<script setup>
import { computed } from 'vue'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'
import { Line } from 'vue-chartjs'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend)

const props = defineProps({
  points: { type: Array, default: () => [] },
})

function formatDate(isoDate) {
  const [y, m, d] = isoDate.split('-')
  return `${d}/${m}`
}

const chartData = computed(() => ({
  labels: props.points.map((p) => formatDate(p.date)),
  datasets: [
    {
      label: 'Entrées',
      data: props.points.map((p) => p.entries),
      borderColor: '#0d9488',
      backgroundColor: 'rgba(13, 148, 136, 0.12)',
      tension: 0.3,
      fill: false,
      pointRadius: 0,
      borderWidth: 2.5,
    },
    {
      label: 'Sorties',
      data: props.points.map((p) => p.exits),
      borderColor: '#7c3aed',
      backgroundColor: 'rgba(124, 58, 237, 0.12)',
      tension: 0.3,
      fill: false,
      pointRadius: 0,
      borderWidth: 2.5,
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
