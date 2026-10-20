/** Tri décroissant par date ISO (jour le plus récent en premier). */
export function sortDaysNewestFirst(rows) {
  if (!rows?.length) return []
  return [...rows].sort((a, b) => String(b.date).localeCompare(String(a.date)))
}
