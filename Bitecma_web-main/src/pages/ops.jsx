import { useEffect, useMemo, useState } from 'react'
import { useOperaciones } from '../hooks/useOperaciones.js'
import { useDb } from '../context/dbContext.jsx'
import { useUi } from '../context/uiContext.jsx'
import { useApp } from '../context/appContext.jsx'
import { getOperacionMetricas } from '../services/operacionesService.js'
import { BOTES } from '../data/botes'
import SvgIcon from '../components/svgIcon.jsx'
import BoteCard from '../components/ops/BoteCard.jsx'
import EvadirPreview from '../components/evadir/EvadirPreview.jsx'
import SearchableSelect from '../components/common/SearchableSelect.jsx'
import EvadirImporter from '../components/ops/EvadirImporter.jsx'


function nextOpId(ops, year) {
  const y = String(year)
  const nums = ops
    .map((o) => String(o?.id || ''))
    .map((id) => {
      const m = id.match(/^OP-(\d{4})-(\d{3})$/)
      if (!m) return null
      if (m[1] !== y) return null
      return parseInt(m[2], 10)
    })
    .filter((n) => Number.isFinite(n))
  const max = nums.length ? Math.max(...nums) : 0
  return `OP-${y}-${String(max + 1).padStart(3, '0')}`
}

