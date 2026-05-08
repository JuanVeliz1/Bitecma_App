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

function normKind(kind) {
  const k = String(kind || '').trim().toUpperCase()
  if (k === 'L-P' || k === 'LP') return 'LP'
  if (k === 'D') return 'D'
  return 'L'
}

function kindFromSample(sample) {
  const s = sample && typeof sample === 'object' ? sample : {}
  if (Object.prototype.hasOwnProperty.call(s, 'd')) return 'D'
  if (Object.prototype.hasOwnProperty.call(s, 'p')) return 'LP'
  return 'L'
}

function normalizeEntry(entry) {
  if (Array.isArray(entry)) {
    const out = {}
    entry.forEach((m) => {
      const k = kindFromSample(m)
      if (!out[k]) out[k] = []
      out[k].push(m)
    })
    return out
  }
  if (entry && typeof entry === 'object') {
    if (Array.isArray(entry.ms)) {
      const k = normKind(entry.type || 'LP')
      return { [k]: ensureArr(entry.ms) }
    }
    const out = {}
    ;['LP', 'L', 'D'].forEach((k) => {
      if (Array.isArray(entry[k])) out[k] = entry[k]
    })
    return out
  }
  return {}
}

function normalizeSample(kind, sample) {
  const s = sample && typeof sample === 'object' ? sample : {}
  const k = normKind(kind)
  if (k === 'LP') {
    const l = normNum(s.l)
    const p = normNum(s.p)
    if (l === null || p === null) return null
    return { l, p }
  }
  if (k === 'D') {
    const d = normNum(s.d)
    if (d === null) return null
    return { d }
  }
  const l = normNum(s.l)
  if (l === null) return null
  return { l }
}

export function ensureKind(lpMuestras, especieId, kind = 'LP') {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  const k = normKind(kind)
  const cur = normalizeEntry(map[sp])
  if (Object.prototype.hasOwnProperty.call(cur, k)) return { ...map, [sp]: cur }
  return { ...map, [sp]: { ...cur, [k]: [] } }
}

export function removeKind(lpMuestras, especieId, kind) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  const k = normKind(kind)
  if (!Object.prototype.hasOwnProperty.call(map, sp)) return map
  const cur = normalizeEntry(map[sp])
  if (!Object.prototype.hasOwnProperty.call(cur, k)) return { ...map, [sp]: cur }
  const nextEntry = { ...cur }
  delete nextEntry[k]
  const hasAny = Object.keys(nextEntry).some((kk) => Array.isArray(nextEntry[kk]))
  if (!hasAny) {
    const next = { ...map }
    delete next[sp]
    return next
  }
  return { ...map, [sp]: nextEntry }
}

export function ensureEspecie(lpMuestras, especieId, kind = 'LP') {
  return ensureKind(lpMuestras, especieId, kind)
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

export function addSample(lpMuestras, especieId, kindOrSample, sampleMaybe) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  const kind = sampleMaybe === undefined ? kindFromSample(kindOrSample) : normKind(kindOrSample)
  const sample = sampleMaybe === undefined ? kindOrSample : sampleMaybe
  const nextSample = normalizeSample(kind, sample)
  if (!nextSample) return map

  const curEntry = normalizeEntry(map[sp])
  const curArr = ensureArr(curEntry[kind])
  const nextEntry = { ...curEntry, [kind]: [...curArr, nextSample] }
  return { ...map, [sp]: nextEntry }
}

export function updateSample(lpMuestras, especieId, kindOrIndex, indexOrSample, sampleMaybe) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  if (typeof kindOrIndex === 'number') {
    const cur = ensureArr(map[sp])
    const idx = Number(kindOrIndex)
    if (!Number.isFinite(idx) || idx < 0 || idx >= cur.length) return map
    const nextSample = normalizeSample(kindFromSample(indexOrSample), indexOrSample)
    if (!nextSample) return map
    return { ...map, [sp]: cur.map((x, i) => (i === idx ? nextSample : x)) }
  }

  const kind = normKind(kindOrIndex)
  const idx = Number(indexOrSample)
  if (!Number.isFinite(idx)) return map
  const nextSample = normalizeSample(kind, sampleMaybe)
  if (!nextSample) return map

  const curEntry = normalizeEntry(map[sp])
  const curArr = ensureArr(curEntry[kind])
  if (idx < 0 || idx >= curArr.length) return { ...map, [sp]: curEntry }
  const nextEntry = { ...curEntry, [kind]: curArr.map((x, i) => (i === idx ? nextSample : x)) }
  return { ...map, [sp]: nextEntry }
}

export function removeSample(lpMuestras, especieId, kindOrIndex, indexMaybe) {
  const map = ensureMap(lpMuestras)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return map
  if (typeof kindOrIndex === 'number') {
    const cur = ensureArr(map[sp])
    const idx = Number(kindOrIndex)
    if (!Number.isFinite(idx) || idx < 0 || idx >= cur.length) return map
    return { ...map, [sp]: cur.filter((_, i) => i !== idx) }
  }

  const kind = normKind(kindOrIndex)
  const idx = Number(indexMaybe)
  if (!Number.isFinite(idx)) return map
  const curEntry = normalizeEntry(map[sp])
  const curArr = ensureArr(curEntry[kind])
  if (idx < 0 || idx >= curArr.length) return { ...map, [sp]: curEntry }
  const nextEntry = { ...curEntry, [kind]: curArr.filter((_, i) => i !== idx) }
  return { ...map, [sp]: nextEntry }
}
