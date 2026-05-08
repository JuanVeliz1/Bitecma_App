import { useMemo, useState } from 'react'

export default function SpeciesGrid({ especies, selectedIds, onChange, multi = true, columns = 3, maxHeight = 380 }) {
  const [q, setQ] = useState('')

  const list = useMemo(() => {
    const arr = Array.isArray(especies) ? especies : []
    return arr.slice().sort((a, b) => String(a?.com || '').localeCompare(String(b?.com || '')))
  }, [especies])

  const filtered = useMemo(() => {
    const norm = (v) =>
      String(v || '')
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .trim()
    const qq = norm(q)
    if (!qq) return list
    return list.filter((e) => norm(e?.com).includes(qq) || norm(e?.sci).includes(qq))
  }, [list, q])

  const selectedSet = useMemo(() => {
    const arr = Array.isArray(selectedIds) ? selectedIds : []
    return new Set(arr.map(Number).filter((x) => Number.isFinite(x)))
  }, [selectedIds])

  const toggle = (id) => {
    const n = Number(id)
    if (!Number.isFinite(n)) return
    if (!multi) {
      onChange?.([n])
      return
    }
    const next = new Set(selectedSet)
    if (next.has(n)) next.delete(n)
    else next.add(n)
    onChange?.([...next])
  }

  return (
    <div style={{ overflow: 'auto', maxHeight, border: '1px solid var(--border)', borderRadius: 10, padding: 10 }}>
      <input
        className="ii"
        placeholder="Buscar especie..."
        value={q}
        onChange={(e) => setQ(e.target.value)}
        style={{ marginBottom: 10 }}
      />
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${columns}, 1fr)`, gap: 10 }}>
        {filtered.map((e) => {
          const id = Number(e?.id)
          const active = selectedSet.has(id)
          return (
            <div
              key={e.id}
              role="button"
              tabIndex={0}
              onClick={() => toggle(e.id)}
              onKeyDown={(ev) => {
                if (ev.key !== 'Enter' && ev.key !== ' ') return
                ev.preventDefault()
                toggle(e.id)
              }}
              style={{
                border: `1px solid ${active ? 'rgba(10,143,126,.45)' : 'var(--border)'}`,
                background: active ? 'var(--teal-lt)' : 'var(--bg)',
                borderRadius: 10,
                padding: '10px 12px',
                minHeight: 62,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
              }}
              title={`${e?.com || ''}${e?.sci ? ` (${e.sci})` : ''}`}
            >
              <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
                <div style={{ fontWeight: 800, color: active ? 'var(--teal)' : 'var(--text)', lineHeight: 1.1 }}>
                  {e?.com || '—'}
                </div>
                {active ? (
                  <div style={{ fontFamily: 'var(--ff-m)', fontSize: 11, color: 'var(--teal)', whiteSpace: 'nowrap' }}>✓</div>
                ) : null}
              </div>
              <div style={{ fontSize: 11, color: 'var(--text3)', fontStyle: 'italic', marginTop: 3, lineHeight: 1.1 }}>
                {e?.sci || '—'}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
