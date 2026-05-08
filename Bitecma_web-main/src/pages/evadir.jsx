/** Página EVADIR: retorna el HTML del listado y botones para generar/visualizar EVADIR a partir de una operación. */
import EvadirRegistradosTable from '../components/evadir/EvadirRegistradosTable.jsx'

export default function EvadirPage({ active }) {
  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-evadir">
      <div className="ph">
        <div>
          <h2>Generar EVADIR</h2>
          <p>
            El EVADIR se construye desde una operación: tabla <strong>DENSIDAD</strong> (transectos) + tablas{' '}
            <strong>PESO-LONGITUD</strong> (muestras por bote/especie)
          </p>
        </div>
        <div className="ph-a"></div>
      </div>

      <div className="card mb">
        <div className="ct">EVADIR registrados</div>
        <EvadirRegistradosTable />
      </div>
    </div>
  )
}
