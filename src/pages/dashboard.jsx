import { useApp } from '../context/appContext.jsx'
import { useDb } from '../context/dbContext.jsx'

function toDateValue(v) {
  const s = String(v || '')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return 0
  const t = new Date(`${s}T00:00:00`).getTime()
  return Number.isFinite(t) ? t : 0
}

function fmtDMY(iso) {
  const s = String(iso || '')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return '—'
  return `${s.slice(8, 10)}/${s.slice(5, 7)}/${s.slice(0, 4)}`
}

export default function DashboardPage({ active }) {
  const { navigate } = useApp()
  const { db } = useDb()

  const especies = Array.isArray(db?.especies) ? db.especies : []
  const ops = Array.isArray(db?.operaciones) ? db.operaciones : []

  const totalOps = ops.length
  const totalUnidades = ops.reduce(
    (acc, op) => acc + (op?.botes || []).reduce((a, b) => a + ((b?.transectos || []).length || 0), 0),
    0,
  )
  const totalMuestras = ops.reduce(
    (acc, op) =>
      acc +
      (op?.botes || []).reduce(
        (a, b) =>
          a +
          Object.values(b?.lpMuestras || {}).reduce(
            (x, arr) => x + (Array.isArray(arr) ? arr.length : 0),
            0,
          ),
        0,
      ),
    0,
  )

  const recentOps = ops
    .slice()
    .sort((a, b) => toDateValue(b?.fechaInicio) - toDateValue(a?.fechaInicio))
    .slice(0, 5)

  const chartData = (() => {
    const byId = new Map((especies || []).map((e) => [Number(e.id), String(e.com || e.sci || e.id)]))
    const counts = new Map()
    for (const op of ops) {
      for (const bote of op?.botes || []) {
        const lp = bote?.lpMuestras && typeof bote.lpMuestras === 'object' ? bote.lpMuestras : {}
        for (const [k, arr] of Object.entries(lp)) {
          const spId = Number(k)
          if (!Number.isFinite(spId)) continue
          const n = Array.isArray(arr) ? arr.length : 0
          counts.set(spId, (counts.get(spId) || 0) + n)
        }
      }
    }

    const palette = ['var(--blue)', 'var(--teal)', 'var(--green)', 'var(--purple)', 'var(--amber)', '#22c55e', '#38bdf8', '#a78bfa']
    const items = [...counts.entries()]
      .map(([spId, value]) => ({ key: `sp-${spId}`, label: byId.get(spId) || `Esp. ${spId}`, value }))
      .filter((x) => x.value > 0)
      .sort((a, b) => b.value - a.value)
      .slice(0, 8)
      .map((x, i) => ({ ...x, color: palette[i % palette.length] }))

    return { items, max: Math.max(1, ...items.map((x) => x.value)) }
  })()

  const yTicksCount = 5
  const yStep = Math.max(1, Math.ceil(chartData.max / yTicksCount))
  const yMax = yStep * yTicksCount
  const yLabels = Array.from({ length: yTicksCount + 1 }, (_, i) => yMax - i * yStep)

  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-dashboard">
      <div className="ph">
        <div>
          <h2>Dashboard</h2>
          <p>Resumen operacional · EVADIR importados</p>
        </div>
        <div className="ph-a"></div>
      </div>
      <div className="g3 mb">
        <div className="sc sc-tl" onClick={() => navigate('ops')}>
          <div className="sc-lbl">Operaciones</div>
          <div className="sc-val">{totalOps}</div>
          <div className="sc-sub">Total registradas</div>
        </div>
        <div className="sc sc-gr">
          <div className="sc-lbl">Muestras L-P</div>
          <div className="sc-val">{totalMuestras}</div>
          <div className="sc-sub">Subconjunto</div>
        </div>
        <div className="sc sc-pu">
          <div className="sc-lbl">Unidades densidad</div>
          <div className="sc-val">{totalUnidades}</div>
          <div className="sc-sub">Transectos y cuadrantes</div>
        </div>
      </div>
      <div className="g2 mb" style={{ gridTemplateColumns: '1.35fr 1fr', alignItems: 'stretch' }}>
        <div className="card" style={{ minHeight: 440, display: 'flex', flexDirection: 'column' }}>
          <div className="ct">
            Operaciones recientes
            <button className="btn b-out b-sm" onClick={() => navigate('ops')}>Ver todas</button>
          </div>
          <div style={{ overflow: 'auto', minHeight: 0 }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Sector</th>
                  <th>Fecha</th>
                  <th>Botes</th>
                </tr>
              </thead>
              <tbody>
                {recentOps.length ? recentOps.map((op) => (
                  <tr key={op.id} onClick={() => navigate('ops')} style={{ cursor: 'pointer' }}>
                    <td><strong>{op.id}</strong></td>
                    <td>{op.sector || '—'}</td>
                    <td>{fmtDMY(op.fechaInicio)}</td>
                    <td>{(op.botes || []).length}</td>
                  </tr>
                )) : (
                  <tr><td colSpan={4} style={{ textAlign: 'center', color: 'var(--text3)', padding: 14 }}>Sin operaciones</td></tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card" style={{ minHeight: 440, display: 'flex', flexDirection: 'column' }}>
          <div className="ct">Composición de muestras por especie</div>
          <div style={{
            background: 'var(--bg2)',
            border: '1px solid var(--border)',
            borderRadius: 0,
            padding: 12,
            minHeight: 0,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            flex: 1
          }}>
            <div style={{ minHeight: 320, display: 'grid', gridTemplateColumns: '34px 1fr', gap: 0 }}>
              <div style={{
                height: 280,
                display: 'grid',
                gridTemplateRows: 'repeat(6, 1fr)',
                alignItems: 'end'
              }}>
                {yLabels.map((n) => (
                  <div key={`y-${n}`} style={{ fontSize: 11, color: 'var(--text3)', textAlign: 'right', paddingRight: 4 }}>{n}</div>
                ))}
              </div>

              <div style={{ overflow: 'hidden' }}>
                <div style={{ width: '100%', display: 'flex', flexDirection: 'column' }}>
                  <div style={{
                    height: 280,
                    display: 'flex',
                    alignItems: 'flex-end',
                    gap: 10,
                    padding: '0',
                    borderLeft: '1px solid var(--border)',
                    borderBottom: '1px solid var(--border2)',
                    backgroundImage: 'repeating-linear-gradient(to top, transparent 0, transparent 46px, rgba(148,163,184,.16) 46px, rgba(148,163,184,.16) 47px)'
                  }}>
                    {chartData.items.map((it) => (
                      <div key={it.key} style={{ flex: 1, minWidth: 0, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', alignItems: 'center' }}>
                        <div style={{ fontSize: 11, color: 'var(--text2)', fontWeight: 700, marginBottom: 6 }}>{it.value}</div>
                        <div style={{ width: 34, height: Math.max(12, Math.round((it.value / yMax) * 260)), background: it.color, borderRadius: 0 }} title={`${it.label}: ${it.value}`} />
                      </div>
                    ))}
                    {!chartData.items.length ? <div style={{ color: 'var(--text3)', fontSize: 12, paddingBottom: 6 }}>Sin muestras registradas</div> : null}
                  </div>

                  <div style={{ display: 'flex', gap: 10, padding: '4px 0 0 0' }}>
                    {chartData.items.map((it) => (
                      <div key={`${it.key}-x`} style={{ flex: 1, minWidth: 0, fontSize: 11, color: 'var(--text2)', textAlign: 'center', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {it.label}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
            <div style={{ marginTop: 2, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {chartData.items.map((it) => (
                <span key={`${it.key}-lg`} style={{ fontSize: 11, color: 'var(--text2)' }}><span style={{ color: it.color }}>■</span> {it.label}</span>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
