import XLSX from 'xlsx-js-style'
import { jsPDF } from 'jspdf'
import autoTable from 'jspdf-autotable'
import { sortDaysNewestFirst } from './sortReportDays'

function formatDateFr(isoDate) {
  if (!isoDate) return ''
  const [y, m, d] = isoDate.split('-')
  return `${d}/${m}/${y}`
}

function displayTimeTo(timeTo) {
  if (timeTo === '24:00' || timeTo === '23:59') return '24:00'
  return timeTo
}

function selectedSiteIds(filters) {
  const ids = Array.isArray(filters.sites) ? filters.sites : []
  return ids.filter((id) => id !== 'all')
}

function siteLabel(filters, sites) {
  const ids = selectedSiteIds(filters)
  const allSelected = sites?.length > 0 && sites.every((s) => ids.includes(s.id))
  if (ids.length === 0 || allSelected) return 'Tous les sites'
  return ids
    .map((id) => sites?.find((s) => s.id === id)?.name || id)
    .join(', ')
}

function buildFileStamp(filters) {
  return `${filters.from}_${filters.to}_${filters.timeFrom.replace(':', '')}-${filters.timeTo.replace(':', '')}`
}

function n(value) {
  return Number(value ?? 0)
}

function presenceOf(entries, exits) {
  return n(entries) - n(exits)
}

function formatAvgFr(value) {
  return n(value).toLocaleString('fr-FR', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })
}

function formatPdfNumber(value) {
  return n(value)
    .toLocaleString('fr-FR')
    .replace(/\u202f|\u00a0/g, ' ')
}

function peakIndexes(rows, getIn, getOut) {
  const list = rows || []
  let maxIn = 0
  let maxOut = 0
  for (const row of list) {
    maxIn = Math.max(maxIn, n(getIn(row)))
    maxOut = Math.max(maxOut, n(getOut(row)))
  }
  return {
    inIdx: new Set(list.map((row, i) => (maxIn > 0 && n(getIn(row)) === maxIn ? i : -1)).filter((i) => i >= 0)),
    outIdx: new Set(list.map((row, i) => (maxOut > 0 && n(getOut(row)) === maxOut ? i : -1)).filter((i) => i >= 0)),
  }
}

function fx(formula) {
  return { t: 'n', f: formula }
}

const XL = {
  navy: '1A2332',
  line: 'D6DBE2',
  muted: '5C6778',
  subtotal: 'EEF2F6',
  total: 'E2E8F0',
  green: 'D1FAE5',
  greenInk: '047857',
  red: 'FEE2E2',
  redInk: 'B91C1C',
}
const INT_FMT = '#,##0;-#,##0;0'
const AVG_FMT = '0.0'
const thinLine = { style: 'thin', color: { rgb: XL.line } }
const GRID = { top: thinLine, bottom: thinLine, left: thinLine, right: thinLine }
const solid = (rgb) => ({ patternType: 'solid', fgColor: { rgb } })

const XL_STYLE = {
  header: {
    font: { bold: true, color: { rgb: 'FFFFFF' } },
    fill: solid(XL.navy),
    alignment: { horizontal: 'center', vertical: 'center', wrapText: true },
    border: GRID,
  },
  body: { border: GRID, alignment: { vertical: 'center' } },
  subtotal: { font: { bold: true }, fill: solid(XL.subtotal) },
  total: {
    font: { bold: true },
    fill: solid(XL.total),
    border: { top: { style: 'medium', color: { rgb: XL.navy } } },
  },
  label: { font: { bold: true, color: { rgb: XL.muted } } },
}

function addStyle(sheet, r, c, style) {
  const ref = XLSX.utils.encode_cell({ r, c })
  if (!sheet[ref]) sheet[ref] = { t: 's', v: '' }
  const prev = sheet[ref].s || {}
  sheet[ref].s = {
    ...prev,
    ...style,
    font: { ...prev.font, ...style.font },
    fill: style.fill ?? prev.fill,
    alignment: { ...prev.alignment, ...style.alignment },
    border: { ...prev.border, ...style.border },
  }
}

