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
    ['Rapport', 'DataExpert – Rapport personnalisé'],
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

function formatPdfNumber(value) {
  return Number(value ?? 0)
    .toLocaleString('fr-FR')
    .replace(/\u202f|\u00a0/g, ' ')
}

const PDF = {
  pageW: 210,
  pageH: 297,
  margin: 16,
  navy: [15, 23, 42],
  ink: [30, 41, 59],
  muted: [100, 116, 139],
  line: [226, 232, 240],
  wash: [248, 250, 252],
  white: [255, 255, 255],
  cyan: [8, 145, 178],
  teal: [13, 148, 136],
  blue: [37, 99, 235],
  violet: [124, 58, 237],
}

function pdfTableTheme() {
  return {
    theme: 'plain',
    margin: { left: PDF.margin, right: PDF.margin, top: 22, bottom: 18 },
    styles: {
      font: 'helvetica',
      fontSize: 8.5,
      textColor: PDF.ink,
      cellPadding: { top: 3.4, bottom: 3.4, left: 4, right: 4 },
      lineColor: PDF.line,
      lineWidth: 0.15,
      valign: 'middle',
    },
    headStyles: {
      fillColor: PDF.navy,
      textColor: PDF.white,
      fontStyle: 'bold',
      fontSize: 8,
      cellPadding: { top: 4, bottom: 4, left: 4, right: 4 },
    },
    alternateRowStyles: { fillColor: PDF.wash },
    footStyles: {
      fillColor: [241, 245, 249],
      textColor: PDF.navy,
      fontStyle: 'bold',
      fontSize: 8.5,
    },
  }
}

function drawPdfHeader(doc, { period, hours, cameras, exportedAt, compact }) {
  const h = compact ? 16 : 40
  doc.setFillColor(...PDF.navy)
  doc.rect(0, 0, PDF.pageW, h, 'F')
  doc.setFillColor(...PDF.cyan)
  doc.rect(0, h, PDF.pageW, 1.1, 'F')

  doc.setTextColor(255, 255, 255)
  if (compact) {
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(10)
    doc.text('DataExpert', PDF.margin, 10)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.setTextColor(148, 163, 184)
    doc.text(`${period}  ·  ${hours}`, PDF.pageW - PDF.margin, 10, { align: 'right' })
    return h + 6
  }

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(18)
  doc.text('DataExpert', PDF.margin, 16)
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(9)
  doc.setTextColor(103, 232, 249)
  doc.text('RAPPORT DE FRÉQUENTATION', PDF.margin, 23)

  doc.setTextColor(203, 213, 225)
  doc.setFontSize(8)
  doc.text(exportedAt, PDF.pageW - PDF.margin, 12, { align: 'right' })
  doc.setTextColor(255, 255, 255)
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(10)
  doc.text(period, PDF.pageW - PDF.margin, 20, { align: 'right' })
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(148, 163, 184)
  doc.text(hours, PDF.pageW - PDF.margin, 26, { align: 'right' })

  const cam = doc.splitTextToSize(`Caméras : ${cameras}`, 178)
  doc.setTextColor(203, 213, 225)
  doc.text(cam.slice(0, 2), PDF.margin, 33)
  return 48
}

function drawPdfFooter(doc, page, total) {
  doc.setFillColor(...PDF.wash)
  doc.rect(0, 287, PDF.pageW, 10, 'F')
  doc.setDrawColor(...PDF.line)
  doc.setLineWidth(0.2)
  doc.line(PDF.margin, 287, PDF.pageW - PDF.margin, 287)
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(7.5)
  doc.setTextColor(...PDF.muted)
  doc.text('DataExpert  ·  Confidentiel', PDF.margin, 293)
  doc.text(`Page ${page} / ${total}`, PDF.pageW - PDF.margin, 293, { align: 'right' })
}

function drawKpiCards(doc, y, kpis) {
  const cards = [
    { label: 'Entrées', value: kpis.totalEntries, hint: 'personnes', color: PDF.teal },
    { label: 'Sorties', value: kpis.totalExits, hint: 'personnes', color: PDF.blue },
    { label: 'Présence / jour', value: kpis.averageDailyPresence, hint: 'moyenne', color: PDF.cyan },
    { label: 'Présence nette', value: kpis.netPresence, hint: `${kpis.dayCount ?? 0} jour(s)`, color: PDF.violet },
  ]
  const gap = 4
  const width = (PDF.pageW - PDF.margin * 2 - gap * 3) / 4
  const height = 28

  cards.forEach((card, i) => {
    const x = PDF.margin + i * (width + gap)
    doc.setFillColor(...PDF.white)
    doc.setDrawColor(...PDF.line)
    doc.setLineWidth(0.25)
    doc.roundedRect(x, y, width, height, 2.2, 2.2, 'FD')
    doc.setFillColor(...card.color)
    doc.rect(x, y, 1.6, height, 'F')

    doc.setFont('helvetica', 'normal')
    doc.setFontSize(7)
    doc.setTextColor(...PDF.muted)
    doc.text(card.label.toUpperCase(), x + 6, y + 7)

    doc.setFont('helvetica', 'bold')
    doc.setFontSize(14)
    doc.setTextColor(...PDF.navy)
    doc.text(formatPdfNumber(card.value), x + 6, y + 17)

    doc.setFont('helvetica', 'normal')
    doc.setFontSize(7)
    doc.setTextColor(148, 163, 184)
    doc.text(card.hint, x + 6, y + 23)
  })
  return y + height + 10
}

