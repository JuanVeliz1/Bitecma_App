function normNum(v) {
  if (v === null || v === undefined || v === '') return null
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  const n = Number(String(v).trim().replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

function ensureMap(lpMuestras) {
  return lpMuestras && typeof lpMuestras === 'object' ? lpMuestras : {}
}

function ensureArr(v) {
  return Array.isArray(v) ? v : []
}

export function ensureEspecie(lpMuestras, especieId) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  if (Object.prototype.hasOwnProperty.call(map, sp)) return map
  return { ...map, [sp]: [] }
}

export function removeEspecie(lpMuestras, especieId) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  if (!Object.prototype.hasOwnProperty.call(map, sp)) return map
  const next = { ...map }
  delete next[sp]
  return next
}

export function addSample(lpMuestras, especieId, sample) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  const cur = ensureArr(map[sp])
  const s = sample && typeof sample === 'object' ? sample : {}

  const nextSample = {}
  if (Object.prototype.hasOwnProperty.call(s, 'p')) {
    const l = normNum(s.l)
    const p = normNum(s.p)
    if (l === null || p === null) return map
    nextSample.l = l
    nextSample.p = p
  } else if (Object.prototype.hasOwnProperty.call(s, 'd')) {
    const d = normNum(s.d)
    if (d === null) return map
    nextSample.d = d
  } else {
    const l = normNum(s.l)
    if (l === null) return map
    nextSample.l = l
  }

  return { ...map, [sp]: [...cur, nextSample] }
}

export function updateSample(lpMuestras, especieId, index, sample) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  const cur = ensureArr(map[sp])
  const idx = Number(index)
  if (!Number.isFinite(idx) || idx < 0 || idx >= cur.length) return map

  const s = sample && typeof sample === 'object' ? sample : {}
  const nextSample = {}
  if (Object.prototype.hasOwnProperty.call(s, 'p')) {
    const l = normNum(s.l)
    const p = normNum(s.p)
    if (l === null || p === null) return map
    nextSample.l = l
    nextSample.p = p
  } else if (Object.prototype.hasOwnProperty.call(s, 'd')) {
    const d = normNum(s.d)
    if (d === null) return map
    nextSample.d = d
  } else {
    const l = normNum(s.l)
    if (l === null) return map
    nextSample.l = l
  }

  return { ...map, [sp]: cur.map((x, i) => (i === idx ? nextSample : x)) }
}

export function removeSample(lpMuestras, especieId, index) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  const cur = ensureArr(map[sp])
  const idx = Number(index)
  if (!Number.isFinite(idx) || idx < 0 || idx >= cur.length) return map
  return { ...map, [sp]: cur.filter((_, i) => i !== idx) }
}

