import * as XLSX from 'xlsx'
import { jsPDF } from 'jspdf'
import autoTable from 'jspdf-autotable'

function formatDateFr(isoDate) {
  if (!isoDate) return ''
  const [y, m, d] = isoDate.split('-')
  return `${d}/${m}/${y}`
}

function displayTimeTo(timeTo) {
  return timeTo === '24:00' ? '00:00' : timeTo
}

function selectedCameraIds(filters) {
  if (Array.isArray(filters.cameras)) return filters.cameras
  if (!filters.camera || filters.camera === 'all') return []
  if (Array.isArray(filters.camera)) return filters.camera
  return String(filters.camera)
    .split(',')
    .map((id) => id.trim())
    .filter(Boolean)
}

function cameraLabel(filters, cameras) {
  const ids = selectedCameraIds(filters)
  if (ids.length === 0) return 'Toutes'
  return ids
    .map((id) => cameras?.find((c) => c.channelId === id)?.name || id)
    .join(', ')
}

function buildFileStamp(filters) {
  return `${filters.from}_${filters.to}_${filters.timeFrom.replace(':', '')}-${filters.timeTo.replace(':', '')}`
}

function metaRows(filters, cameras) {
  return [
    ['Rapport', 'People Counting – Rapport personnalisé'],
    ['Période', `${formatDateFr(filters.from)} → ${formatDateFr(filters.to)}`],
    ['Tranche horaire', `${filters.timeFrom} → ${displayTimeTo(filters.timeTo)}`],
    ['Caméras', cameraLabel(filters, cameras)],
    ['Groupe par', filters.groupBy || 'Jour'],
    ['Exporté le', new Date().toLocaleString('fr-FR')],
  ]
}

function kpiRows(report) {
  const k = report.kpis || {}
  return [
    ['Total entrées', k.totalEntries ?? 0],
    ['Total sorties', k.totalExits ?? 0],
    ['Présence nette', k.netPresence ?? 0],
    ['Présence moyenne / jour', k.averageDailyPresence ?? 0],
    ['Nombre de jours', k.dayCount ?? 0],
  ]
}

function dailyRows(report) {
  return (report.dailyDetails || []).map((row) => [
    formatDateFr(row.date),
    row.entries ?? 0,
    row.exits ?? 0,
    row.presenceEndOfSlot ?? 0,
  ])
}

function weekdayRows(report) {
  return (report.weekdayDistribution || []).map((row) => [
    row.weekday ?? '',
    row.entries ?? 0,
    row.exits ?? 0,
  ])
}

export function exportExcel({ report, filters, cameras }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const wb = XLSX.utils.book_new()

  const resumeAoA = [
    ...metaRows(filters, cameras),
    [],
    ['Indicateur', 'Valeur'],
    ...kpiRows(report),
  ]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(resumeAoA), 'Résumé')

  const detailAoA = [
    ['Date', 'Entrées', 'Sorties', 'Présence (fin tranche)'],
    ...dailyRows(report),
    [
      'Total période',
      report.kpis?.totalEntries ?? 0,
      report.kpis?.totalExits ?? 0,
      report.kpis?.netPresence ?? 0,
    ],
  ]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(detailAoA), 'Détail journalier')

  const weekdayAoA = [['Jour', 'Entrées', 'Sorties'], ...weekdayRows(report)]
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(weekdayAoA), 'Par jour de semaine')

  XLSX.writeFile(wb, `people-counting_${buildFileStamp(filters)}.xlsx`)
}

export function exportPdf({ report, filters, cameras }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const margin = 14
  let y = 16

  doc.setFontSize(16)
  doc.setTextColor(15, 23, 42)
  doc.text('People Counting – Rapport personnalisé', margin, y)
  y += 10

  doc.setFontSize(10)
  doc.setTextColor(71, 85, 105)
  const meta = [
    `Période : ${formatDateFr(filters.from)} → ${formatDateFr(filters.to)}`,
    `Tranche horaire : ${filters.timeFrom} → ${displayTimeTo(filters.timeTo)}`,
    `Caméras : ${cameraLabel(filters, cameras)}`,
    `Groupe par : ${filters.groupBy || 'Jour'}`,
    `Exporté le : ${new Date().toLocaleString('fr-FR')}`,
  ]
  meta.forEach((line) => {
    doc.text(line, margin, y)
    y += 5
  })
  y += 4

  doc.setFontSize(12)
  doc.setTextColor(15, 23, 42)
  doc.text('Indicateurs', margin, y)
  y += 2

  autoTable(doc, {
    startY: y,
    head: [['Indicateur', 'Valeur']],
    body: kpiRows(report).map(([label, value]) => [label, String(value)]),
    theme: 'striped',
    headStyles: { fillColor: [13, 148, 136] },
    margin: { left: margin, right: margin },
  })

  y = (doc.lastAutoTable?.finalY ?? y) + 10
  doc.setFontSize(12)
  doc.text(
    `Détail par jour (${filters.timeFrom} - ${displayTimeTo(filters.timeTo)})`,
    margin,
    y,
  )

  autoTable(doc, {
    startY: y + 2,
    head: [['Date', 'Entrées', 'Sorties', 'Présence (fin tranche)']],
    body: [
      ...dailyRows(report).map((r) => r.map(String)),
      [
        'Total période',
        String(report.kpis?.totalEntries ?? 0),
        String(report.kpis?.totalExits ?? 0),
        String(report.kpis?.netPresence ?? 0),
      ],
    ],
    theme: 'striped',
    headStyles: { fillColor: [13, 148, 136] },
    margin: { left: margin, right: margin },
    styles: { fontSize: 9 },
  })

  y = (doc.lastAutoTable?.finalY ?? y) + 10
  if (y > 250) {
    doc.addPage()
    y = 16
  }

  doc.setFontSize(12)
  doc.setTextColor(15, 23, 42)
  doc.text('Répartition par jour de la semaine', margin, y)

  autoTable(doc, {
    startY: y + 2,
    head: [['Jour', 'Entrées', 'Sorties']],
    body: weekdayRows(report).map((r) => r.map(String)),
    theme: 'striped',
    headStyles: { fillColor: [124, 58, 237] },
    margin: { left: margin, right: margin },
    styles: { fontSize: 9 },
  })

  doc.save(`people-counting_${buildFileStamp(filters)}.pdf`)
}
