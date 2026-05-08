import { useState } from 'react'
import { useOperaciones } from '../hooks/useOperaciones.js'
import { useDb } from '../context/dbContext.jsx'
import { useUi } from '../context/uiContext.jsx'
import { getOperacionMetricas } from '../services/operacionesService.js'
import SvgIcon from '../components/svgIcon.jsx'
import BoteCard from '../components/ops/BoteCard.jsx'
import EvadirPreview from '../components/evadir/EvadirPreview.jsx'
import SearchableSelect from '../components/common/SearchableSelect.jsx'

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

export default function OpsPage({ active }) {
  const { db, upsertOperacion, updateOperacion, deleteOperacion } = useDb()
  const { toast, openModal, closeModal } = useUi()
  const { filtered, sectores, meses, sector, setSector, mes, setMes, texto, setTexto, operaciones } =
    useOperaciones()

  const [expanded, setExpanded] = useState(() => new Set())

  const toggleExpanded = (opId) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(opId)) next.delete(opId)
      else next.add(opId)
      return next
    })
  }

  const regiones = db?.regionesChile || []
  const sectorAmerb = db?.sectoresAmerb || []
  const caletasByRegion = db?.caletasByRegionStatic || {}
  const opa = db?.opa || []

  const openEditOp = (op) => {
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
        .sort((a, b) => String(a.nombrecorto || a.nombre || '').localeCompare(String(b.nombrecorto || b.nombre || '')))
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

        updateOperacion(op.id, (cur) => ({
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
        deleteOperacion(op.id)
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
              options={opaOpts.map((o) => ({ value: String(o.id), label: o.nombrecorto || o.nombre }))}
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
        .sort((a, b) => String(a.nombrecorto || a.nombre || '').localeCompare(String(b.nombrecorto || b.nombre || '')))
        .slice(0, 4000)

      const openCrearBotes = (opId) => {
        const BodyBotes = () => {
          const [rows, setRows] = useState(() =>
            Array.from({ length: 4 }, (_, i) => ({
              zona: i + 1,
              nombre: '',
              buzo: '',
              densTipo: 'transecto',
            })),
          )

          const addRow = () => {
            setRows((prev) => [...prev, { zona: (prev[prev.length - 1]?.zona || 0) + 1, nombre: '', buzo: '', densTipo: 'transecto' }])
          }

          const removeRow = (idx) => {
            setRows((prev) => prev.filter((_, i) => i !== idx))
          }

          const onSaveBotes = () => {
            const clean = rows
              .map((r) => ({
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
            updateOperacion(opId, (cur) => {
              const nextBotes = clean.map((r, i) => ({
                id: `B${i + 1}`,
                nombre: r.nombre,
                buzo: r.buzo,
                zona: r.zona,
                densTipo: r.densTipo,
                lpMuestras: {},
                transectos: [],
              }))
              return { ...cur, botes: nextBotes }
            })
            closeModal()
            toast('Botes creados', 'green')
          }

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
                      <tr key={idx}>
                        <td>{idx + 1}</td>
                        <td style={{ minWidth: 120 }}>
                          <input className="ii" type="number" value={r.zona} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, zona: e.target.value } : x)))} />
                        </td>
                        <td style={{ minWidth: 200 }}>
                          <input className="ii" placeholder="Nombre bote" value={r.nombre} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, nombre: e.target.value } : x)))} />
                        </td>
                        <td style={{ minWidth: 200 }}>
                          <input className="ii" placeholder="Nombre buzo" value={r.buzo} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, buzo: e.target.value } : x)))} />
                        </td>
                        <td style={{ minWidth: 180 }}>
                          <select className="is" value={r.densTipo} onChange={(e) => setRows((p) => p.map((x, i) => (i === idx ? { ...x, densTipo: e.target.value } : x)))}>
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

              <div style={{ display: 'flex', gap: 8, justifyContent: 'space-between', flexWrap: 'wrap' }}>
                <button className="btn b-out" onClick={addRow}>
                  Agregar fila
                </button>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn b-out" onClick={closeModal}>
                    Cancelar
                  </button>
                  <button className="btn b-teal" onClick={onSaveBotes}>
                    Crear botes
                  </button>
                </div>
              </div>
            </div>
          )
        }

        openModal(`Crear botes — ${opId}`, <BodyBotes />, 'wide')
      }

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
        upsertOperacion({
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
        setTimeout(() => openCrearBotes(opId), 50)
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
              options={opaOpts.map((o) => ({ value: String(o.id), label: o.nombrecorto || o.nombre }))}
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
          <button className="btn b-out b-sm" onClick={() => toast('Subida EVADIR (pendiente)', 'blue')}>
            Subir EVADIR
          </button>
          <button className="btn b-teal b-sm" onClick={openNewOp}>
            Nueva operación
          </button>
        </div>
      </div>

      <div className="filters">
        <select className="flt" value={sector} onChange={(e) => setSector(e.target.value)}>
          <option value="">Todos los sectores</option>
          {sectores.map((s) => (
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
          {filtered.length} operaciones
        </span>
      </div>

      <div>
        {filtered.map((op) => {
          const open = expanded.has(op.id)
          const { totalTx, totalLPMuestras } = getOperacionMetricas(op)
          const year = getOperacionYear(op)
          const segLabel = getOperacionSegLabel(op)
          return (
            <div className="op-card card mb" key={op.id} style={{ padding: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 800, color: 'var(--text)' }}>
                    {year || '—'}, {segLabel}, {op.sector || '—'}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 2 }}>
                    {op.org} · {op.fechaInicio}
                  </div>
                  <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <span className="pill p-pur">{(op.botes || []).length} botes</span>
                    <span className="pill p-blu">{totalTx} unidades densidad</span>
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
                        'wide',
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
                    <button
                      className="btn b-teal b-sm"
                      onClick={() => {
                        const Body = () => {
                          const [b, setB] = useState({ nombre: '', buzo: '', zona: 1, densTipo: 'transecto' })
                          return (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                              <div className="ig">
                                <label className="il">Nombre bote</label>
                                <input className="ii" value={b.nombre} onChange={(e) => setB((s) => ({ ...s, nombre: e.target.value }))} />
                              </div>
                              <div className="ig">
                                <label className="il">Buzo</label>
                                <input className="ii" value={b.buzo} onChange={(e) => setB((s) => ({ ...s, buzo: e.target.value }))} />
                              </div>
                              <div className="i2">
                                <div className="ig">
                                  <label className="il">Zona</label>
                                  <input className="ii" type="number" value={b.zona} onChange={(e) => setB((s) => ({ ...s, zona: parseInt(e.target.value, 10) || 1 }))} />
                                </div>
                                <div className="ig">
                                  <label className="il">Unidad de Muestreo</label>
                                  <select className="is" value={b.densTipo} onChange={(e) => setB((s) => ({ ...s, densTipo: e.target.value }))}>
                                    <option value="transecto">Transecto</option>
                                    <option value="cuadrante">Cuadrante</option>
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
                                  onClick={() => {
                                    if (!String(b.nombre || '').trim()) {
                                      toast('Ingresa nombre del bote', 'red')
                                      return
                                    }
                                    updateOperacion(op.id, (cur) => {
                                      const nextBotes = Array.isArray(cur.botes) ? [...cur.botes] : []
                                      const nextId = `B${nextBotes.length + 1}`
                                      nextBotes.push({
                                        id: nextId,
                                        nombre: b.nombre,
                                        buzo: b.buzo,
                                        zona: b.zona,
                                        densTipo: b.densTipo,
                                        lpMuestras: {},
                                        transectos: [],
                                      })
                                      return { ...cur, botes: nextBotes }
                                    })
                                    closeModal()
                                    toast('Bote agregado', 'green')
                                  }}
                                >
                                  Agregar
                                </button>
                              </div>
                            </div>
                          )
                        }
                        openModal('Agregar bote', <Body />, 'slim')
                      }}
                    >
                      + Agregar bote
                    </button>
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
                          updateOperacion={updateOperacion}
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
      </div>
    </div>
  )
}
