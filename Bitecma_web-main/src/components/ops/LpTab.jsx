import { useMemo, useRef, useState } from 'react'
import { addSample, ensureKind, removeEspecie, removeKind, removeSample, updateSample } from '../../services/lpMuestrasService.js'
import SpeciesGrid from '../common/SpeciesGrid.jsx'

function typeForSamples(samples) {
  const arr = Array.isArray(samples) ? samples : []
  const hasPeso = arr.some((x) => x && x.p !== undefined && x.p !== null && x.p !== '')
  const hasD = arr.some((x) => x && x.d !== undefined && x.d !== null && x.d !== '')
  if (hasD) return 'D'
  if (hasPeso) return 'LP'
  return 'L'
}

function normKind(kind) {
  const k = String(kind || '').trim().toUpperCase()
  if (k === 'L-P' || k === 'LP') return 'LP'
  if (k === 'D') return 'D'
  return 'L'
}

function normalizeEntry(entry) {
  if (Array.isArray(entry)) {
    const out = {}
    ;(entry || []).forEach((m) => {
      const k = typeForSamples([m])
      if (!out[k]) out[k] = []
      out[k].push(m)
    })
    return out
  }
  if (entry && typeof entry === 'object') {
    if (Array.isArray(entry.ms)) {
      const k = normKind(entry.type || 'LP')
      return { [k]: Array.isArray(entry.ms) ? entry.ms : [] }
    }
    const out = {}
    ;['LP', 'L', 'D'].forEach((k) => {
      if (Array.isArray(entry[k])) out[k] = entry[k]
    })
    return out
  }
  return {}
}

function normKey(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
}

const ALGA_COM = new Set(
  [
    'Huiro negro',
    'Huiro palo',
    'Cochayuyo',
    'Huiro canutillo',
    'Huiro',
    'Luga roja',
    'Luga negra',
    'Luga cuchara',
  ].map(normKey),
)
const ALGA_SCI = new Set(
  [
    'Lessonia berteroana',
    'Lessonia trabeculata',
    'Durvillaea antarctica',
    'Macrocystis integrifolia',
    'Macrocystis pyrifera',
    'Gigartina skottsbergii',
    'Sarcothalia crispata',
    'Mazzaella laminarioides',
  ].map(normKey),
)

function isAlgaSpecies(sp) {
  return ALGA_COM.has(normKey(sp?.com)) || ALGA_SCI.has(normKey(sp?.sci))
}