/** Lignes 0-based. {@code totalRow} est optionnel (dernière ligne de total général). */
function styleTable(sheet, {
  headerRow,
  lastRow,
  colCount,
  totalRow = -1,
  intCols = [],
  avgCols = [],
  boldCols = [],
  noteCols = [],
  subtotalRows = new Set(),
  peaks = new Map(),
  entriesCol = 1,
  exitsCol = 2,
}) {
  for (let c = 0; c < colCount; c += 1) addStyle(sheet, headerRow, c, XL_STYLE.header)
  for (let r = headerRow + 1; r <= lastRow; r += 1) {
    for (let c = 0; c < colCount; c += 1) {
      addStyle(sheet, r, c, XL_STYLE.body)
      if (intCols.includes(c)) addStyle(sheet, r, c, { numFmt: INT_FMT, alignment: { horizontal: 'right' } })
      if (avgCols.includes(c)) addStyle(sheet, r, c, { numFmt: AVG_FMT, alignment: { horizontal: 'right' } })
      if (boldCols.includes(c)) addStyle(sheet, r, c, { font: { bold: true } })
      if (noteCols.includes(c)) addStyle(sheet, r, c, { font: { italic: true, color: { rgb: XL.muted } } })
      if (subtotalRows.has(r)) addStyle(sheet, r, c, XL_STYLE.subtotal)
      if (r === totalRow) addStyle(sheet, r, c, XL_STYLE.total)
    }
    const peak = peaks.get(r)
    if (peak?.in) {
      addStyle(sheet, r, entriesCol, { fill: solid(XL.green), font: { bold: true, color: { rgb: XL.greenInk } } })
    }
    if (peak?.out) {
      addStyle(sheet, r, exitsCol, { fill: solid(XL.red), font: { bold: true, color: { rgb: XL.redInk } } })
    }
  }
  sheet['!rows'] = sheet['!rows'] || []
  sheet['!rows'][headerRow] = { hpt: 22 }
}

function styleKeyValue(sheet, fromRow, toRow, valueStyle = {}) {
  for (let r = fromRow; r <= toRow; r += 1) {
    addStyle(sheet, r, 0, XL_STYLE.label)
    addStyle(sheet, r, 1, { alignment: { horizontal: 'left' }, ...valueStyle })
  }
}

function resumeSheet(aoa) {
  const sheet = XLSX.utils.aoa_to_sheet(aoa)
  const headerRow = aoa.findIndex((row) => row[0] === 'Indicateur')
  aoa.forEach((row, r) => {
    if (r < headerRow && row.length === 2) styleKeyValue(sheet, r, r)
  })
  addStyle(sheet, 0, 1, { font: { bold: true, sz: 13, color: { rgb: XL.navy } } })
  styleTable(sheet, { headerRow, lastRow: aoa.length - 1, colCount: 2, intCols: [1] })
  sheet['!cols'] = [{ wch: 26 }, { wch: 46 }]
  return sheet
}

function simpleTableSheet(aoa, intCols, cols) {
  const sheet = XLSX.utils.aoa_to_sheet(aoa)
  styleTable(sheet, {
    headerRow: 0,
    lastRow: aoa.length - 1,
    totalRow: aoa.length - 1,
    colCount: aoa[0].length,
    intCols,
  })
  sheet['!cols'] = cols
  return sheet
}

function metaRows(filters, sites) {
  return [
    ['Application', 'GABTRACK'],
    ['Rapport', 'Rapport personnalisé'],
    ['Période', `${formatDateFr(filters.from)} → ${formatDateFr(filters.to)}`],
    ['Tranche horaire', `${filters.timeFrom} → ${displayTimeTo(filters.timeTo)}`],
    ['Sites', siteLabel(filters, sites)],
    ['Exporté le', new Date().toLocaleString('fr-FR')],
  ]
}

function buildResumeAoA(filters, sites, kpis, viewLabel) {
  const rows = [...metaRows(filters, sites), []]
  if (viewLabel) {
    rows.push(['Vue', viewLabel], [])
  }
  rows.push(['Indicateur', 'Valeur'])
  const entriesRow = rows.length + 1
  rows.push(['Total entrées', n(kpis.totalEntries)])
  rows.push(['Total sorties', n(kpis.totalExits)])
  const exitsRow = entriesRow + 1
  rows.push(['Présence nette', fx(`B${entriesRow}-B${exitsRow}`)])
  rows.push(['Présence moyenne / jour', n(kpis.averageDailyPresence)])
  rows.push(['Nombre de jours', n(kpis.dayCount)])
  return rows
}

