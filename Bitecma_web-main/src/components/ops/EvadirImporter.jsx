import { useRef, useState } from 'react'
import EvadirPreview from '../evadir/EvadirPreview.jsx'
import SpeciesGrid from '../common/SpeciesGrid.jsx'
import { addSample } from '../../services/lpMuestrasService.js'

function normText(v) {
  return String(v || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

function normHeader(v) {
  return normText(v).replace(/\s+/g, ' ').trim()
}

function firstNonEmpty(rows, idx) {
  for (const r of rows) {
    const v = r?.[idx]
    const s = String(v ?? '').trim()
    if (s !== '') return s
  }
  return ''
}

function parseIntSafe(v) {
  if (v === null || v === undefined || v === '') return null
  if (typeof v === 'number' && Number.isFinite(v)) return Math.trunc(v)
  const n = parseInt(String(v).trim(), 10)
  return Number.isFinite(n) ? n : null
}

function parseNumSafe(v) {
  if (v === null || v === undefined || v === '') return null
  if (typeof v === 'number' && Number.isFinite(v)) return v
  const n = Number(String(v).trim().replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

function parseDateISO(val, XLSX) {
  if (val == null || val === '') return ''
  if (val instanceof Date && !isNaN(val)) {
    const y = val.getFullYear()
    const m = String(val.getMonth() + 1).padStart(2, '0')
    const d = String(val.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
  }
  if (typeof val === 'number' && Number.isFinite(val) && XLSX?.SSF?.parse_date_code) {
    const dc = XLSX.SSF.parse_date_code(val)
    if (dc && dc.y && dc.m && dc.d) {
      const y = String(dc.y).padStart(4, '0')
      const m = String(dc.m).padStart(2, '0')
      const d = String(dc.d).padStart(2, '0')
      return `${y}-${m}-${d}`
    }
  }
  const s = String(val).trim()
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s
  const m1 = s.match(/^(\d{2})[-/](\d{2})[-/](\d{4})$/)
  if (m1) return `${m1[3]}-${m1[2]}-${m1[1]}`
  const m2 = s.match(/^(\d{4})[-/](\d{2})[-/](\d{2})$/)
  if (m2) return `${m2[1]}-${m2[2]}-${m2[3]}`
  return ''
}

function guessHeaderRow(aoa) {
  const maxScan = Math.min(Array.isArray(aoa) ? aoa.length : 0, 12)
  for (let r = 0; r < maxScan; r++) {
    const row = Array.isArray(aoa[r]) ? aoa[r] : []
    const keys = row.map(normHeader).filter(Boolean)
    const hasBote = keys.some((k) => k === 'bote')
    const hasZona = keys.some((k) => k.includes('zona'))
    const hasNum = keys.some((k) => k.includes('num') && (k.includes('transec') || k.includes('cuadr')))
    if (hasBote && (hasZona || hasNum)) return r
    const hasRegion = keys.some((k) => k === 'region')
    const hasFecha = keys.some((k) => k === 'fecha')
    if (hasRegion && hasBote && hasFecha) return r
  }
  return 0
}

function todayISO() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export default function EvadirImporter({ db, canWrite, toast, openModal, closeModal, operaciones, nextOpId, safeUpsertOperacion }) {
  const evadirInputRef = useRef(null)
  const [isImportingEvadir, setIsImportingEvadir] = useState(false)

  const importEvadirFromXlsx = async (file) => {
    if (!file) return
    if (!canWrite) {
      toast('Modo solo lectura', 'blue')
      return
    }
    if (isImportingEvadir) return
    setIsImportingEvadir(true)
    try {
      const xlsxMod = await import('xlsx-js-style')
      const XLSX = xlsxMod?.default || xlsxMod
      const buf = await file.arrayBuffer()
      const wb = XLSX.read(buf, { type: 'array' })
      const sheetNames = Array.isArray(wb?.SheetNames) ? wb.SheetNames : []
      const evadirSheetName = sheetNames.find((n) => normHeader(n).includes('evadir'))
      if (!evadirSheetName) {
        toast('No se encontró la hoja EVADIR', 'red')
        return
      }

      const especies = Array.isArray(db?.especies) ? db.especies : []
      const regionesChile = Array.isArray(db?.regionesChile) ? db.regionesChile : []
      const opaArr = Array.isArray(db?.opa) ? db.opa : []

      const speciesIdByKey = (() => {
        const m = new Map()
        for (const sp of especies) {
          const id = Number(sp?.id)
          if (!Number.isFinite(id)) continue
          const k1 = normHeader(sp?.com)
          const k2 = normHeader(sp?.sci)
          if (k1) m.set(k1, id)
          if (k2) m.set(k2, id)
        }
        return m
      })()

      const algaIdSet = (() => {
        const m = new Set()
        for (const sp of especies) {
          const id = Number(sp?.id)
          if (!Number.isFinite(id)) continue
          const sci = normHeader(sp?.sci)
          const com = normHeader(sp?.com)
          const isAlga =
            sci.includes('lessonia') ||
            sci.includes('durvillaea') ||
            sci.includes('macrocystis') ||
            sci.includes('gigartina') ||
            sci.includes('sarcothalia') ||
            sci.includes('mazzaella') ||
            com.includes('huiro') ||
            com.includes('cochayuyo') ||
            com.includes('luga')
          if (isAlga) m.add(id)
        }
        return m
      })()

      const genusInitialEpithetToId = (() => {
        const m = new Map()
        for (const sp of especies) {
          const id = Number(sp?.id)
          if (!Number.isFinite(id)) continue
          const ks = normHeader(sp?.sci)
          const parts = ks.split(' ').filter(Boolean)
          if (parts.length < 2) continue
          const gi = parts[0]?.[0]
          const ep = parts[1]
          if (!gi || !ep) continue
          m.set(`${gi} ${ep}`, id)
          m.set(`${gi}${ep}`, id)
        }
        return m
      })()

      const sciAliases = (() => {
        const m = new Map()
        const add = (from, to) => {
          const f = normHeader(from)
          const t = normHeader(to)
          if (f && t) m.set(f, t)
        }
        add('Lessonia nigrescens', 'Lessonia berteroana')
        add('Lessonia nigrenscens', 'Lessonia berteroana')
        add('Lessonia spicata', 'Lessonia berteroana')
        add('L nigrescens', 'Lessonia berteroana')
        add('L nigrenscens', 'Lessonia berteroana')
        add('LNIGRENSCENS', 'Lessonia berteroana')
        add('LNIGRESCENS', 'Lessonia berteroana')
        return m
      })()

      const comAliasToId = (() => {
        const m = new Map()
        const stop = new Set(['de', 'del', 'la', 'el', 'los', 'las', 'y', 'spp', 'sp'])
        const add = (alias, id) => {
          const k = normHeader(alias)
          if (!k) return
          if (k.length < 3) return
          m.set(k, id)
          m.set(k.replace(/\s+/g, ''), id)
        }
        for (const sp of especies) {
          const id = Number(sp?.id)
          if (!Number.isFinite(id)) continue
          const com = String(sp?.com || '').trim()
          const k = normHeader(com)
          if (!k) continue
          add(k, id)
          const parts = k.split(' ').filter((w) => w && !stop.has(w))
          if (!parts.length) continue
          if (parts.length >= 2) {
            const first = parts[0]
            const last = parts[parts.length - 1]
            add(`${first[0]} ${last}`, id)
            add(`${first[0]}${last}`, id)
          }
        }
        return m
      })()

      const guessSpeciesFromSheetName = (sheetName) => {
        const s = normHeader(sheetName)
        if (!s) return null
        const stop = new Set(['evadir', 'long', 'longo', 'largo', 'peso', 'lp', 'l', 'd', 'diam', 'diametro', 'disco'])
        let best = null
        for (const [alias, id] of comAliasToId.entries()) {
          if (!alias || stop.has(alias)) continue
          if (alias.length < 3) continue
          const hit = s.includes(alias) || s.includes(alias.replace(/\s+/g, ''))
          if (!hit) continue
          const score = alias.length
          if (!best || score > best.score) best = { id, score }
        }
        return best?.id ?? null
      }

      const resolveSpeciesId = (rawName) => {
        const baseRaw = String(rawName || '').replace(/\(.*?\)/g, '').trim()
        const base = baseRaw.replace(/^(num|dens)\s+/i, '').trim()
        const k = normHeader(base)
        if (!k) return null

        const aliasTo = sciAliases.get(k)
        if (aliasTo) {
          const idAlias = speciesIdByKey.get(aliasTo)
          if (idAlias != null) return idAlias
        }

        const compact = k.replace(/\s+/g, '')
        const m0 = compact.match(/^([a-z])([a-z]{3,})$/)
        if (m0) {
          const gi = m0[1]
          const ep = m0[2]
          const hit = genusInitialEpithetToId.get(`${gi} ${ep}`) ?? genusInitialEpithetToId.get(`${gi}${ep}`)
          if (hit != null) return hit
          const aliasKey = sciAliases.get(`${gi} ${ep}`) || sciAliases.get(`${gi}${ep}`)
          if (aliasKey) {
            const id2 = speciesIdByKey.get(aliasKey)
            if (id2 != null) return id2
          }
          if (gi === 'l') {
            const aliasKey2 = sciAliases.get(`lessonia ${ep}`)
            if (aliasKey2) {
              const id3 = speciesIdByKey.get(aliasKey2)
              if (id3 != null) return id3
            }
          }
        }

        const sciLike = /^[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ.-]+\s+[A-Za-zÁÉÍÓÚÑáéíóúñ.-]+/.test(baseRaw)
        if (sciLike) {
          const directSci = speciesIdByKey.get(k)
          if (directSci != null) return directSci
        }

        const direct = speciesIdByKey.get(k)
        if (direct != null) return direct

        const toks0 = k.split(' ').filter(Boolean)
        if (toks0.length >= 2 && toks0[0].length === 1) {
          const g0 = toks0[0]
          const epithet = toks0[1]
          const hit = genusInitialEpithetToId.get(`${g0} ${epithet}`) ?? genusInitialEpithetToId.get(`${g0}${epithet}`)
          if (hit != null) return hit
          const aliasKey = sciAliases.get(`${g0} ${epithet}`) || sciAliases.get(`${g0}${epithet}`) || sciAliases.get(`lessonia ${epithet}`)
          if (aliasKey) {
            const id2 = speciesIdByKey.get(aliasKey)
            if (id2 != null) return id2
          }
        }

        const tokens = k.split(' ').filter(Boolean)
        if (!tokens.length) return null
        let best = null
        for (const sp of especies) {
          const id = Number(sp?.id)
          if (!Number.isFinite(id)) continue
          const kc = normHeader(sp?.com)
          const ks = normHeader(sp?.sci)
          const ok = (kc && tokens.every((t) => kc.includes(t))) || (ks && tokens.every((t) => ks.includes(t)))
          if (!ok) continue
          const score = Math.min((kc || '').length || 9999, (ks || '').length || 9999)
          if (!best || score < best.score) best = { id, score }
        }
        return best?.id ?? null
      }

      const regionsByKey = (() => {
        const m = new Map()
        for (const r of regionesChile) {
          const id = String(r?.id ?? '')
          if (!id) continue
          const nom = normHeader(r?.nom)
          const rom = normHeader(r?.rom)
          if (nom) m.set(nom, r.id)
          if (rom) m.set(rom, r.id)
          m.set(normHeader(String(r?.id ?? '')), r.id)
        }
        return m
      })()

      const resolveRegionId = (raw) => {
        const s = String(raw ?? '').trim()
        const n = parseIntSafe(s)
        if (n != null) return n
        const k = normHeader(s)
        if (!k) return regionesChile[0]?.id || 1
        const hit = regionsByKey.get(k)
        if (hit != null) return hit
        const hit2 = regionesChile.find((r) => normHeader(r?.nom).includes(k) || k.includes(normHeader(r?.nom)))
        return hit2?.id ?? (regionesChile[0]?.id || 1)
      }

      const resolveOpa = (rawOrg) => {
        const k = normHeader(rawOrg)
        if (!k) return { opaId: '', org: String(rawOrg || '').trim() }
        const hit = opaArr.find((o) => normHeader(o?.nombrecorto) === k || normHeader(o?.nombre) === k)
        if (hit) return { opaId: String(hit.id), org: String(hit.nombre || hit.nombrecorto || rawOrg || '').trim() }
        const hit2 = opaArr.find(
          (o) =>
            (normHeader(o?.nombrecorto) && k.includes(normHeader(o?.nombrecorto))) ||
            (normHeader(o?.nombre) && k.includes(normHeader(o?.nombre))),
        )
        if (hit2) return { opaId: String(hit2.id), org: String(hit2.nombre || hit2.nombrecorto || rawOrg || '').trim() }
        return { opaId: '', org: String(rawOrg || '').trim() }
      }

      const aoaE = XLSX.utils.sheet_to_json(wb.Sheets[evadirSheetName], { header: 1, raw: true, defval: '' })
      const hdrRowIdx = guessHeaderRow(aoaE)
      const headerRow = Array.isArray(aoaE?.[hdrRowIdx]) ? aoaE[hdrRowIdx] : []
      const keys = headerRow.map(normHeader)
      const dataRows = (Array.isArray(aoaE) ? aoaE : [])
        .slice(hdrRowIdx + 1)
        .filter((r) => Array.isArray(r) && r.some((c) => String(c ?? '').trim() !== ''))

      const idxBy = (tests) => {
        for (let i = 0; i < keys.length; i++) {
          const k = keys[i]
          if (!k) continue
          for (const t of tests) {
            if (typeof t === 'string' && k === t) return i
            if (t instanceof RegExp && t.test(k)) return i
          }
        }
        return -1
      }

      const iTipoUnidad = idxBy([/^tipo( de)? unidad$/, /^tipo unidad$/])
      const iRegion = idxBy(['region'])
      const iCaleta = idxBy([/caleta/, /sector\/caleta/])
      const iSector = iCaleta >= 0 ? iCaleta : idxBy([/nombre sector/, /^sector$/])
      const iTipoOrg = idxBy([/tipo de organizacion/, /tipo organizacion/, /^tipo org$/, /de organiza/])
      const iOrgNombre = idxBy([/^nombre organizacion$/, /nombre.*organizacion/, /^nombre org$/, /nombre.*org/])
      const iOrgGenerico = idxBy([/^organizacion$/, /organizacion/])
      const iOrg = (() => {
        if (iOrgNombre >= 0) return iOrgNombre
        if (iOrgGenerico < 0) return -1
        const k = keys[iOrgGenerico] || ''
        const isTipoCol = (iTipoOrg >= 0 && iOrgGenerico === iTipoOrg) || (k.includes('tipo') && k.includes('organiz'))
        if (!isTipoCol) return iOrgGenerico
        for (let i = 0; i < keys.length; i++) {
          const kk = keys[i] || ''
          if (!kk.includes('organizacion')) continue
          if (kk.includes('tipo')) continue
          return i
        }
        return -1
      })()
      const iFecha = idxBy(['fecha'])
      const iSeg = idxBy([/seg/, /esba/, /seguimiento/])
      const iZona = idxBy([/zona muestreo/, /^zona$/, /zona muestre/])
      const iBote = idxBy(['bote'])
      const iBuzo = idxBy(['buzo'])
      const iNumCuad = idxBy([/num.*cuadr/])
      const iNumTran = idxBy([/num.*transec/, /num.*transe/])
      const iNum = iNumCuad >= 0 ? iNumCuad : iNumTran >= 0 ? iNumTran : idxBy([/^num$/])
      const iAreaCuad = idxBy([/area.*cuadr/])
      const iAreaTran = idxBy([/area.*transec/, /area.*transe/])
      const iArea = iAreaCuad >= 0 ? iAreaCuad : iAreaTran >= 0 ? iAreaTran : idxBy([/^area$/])
      const iSustrato = idxBy([/tipo sustrato/, /tipo de sustrato/, /sustrato/])
      const iCubierta = idxBy([/cubierta biologica/, /cubierta/])
      const iX = idxBy(['x'])
      const iY = idxBy(['y'])
      const iLong = idxBy(['long'])
      const iLat = idxBy(['lat'])
      const iDatum = idxBy(['datum'])

      const numCols = []
      const densCols = []
      for (let c = 0; c < headerRow.length; c++) {
        const rawH = String(headerRow[c] ?? '').trim()
        const k = normHeader(rawH)
        if (!k) continue
        const isMetaNumCol =
          (iSeg >= 0 && c === iSeg) ||
          (iZona >= 0 && c === iZona) ||
          (iNum >= 0 && c === iNum) ||
          (iNumTran >= 0 && c === iNumTran) ||
          (iNumCuad >= 0 && c === iNumCuad) ||
          (iArea >= 0 && c === iArea) ||
          (iX >= 0 && c === iX) ||
          (iY >= 0 && c === iY) ||
          (iLong >= 0 && c === iLong) ||
          (iLat >= 0 && c === iLat)

        if (k.startsWith('num ')) {
          if (!isMetaNumCol) numCols.push({ c, name: rawH })
        } else if (k.startsWith('dens ')) {
          densCols.push({ c, name: rawH })
        }
      }

      const metaRows = []
      const boatMap = new Map()
      const unmatchedEvadirColsUsed = new Map()
      const allDates = []

      for (const row of dataRows) {
        const rawBote = iBote >= 0 ? row[iBote] : ''
        const bote = String(rawBote ?? '').trim()
        const zona = parseIntSafe(iZona >= 0 ? row[iZona] : null) ?? 1
        if (!bote) continue

        const buzo = String(iBuzo >= 0 ? row[iBuzo] ?? '' : '').trim()
        const num = parseIntSafe(iNum >= 0 ? row[iNum] : null)
        if (num == null) continue

        const tipoFromHdr = iNumCuad >= 0 ? 'cuadrante' : 'transecto'
        const tipoFromRow = (() => {
          if (iTipoUnidad < 0) return null
          const v = String(row[iTipoUnidad] ?? '').toLowerCase()
          if (v.includes('cuadr')) return 'cuadrante'
          if (v.includes('tran')) return 'transecto'
          return null
        })()
        const tipo = tipoFromRow || tipoFromHdr

        const area = parseNumSafe(iArea >= 0 ? row[iArea] : null) ?? 0
        const fecha = parseDateISO(iFecha >= 0 ? row[iFecha] : null, XLSX) || ''
        if (fecha) allDates.push(fecha)

        const sustrato = String(iSustrato >= 0 ? row[iSustrato] ?? '' : '').trim()
        const cubierta = String(iCubierta >= 0 ? row[iCubierta] ?? '' : '').trim()
        const coordX = parseNumSafe(iX >= 0 ? row[iX] : null)
        const coordY = parseNumSafe(iY >= 0 ? row[iY] : null)
        const coordLong = parseNumSafe(iLong >= 0 ? row[iLong] : null)
        const coordLat = parseNumSafe(iLat >= 0 ? row[iLat] : null)
        const datum = String(iDatum >= 0 ? row[iDatum] ?? '' : '').trim()

        const counts = {}
        if (numCols.length) {
          for (const col of numCols) {
            const spId = resolveSpeciesId(col.name)
            const rawV = row[col.c]
            const n = parseIntSafe(rawV)
            if (spId == null) {
              if (n != null && n > 0) {
                const keyUn = String(col.name || '').trim()
                if (keyUn) unmatchedEvadirColsUsed.set(keyUn, (unmatchedEvadirColsUsed.get(keyUn) || 0) + 1)
              }
              continue
            }
            if (n == null) continue
            counts[spId] = Math.max(0, n)
          }
        } else if (densCols.length) {
          for (const col of densCols) {
            const spId = resolveSpeciesId(col.name)
            const dens = parseNumSafe(row[col.c])
            if (spId == null) {
              if (dens != null && dens > 0) {
                const keyUn = String(col.name || '').trim()
                if (keyUn) unmatchedEvadirColsUsed.set(keyUn, (unmatchedEvadirColsUsed.get(keyUn) || 0) + 1)
              }
              continue
            }
            if (dens == null) continue
            const cnt = area > 0 ? Math.round(dens * area) : 0
            counts[spId] = Math.max(0, cnt)
          }
        }

        let especieId = null
        if (tipo === 'cuadrante') {
          const entries = Object.entries(counts)
          const pos = entries.find(([, v]) => Number(v) > 0)
          especieId = pos ? Number(pos[0]) : entries.length ? Number(entries[0][0]) : null
        }

        const key = `${zona}::${bote}`
        if (!boatMap.has(key)) {
          boatMap.set(key, { zona, nombre: bote, buzo: buzo || '', transectosByKey: new Map(), lpMuestras: {} })
        }
        const b = boatMap.get(key)
        if (!b.buzo && buzo) b.buzo = buzo

        const uKey = `${tipo}:${num}`
        if (!b.transectosByKey.has(uKey)) {
          b.transectosByKey.set(uKey, {
            num,
            tipo,
            area,
            fecha: fecha || '',
            sustrato,
            cubierta,
            counts: {},
            ...(tipo === 'cuadrante' && especieId != null ? { especieId } : {}),
            ...(coordX != null ? { coordX } : {}),
            ...(coordY != null ? { coordY } : {}),
            ...(coordLong != null ? { coordLong } : {}),
            ...(coordLat != null ? { coordLat } : {}),
            ...(datum ? { datum } : {}),
          })
        }
        const u = b.transectosByKey.get(uKey)
        if (area) u.area = area
        if (fecha) u.fecha = fecha
        if (sustrato) u.sustrato = sustrato
        if (cubierta) u.cubierta = cubierta
        if (coordX != null) u.coordX = coordX
        if (coordY != null) u.coordY = coordY
        if (coordLong != null) u.coordLong = coordLong
        if (coordLat != null) u.coordLat = coordLat
        if (datum) u.datum = datum
        Object.entries(counts).forEach(([k, v]) => {
          u.counts[k] = v
        })

        metaRows.push(row)
      }

      if (!boatMap.size) {
        toast('La hoja EVADIR no contiene filas válidas', 'red')
        return
      }

      const regionRaw = iRegion >= 0 ? firstNonEmpty(metaRows, iRegion) : ''
      const sectorRaw = iSector >= 0 ? firstNonEmpty(metaRows, iSector) : ''
      const tipoOrgRaw = iTipoOrg >= 0 ? firstNonEmpty(metaRows, iTipoOrg) : ''
      const orgRaw = iOrg >= 0 ? firstNonEmpty(metaRows, iOrg) : ''
      const segRaw = iSeg >= 0 ? firstNonEmpty(metaRows, iSeg) : ''

      const regionId = resolveRegionId(regionRaw)
      const { opaId, org } = resolveOpa(orgRaw)
      const tipoOrg = String(tipoOrgRaw || 'STI').trim() || 'STI'
      const sector = String(sectorRaw || '').trim()
      if (!sector) {
        toast('No se detectó Caleta/Sector en la hoja EVADIR', 'red')
        return
      }
      const segNum = parseIntSafe(segRaw)

      const fechas = allDates.filter(Boolean).sort()
      const fechaInicio = fechas[0] || todayISO()
      const fechaFin = fechas.length ? fechas[fechas.length - 1] : fechaInicio
      const year = fechaInicio.slice(0, 4)

      const opId = nextOpId(Array.isArray(operaciones) ? operaciones : [], year)

      const botesOut = Array.from(boatMap.values())
        .sort((a, b) => (a.zona || 0) - (b.zona || 0) || String(a.nombre || '').localeCompare(String(b.nombre || '')))
        .map((b, i) => {
          const transectos = Array.from(b.transectosByKey.values()).sort((x, y) => (x.num || 0) - (y.num || 0))
          const hasTx = transectos.some((t) => t?.tipo !== 'cuadrante')
          const hasCuad = transectos.some((t) => t?.tipo === 'cuadrante')
          const densTipo = hasCuad && !hasTx ? 'cuadrante' : 'transecto'
          return { id: `B${i + 1}`, zona: b.zona, nombre: b.nombre, buzo: b.buzo, densTipo, lpMuestras: b.lpMuestras || {}, transectos }
        })

      const parseLpSheet = (ws) => {
        const aoa = XLSX.utils.sheet_to_json(ws, { header: 1, raw: true, defval: '' })
        const hr = guessHeaderRow(aoa)
        const hdr = Array.isArray(aoa?.[hr]) ? aoa[hr] : []
        const ks = hdr.map(normHeader)
        const rows = (Array.isArray(aoa) ? aoa : []).slice(hr + 1).filter((r) => Array.isArray(r) && r.some((c) => String(c ?? '').trim() !== ''))
        const idx = (tests) => {
          for (let i = 0; i < ks.length; i++) {
            const k = ks[i]
            if (!k) continue
            for (const t of tests) {
              if (typeof t === 'string' && k === t) return i
              if (t instanceof RegExp && t.test(k)) return i
            }
          }
          return -1
        }
        const iz = idx([/zona/])
        const ib = idx(['bote'])
        const ibu = idx(['buzo'])
        const ie = idx([/especie/])
        const il = idx([/longitud/])
        const ip = idx([/peso/])
        const id = idx([/diam/, /disco/])
        if (ib < 0) return null
        const kind = id >= 0 ? 'D' : ip >= 0 ? 'LP' : il >= 0 ? 'L' : null
        if (!kind) return null
        return { rows, iz, ib, ibu, ie, il, ip, id, kind }
      }

      const normBoat = (name) => {
        const toks = normHeader(name).split(' ').filter(Boolean)
        while (toks.length && ['el', 'la', 'los', 'las', 'bote'].includes(toks[0])) toks.shift()
        return toks.join(' ')
      }

      const boatCandidates = botesOut.map((b) => ({
        b,
        zona: Number(b?.zona) || 0,
        raw: String(b?.nombre || '').trim(),
        norm: normBoat(b?.nombre),
      }))

      const boatByZonaNorm = (() => {
        const m = new Map()
        boatCandidates.forEach((x) => {
          if (!x.norm) return
          m.set(`${x.zona}::${x.norm}`, x.b)
        })
        return m
      })()

      const boatByNorm = (() => {
        const m = new Map()
        boatCandidates.forEach((x) => {
          if (!x.norm) return
          if (!m.has(x.norm)) m.set(x.norm, x.b)
        })
        return m
      })()

      const resolveBoatFromLp = (boteRaw, zonaRaw) => {
        const name = String(boteRaw || '').trim()
        if (!name) return null
        const zona = parseIntSafe(zonaRaw) ?? 0
        const n = normBoat(name)
        if (!n) return null
        if (zona > 0) {
          const exactZ = boatByZonaNorm.get(`${zona}::${n}`)
          if (exactZ) return exactZ
        }
        const exact = boatByNorm.get(n)
        if (exact) return exact
        const pool = boatCandidates.filter((x) => (zona > 0 ? x.zona === zona : true))
        let best = null
        for (const x of pool) {
          if (!x.norm) continue
          const ok = x.norm.includes(n) || n.includes(x.norm)
          if (!ok) continue
          const score = x.norm.length
          if (!best || score > best.score) best = { b: x.b, score }
        }
        return best?.b ?? null
      }

      const unresolvedGroups = []
      const unresolvedByKey = new Map()
      const ensureGroup = (sheetName, parsedKind) => {
        const key = `${sheetName}::${parsedKind}`
        if (unresolvedByKey.has(key)) return unresolvedByKey.get(key)
        const g = { key, sheetName, kind: parsedKind, rows: [], items: [], resolvedSpeciesId: null }
        unresolvedByKey.set(key, g)
        unresolvedGroups.push(g)
        return g
      }

      for (const sn of sheetNames) {
        if (sn === evadirSheetName) continue
        const ws = wb.Sheets?.[sn]
        if (!ws) continue
        const parsed = parseLpSheet(ws)
        if (!parsed) continue
        const spHintId = guessSpeciesFromSheetName(sn)
        for (const r of parsed.rows) {
          const zona = parsed.iz >= 0 ? r[parsed.iz] : null
          const zonaNum = parseIntSafe(zona) ?? 0

          const boteCell = String(r[parsed.ib] ?? '').trim()
          const buzoCell = String(parsed.ibu >= 0 ? r[parsed.ibu] ?? '' : '').trim()
          const useAltAsBoat = !boteCell && !!buzoCell

          const boatNameForLookup = boteCell || buzoCell

          let b = boatNameForLookup ? resolveBoatFromLp(boatNameForLookup, zonaNum) : null
          if (!b) {
            if (zonaNum > 0) {
              const pool = boatCandidates.filter((x) => x.zona === zonaNum)
              if (pool.length) b = pool[0].b
            }
            if (!b && boatCandidates.length === 1) b = boatCandidates[0].b
          }
          if (!b) continue

          const espRaw = parsed.ie >= 0 ? String(r[parsed.ie] ?? '').trim() : ''
          const spIdAuto = resolveSpeciesId(espRaw) ?? spHintId
          if (spIdAuto == null) {
            const g = ensureGroup(sn, parsed.kind)
            if (g.rows.length < 120) {
              g.rows.push({
                zona: zonaNum || '',
                bote: boatNameForLookup || '',
                especie: espRaw || '',
                longitud: parsed.il >= 0 ? String(r[parsed.il] ?? '').trim() : '',
                peso: parsed.ip >= 0 ? String(r[parsed.ip] ?? '').trim() : '',
                diametro: parsed.id >= 0 ? String(r[parsed.id] ?? '').trim() : '',
              })
            }
            g.items.push({
              b,
              kind: parsed.kind,
              l: parsed.il >= 0 ? String(r[parsed.il] ?? '').trim() : '',
              p: parsed.ip >= 0 ? String(r[parsed.ip] ?? '').trim() : '',
              d: parsed.id >= 0 ? String(r[parsed.id] ?? '').trim() : '',
            })
            continue
          }
          const spId = spIdAuto

          let map = b.lpMuestras && typeof b.lpMuestras === 'object' ? b.lpMuestras : {}

          const isAlga = algaIdSet.has(Number(spId))
          const forceD = parsed.kind === 'L' && isAlga

          if (parsed.kind === 'D') {
            const d = String(r[parsed.id] ?? '').trim()
            if (!d) continue
            map = addSample(map, spId, 'D', { d })
            b.lpMuestras = map
          } else if (parsed.kind === 'LP') {
            const l = String(r[parsed.il] ?? '').trim()
            const p = String(r[parsed.ip] ?? '').trim()
            map = addSample(map, spId, 'LP', { l, p })
            b.lpMuestras = map
          } else {
            const l = String(r[parsed.il] ?? '').trim()
            if (!l) continue
            if (forceD) map = addSample(map, spId, 'D', { d: l })
            else map = addSample(map, spId, 'L', { l })
            b.lpMuestras = map
          }
          if (!useAltAsBoat) {
            if (!b.buzo && buzoCell) b.buzo = buzoCell
          }
        }
      }

      const resolvePendingSpecies = async () => {
        if (!unresolvedGroups.length) return true
        return await new Promise((resolve) => {
          const BodyResolve = () => {
            const [idx, setIdx] = useState(0)
            const g = unresolvedGroups[idx]
            const sid = Number(g?.resolvedSpeciesId || 0)
            const selectedIds = sid ? [sid] : []
            const onPick = (ids) => {
              const id = Number(Array.isArray(ids) ? ids[0] : null)
              g.resolvedSpeciesId = Number.isFinite(id) ? id : null
            }
            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
                  <div style={{ fontWeight: 800 }}>
                    Resolver especie manualmente ({idx + 1}/{unresolvedGroups.length}) - {g.sheetName}
                  </div>
                  <div style={{ color: 'var(--text3)' }}>{g.items.length} filas pendientes</div>
                </div>
                <div style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 10 }}>
                  <div style={{ fontWeight: 700, marginBottom: 8 }}>Selecciona especie para esta tabla</div>
                  <SpeciesGrid especies={especies} selectedIds={selectedIds} onChange={onPick} multi={false} columns={3} maxHeight={260} />
                </div>
                <div style={{ border: '1px solid var(--border)', borderRadius: 10, overflow: 'auto', maxHeight: 320 }}>
                  <table className="tbl">
                    <thead>
                      <tr>
                        <th>Zona</th>
                        <th>Bote</th>
                        <th>Especie</th>
                        <th>Longitud</th>
                        <th>Peso</th>
                        <th>Diámetro</th>
                      </tr>
                    </thead>
                    <tbody>
                      {g.rows.map((r, i) => (
                        <tr key={`${g.key}-${i}`}>
                          <td>{r.zona}</td>
                          <td>{r.bote}</td>
                          <td>{r.especie}</td>
                          <td>{r.longitud}</td>
                          <td>{r.peso}</td>
                          <td>{r.diametro}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between' }}>
                  <button
                    className="btn b-out"
                    onClick={() => {
                      closeModal()
                      resolve(false)
                    }}
                  >
                    Cancelar importación
                  </button>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button className="btn b-out" disabled={idx === 0} onClick={() => setIdx((p) => Math.max(0, p - 1))}>
                      Anterior
                    </button>
                    <button
                      className="btn b-teal"
                      onClick={() => {
                        if (!g.resolvedSpeciesId) {
                          toast('Selecciona una especie para continuar', 'red')
                          return
                        }
                        if (idx < unresolvedGroups.length - 1) {
                          setIdx((p) => p + 1)
                          return
                        }
                        closeModal()
                        resolve(true)
                      }}
                    >
                      {idx < unresolvedGroups.length - 1 ? 'Siguiente' : 'Aplicar y continuar'}
                    </button>
                  </div>
                </div>
              </div>
            )
          }
          openModal('Resolver especies no identificadas', <BodyResolve />, 'full')
        })
      }

      const pendingOk = await resolvePendingSpecies()
      if (!pendingOk) return

      for (const g of unresolvedGroups) {
        const spId = Number(g.resolvedSpeciesId)
        if (!Number.isFinite(spId)) continue
        for (const it of g.items) {
          const b = it.b
          let map = b.lpMuestras && typeof b.lpMuestras === 'object' ? b.lpMuestras : {}
          const isAlga = algaIdSet.has(Number(spId))
          const forceD = it.kind === 'L' && isAlga
          if (it.kind === 'D') {
            if (!it.d) continue
            map = addSample(map, spId, 'D', { d: it.d })
            b.lpMuestras = map
          } else if (it.kind === 'LP') {
            map = addSample(map, spId, 'LP', { l: it.l || '', p: it.p || '' })
            b.lpMuestras = map
          } else {
            if (!it.l) continue
            if (forceD) map = addSample(map, spId, 'D', { d: it.l })
            else map = addSample(map, spId, 'L', { l: it.l })
            b.lpMuestras = map
          }
        }
      }

      const opDraft = {
        id: opId,
        region: regionId,
        sectorAmerbId: '',
        sectorAmerb: '',
        sector: sector,
        tipoOrg,
        opaId,
        org,
        numSeg: segNum == null ? null : segNum,
        fechaInicio,
        fechaFin,
        botes: botesOut,
      }

      const okPreview = await new Promise((resolve) => {
        const BodyPreview = () => (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <EvadirPreview db={db} op={opDraft} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button
                className="btn b-out"
                onClick={() => {
                  closeModal()
                  resolve(false)
                }}
              >
                Cancelar
              </button>
              <button
                className="btn b-teal"
                onClick={() => {
                  closeModal()
                  resolve(true)
                }}
              >
                Aceptar y subir operación
              </button>
            </div>
          </div>
        )
        openModal('Previsualización importación EVADIR', <BodyPreview />, 'full')
      })
      if (!okPreview) return

      safeUpsertOperacion(opDraft)

      if (unmatchedEvadirColsUsed.size) {
        const list = Array.from(unmatchedEvadirColsUsed.entries())
          .map(([k]) => k)
          .slice(0, 3)
          .join(', ')
        toast(`Operación importada (${opId}). Columnas EVADIR no mapeadas: ${list}`, 'blue')
      } else {
        toast(`Operación importada (${opId}) correctamente`, 'green')
      }
    } catch {
      toast('No se pudo leer el Excel', 'red')
    } finally {
      setIsImportingEvadir(false)
    }
  }

  return (
    <>
      <button
        className="btn b-out b-sm"
        disabled={!canWrite || isImportingEvadir}
        onClick={() => {
          if (!canWrite) {
            toast('Modo solo lectura', 'blue')
            return
          }
          evadirInputRef.current?.click?.()
        }}
      >
        Subir EVADIR
      </button>
      <input
        ref={evadirInputRef}
        type="file"
        accept=".xlsx,.xls"
        style={{ display: 'none' }}
        onChange={(e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          importEvadirFromXlsx(f)
        }}
      />
    </>
  )
}