export default function LpTab({ op, bote, especies, updateOperacion, toast, openModal, closeModal }) {
  const especiesAll = useMemo(() => {
    const arr = Array.isArray(especies) ? especies : []
    return arr.slice().sort((a, b) => String(a?.com || '').localeCompare(String(b?.com || '')))
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

  const openSeleccionarEspecies = () => {
    const Body = () => {
      const initial = spIds
      const [sel, setSel] = useState(() => initial.slice())
      const [kindsBySpId, setKindsBySpId] = useState(() => {
        const out = {}
        initial.forEach((id) => {
          const sp = byId.get(Number(id))
          const isAlga = isAlgaSpecies(sp)
          const entry = normalizeEntry(lpMap?.[id])
          if (isAlga) out[id] = { D: true }
          else {
            const hasLP = Object.prototype.hasOwnProperty.call(entry, 'LP')
            const hasL = Object.prototype.hasOwnProperty.call(entry, 'L')
            out[id] = { LP: hasLP || (!hasLP && !hasL), L: hasL }
          }
        })
        return out
      })

      const prevSet = new Set(initial.map(Number))
      const nextSet = new Set((Array.isArray(sel) ? sel : []).map(Number).filter((x) => Number.isFinite(x)))
      const removed = [...prevSet].filter((x) => !nextSet.has(x))

      const sortedSel = [...nextSet].sort((a, b) => a - b)

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="info-box blue">
            <span>i</span>
            <div>
              Selecciona las especies a muestrear en este bote. Para algas, el ingreso será por <strong>diámetro del disco</strong>.
            </div>
          </div>

          <SpeciesGrid
            especies={especiesAll}
            selectedIds={sel}
            onChange={(ids) => {
              const next = (Array.isArray(ids) ? ids : []).map(Number).filter((x) => Number.isFinite(x))
              setSel(next)
              setKindsBySpId((prev) => {
                const out = {}
                next.forEach((id) => {
                  const prevCfg = prev?.[id]
                  if (prevCfg) {
                    out[id] = prevCfg
                    return
                  }
                  const sp = byId.get(Number(id))
                  const isAlga = isAlgaSpecies(sp)
                  const entry = normalizeEntry(lpMap?.[id])
                  if (isAlga) {
                    out[id] = { D: true }
                  } else {
                    const hasLP = Object.prototype.hasOwnProperty.call(entry, 'LP')
                    const hasL = Object.prototype.hasOwnProperty.call(entry, 'L')
                    out[id] = { LP: hasLP || (!hasLP && !hasL), L: hasL }
                  }
                })
                return out
              })
            }}
            multi
            columns={3}
            maxHeight={320}
          />

          {sortedSel.length ? (
            <div style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 10 }}>
              <div style={{ fontFamily: 'var(--ff-d)', fontSize: 12, fontWeight: 800, color: 'var(--navy)', marginBottom: 8 }}>
                Tipos de muestreo por especie
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {sortedSel.map((spId) => {
                  const sp = byId.get(Number(spId))
                  const isAlga = isAlgaSpecies(sp)
                  const entry = normalizeEntry(lpMap?.[spId])
                  const cntLP = Array.isArray(entry.LP) ? entry.LP.length : 0
                  const cntL = Array.isArray(entry.L) ? entry.L.length : 0
                  const cfg = kindsBySpId?.[spId] || (isAlga ? { D: true } : { LP: true, L: false })
                  return (
                    <div key={spId} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
                      <div style={{ minWidth: 0 }}>
                        <strong>{sp?.com || spId}</strong>
                        <div style={{ fontSize: 11, color: 'var(--text3)' }}>{sp?.sci || ''}</div>
                      </div>
                      {isAlga ? (
                        <div style={{ whiteSpace: 'nowrap', color: 'var(--text2)' }}>D</div>
                      ) : (
                        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                          <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                            <input
                              type="checkbox"
                              checked={!!cfg.LP}
                              disabled={cntLP > 0}
                              onChange={(e) => {
                                const checked = e.target.checked
                                setKindsBySpId((prev) => {
                                  const cur = prev?.[spId] || { LP: true, L: false }
                                  const nextCfg = { ...cur, LP: checked }
                                  if (!nextCfg.LP && !nextCfg.L) nextCfg.LP = true
                                  return { ...(prev || {}), [spId]: nextCfg }
                                })
                              }}
                            />
                            L-P{cntLP ? ` (${cntLP})` : ''}
                          </label>
                          <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                            <input
                              type="checkbox"
                              checked={!!cfg.L}
                              disabled={cntL > 0}
                              onChange={(e) => {
                                const checked = e.target.checked
                                setKindsBySpId((prev) => {
                                  const cur = prev?.[spId] || { LP: true, L: false }
                                  const nextCfg = { ...cur, L: checked }
                                  if (!nextCfg.LP && !nextCfg.L) nextCfg.LP = true
                                  return { ...(prev || {}), [spId]: nextCfg }
                                })
                              }}
                            />
                            L{cntL ? ` (${cntL})` : ''}
                          </label>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          ) : null}

          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cancelar
            </button>
            <button
              className="btn b-teal"
              style={{ flex: 1 }}
              onClick={() => {
                if (removed.length) {
                  const ok = confirm(
                    `Vas a quitar ${removed.length} especie(s) del muestreo. Se eliminarán sus muestras L-P/D asociadas. ¿Continuar?`,
                  )
                  if (!ok) return
                }
                updateOperacion(op.id, (cur) => {
                  const nextBotes = (cur.botes || []).map((x) => {
                    if (x.id !== bote.id) return x
                    let map = x.lpMuestras || {}
                    removed.forEach((id) => {
                      map = removeEspecie(map, id)
                    })
                    ;[...nextSet].forEach((id) => {
                      const sp = byId.get(Number(id))
                      const isAlga = isAlgaSpecies(sp)
                      if (isAlga) {
                        map = ensureKind(map, id, 'D')
                        return
                      }
                      const cfg = kindsBySpId?.[id] || { LP: true, L: false }
                      if (cfg.LP) map = ensureKind(map, id, 'LP')
                      else map = removeKind(map, id, 'LP')
                      if (cfg.L) map = ensureKind(map, id, 'L')
                      else map = removeKind(map, id, 'L')
                    })
                    return { ...x, lpMuestras: map }
                  })
                  return { ...cur, botes: nextBotes }
                })
                closeModal()
                toast?.('Especies actualizadas', 'green')
              }}
            >
              Confirmar
            </button>
          </div>
        </div>
      )
    }
    openModal(`Especies a muestrear (L-P) — Bote ${bote?.nombre || bote?.id}`, <Body />, 'wide')
  }

  const openIngreso = (especieId, forcedType) => {
    const spId = Number(especieId)
    const sp = byId.get(spId)
    const kind0 = normKind(forcedType)

    const Body = () => {
      const entry = normalizeEntry((bote?.lpMuestras || {})[spId])
      const initialSamples = entry?.[kind0] || []
      const [samplesNow, setSamplesNow] = useState(() => (Array.isArray(initialSamples) ? initialSamples : []))
      const kind = kind0

      const [draft, setDraft] = useState(() => (kind === 'LP' ? { l: '', p: '' } : kind === 'D' ? { d: '' } : { l: '' }))
      const [editIdx, setEditIdx] = useState(null)
      const lRef = useRef(null)
      const pRef = useRef(null)
      const dRef = useRef(null)

      const addOrUpdate = () => {
        if (kind === 'LP') {
          const l = draft.l
          const p = draft.p
          updateOperacion(op.id, (cur) => {
            const nextBotes = (cur.botes || []).map((x) => {
              if (x.id !== bote.id) return x
              const map = x.lpMuestras || {}
              const next = editIdx == null ? addSample(map, spId, kind, { l, p }) : updateSample(map, spId, kind, editIdx, { l, p })
              return { ...x, lpMuestras: next }
            })
            return { ...cur, botes: nextBotes }
          })
          setSamplesNow((prev) => {
            const next = prev.slice()
            if (editIdx == null) next.push({ l, p })
            else next[editIdx] = { l, p }
            return next
          })
        } else if (kind === 'D') {
          const d = draft.d
          updateOperacion(op.id, (cur) => {
            const nextBotes = (cur.botes || []).map((x) => {
              if (x.id !== bote.id) return x
              const map = x.lpMuestras || {}
              const next = editIdx == null ? addSample(map, spId, kind, { d }) : updateSample(map, spId, kind, editIdx, { d })
              return { ...x, lpMuestras: next }
            })
            return { ...cur, botes: nextBotes }
          })
          setSamplesNow((prev) => {
            const next = prev.slice()
            if (editIdx == null) next.push({ d })
            else next[editIdx] = { d }
            return next
          })
        } else {
          const l = draft.l
          updateOperacion(op.id, (cur) => {
            const nextBotes = (cur.botes || []).map((x) => {
              if (x.id !== bote.id) return x
              const map = x.lpMuestras || {}
              const next = editIdx == null ? addSample(map, spId, kind, { l }) : updateSample(map, spId, kind, editIdx, { l })
              return { ...x, lpMuestras: next }
            })
            return { ...cur, botes: nextBotes }
          })
          setSamplesNow((prev) => {
            const next = prev.slice()
            if (editIdx == null) next.push({ l })
            else next[editIdx] = { l }
            return next
          })
        }
        setDraft(kind === 'LP' ? { l: '', p: '' } : kind === 'D' ? { d: '' } : { l: '' })
        setEditIdx(null)
        setTimeout(() => {
          if (kind === 'LP') {
            lRef.current?.focus?.()
            lRef.current?.select?.()
          } else if (kind === 'D') {
            dRef.current?.focus?.()
            dRef.current?.select?.()
          } else {
            lRef.current?.focus?.()
            lRef.current?.select?.()
          }
        }, 0)
      }

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
                    ref={lRef}
                    type="number"
                    step="any"
                    value={draft.l}
                    onChange={(e) => setDraft((s) => ({ ...s, l: e.target.value }))}
                    onKeyDown={(e) => {
                      if (e.key !== 'Enter') return
                      e.preventDefault()
                      pRef.current?.focus?.()
                      pRef.current?.select?.()
                    }}
                  />
                </div>
                <div className="ig">
                  <label className="il">Peso (g)</label>
                  <input
                    className="ii lp-num-inp"
                    ref={pRef}
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
                  ref={dRef}
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
                  ref={lRef}
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
                  samplesNow
                    .map((m, idx) => ({ m, idx }))
                    .reverse()
                    .map(({ m, idx }, displayIdx) => (
                    <tr key={idx}>
                      <td>{samplesNow.length - displayIdx}</td>
                      <td>{kind === 'D' ? m?.d ?? '' : m?.l ?? ''}</td>
                      {kind === 'LP' ? <td>{m?.p ?? ''}</td> : null}
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button
                          className="btn b-out b-sm"
                          onClick={() => {
                            setEditIdx(idx)
                            setDraft(kind === 'LP' ? { l: m?.l ?? '', p: m?.p ?? '' } : kind === 'D' ? { d: m?.d ?? '' } : { l: m?.l ?? '' })
                            setTimeout(() => {
                              if (kind === 'LP') {
                                lRef.current?.focus?.()
                                lRef.current?.select?.()
                              } else if (kind === 'D') {
                                dRef.current?.focus?.()
                                dRef.current?.select?.()
                              } else {
                                lRef.current?.focus?.()
                                lRef.current?.select?.()
                              }
                            }, 0)
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
                                const next = removeSample(map, spId, kind, idx)
                                return { ...x, lpMuestras: next }
                              })
                              return { ...cur, botes: nextBotes }
                            })
                            setSamplesNow((prev) => prev.filter((_, i) => i !== idx))
                            if (editIdx === idx) {
                              setEditIdx(null)
                              setDraft(kind === 'LP' ? { l: '', p: '' } : kind === 'D' ? { d: '' } : { l: '' })
                            }
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
        <button className="btn b-teal b-sm" onClick={openSeleccionarEspecies}>
          Seleccionar especies
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
              {spIds.flatMap((spId) => {
                const sp = byId.get(Number(spId))
                const entry = normalizeEntry(lpMap?.[spId])
                const isAlga = isAlgaSpecies(sp)
                const kinds = isAlga ? ['D'] : ['LP', 'L']
                const visibleKinds = kinds.filter((k) => Object.prototype.hasOwnProperty.call(entry, k))
                return visibleKinds.map((kind) => {
                  const samples = entry?.[kind] || []
                  return (
                    <tr key={`${spId}-${kind}`}>
                      <td style={{ textAlign: 'left' }}>
                        <strong>{sp?.com || spId}</strong>
                        <div style={{ fontSize: 11, color: 'var(--text3)' }}>{sp?.sci || ''}</div>
                      </td>
                      <td>{Array.isArray(samples) ? samples.length : 0}</td>
                      <td style={{ minWidth: 120 }}>{kind === 'LP' ? 'L-P' : kind}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button className="btn b-teal b-sm" onClick={() => openIngreso(spId, kind)}>
                          Ingresar
                        </button>
                      </td>
                    </tr>
                  )
                })
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
