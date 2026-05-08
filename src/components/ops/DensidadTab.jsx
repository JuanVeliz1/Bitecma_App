import { useMemo, useRef, useState } from 'react'
import {
  addEspecieToUnidad,
  calcDensidad,
  crearUnidades,
  eliminarUnidad,
  nextUnidadNum,
  removeEspecieFromUnidad,
  setCuadranteEspecie,
  setUnidadCoord,
  setUnidadCount,
  updateUnidad,
} from '../../services/densidadService.js'

function focusNextInput(from, root) {
  const container = root || document
  const inputs = Array.from(container.querySelectorAll('input[data-nav="dens"]'))
  const idx = inputs.indexOf(from)
  if (idx < 0) return
  const next = inputs[idx + 1]
  if (!next) return
  next.focus()
  next.select?.()
}

export default function DensidadTab({ op, bote, especies, updateOperacion, toast, openModal, closeModal }) {
  const rootRef = useRef(null)
  const [openUnits, setOpenUnits] = useState(() => new Set())

  const unidades = useMemo(() => {
    const arr = Array.isArray(bote?.transectos) ? bote.transectos : []
    return [...arr].sort((a, b) => (Number(a?.num) || 0) - (Number(b?.num) || 0))
  }, [bote?.transectos])

  const especiesDens = useMemo(() => {
    const arr = Array.isArray(especies) ? especies : []
    return arr.filter((e) => e?.dens)
  }, [especies])

  const byId = useMemo(() => {
    const m = new Map()
    especiesDens.forEach((e) => m.set(Number(e.id), e))
    return m
  }, [especiesDens])

  const toggleUnit = (num) => {
    setOpenUnits((prev) => {
      const next = new Set(prev)
      if (next.has(num)) next.delete(num)
      else next.add(num)
      return next
    })
  }

  const openCrearTransectos = () => {
    const Body = () => {
      const startNum = nextUnidadNum(bote?.transectos)
      const [rows, setRows] = useState(() =>
        Array.from({ length: 6 }, (_, i) => ({
          num: startNum + i,
          area: 120,
          sustrato: '',
          cubierta: '',
          especiesIds: [],
        })),
      )

      const replicate = () => {
        setRows((prev) => {
          const first = prev[0]
          if (!first) return prev
          return prev.map((r, idx) =>
            idx === 0 ? r : { ...r, area: first.area, sustrato: first.sustrato, cubierta: first.cubierta, especiesIds: first.especiesIds },
          )
        })
      }

      const canSave = rows.some((r) => Array.isArray(r.especiesIds) && r.especiesIds.length)

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="info-box blue">
            <span>i</span>
            <div>Completa el primer transecto y usa “Replicar” para copiar la configuración al resto.</div>
          </div>

          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button className="btn b-out b-sm" onClick={replicate}>
              Replicar fila 1
            </button>
            <button
              className="btn b-out b-sm"
              onClick={() =>
                setRows((prev) => [
                  ...prev,
                  { num: (prev[prev.length - 1]?.num || startNum - 1) + 1, area: prev[0]?.area ?? 120, sustrato: '', cubierta: '', especiesIds: [] },
                ])
              }
            >
              Agregar transecto
            </button>
          </div>

          <div style={{ overflow: 'auto', border: '1px solid var(--border)', borderRadius: 10, maxHeight: '55vh' }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>#</th>
                  <th>N°</th>
                  <th>Área (m²)</th>
                  <th>Tipo de sustrato</th>
                  <th>Cubierta biológica</th>
                  <th>Especies</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, idx) => (
                  <tr key={r.num}>
                    <td>{idx + 1}</td>
                    <td>
                      <strong>T{r.num}</strong>
                    </td>
                    <td style={{ minWidth: 140 }}>
                      <input
                        className="ii"
                        type="number"
                        step="any"
                        value={r.area}
                        onChange={(e) => {
                          const v = e.target.value
                          setRows((prev) => prev.map((x) => (x.num === r.num ? { ...x, area: v } : x)))
                        }}
                      />
                    </td>
                    <td style={{ minWidth: 180 }}>
                      <input
                        className="ii"
                        value={r.sustrato}
                        onChange={(e) => setRows((prev) => prev.map((x) => (x.num === r.num ? { ...x, sustrato: e.target.value } : x)))}
                      />
                    </td>
                    <td style={{ minWidth: 180 }}>
                      <input
                        className="ii"
                        value={r.cubierta}
                        onChange={(e) => setRows((prev) => prev.map((x) => (x.num === r.num ? { ...x, cubierta: e.target.value } : x)))}
                      />
                    </td>
                    <td style={{ minWidth: 240 }}>
                      <select
                        className="is"
                        multiple
                        value={(r.especiesIds || []).map(String)}
                        onChange={(e) => {
                          const selected = Array.from(e.target.selectedOptions).map((o) => Number(o.value)).filter((x) => Number.isFinite(x))
                          setRows((prev) => prev.map((x) => (x.num === r.num ? { ...x, especiesIds: selected } : x)))
                        }}
                      >
                        {especiesDens
                          .slice()
                          .sort((a, b) => String(a.com || '').localeCompare(String(b.com || '')))
                          .map((sp) => (
                            <option key={sp.id} value={sp.id}>
                              {sp.com}
                            </option>
                          ))}
                      </select>
                    </td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button className="btn b-out b-sm" onClick={() => setRows((prev) => prev.filter((x) => x.num !== r.num))}>
                        Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cancelar
            </button>
            <button
              className="btn b-teal"
              style={{ flex: 1 }}
              disabled={!canSave}
              onClick={() => {
                if (!canSave) return
                updateOperacion(op.id, (cur) => {
                  const nextBotes = (cur.botes || []).map((x) => {
                    if (x.id !== bote.id) return x
                    const sorted = rows
                      .slice()
                      .sort((a, b) => (Number(a.num) || 0) - (Number(b.num) || 0))
                    let u = Array.isArray(x.transectos) ? x.transectos : []
                    sorted.forEach((row) => {
                      u = crearUnidades({
                        unidades: u,
                        tipo: 'transecto',
                        cantidad: 1,
                        area: row.area,
                        fecha: String(op?.fechaInicio || ''),
                        sustrato: row.sustrato,
                        cubierta: row.cubierta,
                        especiesIds: row.especiesIds,
                      })
                    })
                    return { ...x, transectos: u }
                  })
                  return { ...cur, botes: nextBotes }
                })
                closeModal()
                toast?.('Transectos creados', 'green')
              }}
            >
              Crear
            </button>
          </div>
        </div>
      )
    }

    openModal(`Agregar transectos — ${bote?.nombre || bote?.id}`, <Body />, 'wide')
  }

  const openCrearCuadrantes = () => {
    const Body = () => {
      const [form, setForm] = useState(() => ({
        cantidad: 30,
        area: 0.25,
        sustrato: '',
        especieId: '',
      }))

      const canSave = String(form.especieId || '').trim() !== '' && Number(form.cantidad) > 0

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="i2">
            <div className="ig">
              <label className="il">Cantidad</label>
              <input className="ii" type="number" value={form.cantidad} onChange={(e) => setForm((s) => ({ ...s, cantidad: parseInt(e.target.value, 10) || 0 }))} />
            </div>
            <div className="ig">
              <label className="il">Área cuadrante</label>
              <select className="is" value={String(form.area)} onChange={(e) => setForm((s) => ({ ...s, area: Number(e.target.value) }))}>
                <option value="1">1 m²</option>
                <option value="0.25">0.25 m²</option>
                <option value="0.0625">0.0625 m²</option>
              </select>
            </div>
          </div>
          <div className="i2">
            <div className="ig">
              <label className="il">Tipo sustrato</label>
              <input className="ii" value={form.sustrato} onChange={(e) => setForm((s) => ({ ...s, sustrato: e.target.value }))} />
            </div>
            <div className="ig">
              <label className="il">Especie</label>
              <select className="is" value={form.especieId} onChange={(e) => setForm((s) => ({ ...s, especieId: e.target.value }))}>
                <option value="">Seleccionar especie...</option>
                {especiesDens
                  .slice()
                  .sort((a, b) => String(a.com || '').localeCompare(String(b.com || '')))
                  .map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.com}
                    </option>
                  ))}
              </select>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cancelar
            </button>
            <button
              className="btn b-teal"
              style={{ flex: 1 }}
              disabled={!canSave}
              onClick={() => {
                if (!canSave) return
                updateOperacion(op.id, (cur) => {
                  const nextBotes = (cur.botes || []).map((x) => {
                    if (x.id !== bote.id) return x
                    const nextUnits = crearUnidades({
                      unidades: x.transectos,
                      tipo: 'cuadrante',
                      cantidad: form.cantidad,
                      area: form.area,
                      fecha: String(op?.fechaInicio || ''),
                      sustrato: form.sustrato,
                      cubierta: '',
                      especieId: form.especieId,
                    })
                    return { ...x, transectos: nextUnits }
                  })
                  return { ...cur, botes: nextBotes }
                })
                closeModal()
                toast?.('Cuadrantes creados', 'green')
              }}
            >
              Crear
            </button>
          </div>
        </div>
      )
    }

    openModal(`Agregar cuadrantes — ${bote?.nombre || bote?.id}`, <Body />, 'wide')
  }

  const openCrearUnidades = () => {
    if (bote?.densTipo === 'cuadrante') openCrearCuadrantes()
    else openCrearTransectos()
  }

  return (
    <div ref={rootRef}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', marginBottom: 10 }}>
        <div style={{ fontFamily: 'var(--ff-d)', fontSize: 14, fontWeight: 800, color: 'var(--navy)' }}>
          {bote?.densTipo === 'cuadrante' ? 'Cuadrantes' : 'Transectos'}
        </div>
        <button className="btn b-teal b-sm" onClick={openCrearUnidades}>
          + Crear
        </button>
      </div>

      {unidades.length === 0 ? (
        <div className="info-box amber">
          <span>i</span>
          <div>Sin unidades de densidad. Crea transectos/cuadrantes para comenzar.</div>
        </div>
      ) : (
        unidades.map((t) => {
          const num = Number(t?.num) || 0
          const isCuad = t?.tipo === 'cuadrante'
          const counts = t?.counts && typeof t.counts === 'object' ? t.counts : {}
          const spIds = Object.keys(counts)
            .map(Number)
            .filter((x) => Number.isFinite(x))
            .sort((a, b) => a - b)
          const usedSet = new Set(spIds)
          const addable = especiesDens.filter((e) => !usedSet.has(Number(e.id)))
          const area = Number(t.area) || 0
          const coordX = t?.coordX ?? ''
          const coordY = t?.coordY ?? ''
          const coordLong = t?.coordLong ?? ''
          const coordLat = t?.coordLat ?? ''
          const speciesChips = spIds
            .map((id) => byId.get(Number(id))?.com)
            .filter(Boolean)
            .slice(0, 12)

          if (isCuad) {
            const spId = Number(t.especieId ?? spIds[0] ?? null)
            const sp = byId.get(spId)
            const cnt = Number(counts?.[spId] ?? 0)
            const dens = calcDensidad(cnt, area)
            const open = openUnits.has(num)
            const summary = `Área ${area || '—'} m² · Sustrato ${t?.sustrato ? t.sustrato : '—'}`

            return (
              <div key={`${bote.id}-${num}`} className="tx-card cuad">
                <div
                  className="tx-hd"
                  onClick={() => toggleUnit(num)}
                  style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}
                >
                  <span className="pill p-pur" style={{ fontSize: 10 }}>
                    C{num}
                  </span>
                  <span style={{ fontWeight: 800, color: 'var(--navy)' }}>Cuadrante</span>
                  <span style={{ fontWeight: 800, color: 'var(--navy)' }}>{sp?.com || '—'}</span>

                  <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ fontSize: 12, color: 'var(--text3)' }}>Cantidad:</span>
                      <input
                        className="ii lp-num-inp"
                        style={{ width: 96, textAlign: 'center' }}
                        type="number"
                        step="1"
                        min="0"
                        data-nav="dens"
                        value={String(cnt)}
                        onClick={(e) => e.stopPropagation()}
                        onKeyDown={(e) => {
                          if (e.key !== 'Enter') return
                          e.preventDefault()
                          focusNextInput(e.currentTarget, rootRef.current)
                        }}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: setUnidadCount(x.transectos, num, spId, v) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                      <span style={{ fontFamily: 'var(--ff-m)', fontSize: 12, fontWeight: 800, color: 'var(--teal)' }}>
                        {dens.toFixed(4)}
                      </span>
                      <span style={{ fontSize: 11, color: 'var(--text3)' }}>ind/m²</span>
                    </div>

                    <div style={{ width: 1, height: 26, background: 'var(--border2)' }} />

                    <div style={{ fontSize: 11, color: 'var(--text3)', whiteSpace: 'nowrap' }}>{summary}</div>

                    <button
                      className="btn b-out b-sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        if (!confirm(`Eliminar C-${num}?`)) return
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: eliminarUnidad(x.transectos, num) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                        toast?.('Cuadrante eliminado', 'green')
                      }}
                    >
                      Eliminar
                    </button>

                    <button className="btn b-out b-sm" onClick={(e) => { e.stopPropagation(); toggleUnit(num) }}>
                      {open ? '▴' : '▾'}
                    </button>
                  </div>
                </div>

                <div className={`tx-body${open ? ' open' : ''}`}>
                  <div className="i2">
                    <div className="ig">
                      <label className="il">Área (m²)</label>
                      <input
                        className="ii"
                        type="number"
                        step="any"
                        value={t.area ?? ''}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: updateUnidad(x.transectos, num, { area: v }) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                    </div>
                    <div className="ig">
                      <label className="il">Sustrato</label>
                      <input
                        className="ii"
                        value={t?.sustrato || ''}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: updateUnidad(x.transectos, num, { sustrato: v }) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                    </div>
                  </div>

                  <div className="i2">
                    <div className="ig">
                      <label className="il">X</label>
                      <input
                        className="ii"
                        type="number"
                        step="any"
                        inputMode="decimal"
                        value={coordX}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: setUnidadCoord(x.transectos, num, 'x', v) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                    </div>
                    <div className="ig">
                      <label className="il">Y</label>
                      <input
                        className="ii"
                        type="number"
                        step="any"
                        inputMode="decimal"
                        value={coordY}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: setUnidadCoord(x.transectos, num, 'y', v) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                    </div>
                  </div>

                  <div className="i2">
                    <div className="ig">
                      <label className="il">LONG</label>
                      <input
                        className="ii"
                        type="number"
                        step="any"
                        inputMode="decimal"
                        value={coordLong}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: setUnidadCoord(x.transectos, num, 'lon', v) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                    </div>
                    <div className="ig">
                      <label className="il">LAT</label>
                      <input
                        className="ii"
                        type="number"
                        step="any"
                        inputMode="decimal"
                        value={coordLat}
                        onChange={(e) => {
                          const v = e.target.value
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: setUnidadCoord(x.transectos, num, 'lat', v) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                        }}
                      />
                    </div>
                  </div>
                </div>
              </div>
            )
          }

          const open = openUnits.has(num)

          return (
            <div key={`${bote.id}-${num}`} className="tx-card">
              <div className="tx-hd" onClick={() => toggleUnit(num)}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <div style={{ fontWeight: 800, color: 'var(--navy)' }}>{`T-${num}`}</div>
                  {speciesChips.length ? (
                    speciesChips.map((name) => (
                      <span key={name} className="pill p-teal" style={{ fontSize: 10 }}>
                        {name}
                      </span>
                    ))
                  ) : (
                    <span style={{ fontSize: 11, color: 'var(--text3)' }}>Sin especies</span>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  <span style={{ fontSize: 11, color: 'var(--text3)' }}>
                    Área {area || '—'} · {t?.sustrato ? `Sustrato ${t.sustrato}` : 'Sustrato —'} ·{' '}
                    {t?.cubierta ? `Cubierta ${t.cubierta}` : 'Cubierta —'}
                  </span>
                  <button
                    className="btn b-out b-sm"
                    onClick={(e) => {
                      e.stopPropagation()
                      if (!confirm(`Eliminar T-${num}?`)) return
                      updateOperacion(op.id, (cur) => {
                        const nextBotes = (cur.botes || []).map((x) => {
                          if (x.id !== bote.id) return x
                          return { ...x, transectos: eliminarUnidad(x.transectos, num) }
                        })
                        return { ...cur, botes: nextBotes }
                      })
                      toast?.('Transecto eliminado', 'green')
                    }}
                  >
                    Eliminar
                  </button>
                </div>
              </div>

              <div className={`tx-body${open ? ' open' : ''}`}>
                <div className="i2">
                  <div className="ig">
                    <label className="il">Área</label>
                    <input
                      className="ii"
                      type="number"
                      step="any"
                      value={t.area ?? ''}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: updateUnidad(x.transectos, num, { area: v }) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                  <div className="ig">
                    <label className="il">Fecha</label>
                    <input
                      className="ii"
                      type="date"
                      value={String(t.fecha || '')}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: updateUnidad(x.transectos, num, { fecha: v }) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                </div>

                <div className="i2">
                  <div className="ig">
                    <label className="il">Sustrato</label>
                    <input
                      className="ii"
                      value={t.sustrato || ''}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: updateUnidad(x.transectos, num, { sustrato: v }) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                  <div className="ig">
                    <label className="il">Cubierta biológica</label>
                    <input
                      className="ii"
                      value={t.cubierta || ''}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: updateUnidad(x.transectos, num, { cubierta: v }) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                </div>

                <div className="i2">
                  <div className="ig">
                    <label className="il">X</label>
                    <input
                      className="ii"
                      type="number"
                      step="any"
                      inputMode="decimal"
                      value={coordX}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: setUnidadCoord(x.transectos, num, 'x', v) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                  <div className="ig">
                    <label className="il">Y</label>
                    <input
                      className="ii"
                      type="number"
                      step="any"
                      inputMode="decimal"
                      value={coordY}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: setUnidadCoord(x.transectos, num, 'y', v) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                </div>

                <div className="i2">
                  <div className="ig">
                    <label className="il">LONG</label>
                    <input
                      className="ii"
                      type="number"
                      step="any"
                      inputMode="decimal"
                      value={coordLong}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: setUnidadCoord(x.transectos, num, 'lon', v) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                  <div className="ig">
                    <label className="il">LAT</label>
                    <input
                      className="ii"
                      type="number"
                      step="any"
                      inputMode="decimal"
                      value={coordLat}
                      onChange={(e) => {
                        const v = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: setUnidadCoord(x.transectos, num, 'lat', v) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    />
                  </div>
                </div>

                {isCuad ? (
                  <div className="ig">
                    <label className="il">Especie (cuadrante)</label>
                    <select
                      className="is"
                      value={String(t.especieId ?? spIds[0] ?? '')}
                      onChange={(e) => {
                        const nextId = e.target.value
                        updateOperacion(op.id, (cur) => {
                          const nextBotes = (cur.botes || []).map((x) => {
                            if (x.id !== bote.id) return x
                            return { ...x, transectos: setCuadranteEspecie(x.transectos, num, nextId) }
                          })
                          return { ...cur, botes: nextBotes }
                        })
                      }}
                    >
                      <option value="">Selecciona...</option>
                      {especiesDens.map((e) => (
                        <option key={e.id} value={e.id}>
                          {e.com}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}

                <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap', marginTop: 6 }}>
                  {!isCuad ? (
                    <div className="ig" style={{ marginBottom: 0, minWidth: 240 }}>
                      <label className="il">Agregar especie</label>
                      <select
                        className="is"
                        value=""
                        onChange={(e) => {
                          const spId = e.target.value
                          if (!spId) return
                          updateOperacion(op.id, (cur) => {
                            const nextBotes = (cur.botes || []).map((x) => {
                              if (x.id !== bote.id) return x
                              return { ...x, transectos: addEspecieToUnidad(x.transectos, num, spId) }
                            })
                            return { ...cur, botes: nextBotes }
                          })
                          e.target.value = ''
                        }}
                      >
                        <option value="">Selecciona...</option>
                        {addable.map((e) => (
                          <option key={e.id} value={e.id}>
                            {e.com}
                          </option>
                        ))}
                      </select>
                    </div>
                  ) : null}
                </div>

                <div style={{ overflowX: 'auto', marginTop: 10 }}>
                  <table className="tbl lp-tbl">
                    <thead>
                      <tr>
                        <th>Especie</th>
                        <th>N° IND</th>
                        <th>Dens</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {spIds.length ? (
                        spIds.map((spId) => {
                          const sp = byId.get(Number(spId))
                          const cnt = Number(counts?.[spId] ?? 0)
                          const dens = calcDensidad(cnt, area)
                          return (
                            <tr key={`${num}-${spId}`}>
                              <td style={{ textAlign: 'left' }}>{sp?.com || spId}</td>
                              <td>
                                <input
                                  className="ii lp-num-inp"
                                  style={{ width: 90, textAlign: 'center' }}
                                  type="number"
                                  step="1"
                                  min="0"
                                  data-nav="dens"
                                  value={String(cnt)}
                                  onKeyDown={(e) => {
                                    if (e.key !== 'Enter') return
                                    e.preventDefault()
                                    focusNextInput(e.currentTarget, rootRef.current)
                                  }}
                                  onChange={(e) => {
                                    const v = e.target.value
                                    updateOperacion(op.id, (cur) => {
                                      const nextBotes = (cur.botes || []).map((x) => {
                                        if (x.id !== bote.id) return x
                                        return { ...x, transectos: setUnidadCount(x.transectos, num, spId, v) }
                                      })
                                      return { ...cur, botes: nextBotes }
                                    })
                                  }}
                                />
                              </td>
                              <td>{dens.toFixed(4)}</td>
                              <td style={{ textAlign: 'right' }}>
                                <button
                                  className="btn b-out b-sm"
                                  onClick={() => {
                                    updateOperacion(op.id, (cur) => {
                                      const nextBotes = (cur.botes || []).map((x) => {
                                        if (x.id !== bote.id) return x
                                        return { ...x, transectos: removeEspecieFromUnidad(x.transectos, num, spId) }
                                      })
                                      return { ...cur, botes: nextBotes }
                                    })
                                  }}
                                >
                                  Quitar
                                </button>
                              </td>
                            </tr>
                          )
                        })
                      ) : (
                        <tr>
                          <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text3)' }}>
                            Agrega especies para contar
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )
        })
      )}
    </div>
  )
}