function tableWithPresenceAndTotal(header, dataRows, totalLabel) {
  const body = dataRows.map((row, i) => {
    const r = i + 2
    return [row[0], row[1], row[2], fx(`B${r}-C${r}`), ...row.slice(4)]
  })
  const first = 2
  const last = Math.max(1, dataRows.length + 1)
  const totalRow = dataRows.length + 2
  const hasRows = dataRows.length > 0
  const pad = Math.max(0, header.length - 4)
  const total = [
    totalLabel,
    hasRows ? fx(`SUM(B${first}:B${last})`) : 0,
    hasRows ? fx(`SUM(C${first}:C${last})`) : 0,
    hasRows ? fx(`B${totalRow}-C${totalRow}`) : 0,
    ...Array(pad).fill(''),
  ]
  return [header, ...body, total]
}

function dailyRows(report) {
  return sortDaysNewestFirst(report.dailyDetails || []).map((row) => [
    formatDateFr(row.date),
    n(row.entries),
    n(row.exits),
    presenceOf(row.entries, row.exits),
  ])
}

function weekdayRows(report) {
  return (report.weekdayDistribution || []).map((row) => [
    row.weekday ?? '',
    n(row.entries),
    n(row.exits),
  ])
}

function flattenSlotExportRows(slots, global) {
  const lines = []
  ;(slots || []).forEach((slot, slotIndex) => {
    const label = `${slot.hourStart} – ${slot.hourEnd}`
    const totalIn = n(global ? slot.totalEntries : slot.entries)
    const totalOut = n(global ? slot.totalExits : slot.exits)
    const sites = slot.sites || []
    sites.forEach((site) => {
      lines.push({
        label,
        site: site.siteName || 'Site',
        entries: n(site.entries),
        exits: n(site.exits),
        avgIn: site.averageEntries == null ? null : n(site.averageEntries),
        avgOut: site.averageExits == null ? null : n(site.averageExits),
        isSum: false,
        slotIndex,
      })
    })
    lines.push({
      label,
      site: 'Total',
      entries: totalIn,
      exits: totalOut,
      avgIn: slot.averageEntries == null ? null : n(slot.averageEntries),
      avgOut: slot.averageExits == null ? null : n(slot.averageExits),
      isSum: true,
      slotIndex,
    })
  })
  return lines
}

function slotPeaksOnFlatRows(slots, global, lines) {
  const source = (slots || []).map((slot) => ({
    entries: n(global ? slot.totalEntries : slot.entries),
    exits: n(global ? slot.totalExits : slot.exits),
  }))
  const peaks = peakIndexes(source, (r) => r.entries, (r) => r.exits)
  const inIdx = new Set()
  const outIdx = new Set()
  const sumIdx = new Set()
  lines.forEach((line, i) => {
    if (!line.isSum) return
    sumIdx.add(i)
    if (peaks.inIdx.has(line.slotIndex)) inIdx.add(i)
    if (peaks.outIdx.has(line.slotIndex)) outIdx.add(i)
  })
  return { inIdx, outIdx, sumIdx }
}

function slotMerges(lines, firstDataRow) {
  const merges = []
  let i = 0
  while (i < lines.length) {
    const blockStart = i
    const label = lines[i].label
    i += 1
    while (i < lines.length && lines[i].label === label) i += 1
    const blockEnd = i - 1
    if (blockEnd > blockStart) {
      merges.push({
        s: { r: firstDataRow + blockStart - 1, c: 0 },
        e: { r: firstDataRow + blockEnd - 1, c: 0 },
      })
    }
  }
  return merges
}

const SLOT_COLS = [
  { wch: 16 },
  { wch: 32 },
  { wch: 12 },
  { wch: 12 },
  { wch: 14 },
  { wch: 14 },
  { wch: 14 },
  { wch: 18 },
]

function sheetFromSlotAoA(aoa, slotSheet) {
  const sheet = XLSX.utils.aoa_to_sheet(aoa)
  if (slotSheet.merges?.length) sheet['!merges'] = slotSheet.merges
  sheet['!cols'] = SLOT_COLS
  styleTable(sheet, {
    headerRow: slotSheet.headerRow - 1,
    lastRow: slotSheet.totalRow - 1,
    totalRow: slotSheet.totalRow - 1,
    colCount: SLOT_COLS.length,
    intCols: [2, 3, 4],
    avgCols: [5, 6],
    boldCols: [0],
    noteCols: [7],
    subtotalRows: new Set(slotSheet.sumRows.map((row) => row - 1)),
    peaks: new Map(slotSheet.peakRows.map((p) => [p.row - 1, p])),
    entriesCol: 2,
    exitsCol: 3,
  })
  return sheet
}