function todayISO() {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function getOperacionYear(op) {
  const fi = String(op?.fechaInicio || '').trim()
  if (/^\d{4}-\d{2}-\d{2}$/.test(fi)) return fi.slice(0, 4)
  if (/^\d{4}/.test(fi)) return fi.slice(0, 4)
  const id = String(op?.id || '')
  const m = id.match(/^OP-(\d{4})-/)
  return m ? m[1] : ''
}

function getOperacionSegLabel(op) {
  const n = Number(op?.numSeg)
  if (!Number.isFinite(n)) return 'SEG—'
  return `SEG${String(Math.trunc(n)).padStart(2, '0')}`
}

function fmtDMY(iso) {
  const s = String(iso || '').trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return s || '—'
  return `${s.slice(8, 10)}/${s.slice(5, 7)}/${s.slice(0, 4)}`
}

function toRoman(n) {
  const num = Math.trunc(Number(n) || 0)
  if (num <= 0) return ''
  const map = [
    [1000, 'M'],
    [900, 'CM'],
    [500, 'D'],
    [400, 'CD'],
    [100, 'C'],
    [90, 'XC'],
    [50, 'L'],
    [40, 'XL'],
    [10, 'X'],
    [9, 'IX'],  
    [5, 'V'],
    [4, 'IV'],
    [1, 'I'],
  ]
  let x = num
  let out = ''
  map.forEach(([v, s]) => {
    while (x >= v) {
      out += s
      x -= v
    }
  })
  return out
}

 export default function OpsPage({ active }) {
  const { db, upsertOperacion, updateOperacion, deleteOperacion, upsertBoteMaestro } = useDb()
  const { toast, openModal, closeModal } = useUi()
  const { canWrite, isViewer, navigate } = useApp()
  const { filtered, meses, sector, setSector, mes, setMes, texto, setTexto, operaciones } =
    useOperaciones()

  const [expanded, setExpanded] = useState('')
  const [regionSel, setRegionSel] = useState('')

  useEffect(() => {
    if (!active) return
    if (!isViewer) return
    toast('Acceso restringido: Visualizador no puede entrar a Operaciones', 'red')
    navigate('dashboard')
  }, [active, isViewer, navigate, toast])

  const toggleExpanded = (opId) => {
    setExpanded((prev) => {
      const id = String(opId ?? '')
      if (!id) return ''
      return String(prev || '') === id ? '' : id
    })
  }

  const regiones = useMemo(() => db?.regionesChile || [], [db?.regionesChile])
  const sectorAmerb = db?.sectoresAmerb || []
  const caletasByRegion = db?.caletasByRegionStatic || {}
  const opa = useMemo(() => db?.opa || [], [db?.opa])
  const regionMetaById = useMemo(() => new Map(regiones.map((r) => [String(r?.id), r])), [regiones])
  const regionNameById = useMemo(
    () => new Map(regiones.map((r) => [String(r?.id), String(r?.nom || r?.rom || r?.id || '')])),
    [regiones],
  )
  const opaShortById = useMemo(() => new Map(opa.map((o) => [String(o?.id), String(o?.nombrecorto || o?.nombre || '')])), [opa])

  const regionButtons = useMemo(() => {
    const ops = Array.isArray(operaciones) ? operaciones : []
    const ids = ops
      .map((o) => (o?.region == null ? '' : String(o.region)))
      .filter((x) => x)
    const uniq = [...new Set(ids)]
    uniq.sort((a, b) => (Number(a) || 0) - (Number(b) || 0))
    return uniq.map((id) => {
      const r = regionMetaById.get(String(id))
      const nom = String(r?.nom || '')
      const rom = String(r?.rom || '')
      const det = rom || toRoman(id)
      const label = nom ? `Región de ${nom}` : `Región ${id}`
      return { id: String(id), label, det }
    })
  }, [operaciones, regionMetaById])

  const filteredByRegion = useMemo(() => {
    const rid = String(regionSel || '')
    const arr = Array.isArray(filtered) ? filtered : []
    if (!rid) return []
    return arr.filter((o) => String(o?.region ?? '') === rid)
  }, [filtered, regionSel])

  const sectoresInRegion = useMemo(() => {
    const rid = String(regionSel || '')
    if (!rid) return []
    const set = new Set()
    ;(Array.isArray(operaciones) ? operaciones : []).forEach((o) => {
      if (String(o?.region ?? '') !== rid) return
      const s = String(o?.sector || '').trim()
      if (s) set.add(s)
    })
    return Array.from(set).sort((a, b) => a.localeCompare(b))
  }, [operaciones, regionSel])

  useEffect(() => {
    if (!regionSel) return
    if (!sector) return
    if (sectoresInRegion.includes(sector)) return
    setSector('')
  }, [regionSel, sector, sectoresInRegion, setSector])

  const safeUpdateOperacion = (opId, updater) => {
    if (!canWrite) {
      toast('Modo solo lectura', 'blue')
      return
    }
    updateOperacion(opId, updater)
  }

  const safeUpsertOperacion = (opData) => {
    if (!canWrite) {
      toast('Modo solo lectura', 'blue')
      return
    }
    upsertOperacion(opData)
  }

  const safeDeleteOperacion = (opId) => {
    if (!canWrite) {
      toast('Modo solo lectura', 'blue')
      return
    }
    deleteOperacion(opId)
  }

  const openAddBoteModal = (onBoatCreated) => {
    const initialRegion = regiones[0]?.rom || 'I'
    const initialCaletas = caletasByRegion[initialRegion] || []

    const Body = () => {
      const [form, setForm] = useState({
        region: initialRegion,
        nombre: '',
        nrpa: '',
        nmatricula: '',
        caleta: initialCaletas[0] || ''
      })

      const caletas = caletasByRegion[form.region] || []

      const onSave = () => {
        if (!form.nombre.trim()) {
          toast('Ingresa el nombre del bote', 'red')
          return
        }
        if (!form.caleta) {
          toast('Selecciona una caleta', 'red')
          return
        }

        const newBote = {
          id: Date.now().toString(),
          region: form.region,
          nombre: form.nombre.toUpperCase().trim(),
          nrpa: form.nrpa.trim(),
          nmatricula: form.nmatricula.trim(),
          caleta: form.caleta.toUpperCase().trim()
        }

        upsertBoteMaestro(newBote)
        toast('Bote agregado correctamente', 'green')
        closeModal()
        if (typeof onBoatCreated === 'function') {
          onBoatCreated(newBote.nombre)
        }
      }

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="i2">
            <div className="ig">
              <label className="il">Región</label>
              <select
                className="is"
                value={form.region}
                onChange={(e) => {
                  const newRegion = e.target.value
                  const newCaletas = caletasByRegion[newRegion] || []
                  setForm((p) => ({ ...p, region: newRegion, caleta: newCaletas[0] || '' }))
                }}
              >
                {regiones.map((r) => (
                  <option key={r.id} value={r.rom}>{r.rom} — {r.nom}</option>
                ))}
              </select>
            </div>
            <div className="ig">
              <label className="il">Nombre de Bote</label>
              <input
                className="ii"
                placeholder="Ej: CHIPANA"
                value={form.nombre}
                onChange={(e) => setForm((p) => ({ ...p, nombre: e.target.value }))}
                autoFocus
              />
            </div>
          </div>
          <div className="i2">
            <div className="ig">
              <label className="il">RPA</label>
              <input
                className="ii"
                placeholder="Ej: 401"
                value={form.nrpa}
                onChange={(e) => setForm((p) => ({ ...p, nrpa: e.target.value }))}
              />
            </div>
            <div className="ig">
              <label className="il">Matrícula</label>
              <input
                className="ii"
                placeholder="Ej: 100"
                value={form.nmatricula}
                onChange={(e) => setForm((p) => ({ ...p, nmatricula: e.target.value }))}
              />
            </div>
          </div>
          <div className="ig">
            <label className="il">Caleta</label>
            <select
              className="is"
              value={form.caleta}
              onChange={(e) => setForm((p) => ({ ...p, caleta: e.target.value }))}
            >
              {caletas.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cancelar
            </button>
            <button className="btn b-teal" style={{ flex: 1 }} onClick={onSave}>
              Guardar
            </button>
          </div>
        </div>
      )
    }

    openModal('Agregar Nuevo Bote', <Body />, 'normal')
  }

  const BotesEditor = ({ opId, opFallback, onCancel, onSaved }) => {
    const opBase = (operaciones || []).find((o) => String(o?.id) === String(opId)) || null
    const base = opBase || opFallback || null
    const seed = Array.isArray(base?.botes) ? base.botes : []
    const opCaleta = String(base?.sector || base?.caleta || '').trim()
    const caletaKey = String(opCaleta || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, ' ')
      .trim()

    const [rows, setRows] = useState(() => {
      if (seed.length) {
        return seed.map((b, i) => ({
          sourceId: String(b?.id || ''),
          zona: Number(b?.zona) || i + 1,
          nombre: String(b?.nombre || ''),
          buzo: String(b?.buzo || ''),
          densTipo: b?.densTipo === 'cuadrante' ? 'cuadrante' : 'transecto',
        }))
      }
      return Array.from({ length: 4 }, (_, i) => ({
        sourceId: '',
        zona: i + 1,
        nombre: '',
        buzo: '',
        densTipo: 'transecto',
      }))
    })

    const [showPanel, setShowPanel] = useState(false)
    const [currentRowIdx, setCurrentRowIdx] = useState(null)
    const [searchTerm, setSearchTerm] = useState('')

    const addRow = () => {
      setRows((prev) => [...prev, { sourceId: '', zona: (prev[prev.length - 1]?.zona || 0) + 1, nombre: '', buzo: '', densTipo: 'transecto' }])
    }

    const removeRow = (idx) => {
      setRows((prev) => prev.filter((_, i) => i !== idx))
      if (currentRowIdx === idx) {
        setShowPanel(false)
        setCurrentRowIdx(null)
      }
    }

    const openPanel = (idx) => {
      setCurrentRowIdx(idx)
      setShowPanel(true)
    }

    const closePanel = () => {
      setShowPanel(false)
      setCurrentRowIdx(null)
      setSearchTerm('')
    }

    const handleSelectBoat = (boatName) => {
      if (currentRowIdx !== null) {
        setRows((prev) => prev.map((x, i) => (i === currentRowIdx ? { ...x, nombre: boatName } : x)))
      }
      closePanel()
    }

    const handleAddNewBoat = () => {
      openAddBoteModal((newBoatName) => {
        if (newBoatName && currentRowIdx !== null) {
          setRows((prev) => prev.map((x, i) => (i === currentRowIdx ? { ...x, nombre: newBoatName } : x)))
        }
        closePanel()
      })
    }

    const onSaveBotes = () => {
      const clean = rows
        .map((r) => ({
          sourceId: String(r.sourceId || ''),
          zona: parseInt(r.zona, 10) || 1,
          nombre: String(r.nombre || '').trim(),
          buzo: String(r.buzo || '').trim(),
          densTipo: r.densTipo === 'cuadrante' ? 'cuadrante' : 'transecto',
        }))
        .filter((r) => r.nombre)
      if (!clean.length) {
        toast('Ingresa al menos un bote', 'red')
        return
      }
      safeUpdateOperacion(opId, (cur) => {
        const prevBotes = Array.isArray(cur?.botes) ? cur.botes : []
        const prevById = new Map(prevBotes.map((b) => [String(b?.id || ''), b]))
        const nextBotes = clean.map((r, i) => {
          const prev = prevById.get(r.sourceId)
          const prevDensTipo = prev?.densTipo === 'cuadrante' ? 'cuadrante' : 'transecto'
          const keepDensidad = prev && prevDensTipo === r.densTipo
          return {
            id: `B${i + 1}`,
            nombre: r.nombre,
            buzo: r.buzo,
            zona: r.zona,
            densTipo: r.densTipo,
            lpMuestras: prev?.lpMuestras && typeof prev.lpMuestras === 'object' ? prev.lpMuestras : {},
            transectos: keepDensidad ? (Array.isArray(prev?.transectos) ? prev.transectos : []) : [],
          }
        })
        return { ...cur, botes: nextBotes }
      })
      toast('Botes actualizados', 'green')
      if (typeof onSaved === 'function') onSaved()
    }

    const masterBotes = useMemo(() => {
      const arr = db?.botesMaestro
      return Array.isArray(arr) ? arr : []
    }, [db?.botesMaestro])

    const filteredBotes = useMemo(() => {
      const term = String(searchTerm || '')
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim()
      return masterBotes.filter(
        (b) =>
          String(b?.caleta || '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/[^a-z0-9]+/g, ' ')
            .trim() === caletaKey &&
          (String(b?.nombre || '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/[^a-z0-9]+/g, ' ')
            .trim()
            .includes(term) ||
            String(b?.nrpa || '')
              .toLowerCase()
              .normalize('NFD')
              .replace(/[\u0300-\u036f]/g, '')
              .replace(/[^a-z0-9]+/g, ' ')
              .trim()
              .includes(term) ||
            String(b?.nmatricula || '')
              .toLowerCase()
              .normalize('NFD')
              .replace(/[\u0300-\u036f]/g, '')
              .replace(/[^a-z0-9]+/g, ' ')
              .trim()
              .includes(term))
      )
    }, [searchTerm, caletaKey, masterBotes])

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ overflow: 'auto', border: '1px solid var(--border)', borderRadius: 10 }}>
          <table className="tbl">
            <thead>
              <tr>
                <th>#</th>
                <th>Zona muestreo</th>
                <th>Bote</th>
                <th>Buzo</th>
                <th>Unidad de muestreo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, idx) => (
                <tr key={`${r.sourceId || 'new'}-${idx}`}>
                  <td>{idx + 1}</td>
                  <td style={{ minWidth: 120 }}>
                    <input className="ii" type="number" value={r.zona} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, zona: e.target.value } : x)))} />
                  </td>
                  <td style={{ minWidth: 220 }}>
                    <input
                      className="ii"
                      placeholder="Nombre bote"
                      value={r.nombre}
                      onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, nombre: e.target.value } : x)))}
                      onClick={() => openPanel(idx)}
                      onFocus={() => openPanel(idx)}
                      style={{
                        borderColor: currentRowIdx === idx ? 'var(--teal)' : undefined,
                        boxShadow: currentRowIdx === idx ? '0 0 0 2px rgba(10,143,126,0.1)' : undefined,
                      }}
                    />
                  </td>
                  <td style={{ minWidth: 220 }}>
                    <input className="ii" placeholder="Nombre buzo" value={r.buzo} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, buzo: e.target.value } : x)))} />
                  </td>
                  <td style={{ minWidth: 190 }}>
                    <select 
                      className="is" 
                      value={r.densTipo} 
                      onChange={(e) => {
                        const newDensTipo = e.target.value
                        if (r.densTipo !== newDensTipo) {
                          const ok = confirm('Al cambiar la unidad de muestreo, solo se perderán los datos de densidad (los datos de peso-longitud se mantendrán). ¿Continuar?')
                          if (!ok) return
                        }
                        setRows((p) => p.map((x, i) => (i === idx ? { ...x, densTipo: newDensTipo } : x)))
                      }}
                    >
                      <option value="transecto">Transecto</option>
                      <option value="cuadrante">Cuadrante</option>
                    </select>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button className="btn b-out b-sm" onClick={() => removeRow(idx)}>
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {showPanel && (
          <div style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 16, backgroundColor: 'var(--bg)', boxShadow: 'var(--shadow)', marginTop: 4 }}>
            <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
              <input
                className="ii"
                placeholder="Buscar bote, RPA o matrícula..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{ flexGrow: 1, minWidth: 200 }}
                autoFocus
              />
              <button className="btn b-out" onClick={handleAddNewBoat}>
                Agregar nuevo
              </button>
              <button className="btn b-out" onClick={closePanel}>
                Cerrar
              </button>
            </div>

            <div style={{ maxHeight: 240, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 8 }}>
              {filteredBotes.length === 0 ? (
                <div style={{ padding: '16px', color: 'var(--text3)', textAlign: 'center' }}>
                  No se encontraron botes para "{searchTerm}" en la caleta {opCaleta || '(ninguna)'}.
                </div>
              ) : (
                filteredBotes.map((boat) => (
                  <div
                    key={boat.id}
                    style={{
                      padding: '12px 16px',
                      cursor: 'pointer',
                      borderBottom: '1px solid var(--border)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      transition: 'background-color 0.15s',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--bg2)')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                    onClick={() => handleSelectBoat(boat.nombre)}
                  >
                    <div>
                      <div style={{ fontWeight: 800, color: 'var(--navy)', fontSize: 14 }}>{boat.nombre}</div>
                      <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 4 }}>
                        RPA {boat.nrpa} · Caleta {boat.caleta}
                      </div>
                    </div>
                    <div style={{ backgroundColor: 'var(--bg2)', padding: '4px 8px', borderRadius: 12, fontSize: 11, fontWeight: 600, color: 'var(--text2)' }}>
                      {boat.region}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', flexWrap: 'wrap', marginTop: 8 }}>
          <button className="btn b-out" onClick={addRow}>
            Agregar fila
          </button>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              className="btn b-out"
              onClick={() => {
                if (typeof onCancel === 'function') onCancel()
              }}
            >
              Cancelar
            </button>
            <button className="btn b-teal" onClick={onSaveBotes}>
              Guardar botes
            </button>
          </div>
        </div>
      </div>
    )
  }

  const openBotesTable = (opId, opFallback) => {
    const BodyBotes = () => {
      const opBase = (operaciones || []).find((o) => String(o?.id) === String(opId)) || opFallback || null
      const seed = Array.isArray(opBase?.botes) ? opBase.botes : []
      const opCaleta = String(opBase?.sector || opBase?.caleta || '').trim()
      const caletaKey = String(opCaleta || '')
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim()

      const [rows, setRows] = useState(() => {
        if (seed.length) {
          return seed.map((b, i) => ({
            sourceId: String(b?.id || ''),
            zona: Number(b?.zona) || i + 1,
            nombre: String(b?.nombre || ''),
            buzo: String(b?.buzo || ''),
            densTipo: b?.densTipo === 'cuadrante' ? 'cuadrante' : 'transecto',
          }))
        }
        return Array.from({ length: 4 }, (_, i) => ({
          sourceId: '',
          zona: i + 1,
          nombre: '',
          buzo: '',
          densTipo: 'transecto',
        }))
      })

      const [showPanel, setShowPanel] = useState(false)
      const [currentRowIdx, setCurrentRowIdx] = useState(null)
      const [searchTerm, setSearchTerm] = useState('')

      const addRow = () => {
        setRows((prev) => [...prev, { sourceId: '', zona: (prev[prev.length - 1]?.zona || 0) + 1, nombre: '', buzo: '', densTipo: 'transecto' }])
      }

      const removeRow = (idx) => {
        setRows((prev) => prev.filter((_, i) => i !== idx))
        if (currentRowIdx === idx) {
          setShowPanel(false)
          setCurrentRowIdx(null)
        }
      }

      const openPanel = (idx) => {
        setCurrentRowIdx(idx)
        setShowPanel(true)
      }

      const closePanel = () => {
        setShowPanel(false)
        setCurrentRowIdx(null)
        setSearchTerm('')
      }

      const handleSelectBoat = (boatName) => {
        if (currentRowIdx !== null) {
          setRows((prev) => prev.map((x, i) => (i === currentRowIdx ? { ...x, nombre: boatName } : x)))
        }
        closePanel()
      }

      const handleAddNewBoat = () => {
        openAddBoteModal((newBoatName) => {
          if (newBoatName && currentRowIdx !== null) {
            setRows((prev) => prev.map((x, i) => (i === currentRowIdx ? { ...x, nombre: newBoatName } : x)))
          }
          closePanel()
        })
      }

      const onSaveBotes = () => {
        const clean = rows
          .map((r) => ({
            sourceId: String(r.sourceId || ''),
            zona: parseInt(r.zona, 10) || 1,
            nombre: String(r.nombre || '').trim(),
            buzo: String(r.buzo || '').trim(),
            densTipo: r.densTipo === 'cuadrante' ? 'cuadrante' : 'transecto',
          }))
          .filter((r) => r.nombre)
        if (!clean.length) {
          toast('Ingresa al menos un bote', 'red')
          return
        }
        safeUpdateOperacion(opId, (cur) => {
          const prevBotes = Array.isArray(cur?.botes) ? cur.botes : []
          const prevById = new Map(prevBotes.map((b) => [String(b?.id || ''), b]))
          const nextBotes = clean.map((r, i) => {
            const prev = prevById.get(r.sourceId)
            const prevDensTipo = prev?.densTipo === 'cuadrante' ? 'cuadrante' : 'transecto'
            const keepDensidad = prev && prevDensTipo === r.densTipo
            return {
              id: `B${i + 1}`,
              nombre: r.nombre,
              buzo: r.buzo,
              zona: r.zona,
              densTipo: r.densTipo,
              lpMuestras: prev?.lpMuestras && typeof prev.lpMuestras === 'object' ? prev.lpMuestras : {},
              transectos: keepDensidad ? (Array.isArray(prev?.transectos) ? prev.transectos : []) : [],
            }
          })
          return { ...cur, botes: nextBotes }
        })
        closeModal()
        toast('Botes actualizados', 'green')
      }

      const masterBotes = useMemo(() => {
        const arr = db?.botesMaestro
        return Array.isArray(arr) ? arr : []
      }, [db?.botesMaestro])

      const filteredBotes = useMemo(() => {
        const term = String(searchTerm || '')
          .toLowerCase()
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .replace(/[^a-z0-9]+/g, ' ')
          .trim()
        return masterBotes.filter(
          (b) =>
            String(b?.caleta || '')
              .toLowerCase()
              .normalize('NFD')
              .replace(/[\u0300-\u036f]/g, '')
              .replace(/[^a-z0-9]+/g, ' ')
              .trim() === caletaKey &&
            (String(b?.nombre || '')
              .toLowerCase()
              .normalize('NFD')
              .replace(/[\u0300-\u036f]/g, '')
              .replace(/[^a-z0-9]+/g, ' ')
              .trim()
              .includes(term) ||
              String(b?.nrpa || '')
                .toLowerCase()
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '')
                .replace(/[^a-z0-9]+/g, ' ')
                .trim()
                .includes(term) ||
              String(b?.nmatricula || '')
                .toLowerCase()
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '')
                .replace(/[^a-z0-9]+/g, ' ')
                .trim()
                .includes(term))
        )
      }, [searchTerm, caletaKey, masterBotes])

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ overflow: 'auto', border: '1px solid var(--border)', borderRadius: 10 }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Zona muestreo</th>
                  <th>Bote</th>
                  <th>Buzo</th>
                  <th>Unidad de muestreo</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, idx) => (
                  <tr key={`${r.sourceId || 'new'}-${idx}`}>
                    <td>{idx + 1}</td>
                    <td style={{ minWidth: 120 }}>
                      <input className="ii" type="number" value={r.zona} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, zona: e.target.value } : x)))} />
                    </td>
                    <td style={{ minWidth: 220 }}>
                      <input
                        className="ii"
                        placeholder="Nombre bote"
                        value={r.nombre}
                        onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, nombre: e.target.value } : x)))}
                        onClick={() => openPanel(idx)}
                        onFocus={() => openPanel(idx)}
                        style={{
                          borderColor: currentRowIdx === idx ? 'var(--teal)' : undefined,
                          boxShadow: currentRowIdx === idx ? '0 0 0 2px rgba(10,143,126,0.1)' : undefined
                        }}
                      />
                    </td>
                    <td style={{ minWidth: 220 }}>
                      <input className="ii" placeholder="Nombre buzo" value={r.buzo} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, buzo: e.target.value } : x)))} />
                    </td>
                    <td style={{ minWidth: 190 }}>
                      <select 
                        className="is" 
                        value={r.densTipo} 
                        onChange={(e) => {
                          const newDensTipo = e.target.value
                          if (r.densTipo !== newDensTipo) {
                            const ok = confirm('Al cambiar la unidad de muestreo, solo se perderán los datos de densidad (los datos de peso-longitud se mantendrán). ¿Continuar?')
                            if (!ok) return
                          }
                          setRows((p) => p.map((x, i) => (i === idx ? { ...x, densTipo: newDensTipo } : x)))
                        }}
                      >
                        <option value="transecto">Transecto</option>
                        <option value="cuadrante">Cuadrante</option>
                      </select>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button className="btn b-out b-sm" onClick={() => removeRow(idx)}>
                        Eliminar
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {showPanel && (
            <div style={{
              border: '1px solid var(--border)',
              borderRadius: 10,
              padding: 16,
              backgroundColor: 'var(--bg)',
              boxShadow: 'var(--shadow)',
              marginTop: 4,
            }}>
              <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
                <input
                  className="ii"
                  placeholder="Buscar bote, RPA o matrícula..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{ flexGrow: 1, minWidth: 200 }}
                  autoFocus
                />
                <button className="btn b-out" onClick={handleAddNewBoat}>
                  Agregar nuevo
                </button>
                <button className="btn b-out" onClick={closePanel}>
                  Cerrar
                </button>
              </div>

              <div style={{ maxHeight: 240, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 8 }}>
                {filteredBotes.length === 0 ? (
                  <div style={{ padding: '16px', color: 'var(--text3)', textAlign: 'center' }}>
                    No se encontraron botes para "{searchTerm}" en la caleta {opCaleta || '(ninguna)'}.
                  </div>
                ) : (
                  filteredBotes.map((boat) => (
                    <div
                      key={boat.id}
                      style={{
                        padding: '12px 16px',
                        cursor: 'pointer',
                        borderBottom: '1px solid var(--border)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        transition: 'background-color 0.15s',
                      }}
                      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--bg2)'}
                      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                      onClick={() => handleSelectBoat(boat.nombre)}
                    >
                      <div>
                        <div style={{ fontWeight: 800, color: 'var(--navy)', fontSize: 14 }}>
                          {boat.nombre}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 4 }}>
                          RPA {boat.nrpa} · Caleta {boat.caleta}
                        </div>
                      </div>
                      <div style={{
                        backgroundColor: 'var(--bg2)',
                        padding: '4px 8px',
                        borderRadius: 12,
                        fontSize: 11,
                        fontWeight: 600,
                        color: 'var(--text2)'
                      }}>
                        {boat.region}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', flexWrap: 'wrap', marginTop: 8 }}>
            <button className="btn b-out" onClick={addRow}>
              Agregar fila
            </button>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn b-out" onClick={closeModal}>
                Cancelar
              </button>
              <button className="btn b-teal" onClick={onSaveBotes}>
                Guardar botes
              </button>
            </div>
          </div>
        </div>
      )
    }

    openModal(`Botes — ${opId}`, <BodyBotes />, 'wide')
  }

  const openEditOp = (op) => {
    if (!canWrite) {
      toast('Modo solo lectura', 'blue')
      return
    }
    const iso = todayISO()
    const form = {
      region: op?.region ?? (regiones[0]?.id || 1),
      sectorAmerbId: String(op?.sectorAmerbId || ''),
      sectorAmerb: String(op?.sectorAmerb || ''),
      sector: String(op?.sector || ''),
      tipoOrg: String(op?.tipoOrg || 'STI'),
      opaId: String(op?.opaId || ''),
      org: String(op?.org || ''),
      numSeg: op?.numSeg == null ? '' : String(op.numSeg),
      fechaInicio: String(op?.fechaInicio || iso),
      fechaFin: String(op?.fechaFin || iso),
    }

    const Body = () => {
      const [s, setS] = useState(form)
      const [tab, setTab] = useState('op')

      const amerbOpts = sectorAmerb
        .filter((a) => a.region === s.region)
        .slice()
        .sort((a, b) => String(a.nombreamerb || '').localeCompare(String(b.nombreamerb || '')))
        .slice(0, 4000)

      const caletasOpts = (Array.isArray(caletasByRegion?.[s.region]) ? caletasByRegion[s.region] : [])
        .slice()
        .sort((a, b) => String(a).localeCompare(String(b)))
        .slice(0, 4000)

      const opaOpts = opa
        .filter((o) => o.region === s.region)
        .slice()
        .sort((a, b) => String(a.nombre || a.nombrecorto || '').localeCompare(String(b.nombre || b.nombrecorto || '')))
        .slice(0, 4000)

      const onSave = () => {
        const segRaw = String(s.numSeg || '').trim()
        const segNum = segRaw === '' ? null : parseInt(segRaw, 10)
        if (segRaw !== '' && !Number.isFinite(segNum)) {
          toast('Seguimiento inválido', 'red')
          return
        }
        if (!String(s.sectorAmerb || '').trim()) {
          toast('Selecciona Sector AMERB', 'red')
          return
        }
        if (!String(s.sector || '').trim()) {
          toast('Selecciona Caleta', 'red')
          return
        }

        safeUpdateOperacion(op.id, (cur) => ({
          ...cur,
          region: s.region,
          sectorAmerbId: s.sectorAmerbId,
          sectorAmerb: s.sectorAmerb,
          sector: s.sector,
          tipoOrg: s.tipoOrg,
          org: s.org,
          opaId: s.opaId,
          numSeg: segNum,
          fechaInicio: s.fechaInicio,
          fechaFin: s.fechaFin,
        }))
        closeModal()
        toast('Operación actualizada', 'green')
      }

      const onDelete = () => {
        const ok1 = confirm(
          `Vas a eliminar la operación ${op.id}. Se perderán todos los datos de transectos/cuadrantes, botes y muestras. ¿Continuar?`,
        )
        if (!ok1) return
        const ok2 = confirm(`Confirmación final: ¿Eliminar definitivamente ${op.id}?`)
        if (!ok2) return
        safeDeleteOperacion(op.id)
        setExpanded((prev) => {
          const next = new Set(prev)
          next.delete(op.id)
          return next
        })
        closeModal()
        toast('Operación eliminada', 'green')
      }

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="btabs">
            <div className={`btab${tab === 'op' ? ' on' : ''}`} onClick={() => setTab('op')}>
              Operación
            </div>
            <div className={`btab${tab === 'botes' ? ' on' : ''}`} onClick={() => setTab('botes')}>
              Botes
            </div>
          </div>

          {tab === 'op' ? (
            <>
          <div className="i2">
            <div className="ig">
              <label className="il">Región</label>
              <select
                className="is"
                value={s.region}
                onChange={(e) => {
                  const rid = parseInt(e.target.value, 10)
                  setS((p) => ({
                    ...p,
                    region: Number.isFinite(rid) ? rid : p.region,
                    sectorAmerbId: '',
                    sectorAmerb: '',
                    sector: '',
                    opaId: '',
                    org: '',
                  }))
                }}
              >
                {regiones.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.rom} — {r.nom}
                  </option>
                ))}
              </select>
            </div>
            <div className="ig">
              <label className="il">N° Seguimiento / ESBA</label>
              <input className="ii" placeholder="Ej: 16" value={s.numSeg} onChange={(e) => setS((p) => ({ ...p, numSeg: e.target.value }))} />
            </div>
          </div>

          <SearchableSelect
            label="Sector AMERB"
            value={s.sectorAmerbId}
            options={amerbOpts.map((a) => ({ value: String(a.id), label: a.nombreamerb }))}
            placeholder="Buscar sector AMERB..."
            onChange={(id) => {
              const f = amerbOpts.find((x) => String(x.id) === String(id))
              setS((p) => ({ ...p, sectorAmerbId: String(id || ''), sectorAmerb: f?.nombreamerb || '' }))
            }}
            onAdd={() => {
              const name = prompt('Nuevo Sector AMERB (no se guardará aún):')
              if (!name) return
              toast('Sector AMERB agregado solo para esta operación (pendiente BD)', 'blue')
              setS((p) => ({ ...p, sectorAmerbId: 'custom', sectorAmerb: String(name).trim() }))
            }}
            addLabel="Agregar Sector..."
          />

          <SearchableSelect
            label="Caleta"
            value={s.sector}
            options={caletasOpts.map((c) => ({ value: c, label: c }))}
            placeholder="Buscar caleta..."
            onChange={(v) => setS((p) => ({ ...p, sector: String(v || '') }))}
            onAdd={() => {
              const name = prompt('Nueva Caleta (no se guardará aún):')
              if (!name) return
              toast('Caleta agregada solo para esta operación (pendiente BD)', 'blue')
              setS((p) => ({ ...p, sector: String(name).trim() }))
            }}
            addLabel="Agregar Caleta..."
          />

          <div className="i2">
            <div className="ig">
              <label className="il">Tipo organización</label>
              <select className="is" value={s.tipoOrg} onChange={(e) => setS((p) => ({ ...p, tipoOrg: e.target.value }))}>
                <option value="STI">STI</option>
                <option value="ASOC">ASOC</option>
                <option value="OTRO">OTRO</option>
              </select>
            </div>
            <SearchableSelect
              label="Organización (OPA)"
              value={s.opaId}
              options={opaOpts.map((o) => ({ value: String(o.id), label: o.nombre || o.nombrecorto }))}
              placeholder="Buscar organización..."
              onChange={(id) => {
                const f = opaOpts.find((x) => String(x.id) === String(id))
                setS((p) => ({ ...p, opaId: String(id || ''), org: f?.nombre || '' }))
              }}
              onAdd={() => {
                const name = prompt('Nueva Organización (pendiente BD):')
                if (!name) return
                toast('Organización agregada solo para esta operación (pendiente BD)', 'blue')
                setS((p) => ({ ...p, opaId: 'custom', org: String(name).trim() }))
              }}
              addLabel="Agregar Organización..."
            />
          </div>

          <div className="i2">
            <div className="ig">
              <label className="il">Fecha inicio</label>
              <input className="ii" type="date" value={s.fechaInicio} onChange={(e) => setS((p) => ({ ...p, fechaInicio: e.target.value }))} />
            </div>
            <div className="ig">
              <label className="il">Fecha fin</label>
              <input className="ii" type="date" value={s.fechaFin} onChange={(e) => setS((p) => ({ ...p, fechaFin: e.target.value }))} />
            </div>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cancelar
            </button>
            <button className="btn b-teal" style={{ flex: 1 }} onClick={onSave}>
              Guardar cambios
            </button>
          </div>

          <button className="btn" style={{ border: '1.5px solid var(--red)', background: 'transparent', color: 'var(--red)' }} onClick={onDelete}>
            ELIMINAR OPERACION
          </button>
            </>
          ) : (
            <BotesEditor opId={op.id} opFallback={{ ...op, sector: s.sector, caleta: s.sector }} onCancel={() => setTab('op')} />
          )}
        </div>
      )
    }

    openModal(`Editar operación — ${op.id}`, <Body />, 'wide')
  }

  const openNewOp = () => {
    const iso = todayISO()
    const y = iso.slice(0, 4)
    const baseRegion = regiones[0]?.id || 1
    const form = {
      region: baseRegion,
      sectorAmerbId: '',
      sectorAmerb: '',
      sector: '',
      tipoOrg: 'STI',
      opaId: '',
      org: '',
      numSeg: '',
      fechaInicio: iso,
      fechaFin: iso,
    }

    const Body = () => {
      const [s, setS] = useState(form)
      const amerbOpts = sectorAmerb
        .filter((a) => a.region === s.region)
        .slice()
        .sort((a, b) => String(a.nombreamerb || '').localeCompare(String(b.nombreamerb || '')))
        .slice(0, 4000)
      const caletasOpts = (Array.isArray(caletasByRegion?.[s.region]) ? caletasByRegion[s.region] : [])
        .slice()
        .sort((a, b) => String(a).localeCompare(String(b)))
        .slice(0, 4000)
      const opaOpts = opa
        .filter((o) => o.region === s.region)
        .slice()
        .sort((a, b) => String(a.nombre || a.nombrecorto || '').localeCompare(String(b.nombre || b.nombrecorto || '')))
        .slice(0, 4000)

      const onSave = () => {
        const segRaw = String(s.numSeg || '').trim()
        const segNum = segRaw === '' ? null : parseInt(segRaw, 10)
        if (segRaw !== '' && !Number.isFinite(segNum)) {
          toast('SEG inválido', 'red')
          return
        }
        if (!String(s.sector || '').trim()) {
          toast('Ingresa sector/caleta', 'red')
          return
        }
        const opId = nextOpId(operaciones, y)
        safeUpsertOperacion({
          id: opId,
          region: s.region,
          sectorAmerbId: s.sectorAmerbId,
          sectorAmerb: s.sectorAmerb,
          sector: s.sector,
          tipoOrg: s.tipoOrg,
          org: s.org,
          opaId: s.opaId,
          numSeg: segNum,
          fechaInicio: s.fechaInicio,
          fechaFin: s.fechaFin,
          botes: [],
        })
        closeModal()
        toast('Operación creada', 'green')
        setTimeout(() => openBotesTable(opId, { id: opId, sector: s.sector, caleta: s.sector, sectorAmerb: s.sectorAmerb }), 50)
      }

      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="i2">
            <div className="ig">
              <label className="il">Región</label>
              <select
                className="is"
                value={s.region}
                onChange={(e) => {
                  const rid = parseInt(e.target.value, 10)
                  setS((p) => ({
                    ...p,
                    region: Number.isFinite(rid) ? rid : p.region,
                    sectorAmerbId: '',
                    sectorAmerb: '',
                    sector: '',
                    opaId: '',
                    org: '',
                  }))
                }}
              >
                {regiones.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.rom} — {r.nom}
                  </option>
                ))}
              </select>
            </div>
            <div className="ig">
              <label className="il">N° Seguimiento / ESBA</label>
              <input
                className="ii"
                placeholder="Ej: 16"
                value={s.numSeg}
                onChange={(e) => setS((p) => ({ ...p, numSeg: e.target.value }))}
              />
            </div>
          </div>
          <SearchableSelect
            label="Sector AMERB"
            value={s.sectorAmerbId}
            options={amerbOpts.map((a) => ({ value: String(a.id), label: a.nombreamerb }))}
            placeholder="Buscar sector AMERB..."
            onChange={(id) => {
              const f = amerbOpts.find((x) => String(x.id) === String(id))
              setS((p) => ({ ...p, sectorAmerbId: String(id || ''), sectorAmerb: f?.nombreamerb || '' }))
            }}
            onAdd={() => {
              const name = prompt('Nuevo Sector AMERB (no se guardará aún):')
              if (!name) return
              toast('Sector AMERB agregado solo para esta operación (pendiente BD)', 'blue')
              setS((p) => ({ ...p, sectorAmerbId: 'custom', sectorAmerb: String(name).trim() }))
            }}
            addLabel="Agregar Sector..."
          />
          <SearchableSelect
            label="Caleta"
            value={s.sector}
            options={caletasOpts.map((c) => ({ value: c, label: c }))}
            placeholder="Buscar caleta..."
            onChange={(v) => setS((p) => ({ ...p, sector: String(v || '') }))}
            onAdd={() => {
              const name = prompt('Nueva Caleta (no se guardará aún):')
              if (!name) return
              toast('Caleta agregada solo para esta operación (pendiente BD)', 'blue')
              setS((p) => ({ ...p, sector: String(name).trim() }))
            }}
            addLabel="Agregar Caleta..."
          />
          <div className="i2">
            <div className="ig">
              <label className="il">Tipo organización</label>
              <select
                className="is"
                value={s.tipoOrg}
                onChange={(e) => setS((p) => ({ ...p, tipoOrg: e.target.value }))}
              >
                <option value="STI">STI</option>
                <option value="ASOC">ASOC</option>
                <option value="OTRO">OTRO</option>
              </select>
            </div>
            <SearchableSelect
              label="Organización (OPA)"
              value={s.opaId}
              options={opaOpts.map((o) => ({ value: String(o.id), label: o.nombre || o.nombrecorto }))}
              placeholder="Buscar organización..."
              onChange={(id) => {
                const f = opaOpts.find((x) => String(x.id) === String(id))
                setS((p) => ({ ...p, opaId: String(id || ''), org: f?.nombre || '' }))
              }}
              onAdd={() => {
                const name = prompt('Nueva Organización (pendiente BD):')
                if (!name) return
                toast('Organización agregada solo para esta operación (pendiente BD)', 'blue')
                setS((p) => ({ ...p, opaId: 'custom', org: String(name).trim() }))
              }}
              addLabel="Agregar Organización..."
            />
          </div>
          <div className="i2">
            <div className="ig">
              <label className="il">Fecha inicio</label>
              <input
                className="ii"
                type="date"
                value={s.fechaInicio}
                onChange={(e) => setS((p) => ({ ...p, fechaInicio: e.target.value }))}
              />
            </div>
            <div className="ig">
              <label className="il">Fecha fin</label>
              <input
                className="ii"
                type="date"
                value={s.fechaFin}
                onChange={(e) => setS((p) => ({ ...p, fechaFin: e.target.value }))}
              />
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn b-out" style={{ flex: 1 }} onClick={closeModal}>
              Cancelar
            </button>
            <button className="btn b-teal" style={{ flex: 1 }} onClick={onSave}>
              Crear
            </button>
          </div>
        </div>
      )
    }

    openModal('Nueva operación', <Body />, 'wide')
  }

  return (
    isViewer ? null : (
    <div className={`page${active ? ' active' : ''}`} id="pg-ops">
      <div className="ph">
        <div>
          <h2>Operaciones</h2>
          <p>
            Cada operación agrupa botes con sus datos de{' '}
            <strong>
              Peso-Longitud, Diametro del Disco de fijación y Transectos de
              densidad
            </strong>
          </p>
        </div>
        <div className="ph-a">
          <EvadirImporter
            db={db}
            canWrite={canWrite}
            toast={toast}
            openModal={openModal}
            closeModal={closeModal}
            operaciones={operaciones}
            nextOpId={nextOpId}
            safeUpsertOperacion={safeUpsertOperacion}
          />
          <button
            className="btn b-teal b-sm"
            disabled={!canWrite}
            onClick={() => {
              if (!canWrite) {
                toast('Modo solo lectura', 'blue')
                return
              }
              openNewOp()
            }}
          >
            Nueva operación
          </button>
        </div>
      </div>



      <div>
        {!regionSel ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {regionButtons.map((r) => (
              <button
                key={r.id}
                className="card"
                onClick={() => setRegionSel(r.id)}
                style={{
                  width: '100%',
                  textAlign: 'left',
                  padding: '18px 18px',
                  borderRadius: 18,
                  border: '1px solid var(--border)',
                  background: 'var(--bg)',
                  cursor: 'pointer',
                }}
              >
                <div style={{ fontWeight: 800, color: 'var(--text)', display: 'flex', alignItems: 'baseline', gap: 8 }}>
                  <span>{r.label}</span>
                  {r.det ? (
                    <span style={{ fontFamily: 'var(--ff-m)', fontSize: 11, color: 'var(--text3)', whiteSpace: 'nowrap' }}>
                      — {r.det}
                    </span>
                  ) : null}
                </div>
              </button>
            ))}
            {!regionButtons.length ? (
              <div className="info-box amber">
                <span>i</span>
                <div>Sin operaciones registradas.</div>
              </div>
            ) : null}
          </div>
        ) : (
          <>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, marginBottom: 12 }}>
              <button className="btn b-out b-sm" onClick={() => setRegionSel('')}>
                Volver a regiones
              </button>
              <div className="region-title">
                {regionButtons.find((x) => x.id === regionSel)?.label || `Región ${regionSel}`}
              </div>
            </div>

            {regionSel ? (
              <div className="filters">
                <select className="flt" value={sector} onChange={(e) => setSector(e.target.value)}>
                  <option value="">Todos los sectores</option>
                  {sectoresInRegion.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
                <select className="flt" value={mes} onChange={(e) => setMes(e.target.value)}>
                  <option value="">Todas las fechas</option>
                  {meses.map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </select>
                <input
                  className="flt"
                  type="text"
                  placeholder="Buscar operación, buzo, org..."
                  style={{ minWidth: 220 }}
                  value={texto}
                  onChange={(e) => setTexto(e.target.value)}
                />
                <button
                  className="btn b-out b-sm"
                  onClick={() => {
                    setSector('')
                    setMes('')
                    setTexto('')
                  }}
                >
                  Limpiar
                </button>
                <span style={{ fontFamily: 'var(--ff-m)', fontSize: 11, color: 'var(--text3)', marginLeft: 4 }}>
                  {filteredByRegion.length} operaciones
                </span>
              </div>
            ) : null}

            {filteredByRegion.length === 0 ? (
              <div className="info-box amber">
                <span>i</span>
                <div>Sin operaciones para esta región con los filtros actuales.</div>
              </div>
            ) : null}

            {filteredByRegion.map((op) => {
          const open = String(expanded || '') === String(op?.id ?? '')
          const { totalTx, totalLPMuestras } = getOperacionMetricas(op)
          const year = getOperacionYear(op)
          const segLabel = getOperacionSegLabel(op)
          const regionLabel = regionNameById.get(String(op?.region ?? '')) || String(op?.region || '—')
          const orgShort = opaShortById.get(String(op?.opaId ?? '')) || String(op?.org || '—')
          return (
            <div className="op-card card mb" key={op.id} style={{ padding: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 800, color: 'var(--text)' }}>
                    {year || '—'}, {segLabel}, {op.sector || '—'}
                  </div>
                  <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                    <span className="pill p-teal">Región {regionLabel}</span>
                    <span className="pill p-blu">{orgShort}</span>
                    <span className="pill p-grn">{fmtDMY(op.fechaInicio)}</span>
                    <span className="pill p-pur">{(op.botes || []).length} botes</span>
                    <span className="pill p-pur">{totalTx} unidades densidad</span>
                    <span className="pill p-amb">{totalLPMuestras} muestras L-P</span>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                  <button className="btn b-out b-sm" onClick={() => toggleExpanded(op.id)}>
                    {open ? 'Ocultar' : 'Abrir'}
                  </button>
                  <button
                    className="btn b-teal b-sm"
                    onClick={() => {
                      openModal(
                        'Previsualización EVADIR',
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                          <EvadirPreview db={db} op={op} />
                          <button className="btn b-teal" onClick={closeModal}>
                            Cerrar
                          </button>
                        </div>,
                        'full',
                      )
                    }}
                  >
                    Previsualizar EVADIR
                  </button>
                  <button className="tb-btn" title="Editar" onClick={() => openEditOp(op)}>
                    <SvgIcon name="edit" aria-hidden="true" />
                  </button>
                </div>
              </div>

              {open ? (
                <div style={{ marginTop: 12, borderTop: '1px solid var(--border)', paddingTop: 12 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
                    <div style={{ fontFamily: 'var(--ff-d)', fontSize: 14, fontWeight: 800, color: 'var(--navy)' }}>
                      Botes
                    </div>
                  </div>

                  {(op.botes || []).length === 0 ? (
                    <div className="info-box amber">
                      <span>i</span>
                      <div>Esta operación no tiene botes aún.</div>
                    </div>
                  ) : (
                    (op.botes || [])
                      .slice()
                      .sort((a, b) => {
                        const za = Number(a?.zona) || 0
                        const zb = Number(b?.zona) || 0
                        if (za !== zb) return za - zb
                        return String(a?.nombre || '').localeCompare(String(b?.nombre || ''))
                      })
                      .map((b) => (
                        <BoteCard
                          key={b.id}
                          op={op}
                          bote={b}
                          especies={db?.especies || []}
                          updateOperacion={safeUpdateOperacion}
                          toast={toast}
                          openModal={openModal}
                          closeModal={closeModal}
                        />
                      ))
                  )}
                </div>
              ) : null}
            </div>
          )
        })}
          </>
        )}
      </div>
    </div>
    )
  )
}
