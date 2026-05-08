import { useEvadirRegistrados } from '../../hooks/useEvadirRegistrados.js'
import { fmtDMY } from '../../services/evadirService.js'
import { useDb } from '../../context/dbContext.jsx'
import { useUi } from '../../context/uiContext.jsx'
import { exportEvadirXlsx } from '../../utils/evadirExport.js'
import EvadirPreview from './EvadirPreview.jsx'

export default function EvadirRegistradosTable() {
  const { db } = useDb()
  const { toast, openModal, closeModal } = useUi()
  const { rows } = useEvadirRegistrados()

  return (
    <table className="tbl">
      <thead>
        <tr>
          <th>Sector</th>
          <th>SEG/ESBA</th>
          <th>Operación origen</th>
          <th>Fecha</th>
          <th>Transectos/Cuadrantes</th>
          <th>Botes</th>
          <th>Acción</th>
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <td colSpan={7} style={{ textAlign: 'center', color: 'var(--text3)', padding: 14 }}>
              Sin EVADIR registrados
            </td>
          </tr>
        ) : (
          rows.map((r) => {
            const txCqUI =
              r.totalTx > 0 || r.totalCq > 0 ? (
                <>
                  {r.totalTx ? (
                    <span className="pill p-blu" style={{ fontSize: 10 }}>
                      T {r.totalTx}
                    </span>
                  ) : null}{' '}
                  {r.totalCq ? (
                    <span className="pill p-pur" style={{ fontSize: 10 }}>
                      C {r.totalCq}
                    </span>
                  ) : null}
                </>
              ) : (
                '—'
              )

            return (
              <tr key={r.id}>
                <td>{r.sector}</td>
                <td>SEG-{r.numSeg}</td>
                <td>{r.id}</td>
                <td>{fmtDMY(r.fechaInicio)}</td>
                <td>{txCqUI}</td>
                <td>{r.totalBotes}</td>
                <td>
                  <button
                    className="btn b-out b-xs"
                    onClick={() => {
                      const op = (db?.operaciones || []).find((o) => o.id === r.id) || null
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
                    Ver
                  </button>{' '}
                  <button
                    className="btn b-teal b-xs"
                    onClick={() => exportEvadirXlsx({ db, opId: r.id, toast })}
                  >
                    CSV
                  </button>
                </td>
              </tr>
            )
          })
        )}
      </tbody>
    </table>
  )
}