function avgCell(value) {
  return value == null || value === '' ? '' : n(value)
}

function sumOfRows(rows, column) {
  if (!rows.length) return 0
  return fx(`SUM(${rows.map((row) => `${column}${row}`).join(',')})`)
}

function slotSheetAoA(slots, global, firstDataRow, totalLabel) {
  const lines = flattenSlotExportRows(slots, global)
  const source = (slots || []).map((slot) => ({
    entries: n(global ? slot.totalEntries : slot.entries),
    exits: n(global ? slot.totalExits : slot.exits),
  }))
  const slotPeaks = peakIndexes(source, (r) => r.entries, (r) => r.exits)
  const header = ['Tranche', 'Site', 'Entrées', 'Sorties', 'Présence', 'Moy. entrées', 'Moy. sorties', 'Pic']
  const body = []
  const sumRows = []
  const peakRows = []
  let i = 0
  let excelRow = firstDataRow
  while (i < lines.length) {
    const blockStart = excelRow
    while (i < lines.length && !lines[i].isSum) {
      const line = lines[i]
      body.push([
        line.label,
        line.site,
        line.entries,
        line.exits,
        fx(`C${excelRow}-D${excelRow}`),
        avgCell(line.avgIn),
        avgCell(line.avgOut),
        '',
      ])
      excelRow += 1
      i += 1
    }
    if (i >= lines.length) break
    const sum = lines[i]
    const hasSites = excelRow > blockStart
    body.push([
      sum.label,
      'Total',
      hasSites ? fx(`SUM(C${blockStart}:C${excelRow - 1})`) : sum.entries,
      hasSites ? fx(`SUM(D${blockStart}:D${excelRow - 1})`) : sum.exits,
      fx(`C${excelRow}-D${excelRow}`),
      avgCell(sum.avgIn),
      avgCell(sum.avgOut),
      peakLabel(slotPeaks, sum.slotIndex),
    ])
    sumRows.push(excelRow)
    const peakIn = slotPeaks.inIdx.has(sum.slotIndex)
    const peakOut = slotPeaks.outIdx.has(sum.slotIndex)
    if (peakIn || peakOut) peakRows.push({ row: excelRow, in: peakIn, out: peakOut })
    excelRow += 1
    i += 1
  }
  const totalRow = excelRow
  const total = [
    totalLabel,
    '',
    sumOfRows(sumRows, 'C'),
    sumOfRows(sumRows, 'D'),
    fx(`C${totalRow}-D${totalRow}`),
    '',
    '',
    '',
  ]
  return {
    aoa: [header, ...body, total],
    lines,
    sumRows,
    peakRows,
    headerRow: firstDataRow - 1,
    totalRow,
    merges: slotMerges(lines, firstDataRow),
  }
}

export function exportExcel({ report, filters, sites }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const wb = XLSX.utils.book_new()
  const kpis = report.kpis || {}

  XLSX.utils.book_append_sheet(wb, resumeSheet(buildResumeAoA(filters, sites, kpis)), 'Résumé')

  const days = dailyRows(report)
  XLSX.utils.book_append_sheet(
    wb,
    simpleTableSheet(
      tableWithPresenceAndTotal(['Date', 'Entrées', 'Sorties', 'Présence'], days, 'Total période'),
      [1, 2, 3],
      [{ wch: 16 }, { wch: 12 }, { wch: 12 }, { wch: 12 }],
    ),
    'Détail journalier',
  )

  const weekdays = weekdayRows(report)
  const weekdayHeader = ['Jour', 'Entrées', 'Sorties']
  const weekdayBody = weekdays.map((row) => [row[0], row[1], row[2]])
  const wFirst = 2
  const wLast = Math.max(1, weekdays.length + 1)
  const weekdayAoA = [
    weekdayHeader,
    ...weekdayBody,
    [
      'Total période',
      weekdays.length ? fx(`SUM(B${wFirst}:B${wLast})`) : 0,
      weekdays.length ? fx(`SUM(C${wFirst}:C${wLast})`) : 0,
    ],
  ]
  XLSX.utils.book_append_sheet(
    wb,
    simpleTableSheet(weekdayAoA, [1, 2], [{ wch: 16 }, { wch: 12 }, { wch: 12 }]),
    'Par jour de semaine',
  )

  XLSX.writeFile(wb, `GABTRACK_${buildFileStamp(filters)}.xlsx`)
}