function drawSectionTitle(doc, y, title) {
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(11)
  doc.setTextColor(...PDF.navy)
  doc.text(title, PDF.margin, y)
  doc.setDrawColor(...PDF.cyan)
  doc.setLineWidth(0.8)
  const tw = doc.getTextWidth(title)
  doc.line(PDF.margin, y + 2.2, PDF.margin + Math.min(tw, 42), y + 2.2)
  return y + 7
}

export function exportPdf({ report, filters, cameras }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const period = `${formatDateFr(filters.from)}  →  ${formatDateFr(filters.to)}`
  const hours = `${filters.timeFrom} – ${displayTimeTo(filters.timeTo)}`
  const camerasText = cameraLabel(filters, cameras)
  const exportedAt = `Exporté le ${new Date().toLocaleString('fr-FR')}`
  const kpis = report.kpis || {}

  const headerMeta = { period, hours, cameras: camerasText, exportedAt }
  let y = drawPdfHeader(doc, headerMeta)
  y = drawKpiCards(doc, y, kpis)
  y = drawSectionTitle(doc, y, `Détail journalier  ·  ${hours}`)

  const tableTheme = pdfTableTheme()
  const onTablePage = () => {
    if (doc.internal.getCurrentPageInfo().pageNumber > 1) {
      drawPdfHeader(doc, { ...headerMeta, compact: true })
    }
  }

  autoTable(doc, {
    ...tableTheme,
    startY: y,
    head: [['Date', 'Entrées', 'Sorties', 'Présence']],
    body: dailyRows(report).map((row) => [
      row[0],
      formatPdfNumber(row[1]),
      formatPdfNumber(row[2]),
      formatPdfNumber(row[3]),
    ]),
    foot: [[
      'Total période',
      formatPdfNumber(kpis.totalEntries),
      formatPdfNumber(kpis.totalExits),
      formatPdfNumber(kpis.netPresence),
    ]],
    showHead: 'everyPage',
    columnStyles: {
      0: { cellWidth: 46, fontStyle: 'bold' },
      1: { halign: 'right' },
      2: { halign: 'right' },
      3: { halign: 'right' },
    },
    didDrawPage: onTablePage,
  })

  y = (doc.lastAutoTable?.finalY ?? y) + 12
  if (y > 230) {
    doc.addPage()
    y = 24
  }
  y = drawSectionTitle(doc, y, 'Répartition par jour de la semaine')

  const weekBody = weekdayRows(report)
  const maxEntries = Math.max(1, ...weekBody.map((r) => Number(r[1]) || 0))

  autoTable(doc, {
    ...tableTheme,
    startY: y,
    head: [['Jour', 'Entrées', 'Sorties', 'Volume']],
    body: weekBody.map((row) => [
      row[0],
      formatPdfNumber(row[1]),
      formatPdfNumber(row[2]),
      '',
    ]),
    columnStyles: {
      0: { cellWidth: 42, fontStyle: 'bold' },
      1: { halign: 'right', cellWidth: 32 },
      2: { halign: 'right', cellWidth: 32 },
      3: { cellWidth: 'auto' },
    },
    didDrawPage: onTablePage,
    didDrawCell: (data) => {
      if (data.section !== 'body' || data.column.index !== 3) return
      const entries = Number(weekBody[data.row.index]?.[1]) || 0
      const maxW = data.cell.width - 8
      const barW = Math.max(1.2, (entries / maxEntries) * maxW)
      const bx = data.cell.x + 4
      const by = data.cell.y + data.cell.height / 2 - 1.6
      doc.setFillColor(226, 232, 240)
      doc.roundedRect(bx, by, maxW, 3.2, 1, 1, 'F')
      doc.setFillColor(...PDF.cyan)
      doc.roundedRect(bx, by, barW, 3.2, 1, 1, 'F')
    },
  })

  const total = doc.getNumberOfPages()
  for (let i = 1; i <= total; i += 1) {
    doc.setPage(i)
    drawPdfFooter(doc, i, total)
  }

  doc.save(`DataExpert_${buildFileStamp(filters)}.pdf`)
}
