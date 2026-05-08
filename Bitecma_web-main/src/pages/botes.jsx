import { useMemo, useState } from 'react'
import { useDb } from '../context/dbContext.jsx'
import { useUi } from '../context/uiContext.jsx'

export default function BotesPage({ active }) {
  const { db, upsertBoteMaestro } = useDb()
  const { openModal, closeModal, toast } = useUi()
  const regiones = useMemo(() => {
    const arr = db?.regionesChile
    return Array.isArray(arr) ? arr : []
  }, [db?.regionesChile])
  const botes = useMemo(() => {
    const arr = db?.botesMaestro
    return Array.isArray(arr) ? arr : []
  }, [db?.botesMaestro])
  const caletasByRegion = useMemo(() => db?.caletasByRegionStatic || {}, [db?.caletasByRegionStatic])

  const [regionRom, setRegionRom] = useState(regiones[0]?.rom || 'I')
  const [q, setQ] = useState('')
  const botesFiltrados = useMemo(() => {
    const query = String(q || '').toLowerCase().trim()
    return botes
      .filter((b) => String(b.region || '') === String(regionRom || ''))
      .filter((b) =>
        !query
          ? true
          : String(b.nombre || '').toLowerCase().includes(query) ||
            String(b.caleta || '').toLowerCase().includes(query) ||
            String(b.nrpa || '').toLowerCase().includes(query) ||
            String(b.nmatricula || '').toLowerCase().includes(query),
      )
      .sort((a, b) => String(a.nombre || '').localeCompare(String(b.nombre || '')))
      .slice(0, 2000)
  }, [botes, regionRom, q])

  const openAddBoteModal = () => {
    const regionSel = regiones.find((r) => r.rom === regionRom)
    const initialRegion = regionSel?.rom || regiones[0]?.rom || 'I'
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

  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-botes">
      <div className="ph">
        <div>
          <h2>Botes</h2>
          <p>Listado de botes y embarcaciones por región</p>
        </div>
      </div>
      <div className="admin-layout" id="mb-layout" style={{ height: 'calc(100vh - 190px)', alignItems: 'stretch' }}>
        <div className="card admin-menu" style={{ minHeight: 0, overflowY: 'auto' }}>
          {regiones.map((r) => (
            <div
              key={r.id}
              className={`admin-item ${regionRom === r.rom ? 'on' : ''}`}
              onClick={() => setRegionRom(r.rom)}
            >
              {r.rom} — {r.nom}
            </div>
          ))}
        </div>
        <div id="mb-right" style={{ minHeight: 0 }}>
          <div className="card admin-content" style={{ height: '100%', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
              <input className="flt" placeholder="Buscar bote..." value={q} onChange={(e) => setQ(e.target.value)} style={{ flexGrow: 1 }} />
              <button className="btn b-teal" onClick={openAddBoteModal}>
                Agregar
              </button>
            </div>
            <div style={{ overflow: 'auto', minHeight: 0 }}>
              <table className="tbl">
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Caleta</th>
                    <th>NRPA</th>
                    <th>Matrícula</th>
                  </tr>
                </thead>
                <tbody>
                  {botesFiltrados.length ? (
                    botesFiltrados.map((b) => (
                      <tr key={b.id}>
                        <td>
                          <strong>{b.nombre}</strong>
                        </td>
                        <td>{b.caleta || '—'}</td>
                        <td>{b.nrpa || '—'}</td>
                        <td>{b.nmatricula || '—'}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text3)', padding: 14 }}>Sin resultados</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