export function exportExcelDetail({ report, filters, sites }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const wb = XLSX.utils.book_new()
  const kpis = report.kpis || {}
  XLSX.utils.book_append_sheet(
    wb,
    resumeSheet(buildResumeAoA(filters, sites, kpis, 'Détail par jour et tranches')),
    'Résumé',
  )

  const globalSheet = slotSheetAoA(report.hourlySlotAverages || [], true, 2, 'Total période')
  XLSX.utils.book_append_sheet(wb, sheetFromSlotAoA(globalSheet.aoa, globalSheet), 'Global tranches')

  for (const day of sortDaysNewestFirst(report.dailyBreakdown || [])) {
    const sheetName = formatDateFr(day.date).replace(/\//g, '-').slice(0, 31)
    const slotSheet = slotSheetAoA(day.hourlySlots || [], false, 7, 'Total jour')
    const dayAoA = [
      ['Date', formatDateFr(day.date)],
      ['Total entrées', sumOfRows(slotSheet.sumRows, 'C')],
      ['Total sorties', sumOfRows(slotSheet.sumRows, 'D')],
      ['Présence (fin tranche)', fx('B2-B3')],
      [],
      ...slotSheet.aoa,
    ]
    const sheet = sheetFromSlotAoA(dayAoA, slotSheet)
    styleKeyValue(sheet, 0, 0, { font: { bold: true, sz: 13, color: { rgb: XL.navy } } })
    styleKeyValue(sheet, 1, 3, { numFmt: INT_FMT, font: { bold: true } })
    XLSX.utils.book_append_sheet(wb, sheet, sheetName)
  }

  XLSX.writeFile(wb, `GABTRACK_detail_${buildFileStamp(filters)}.xlsx`)
}

function peakLabel(peaks, index) {
  const parts = []
  if (peaks.inIdx.has(index)) parts.push('max entrées')
  if (peaks.outIdx.has(index)) parts.push('max sorties')
  return parts.join(' / ')
}

const PDF = {
  pageW: 210,
  pageH: 297,
  margin: 16,
  contentTop: 24,
  pageBottom: 274,
  navy: [26, 35, 50],
  ink: [26, 35, 50],
  muted: [92, 103, 120],
  line: [214, 219, 226],
  wash: [248, 250, 252],
  white: [255, 255, 255],
  green: [209, 250, 229],
  greenInk: [4, 120, 87],
  red: [254, 226, 226],
  redInk: [185, 28, 28],
}

function buildPdfContext(filters, sites, reportTitle) {
  return {
    title: reportTitle || 'Rapport personnalisé',
    period: `${formatDateFr(filters.from)} – ${formatDateFr(filters.to)}`,
    hours: `${filters.timeFrom} – ${displayTimeTo(filters.timeTo)}`,
    sites: siteLabel(filters, sites),
    exportedAt: new Date().toLocaleString('fr-FR'),
  }
}

function pdfTableTheme(darkHead = false) {
  return {
    theme: 'grid',
    margin: { left: PDF.margin, right: PDF.margin, top: PDF.contentTop, bottom: 18 },
    styles: {
      font: 'helvetica',
      fontSize: 8.5,
      textColor: PDF.ink,
      cellPadding: { top: 3.2, bottom: 3.2, left: 3.2, right: 3.2 },
      lineColor: PDF.line,
      lineWidth: 0.15,
      valign: 'middle',
    },
    headStyles: {
      fillColor: darkHead ? PDF.navy : [241, 245, 249],
      textColor: darkHead ? PDF.white : PDF.ink,
      fontStyle: 'bold',
      fontSize: 8,
    },
    alternateRowStyles: { fillColor: PDF.wash },
    footStyles: {
      fillColor: [241, 245, 249],
      textColor: PDF.ink,
      fontStyle: 'bold',
      fontSize: 8.5,
    },
  }
}

function pdfPageHooks(doc, ctx) {
  return {
    showHead: 'everyPage',
    didDrawPage: (data) => {
      if (data.pageNumber === 1) return
      drawPdfRunningHeader(doc, ctx)
    },
  }
}

function peakCellHook(peaks) {
  return {
    didParseCell: (data) => {
      if (data.section !== 'body') return
      const peakIn = peaks.inIdx.has(data.row.index)
      const peakOut = peaks.outIdx.has(data.row.index)
      if (peaks.sumIdx?.has(data.row.index)) {
        data.cell.styles.fontStyle = 'bold'
      }
      if (peakIn && (data.column.index === 0 || data.column.index === 2)) {
        data.cell.styles.fillColor = PDF.green
        if (data.column.index === 2) {
          data.cell.styles.textColor = PDF.greenInk
          data.cell.styles.fontStyle = 'bold'
        }
      }
      if (peakOut && data.column.index === 3) {
        data.cell.styles.fillColor = PDF.red
        data.cell.styles.textColor = PDF.redInk
        data.cell.styles.fontStyle = 'bold'
      }
      if (peakOut && data.column.index === 0 && !peakIn) {
        data.cell.styles.fillColor = PDF.red
      }
      if (data.column.index === 1 && (peakIn || peakOut)) {
        data.cell.styles.fontStyle = 'bold'
      }
    },
  }
}

function drawPdfCover(doc, ctx) {
  doc.setFillColor(...PDF.navy)
  doc.rect(0, 0, PDF.pageW, 36, 'F')

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(16)
  doc.setTextColor(...PDF.white)
  doc.text('GABTRACK', PDF.margin, 16)

  doc.setFont('helvetica', 'normal')
  doc.setFontSize(10)
  doc.setTextColor(203, 213, 225)
  doc.text(ctx.title, PDF.margin, 24)

  doc.setFontSize(8.5)
  doc.text(`${ctx.period}   ·   ${ctx.hours}`, PDF.margin, 31)

  doc.setFont('helvetica', 'normal')
  doc.setFontSize(9)
  doc.setTextColor(...PDF.muted)
  const siteLines = doc.splitTextToSize(`Sites : ${ctx.sites}`, PDF.pageW - PDF.margin * 2)
  doc.text(siteLines.slice(0, 2), PDF.margin, 44)
  doc.text(`Généré le ${ctx.exportedAt}`, PDF.margin, 44 + siteLines.slice(0, 2).length * 5)
  return 56
}

function drawPdfKpiBoxes(doc, y, kpis) {
  const gap = 4
  const boxW = (PDF.pageW - PDF.margin * 2 - gap * 3) / 4
  const boxH = 18
  const items = [
    { label: 'Entrées', value: formatPdfNumber(kpis.totalEntries) },
    { label: 'Sorties', value: formatPdfNumber(kpis.totalExits) },
    { label: 'Présence nette', value: formatPdfNumber(presenceOf(kpis.totalEntries, kpis.totalExits)) },
    { label: 'Jours', value: formatPdfNumber(kpis.dayCount) },
  ]
  items.forEach((item, i) => {
    const x = PDF.margin + i * (boxW + gap)
    doc.setFillColor(...PDF.wash)
    doc.setDrawColor(...PDF.line)
    doc.setLineWidth(0.2)
    doc.roundedRect(x, y, boxW, boxH, 1.4, 1.4, 'FD')
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(7)
    doc.setTextColor(...PDF.muted)
    doc.text(item.label, x + 4, y + 6)
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(11)
    doc.setTextColor(...PDF.ink)
    doc.text(item.value, x + 4, y + 14)
  })
  return y + boxH + 8
}

function drawLegend(doc, y) {
  doc.setFillColor(...PDF.green)
  doc.rect(PDF.margin, y - 3, 4, 4, 'F')
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(...PDF.muted)
  doc.text('Pic d’entrées', PDF.margin + 6, y)
  doc.setFillColor(...PDF.red)
  doc.rect(PDF.margin + 42, y - 3, 4, 4, 'F')
  doc.text('Pic de sorties', PDF.margin + 48, y)
  return y + 6
}

function drawSectionTitle(doc, y, title) {
  doc.setFont('helvetica', 'bold')
  doc.setFontSize(11)
  doc.setTextColor(...PDF.navy)
  doc.text(title, PDF.margin, y)
  return y + 6
}

function drawDayBanner(doc, y, day) {
  const barH = 14
  const barW = PDF.pageW - PDF.margin * 2
  doc.setFillColor(...PDF.wash)
  doc.setDrawColor(...PDF.line)
  doc.setLineWidth(0.2)
  doc.roundedRect(PDF.margin, y, barW, barH, 1.2, 1.2, 'FD')

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(10)
  doc.setTextColor(...PDF.ink)
  doc.text(formatDateFr(day.date), PDF.margin + 4, y + 9)

  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(...PDF.muted)
  doc.text(
    `Entrées ${formatPdfNumber(day.entries)}    Sorties ${formatPdfNumber(day.exits)}    Présence ${formatPdfNumber(presenceOf(day.entries, day.exits))}`,
    PDF.pageW - PDF.margin - 4,
    y + 9,
    { align: 'right' },
  )
  return y + barH + 3
}

function drawPdfRunningHeader(doc, ctx) {
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(...PDF.muted)
  doc.text(`GABTRACK  ·  ${ctx.period}  ·  ${ctx.hours}`, PDF.margin, 12)
}

function drawPdfFooter(doc, page, total) {
  const y = PDF.pageH - 10
  doc.setDrawColor(...PDF.line)
  doc.setLineWidth(0.2)
  doc.line(PDF.margin, y - 4, PDF.pageW - PDF.margin, y - 4)
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8)
  doc.setTextColor(...PDF.muted)
  doc.text('GABTRACK', PDF.margin, y)
  doc.text(`${page} / ${total}`, PDF.pageW - PDF.margin, y, { align: 'right' })
}

