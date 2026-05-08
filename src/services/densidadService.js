function normInt(v) {
  if (v === null || v === undefined || v === '') return 0
  const n = typeof v === 'number' ? v : Number(String(v).trim().replace(',', '.'))
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.trunc(n))
}

function normNum(v) {
  if (v === null || v === undefined || v === '') return null
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  const n = Number(String(v).trim().replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

export function calcDensidad(count, area) {
  const c = normInt(count)
  const a = Number(area) || 0
  if (!(a > 0)) return 0
  return c / a
}

export function nextUnidadNum(unidades) {
  const arr = Array.isArray(unidades) ? unidades : []
  const nums = arr.map((u) => Number(u?.num)).filter((n) => Number.isFinite(n))
  return (nums.length ? Math.max(...nums) : 0) + 1
}

export function crearUnidades({
  unidades,
  tipo,
  cantidad,
  area,
  fecha,
  sustrato,
  cubierta,
  especieId,
  especiesIds,
}) {
  const base = Array.isArray(unidades) ? unidades : []
  const t = tipo === 'cuadrante' ? 'cuadrante' : 'transecto'
  const n = Math.max(0, Math.trunc(Number(cantidad) || 0))
  if (!n) return base
  const start = nextUnidadNum(base)
  const a = Number(area) || 0
  const f = String(fecha || '').trim()
  const sus = String(sustrato || '').trim()
  const cub = String(cubierta || '').trim()
  const spId = especieId == null || especieId === '' ? null : Number(especieId)
  const spIds = Array.isArray(especiesIds)
    ? especiesIds.map(Number).filter((x) => Number.isFinite(x))
    : []

  const created = Array.from({ length: n }, (_, i) => {
    const num = start + i
    const unit = {
      num,
      tipo: t,
      area: a || (t === 'cuadrante' ? 1 : 120),
      fecha: f,
      sustrato: sus,
      cubierta: cub,
      counts: t === 'transecto' && spIds.length ? Object.fromEntries(spIds.map((id) => [id, 0])) : {},
    }
    if (t === 'cuadrante' && Number.isFinite(spId)) {
      unit.especieId = spId
      unit.counts = { [spId]: 0 }
    }
    return unit
  })

  return [...base, ...created]
}

export function eliminarUnidad(unidades, num) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  return base.filter((u) => Number(u?.num) !== n)
}

export function updateUnidad(unidades, num, patch) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  return base.map((u) => {
    if (Number(u?.num) !== n) return u
    const next = { ...u, ...(patch || {}) }
    if (patch && Object.prototype.hasOwnProperty.call(patch, 'area')) {
      next.area = Number(patch.area) || 0
    }
    if (patch && Object.prototype.hasOwnProperty.call(patch, 'fecha')) {
      next.fecha = String(patch.fecha || '').trim()
    }
    if (patch && Object.prototype.hasOwnProperty.call(patch, 'sustrato')) {
      next.sustrato = String(patch.sustrato || '').trim()
    }
    if (patch && Object.prototype.hasOwnProperty.call(patch, 'cubierta')) {
      next.cubierta = String(patch.cubierta || '').trim()
    }
    return next
  })
}

export function setUnidadCoord(unidades, num, key, value) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  const v = normNum(value)
  const field =
    key === 'x' ? 'coordX' : key === 'y' ? 'coordY' : key === 'lon' ? 'coordLong' : key === 'lat' ? 'coordLat' : null
  if (!field) return base
  return base.map((u) => (Number(u?.num) !== n ? u : { ...u, [field]: v }))
}

export function addEspecieToUnidad(unidades, num, especieId) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return base
  return base.map((u) => {
    if (Number(u?.num) !== n) return u
    if (u?.tipo === 'cuadrante') return u
    const counts = u?.counts && typeof u.counts === 'object' ? u.counts : {}
    if (Object.prototype.hasOwnProperty.call(counts, sp)) return u
    return { ...u, counts: { ...counts, [sp]: 0 } }
  })
}

export function removeEspecieFromUnidad(unidades, num, especieId) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return base
  return base.map((u) => {
    if (Number(u?.num) !== n) return u
    if (u?.tipo === 'cuadrante') return u
    const counts = u?.counts && typeof u.counts === 'object' ? u.counts : {}
    if (!Object.prototype.hasOwnProperty.call(counts, sp)) return u
    const nextCounts = { ...counts }
    delete nextCounts[sp]
    return { ...u, counts: nextCounts }
  })
}

export function setUnidadCount(unidades, num, especieId, value) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return base
  const cnt = normInt(value)
  return base.map((u) => {
    if (Number(u?.num) !== n) return u
    const counts = u?.counts && typeof u.counts === 'object' ? u.counts : {}
    return { ...u, counts: { ...counts, [sp]: cnt } }
  })
}

export function setCuadranteEspecie(unidades, num, especieId) {
  const base = Array.isArray(unidades) ? unidades : []
  const n = Number(num)
  const sp = Number(especieId)
  if (!Number.isFinite(sp)) return base
  return base.map((u) => {
    if (Number(u?.num) !== n) return u
    if (u?.tipo !== 'cuadrante') return u
    const curCounts = u?.counts && typeof u.counts === 'object' ? u.counts : {}
    const curSp = u?.especieId == null ? null : Number(u.especieId)
    const curVal = curSp != null && Number.isFinite(curSp) ? Number(curCounts[curSp] ?? 0) : 0
    return { ...u, especieId: sp, counts: { [sp]: normInt(curVal) } }
  })
}
