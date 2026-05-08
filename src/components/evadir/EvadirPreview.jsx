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

export default function EvadirPreview({ db, op }) {
  const { sheets } = useMemo(() => buildEvadirPreviewSheets({ db, op }), [db, op])
  const [tab, setTab] = useState(() => sheets[0]?.name || 'EVADIR')

  const active = sheets.find((s) => s.name === tab) || sheets[0] || null
  const aoa = active?.aoa || []
  const header = Array.isArray(aoa?.[0]) ? aoa[0] : []
  const rows = aoa.slice(1)

  if (!op) {
    return (
      <div className="info-box red">
        <span>!</span>
        <div>Operación no encontrada</div>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div className="info-box blue">
        <span>i</span>
        <div>
          <strong>{op.id}</strong> · SEG-{op.numSeg ?? '—'} · {op.sector}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {sheets.map((s) => (
          <button key={s.name} className={`btn b-sm ${s.name === tab ? 'b-teal' : 'b-out'}`} onClick={() => setTab(s.name)}>
            {s.name}
          </button>
        ))}
      </div>

      <div style={{ overflow: 'auto', border: '1px solid var(--border)', borderRadius: 10, maxHeight: '70vh' }}>
        <table className="tbl lp-tbl">
          <thead>
            <tr>
              {header.map((h, idx) => (
                <th key={idx}>{h}</th>
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