function ensurePdfSpace(doc, ctx, y, needed) {
  if (y + needed > PDF.pageBottom) {
    doc.addPage()
    drawPdfRunningHeader(doc, ctx)
    return PDF.contentTop
  }
  return y
}

function finalizePdfPages(doc) {
  const total = doc.getNumberOfPages()
  for (let i = 1; i <= total; i += 1) {
    doc.setPage(i)
    drawPdfFooter(doc, i, total)
  }
}

function slotTableBody(lines) {
  const body = []
  let i = 0
  while (i < lines.length) {
    const blockStart = i
    const label = lines[i].label
    const block = []
    while (i < lines.length && lines[i].label === label) {
      block.push(lines[i])
      i += 1
    }
    block.forEach((row, idx) => {
      const cells = []
      if (idx === 0) {
        cells.push({
          content: row.label,
          rowSpan: block.length,
          styles: { fontStyle: 'bold', valign: 'middle' },
        })
      }
      cells.push(
        row.site,
        formatPdfNumber(row.entries),
        formatPdfNumber(row.exits),
        formatPdfNumber(presenceOf(row.entries, row.exits)),
        row.avgIn == null || row.avgIn === '' ? '' : formatAvgFr(row.avgIn),
        row.avgOut == null || row.avgOut === '' ? '' : formatAvgFr(row.avgOut),
      )
      body.push(cells)
    })
    if (blockStart === i) break
  }
  return body
}

