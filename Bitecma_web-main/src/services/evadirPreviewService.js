function normNumber(v) {
  if (v === null || v === undefined || v === '') return null
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  const n = Number(String(v).trim().replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

function numOrBlank(v) {
  const n = normNumber(v)
  return n === null ? '' : n
}

function getTxCoordValue(t, key) {
  if (!t) return ''
  const map = {
    x: ['coordX', 'x'],
    y: ['coordY', 'y'],
    lon: ['coordLong', 'lon'],
    lat: ['coordLat', 'lat'],
  }
  const candidates = map[key] || []
  for (const k of candidates) {
    const v = t[k]
    if (v === null || v === undefined) continue
    if (typeof v === 'number' && Number.isFinite(v)) return v
    const n = normNumber(v)
    if (n !== null) return n
    const s = String(v).trim()
    if (s !== '') return s
  }
  return ''
}

export function buildEvadirPreviewSheets({ db, op }) {
  const ESPECIES = Array.isArray(db?.especies) ? db.especies : []
  if (!op) return { sheets: [], meta: null }

  const allTx = (op.botes || []).flatMap((b) => (b.transectos || []).map((t) => ({ b, t })))
  const hasAnyTx = allTx.some((x) => x.t?.tipo !== 'cuadrante')
  const hasAnyCuad = allTx.some((x) => x.t?.tipo === 'cuadrante')
  const mixedTypes = hasAnyTx && hasAnyCuad

  const allSpIds = [
    ...new Set(allTx.flatMap((x) => Object.keys(x.t?.counts || {}).map(Number)).filter((x) => !isNaN(x))),
  ].sort((a, b) => a - b)
  const allSp = allSpIds.map((id) => ESPECIES.find((e) => e.id == id)).filter(Boolean)

  const txSpeciesIds = new Set()
  const cuadSpeciesIds = new Set()
  allTx.forEach(({ t }) => {
    const tipo = String(t?.tipo || 'transecto')
    const countIds = Object.keys(t?.counts || {})
      .map(Number)
      .filter((x) => Number.isFinite(x))
    if (tipo === 'cuadrante') {
      const especieId = Number(t?.especieId)
      if (Number.isFinite(especieId)) cuadSpeciesIds.add(especieId)
      countIds.forEach((id) => cuadSpeciesIds.add(id))
      return
    }
    countIds.forEach((id) => txSpeciesIds.add(id))
  })

  const getCountCell = (t, spId) => {
    const tipo = String(t?.tipo || 'transecto')
    const counts = t?.counts && typeof t.counts === 'object' ? t.counts : {}
    const hasOwn = Object.prototype.hasOwnProperty.call(counts, spId)
    if (tipo === 'cuadrante') {
      const especieId = Number(t?.especieId)
      const isRowSpecies = Number.isFinite(especieId) ? especieId === spId : hasOwn
      if (!isRowSpecies) return ''
      return Number(counts?.[spId] ?? 0)
    }
    if (mixedTypes && !txSpeciesIds.has(spId) && cuadSpeciesIds.has(spId)) return ''
    return Number(counts?.[spId] ?? 0)
  }

  const densHeader = [
    'REGION',
    'NOMBRE SECTOR',
    'TIPO DE ORGANIZACIÓN',
    'NOMBRE ORGANIZACIÓN',
    'FECHA',
    'DIA',
    'MES',
    'AÑO',
    'NUM SEG ESBA',
    'ZONA MUESTREO',
    'BOTE',
    'BUZO',
    'TIPO UNIDAD',
    'NUM',
    'AREA',
    ...allSp.map((s) => `NUM ${String(s.com || '').toUpperCase()}`),
    'TIPO SUSTRATO',
    'CUBIERTA BIOLOGICA',
    ...allSp.map((s) => `DENS ${String(s.com || '').toUpperCase()} (N° IND/M2)`),
    'X',
    'Y',
    'LONG',
    'LAT',
    'DATUM',
  ]

  const densAoa = [densHeader]
  ;(op.botes || []).forEach((b) => {
    ;(b.transectos || []).forEach((t) => {
      if (!t) return
      const f = String(t.fecha || op.fechaInicio || '')
      const dia = /^\d{4}-\d{2}-\d{2}$/.test(f) ? f.slice(8, 10) : ''
      const mes = /^\d{4}-\d{2}-\d{2}$/.test(f) ? f.slice(5, 7) : ''
      const año = /^\d{4}-\d{2}-\d{2}$/.test(f) ? f.slice(0, 4) : ''
      const tipoUnidad = t.tipo === 'cuadrante' ? 'Cuadrante' : 'Transecto'
      const row = []
      row.push(
        op.region,
        op.sector,
        op.tipoOrg,
        op.org,
        f,
        dia,
        mes,
        año,
        op.numSeg ?? '',
        b.zona,
        b.nombre,
        b.buzo,
        tipoUnidad,
        t.num,
        t.area,
      )
      allSpIds.forEach((id) => row.push(getCountCell(t, id)))
      row.push(String(t.sustrato || ''), String(t.cubierta || ''))
      allSpIds.forEach((id) => {
        const area = Number(t.area) || 0
        const cntCell = getCountCell(t, id)
        if (cntCell === '') {
          row.push('')
          return
        }
        const cnt = Number(cntCell)
        const dens = area > 0 ? cnt / area : 0
        row.push(dens)
      })
      row.push(
        numOrBlank(getTxCoordValue(t, 'x')),
        numOrBlank(getTxCoordValue(t, 'y')),
        numOrBlank(getTxCoordValue(t, 'lon')),
        numOrBlank(getTxCoordValue(t, 'lat')),
        String(t.datum || 'WGS 84'),
      )
      densAoa.push(row)
    })
  })

  const ALGA_IDS = new Set([14, 15, 16, 17, 18, 30, 31, 32])
  const isAlgaId = (spId) => ALGA_IDS.has(parseInt(spId))

  const lpGroups = new Map()
  const pushLP = (kind, spId, row) => {
    const key = `${kind}:${spId}`
    if (!lpGroups.has(key)) lpGroups.set(key, [])
    lpGroups.get(key).push(row)
  }

  const normKind = (kind) => {
    const k = String(kind || '').trim().toUpperCase()
    if (k === 'L-P' || k === 'LP') return 'LP'
    if (k === 'D') return 'D'
    return 'L'
  }
  const eachLpSample = (entry, cb) => {
    if (Array.isArray(entry)) {
      entry.forEach((m) => cb(m, null))
      return
    }
    if (entry && typeof entry === 'object') {
      if (Array.isArray(entry.ms)) {
        const k = normKind(entry.type || 'LP')
        entry.ms.forEach((m) => cb(m, k))
        return
      }
      ;['D', 'LP', 'L'].forEach((k) => {
        const arr = entry?.[k]
        if (Array.isArray(arr)) arr.forEach((m) => cb(m, k))
      })
    }
  }

  ;(op.botes || []).forEach((b) => {
    Object.entries(b.lpMuestras || {}).forEach(([spIdRaw, entry]) => {
      const spId = parseInt(spIdRaw)
      const sp = ESPECIES.find((e) => e.id == spId)
      eachLpSample(entry, (m, forcedKind) => {
        const isAlga = isAlgaId(spId)
        const hasPeso = m && m.p !== undefined && m.p !== null && m.p !== ''
        const kind = forcedKind || (isAlga ? 'D' : hasPeso ? 'LP' : 'L')
        pushLP(kind, spId, {
          region: op.region,
          sector: op.sector,
          tipoOrg: op.tipoOrg,
          org: op.org,
          fecha: op.fechaInicio,
          dia: String(op.fechaInicio || '').slice(8, 10),
          mes: String(op.fechaInicio || '').slice(5, 7),
          año: String(op.fechaInicio || '').slice(0, 4),
          seg: op.numSeg ?? '',
          zona: b.zona,
          bote: b.nombre,
          buzo: b.buzo,
          especie: sp?.sci || sp?.com || '',
          l: m?.l ?? m?.d ?? '',
          p: m?.p ?? '',
          d: m?.d ?? m?.l ?? '',
        })
      })
    })
  })

  const sheets = [{ name: 'EVADIR', aoa: densAoa }]

  ;[...lpGroups.entries()].forEach(([key, rows]) => {
    const [kind, spIdRaw] = key.split(':')
    const spId = parseInt(spIdRaw)
    const sp = ESPECIES.find((e) => e.id == spId)
    const com = String(sp?.com || sp?.sci || spIdRaw)
    if (kind === 'LP') {
      const header = [
        'REGION',
        'SECTOR',
        'TIPO ORG',
        'ORGANIZACIÓN',
        'FECHA',
        'DIA',
        'MES',
        'AÑO',
        'SEG',
        'ZONA',
        'BOTE',
        'BUZO',
        'ESPECIE',
        'LONGITUD MM',
        'PESO G',
        'IC',
      ]
      const aoa = [
        header,
        ...rows.map((r) => {
          const l = numOrBlank(r.l)
          const p = numOrBlank(r.p)
          const ic = l && p && Number(l) > 0 ? Number(p) / Math.pow(Number(l), 3) : 0
          return [
            r.region,
            r.sector,
            r.tipoOrg,
            r.org,
            r.fecha,
            r.dia,
            r.mes,
            r.año,
            r.seg,
            r.zona,
            r.bote,
            r.buzo,
            r.especie,
            l,
            p,
            ic,
          ]
        }),
      ]
      sheets.push({ name: `LP ${com}`, aoa })
    } else if (kind === 'L') {
      const header = [
        'REGION',
        'SECTOR',
        'TIPO ORG',
        'ORGANIZACIÓN',
        'FECHA',
        'DIA',
        'MES',
        'AÑO',
        'SEG',
        'ZONA',
        'BOTE',
        'BUZO',
        'ESPECIE',
        'LONGITUD MM',
      ]
      const aoa = [
        header,
        ...rows.map((r) => [
          r.region,
          r.sector,
          r.tipoOrg,
          r.org,
          r.fecha,
          r.dia,
          r.mes,
          r.año,
          r.seg,
          r.zona,
          r.bote,
          r.buzo,
          r.especie,
          numOrBlank(r.l),
        ]),
      ]
      sheets.push({ name: `L ${com}`, aoa })
    } else if (kind === 'D') {
      const header = [
        'REGION',
        'SECTOR',
        'TIPO ORG',
        'ORGANIZACIÓN',
        'FECHA',
        'DIA',
        'MES',
        'AÑO',
        'SEG',
        'ZONA',
        'BOTE',
        'BUZO',
        'ESPECIE',
        'DIAM DISCO CM',
      ]
      const aoa = [
        header,
        ...rows.map((r) => [
          r.region,
          r.sector,
          r.tipoOrg,
          r.org,
          r.fecha,
          r.dia,
          r.mes,
          r.año,
          r.seg,
          r.zona,
          r.bote,
          r.buzo,
          r.especie,
          numOrBlank(r.d),
        ]),
      ]
      sheets.push({ name: `D ${com}`, aoa })
    }
  })

  return {
    meta: { opId: op.id, sector: op.sector, seg: op.numSeg, fechaInicio: op.fechaInicio },
    sheets,
  }
}
