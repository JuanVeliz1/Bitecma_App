import { useState } from 'react'
import DensidadTab from './DensidadTab.jsx'
import LpTab from './LpTab.jsx'

export default function BoteCard({ op, bote, especies, updateOperacion, toast, openModal, closeModal }) {
  const [open, setOpen] = useState(false)
  const [tab, setTab] = useState('dens')

  const totalUnidades = Array.isArray(bote?.transectos) ? bote.transectos.length : 0
  const totalMuestras = (() => {
    const map = bote?.lpMuestras && typeof bote.lpMuestras === 'object' ? bote.lpMuestras : {}
    return Object.values(map).reduce((acc, arr) => acc + (Array.isArray(arr) ? arr.length : 0), 0)
  })()

  return (
    <div className="bote-card">
      <div
        className={`bote-hd${open ? ' open-hd' : ''}`}
        onClick={() => setOpen((v) => !v)}
      >
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', minWidth: 0 }}>
          <div className={`bote-icon${open ? ' open-ic' : ''}`} />
          <div style={{ minWidth: 0 }}>
            <div className="bote-name">
              {bote?.nombre || '—'} · Zona {bote?.zona ?? '—'}
            </div>
            <div className="bote-meta">
              {bote?.buzo || '—'} · {bote?.densTipo === 'cuadrante' ? 'Cuadrantes' : 'Transectos'}
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <span className="pill p-amb">{totalUnidades} unidades</span>
          <span className="pill p-teal">{totalMuestras} muestras</span>
          <button
            className="btn b-out b-sm"
            onClick={(e) => {
              e.stopPropagation()
              if (!confirm(`Eliminar bote "${bote?.nombre || bote?.id}"?`)) return
              updateOperacion(op.id, (cur) => ({
                ...cur,
                botes: (cur.botes || []).filter((x) => x.id !== bote.id),
              }))
              toast?.('Bote eliminado', 'green')
            }}
          >
            Eliminar
          </button>
        </div>
      </div>

      <div className={`bote-body${open ? ' open' : ''}`}>
        <div className="btabs">
          <div className={`btab${tab === 'dens' ? ' on' : ''}`} onClick={() => setTab('dens')}>
            Densidad
          </div>
          <div className={`btab${tab === 'lp' ? ' on' : ''}`} onClick={() => setTab('lp')}>
            Peso-Longitud
          </div>
        </div>

        {tab === 'dens' ? (
          <DensidadTab
            op={op}
            bote={bote}
            especies={especies}
            updateOperacion={updateOperacion}
            toast={toast}
            openModal={openModal}
            closeModal={closeModal}
          />
        ) : (
          <LpTab
            op={op}
            bote={bote}
            especies={especies}
            updateOperacion={updateOperacion}
            toast={toast}
            openModal={openModal}
            closeModal={closeModal}
          />
        )}
      </div>
    </div>
  )
}