export function exportPdfDetail({ report, filters, sites }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const ctx = buildPdfContext(filters, sites, 'Détail par jour et tranches')
  const kpis = report.kpis || {}
  const globalLines = flattenSlotExportRows(report.hourlySlotAverages || [], true)
  const globalPeaks = slotPeaksOnFlatRows(report.hourlySlotAverages || [], true, globalLines)

  let y = drawPdfCover(doc, ctx)
  y = drawPdfKpiBoxes(doc, y, kpis)
  y = drawSectionTitle(doc, y, 'Détail global par tranche')
  y = drawLegend(doc, y)

  autoTable(doc, {
    ...pdfTableTheme(true),
    startY: y,
    head: [['Tranche', 'Site', 'Entrées', 'Sorties', 'Présence', 'Moy. entr.', 'Moy. sort.']],
    body: slotTableBody(globalLines),
    foot: [[
      'Total période',
      '',
      formatPdfNumber(kpis.totalEntries),
      formatPdfNumber(kpis.totalExits),
      formatPdfNumber(presenceOf(kpis.totalEntries, kpis.totalExits)),
      '',
      '',
    ]],
    columnStyles: {
      0: { cellWidth: 24 },
      1: { cellWidth: 48 },
      2: { halign: 'right' },
      3: { halign: 'right' },
      4: { halign: 'right' },
      5: { halign: 'right' },
      6: { halign: 'right' },
    },
    ...pdfPageHooks(doc, ctx),
    ...peakCellHook(globalPeaks),
  })

  y = (doc.lastAutoTable?.finalY ?? y) + 10
  y = ensurePdfSpace(doc, ctx, y, 24)
  y = drawSectionTitle(doc, y, 'Détail par jour')

  for (const day of sortDaysNewestFirst(report.dailyBreakdown || [])) {
    const lines = flattenSlotExportRows(day.hourlySlots || [], false)
    const dayPeaks = slotPeaksOnFlatRows(day.hourlySlots || [], false, lines)
    y = ensurePdfSpace(doc, ctx, y, 22 + Math.min(lines.length, 8) * 5)
    y = drawDayBanner(doc, y, day)

    autoTable(doc, {
      ...pdfTableTheme(false),
      startY: y,
      head: [['Tranche', 'Site', 'Entrées', 'Sorties', 'Présence', 'Moy. entr.', 'Moy. sort.']],
      body: slotTableBody(lines),
      foot: [[
        'Total jour',
        '',
        formatPdfNumber(day.entries),
        formatPdfNumber(day.exits),
        formatPdfNumber(presenceOf(day.entries, day.exits)),
        '',
        '',
      ]],
      columnStyles: {
        0: { cellWidth: 24 },
        1: { cellWidth: 48 },
        2: { halign: 'right' },
        3: { halign: 'right' },
        4: { halign: 'right' },
        5: { halign: 'right' },
        6: { halign: 'right' },
      },
      ...pdfPageHooks(doc, ctx),
      ...peakCellHook(dayPeaks),
    })
    y = (doc.lastAutoTable?.finalY ?? y) + 7
  }

  finalizePdfPages(doc)
  doc.save(`GABTRACK_detail_${buildFileStamp(filters)}.pdf`)
}

