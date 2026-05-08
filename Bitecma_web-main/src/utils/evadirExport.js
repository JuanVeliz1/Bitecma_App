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

function safeFilePart(s) {
  const raw = String(s || '')
  let out = ''
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i]
    const code = raw.charCodeAt(i)
    if (code < 32) {
      out += ' '
      continue
    }
    if ('<>:"/\\|?*'.includes(ch)) {
      out += ' '
      continue
    }
    out += ch
  }
  return out.replace(/\s+/g, ' ').trim()
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
    if (typeof v === 'number' && Number.isFinite(v)) return String(v)
    const s = String(v).trim()
    if (s !== '') return s
  }
  return ''
}

export async function exportEvadirXlsx({ db, opId, toast }) {
  const op = (db?.operaciones || []).find((o) => o.id === opId)
  const ESPECIES = db?.especies || []
  if (!op) {
    toast?.('Operación no encontrada', 'red')
    return
  }

  try {
    const xlsxMod = await import('xlsx-js-style')
    const XLSX = xlsxMod?.default || xlsxMod
    const wb = XLSX.utils.book_new()

    const usedSheetNames = new Set()
    const sanitizeSheetName = (s) => {
      const raw = String(s || '')
      let out = ''
      for (let i = 0; i < raw.length; i++) {
        const ch = raw[i]
        if ('[]:*?/\\'.includes(ch)) out += ' '
        else out += ch
      }
      return out.replace(/\s+/g, ' ').trim()
    }
    const makeSheetName = (base) => {
      let name = sanitizeSheetName(base).slice(0, 31) || 'Hoja'
      if (!usedSheetNames.has(name)) {
        usedSheetNames.add(name)
        return name
      }
      const baseTrim = name.slice(0, 28)
      let i = 2
      while (i < 1000) {
        const cand = `${baseTrim} ${i}`.slice(0, 31)
        if (!usedSheetNames.has(cand)) {
          usedSheetNames.add(cand)
          return cand
        }
        i++
      }
      return name
    }

    const headerStyle = {
      font: { name: 'Arial', sz: 10, bold: true, color: { rgb: 'FFFFFFFF' } },
      fill: { patternType: 'solid', fgColor: { rgb: 'FF16365C' } },
      alignment: { horizontal: 'center', vertical: 'center' },
    }
    const bodyStyle = {
      font: { name: 'Calibri', sz: 11 },
      alignment: { horizontal: 'center', vertical: 'center' },
    }

    const getDisplayVal = (v) => {
      if (v === null || v === undefined) return ''
      if (typeof v === 'object') return v.v ?? ''
      return v
    }

    const applySheetStyles = (ws, aoa) => {
      const ref = ws['!ref']
      if (!ref) return
      const range = XLSX.utils.decode_range(ref)
      const headerRow = Array.isArray(aoa?.[0]) ? aoa[0] : []
      const headerTextAt = (c) => String(headerRow?.[c] ?? '').trim()
      const isCoordHeader = (h) => h === 'X' || h === 'Y' || h === 'LONG' || h === 'LAT'
      const isDensHeader = (h) => /^DENS /i.test(h)
      const isIcHeader = (h) => h === 'IC'
      const isAreaHeader = (h) => /^AREA(\s|$)/i.test(h)
      const intFmt = '0'
      const densFmt = '0.0000'
      const icFmt = '0.0000000000'
      const coordFmt = '0.########'
      const areaFmt = '0.####'

      for (let r = range.s.r; r <= range.e.r; r++) {
        for (let c = range.s.c; c <= range.e.c; c++) {
          const addr = XLSX.utils.encode_cell({ r, c })
          const cell = ws[addr]
          if (!cell) continue
          const h = headerTextAt(c)
          if (r === 0) {
            cell.s = headerStyle
            continue
          }
          cell.s = bodyStyle
          if (cell.t === 'n' || cell.f) {
            if (isIcHeader(h)) cell.z = icFmt
            else if (isDensHeader(h)) cell.z = densFmt
            else if (isCoordHeader(h)) cell.z = coordFmt
            else if (isAreaHeader(h)) {
              const v = typeof cell.v === 'number' ? cell.v : Number(cell.v)
              cell.z = Number.isFinite(v) && v < 1 ? areaFmt : intFmt
            } else {
              cell.z = intFmt
            }
          }
        }
      }

      const colCount = Array.isArray(aoa?.[0]) ? aoa[0].length : 0
      const cols = Array.from({ length: colCount }, () => ({ wch: 10 }))
      for (let c = 0; c < colCount; c++) {
        let maxLen = 0
        const rowsToScan = Math.min(aoa.length, 500)
        for (let r = 0; r < rowsToScan; r++) {
          const v = getDisplayVal(aoa[r]?.[c])
          const s = v === '' ? '' : String(v)
          if (s.length > maxLen) maxLen = s.length
        }
        let wch = Math.min(Math.max(maxLen + 2, 8), 45)
        const header = headerTextAt(c)
        if (header === 'IC') wch = Math.max(wch, 18)
        if (/^DENS /i.test(header)) wch = Math.max(wch, 18)
        cols[c] = { wch }
      }
      ws['!cols'] = cols
    }

    const dateISO = String(op.fechaInicio || '')
    const year = /^\d{4}-\d{2}-\d{2}$/.test(dateISO)
      ? dateISO.slice(0, 4)
      : String(op.id || '').match(/\b(19|20)\d{2}\b/)?.[0] || '0000'
    const seg = String(parseInt(op.numSeg) || 0).padStart(2, '0')
    const caleta = String(op.sector || '').trim() || String(op.id || '').trim()
    const filename = `${safeFilePart(year)} SEG${safeFilePart(seg)} ${safeFilePart(caleta)} EVADIR.xlsx`.trim()

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
    const colArea = densHeader.indexOf('AREA')
    const colCountsStart = densHeader.indexOf(`NUM ${String(allSp[0]?.com || '').toUpperCase()}`)

    ;(op.botes || []).forEach((b) => {
      ;(b.transectos || []).forEach((t) => {
        if (!t) return
        const f = String(t.fecha || op.fechaInicio || '')
        const dia = /^\d{4}-\d{2}-\d{2}$/.test(f) ? f.slice(8, 10) : ''
        const mes = /^\d{4}-\d{2}-\d{2}$/.test(f) ? f.slice(5, 7) : ''
        const año = /^\d{4}-\d{2}-\d{2}$/.test(f) ? f.slice(0, 4) : year
        const row = []
        const tipoUnidad = t.tipo === 'cuadrante' ? 'Cuadrante' : 'Transecto'
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

        const r = densAoa.length
        allSpIds.forEach((id, idx) => {
          const countCol = colCountsStart + idx
          const areaRef = XLSX.utils.encode_cell({ r, c: colArea })
          const countRef = XLSX.utils.encode_cell({ r, c: countCol })
          const area = Number(t.area) || 0
          const cntCell = getCountCell(t, id)
          const cnt = cntCell === '' ? '' : Number(cntCell)
          const dens = area > 0 ? cnt / area : 0
          row.push({
            t: 'n',
            f: `IF(ISBLANK(${countRef}),"",IFERROR(${countRef}/${areaRef},0))`,
            v: cntCell === '' ? '' : dens,
            z: '0.0000',
          })
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

    const wsDens = XLSX.utils.aoa_to_sheet(densAoa)
    applySheetStyles(wsDens, densAoa)
    XLSX.utils.book_append_sheet(wb, wsDens, makeSheetName('EVADIR'))

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

    const addSheetFromRows = (sheetBaseName, header, rows, buildRowFn, addIc) => {
      const aoa = [header, ...rows.map(buildRowFn)]
      const ws = XLSX.utils.aoa_to_sheet(aoa)
      if (addIc) {
        const icCol = header.length - 1
        const lenCol = header.indexOf('LONGITUD MM')
        const pesoCol = header.indexOf('PESO G')
        for (let rr = 2; rr <= aoa.length; rr++) {
          const r0 = rr - 1
          const lenRef = XLSX.utils.encode_cell({ r: r0, c: lenCol })
          const pesoRef = XLSX.utils.encode_cell({ r: r0, c: pesoCol })
          const icAddr = XLSX.utils.encode_cell({ r: r0, c: icCol })
          if (!ws[icAddr]) ws[icAddr] = { t: 'n', v: 0 }
          ws[icAddr].f = `IFERROR(${pesoRef}/(${lenRef}^3),0)`
          ws[icAddr].z = '0.0000000000'
        }
      }
      applySheetStyles(ws, aoa)
      XLSX.utils.book_append_sheet(wb, ws, makeSheetName(sheetBaseName))
    }

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
        addSheetFromRows(
          `LP ${com}`,
          header,
          rows,
          (r) => {
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
          },
          true,
        )
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
        addSheetFromRows(
          `L ${com}`,
          header,
          rows,
          (r) => [
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
          ],
          false,
        )
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
        addSheetFromRows(
          `D ${com}`,
          header,
          rows,
          (r) => [
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
          ],
          false,
        )
      }
    })

    const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
    const blob = new Blob([out], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1200)
    toast?.('Excel EVADIR descargado', 'green')
  } catch {
    toast?.('No se pudo generar el Excel', 'red')
  }
}
