import { useMemo, useState } from 'react'
import { buildEvadirPreviewSheets } from '../../services/evadirPreviewService.js'

function formatCell(header, v) {
  if (v === null || v === undefined) return ''
  const h = String(header || '')
  if (typeof v === 'number' && Number.isFinite(v)) {
    if (h === 'X' || h === 'Y' || h === 'LONG' || h === 'LAT') return v.toFixed(8)
    if (/^DENS /i.test(h)) return v.toFixed(4)
    if (h === 'IC') return v.toFixed(10)
    return Number.isInteger(v) ? String(v) : String(v)
  }
  if (typeof v === 'object') return v?.v ?? ''
  return String(v)
}

function defaultColWidth(h) {
  const k = String(h || '').toUpperCase().trim()
  if (k === 'ESPECIE') return 240
  if (k === 'SECTOR') return 220
  if (k === 'AMERB') return 240
  if (k === 'ORGANIZACION' || k === 'ORGANIZACIÓN') return 260
  if (k === 'OBS' || k === 'OBSERVACION' || k === 'OBSERVACIÓN') return 260
  if (k === 'LONG' || k === 'LAT') return 180
  if (k === 'X' || k === 'Y') return 160
  if (k.startsWith('DENS')) return 260
  if (k === 'IC') return 180
  return 150
}

export default function EvadirPreview({ db, op }) {
  const { sheets } = useMemo(() => buildEvadirPreviewSheets({ db, op }), [db, op])
  const [tab, setTab] = useState(() => sheets[0]?.name || 'EVADIR')

  const active = sheets.find((s) => s.name === tab) || sheets[0] || null
  const aoa = active?.aoa || []
  const header = Array.isArray(aoa?.[0]) ? aoa[0] : []
  const rows = aoa.slice(1)
  const [colWidths, setColWidths] = useState(() => (Array.isArray(sheets?.[0]?.aoa?.[0]) ? sheets[0].aoa[0] : []).map(defaultColWidth))

  const activeColWidths =
    Array.isArray(colWidths) && colWidths.length === header.length ? colWidths : header.map(defaultColWidth)

  const startResize = (idx, e) => {
    e.preventDefault()
    e.stopPropagation()
    const startX = e.clientX
    const startW = Number(colWidths[idx] ?? defaultColWidth(header[idx]))
    const minW = 90

    const onMove = (ev) => {
      const dx = ev.clientX - startX
      const nextW = Math.max(minW, startW + dx)
      setColWidths((prev) => {
        const next = prev.slice()
        next[idx] = nextW
        return next
      })
    }

    const onUp = () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }

    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
  }

  if (!op) {
    return (
      <div className="info-box red">
        <span>!</span>
        <div>Operación no encontrada</div>
      </div>
    )
  }

  return (
    <div className="evp">
      <div className="info-box blue">
        <span>i</span>
        <div>
          <strong>{op.id}</strong> · SEG-{op.numSeg ?? '—'} · {op.sector}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {sheets.map((s) => (
          <button
            key={s.name}
            className={`btn b-sm ${s.name === tab ? 'b-teal' : 'b-out'}`}
            onClick={() => {
              setTab(s.name)
              const nextHeader = Array.isArray(s?.aoa?.[0]) ? s.aoa[0] : []
              setColWidths(nextHeader.map(defaultColWidth))
            }}
          >
            {s.name}
          </button>
        ))}
      </div>

      <div className="evp-wrap">
        <table className="tbl evp-tbl">
          <colgroup>
            {header.map((_, idx) => (
              <col key={idx} style={{ width: `${Math.max(90, Number(activeColWidths[idx] ?? defaultColWidth(header[idx])))}px` }} />
            ))}
          </colgroup>
          <thead>
            <tr>
              {header.map((h, idx) => (
                <th key={idx}>
                  {h}
                  <span className="evp-resizer" onMouseDown={(e) => startResize(idx, e)} />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length ? (
              rows.map((r, ridx) => (
                <tr key={ridx}>
                  {header.map((h, cidx) => (
                    <td key={cidx}>{formatCell(h, r?.[cidx])}</td>
                  ))}
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={header.length || 1} style={{ textAlign: 'center', color: 'var(--text3)' }}>
                  Sin datos
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
