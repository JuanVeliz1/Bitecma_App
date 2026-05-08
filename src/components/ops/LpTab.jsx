import { useMemo, useState } from 'react'
import { addSample, ensureEspecie, removeEspecie, removeSample, updateSample } from '../../services/lpMuestrasService.js'

function typeForSamples(samples) {
  const arr = Array.isArray(samples) ? samples : []
  const hasPeso = arr.some((x) => x && x.p !== undefined && x.p !== null && x.p !== '')
  const hasD = arr.some((x) => x && x.d !== undefined && x.d !== null && x.d !== '')
  if (hasD) return 'D'
  if (hasPeso) return 'LP'
  return 'L'
}

export default function LpTab({ op, bote, especies, updateOperacion, toast, openModal, closeModal }) {
  const especiesLp = useMemo(() => {
    const arr = Array.isArray(especies) ? especies : []
    return arr.filter((e) => e?.lp)
  }, [especies])

  const byId = useMemo(() => {
    const m = new Map()
    ;(Array.isArray(especies) ? especies : []).forEach((e) => m.set(Number(e.id), e))
    return m
  }, [especies])

  const lpMap = bote?.lpMuestras && typeof bote.lpMuestras === 'object' ? bote.lpMuestras : {}
  const spIds = Object.keys(lpMap)
    .map(Number)
    .filter((x) => Number.isFinite(x))
    .sort((a, b) => a - b)

  const openAgregarEspecie = () => {
    const Body = () => {
      const [q, setQ] = useState('')
      const [tipo, setTipo] = useState('LP')
      const filtradas = especiesLp
        .filter((e) => {
          const s = String(e?.com || '').toLowerCase()
          const sci = String(e?.sci || '').toLowerCase()
          const qq = String(q || '').toLowerCase().trim()
          return !qq ? true : s.includes(qq) || sci.includes(qq)
        })
        .slice(0, 200)

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="ig">
            <label className="il">Buscar especie</label>
            <input className="ii" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ej: Loco" />
          </div>
          <div className="ig">
            <label className="il">Tipo de registro</label>
            <select className="is" value={tipo} onChange={(e) => setTipo(e.target.value)}>
              <option value="LP">Peso-Longitud</option>
              <option value="L">Longitud</option>
              <option value="D">Diámetro disco</option>
            </select>
          </div>

          <div style={{ maxHeight: 320, overflow: 'auto', border: '1px solid var(--border)', borderRadius: 10 }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th style={{ textAlign: 'left' }}>Especie</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtradas.map((e) => (
                  <tr key={e.id}>
                    <td style={{ textAlign: 'left' }}>
                      <strong>{e.com}</strong>
                      <div style={{ fontSize: 11, color: 'var(--text3)' }}>{e.sci}</div>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button
                        className="btn b-teal b-sm"
                        onClick={() => {
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              const nextLp = ensureEspecie(x.lpMuestras, e.id)
                              return { ...x, lpMuestras: nextLp }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                          closeModal()
                          toast?.('Especie agregada', 'green')
                          setTimeout(() => openIngreso(e.id, tipo), 50)
                        }}
                      >
                        Agregar
                      </button>
                    </td>
                  </tr>
                ))}
                {filtradas.length === 0 ? (
                  <tr>
                    <td colSpan={2} style={{ textAlign: 'center', color: 'var(--text3)' }}>
                      Sin resultados
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
          <button className="btn b-out" onClick={closeModal}>
            Cerrar
          </button>
        </div>
      )
    }
    openModal('Agregar especie L-P', <Body />, 'wide')
  }

  const openIngreso = (especieId, forcedType) => {
    const spId = Number(especieId)
    const sp = byId.get(spId)

    const Body = () => {
      const samples = (bote?.lpMuestras || {})[spId] || []
      const inferred = typeForSamples(samples)
      const kind = forcedType || inferred

      const [draft, setDraft] = useState(() => (kind === 'LP' ? { l: '', p: '' } : kind === 'D' ? { d: '' } : { l: '' }))
      const [editIdx, setEditIdx] = useState(null)

      const addOrUpdate = () => {
        if (kind === 'LP') {
          const l = draft.l
          const p = draft.p
          updateOperacion(op.id, (cur) => {
            const nextBotes = (cur.botes || []).map((x) => {
              if (x.id !== bote.id) return x
              const map = x.lpMuestras || {}
              const next = editIdx == null ? addSample(map, spId, { l, p }) : updateSample(map, spId, editIdx, { l, p })
              return { ...x, lpMuestras: next }
            })
            return { ...cur, botes: nextBotes }
          })
        } else if (kind === 'D') {
          const d = draft.d
          updateOperacion(op.id, (cur) => {
            const nextBotes = (cur.botes || []).map((x) => {
              if (x.id !== bote.id) return x
              const map = x.lpMuestras || {}
              const next = editIdx == null ? addSample(map, spId, { d }) : updateSample(map, spId, editIdx, { d })
              return { ...x, lpMuestras: next }
            })
            return { ...cur, botes: nextBotes }
          })
        } else {
          const l = draft.l
          updateOperacion(op.id, (cur) => {
            const nextBotes = (cur.botes || []).map((x) => {
              if (x.id !== bote.id) return x
              const map = x.lpMuestras || {}
              const next = editIdx == null ? addSample(map, spId, { l }) : updateSample(map, spId, editIdx, { l })
              return { ...x, lpMuestras: next }
            })
            return { ...cur, botes: nextBotes }
          })
        }
        setDraft(kind === 'LP' ? { l: '', p: '' } : kind === 'D' ? { d: '' } : { l: '' })
        setEditIdx(null)
      }

      const samplesNow = (bote?.lpMuestras || {})[spId] || []

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="info-box blue">
            <span>i</span>
            <div>
              <strong>{sp?.com || spId}</strong> · {kind === 'LP' ? 'Peso-Longitud' : kind === 'D' ? 'Diámetro disco' : 'Longitud'}
            </div>
          </div>

          <div className="lp-input-row">
            {kind === 'LP' ? (
              <>
                <div className="ig">
                  <label className="il">Longitud (mm)</label>
                  <input
                    className="ii lp-num-inp"
                    type="number"
                    step="any"
                    value={draft.l}
                    onChange={(e) => setDraft((s) => ({ ...s, l: e.target.value }))}
                    onKeyDown={(e) => {
                      if (e.key !== 'Enter') return
                      e.preventDefault()
                      e.currentTarget?.form?.querySelector('input[name="lp-p"]')?.focus?.()
                    }}
                  />
                </div>
                <div className="ig">
                  <label className="il">Peso (g)</label>
                  <input
                    className="ii lp-num-inp"
                    name="lp-p"
                    type="number"
                    step="any"
                    value={draft.p}
                    onChange={(e) => setDraft((s) => ({ ...s, p: e.target.value }))}
                    onKeyDown={(e) => {
                      if (e.key !== 'Enter') return
                      e.preventDefault()
                      addOrUpdate()
                    }}
                  />
                </div>
              </>
            ) : kind === 'D' ? (
              <div className="ig">
                <label className="il">Diámetro disco (cm)</label>
                <input
                  className="ii lp-num-inp"
                  type="number"
                  step="any"
                  value={draft.d}
                  onChange={(e) => setDraft((s) => ({ ...s, d: e.target.value }))}
                  onKeyDown={(e) => {
                    if (e.key !== 'Enter') return
                    e.preventDefault()
                    addOrUpdate()
                  }}
                />
              </div>
            ) : (
              <div className="ig">
                <label className="il">Longitud (mm)</label>
                <input
                  className="ii lp-num-inp"
                  type="number"
                  step="any"
                  value={draft.l}
                  onChange={(e) => setDraft((s) => ({ ...s, l: e.target.value }))}
                  onKeyDown={(e) => {
                    if (e.key !== 'Enter') return
                    e.preventDefault()
                    addOrUpdate()
                  }}
                />
              </div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end' }}>
              <div className="lp-counter">{samplesNow.length} muestra(s)</div>
              <button className="btn b-teal b-sm" onClick={addOrUpdate}>
                {editIdx == null ? 'Agregar' : 'Guardar'}
              </button>
            </div>
          </div>

          <div style={{ overflow: 'auto', maxHeight: 280, border: '1px solid var(--border)', borderRadius: 10 }}>
            <table className="tbl lp-tbl">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{kind === 'D' ? 'D (cm)' : 'L (mm)'}</th>
                  {kind === 'LP' ? <th>P (g)</th> : null}
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {samplesNow.length ? (
                  samplesNow.map((m, idx) => (
                    <tr key={idx}>
                      <td>{idx + 1}</td>
                      <td>{kind === 'D' ? m?.d ?? '' : m?.l ?? ''}</td>
                      {kind === 'LP' ? <td>{m?.p ?? ''}</td> : null}
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button
                          className="btn b-out b-sm"
                          onClick={() => {
                            setEditIdx(idx)
                            setDraft(kind === 'LP' ? { l: m?.l ?? '', p: m?.p ?? '' } : kind === 'D' ? { d: m?.d ?? '' } : { l: m?.l ?? '' })
                          }}
                        >
                          Editar
                        </button>{' '}
                        <button
                          className="btn b-out b-sm"
                          onClick={() => {
                            updateOperacion(op.id, (cur) => {
                              const nextBotes = (cur.botes || []).map((x) => {
                                if (x.id !== bote.id) return x
                                const map = x.lpMuestras || {}
                                const next = removeSample(map, spId, idx)
                                return { ...x, lpMuestras: next }
                              })
                              return { ...cur, botes: nextBotes }
                            })
                          }}
                        >
                          Eliminar
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={kind === 'LP' ? 4 : 3} style={{ textAlign: 'center', color: 'var(--text3)' }}>
                      Sin muestras
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cerrar
            </button>
            <button
              className="btn b-out"
              style={{ flex: 1, color: 'var(--red)' }}
              onClick={() => {
                if (!confirm(`Quitar especie ${sp?.com || spId}?`)) return
                updateOperacion(op.id, (cur) => {
                  const nextBotes = (cur.botes || []).map((x) => {
                    if (x.id !== bote.id) return x
                    const next = removeEspecie(x.lpMuestras, spId)
                    return { ...x, lpMuestras: next }
                  })
                  return { ...cur, botes: nextBotes }
                })
                closeModal()
                toast?.('Especie removida', 'green')
              }}
            >
              Quitar especie
            </button>
          </div>
        </div>
      )
    }

    openModal(`Ingreso ${sp?.com || spId}`, <Body />, 'wide')
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', marginBottom: 10 }}>
        <div style={{ fontFamily: 'var(--ff-d)', fontSize: 14, fontWeight: 800, color: 'var(--navy)' }}>Peso-Longitud</div>
        <button className="btn b-teal b-sm" onClick={openAgregarEspecie}>
          + Agregar especie
        </button>
      </div>

      {spIds.length === 0 ? (
        <div className="info-box amber">
          <span>i</span>
          <div>Sin especies para muestreo. Agrega una especie para ingresar muestras.</div>
        </div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table className="tbl">
            <thead>
              <tr>
                <th style={{ textAlign: 'left' }}>Especie</th>
                <th>Muestras</th>
                <th>Tipo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {spIds.map((spId) => {
                const sp = byId.get(Number(spId))
                const samples = lpMap?.[spId] || []
                const kind = typeForSamples(samples)
                return (
                  <tr key={spId}>
                    <td style={{ textAlign: 'left' }}>
                      <strong>{sp?.com || spId}</strong>
                      <div style={{ fontSize: 11, color: 'var(--text3)' }}>{sp?.sci || ''}</div>
                    </td>
                    <td>{Array.isArray(samples) ? samples.length : 0}</td>
                    <td>{kind === 'LP' ? 'L-P' : kind === 'D' ? 'D' : 'L'}</td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button className="btn b-teal b-sm" onClick={() => openIngreso(spId)}>
                        Ingresar
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