export function exportPdf({ report, filters, sites }) {
  if (!report) throw new Error('Aucun rapport à exporter. Cliquez d’abord sur « Explorer ».')

  const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' })
  const ctx = buildPdfContext(filters, sites, 'Rapport personnalisé')
  const kpis = report.kpis || {}
  const days = dailyRows(report)
  const weekdays = weekdayRows(report)

  let y = drawPdfCover(doc, ctx)
  y = drawPdfKpiBoxes(doc, y, kpis)
  y = drawSectionTitle(doc, y, 'Détail par jour')

  autoTable(doc, {
    ...pdfTableTheme(false),
    startY: y,
    head: [['Date', 'Entrées', 'Sorties', 'Présence']],
    body: days.map((row) => [
      row[0],
      formatPdfNumber(row[1]),
      formatPdfNumber(row[2]),
      formatPdfNumber(row[3]),
    ]),
    foot: [[
      'Total période',
      formatPdfNumber(kpis.totalEntries),
      formatPdfNumber(kpis.totalExits),
      formatPdfNumber(presenceOf(kpis.totalEntries, kpis.totalExits)),
    ]],
    columnStyles: {
      0: { cellWidth: 44 },
      1: { halign: 'right' },
      2: { halign: 'right' },
      3: { halign: 'right' },
    },
    ...pdfPageHooks(doc, ctx),
  })

  y = (doc.lastAutoTable?.finalY ?? y) + 10
  y = ensurePdfSpace(doc, ctx, y, 36)
  y = drawSectionTitle(doc, y, 'Répartition par jour de la semaine')

  autoTable(doc, {
    ...pdfTableTheme(false),
    startY: y,
    head: [['Jour', 'Entrées', 'Sorties']],
    body: weekdays.map((row) => [
      row[0],
      formatPdfNumber(row[1]),
      formatPdfNumber(row[2]),
    ]),
    foot: [[
      'Total période',
      formatPdfNumber(kpis.totalEntries),
      formatPdfNumber(kpis.totalExits),
    ]],
    columnStyles: {
      0: { cellWidth: 48 },
      1: { halign: 'right' },
      2: { halign: 'right' },
    },
    ...pdfPageHooks(doc, ctx),
  })

  finalizePdfPages(doc)
  doc.save(`GABTRACK_${buildFileStamp(filters)}.pdf`)
}
