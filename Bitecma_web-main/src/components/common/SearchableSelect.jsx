import { useEffect, useMemo, useRef, useState } from 'react'

export default function SearchableSelect({
  label,
  placeholder,
  value,
  options,
  onChange,
  onAdd,
  addLabel,
  disabled,
}) {
  const [open, setOpen] = useState(false)
  const [q, setQ] = useState('')
  const wrapRef = useRef(null)

  const opts = Array.isArray(options) ? options : []
  const selected = useMemo(() => opts.find((o) => String(o.value) === String(value)) || null, [opts, value])

  const filtered = useMemo(() => {
    const query = String(q || '').toLowerCase().trim()
    if (!query) return opts.slice(0, 400)
    return opts
      .filter((o) => String(o.label || '').toLowerCase().includes(query))
      .slice(0, 400)
  }, [opts, q])

  useEffect(() => {
    const onDoc = (e) => {
      if (!wrapRef.current) return
      if (wrapRef.current.contains(e.target)) return
      setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  return (
    <div className="ig" ref={wrapRef} style={{ position: 'relative' }}>
      {label ? <label className="il">{label}</label> : null}
      <div style={{ display: 'flex', gap: 8 }}>
        <input
          className="ii"
          disabled={!!disabled}
          value={open ? q : selected?.label || ''}
          placeholder={placeholder || 'Selecciona...'}
          onFocus={() => {
            if (disabled) return
            setOpen(true)
            setQ('')
          }}
          onChange={(e) => {
            setOpen(true)
            setQ(e.target.value)
          }}
        />
        <button
          type="button"
          className="btn b-out b-sm"
          disabled={!!disabled}
          onClick={() => {
            if (disabled) return
            setOpen((v) => !v)
            setQ('')
          }}
        >
          ▾
        </button>
      </div>

      {open ? (
        <div
          style={{
            position: 'absolute',
            zIndex: 50,
            top: label ? 54 : 40,
            left: 0,
            right: 0,
            background: 'var(--white)',
            border: '1.5px solid var(--border)',
            borderRadius: 10,
            boxShadow: 'var(--shadow)',
            maxHeight: 280,
            overflow: 'auto',
          }}
        >
          {onAdd ? (
            <div
              role="button"
              tabIndex={0}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                setOpen(false)
                setQ('')
                onAdd()
              }}
              style={{
                padding: '10px 12px',
                cursor: 'pointer',
                borderBottom: '1px solid var(--border)',
                color: 'var(--teal)',
                fontWeight: 800,
              }}
            >
              {addLabel || 'Agregar...'}
            </div>
          ) : null}

          {filtered.length ? (
            filtered.map((o) => (
              <div
                key={String(o.value)}
                role="button"
                tabIndex={0}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  onChange?.(o.value, o)
                  setOpen(false)
                  setQ('')
                }}
                style={{
                  padding: '9px 12px',
                  cursor: 'pointer',
                  background: String(o.value) === String(value) ? 'var(--teal-lt)' : 'transparent',
                }}
              >
                {o.label}
              </div>
            ))
          ) : (
            <div style={{ padding: '10px 12px', color: 'var(--text3)' }}>Sin resultados</div>
          )}
        </div>
      ) : null}
    </div>
  )
}

