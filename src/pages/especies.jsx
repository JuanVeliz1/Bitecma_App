import { useEspecies } from '../hooks/useEspecies.js'
import { useUi } from '../context/uiContext.jsx'

export default function EspeciesPage({ active }) {
  const { especies } = useEspecies()
  const { toast } = useUi()

  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-especies">
      <div className="ph">
        <div>
          <h2>Maestro de Especies</h2>
          <p>36 especies bentónicas de Chile</p>
        </div>
        <div className="ph-a">
          <button className="btn b-teal b-sm" onClick={() => toast('Nueva especie (pendiente)', 'blue')}>
            Nueva especie
          </button>
        </div>
      </div>
      <div className="card">
        <table className="tbl">
          <thead>
            <tr>
              <th>#</th>
              <th>Nombre común</th>
              <th>Nombre científico</th>
              <th>Clase</th>
              <th>Registro habitual</th>
            </tr>
          </thead>
          <tbody>
            {especies.map((e, idx) => (
              <tr key={e.id ?? idx}>
                <td>{idx + 1}</td>
                <td>
                  <strong>{e.com}</strong>
                </td>
                <td>
                  <em>{e.sci}</em>
                </td>
                <td>{e.clase || '—'}</td>
                <td>{e.registro || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
