import { useUi } from '../context/uiContext.jsx'

export default function HistoricoPage({ active }) {
  const { toast } = useUi()
  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-historico">
      <div className="ph">
        <div>
          <h2>Registro Histórico</h2>
          <p>36.127 transectos · 1999–2026</p>
        </div>
        <div className="ph-a">
          <button className="btn b-teal b-sm" onClick={() => toast('Vista completa (pendiente)', 'blue')}>
            Vista completa
          </button>
        </div>
      </div>
      <div className="g3 mb">
        <div className="sc sc-tl" style={{ cursor: 'default' }}>
          <div className="sc-lbl">Total registros</div>
          <div className="sc-val">36.127</div>
        </div>
        <div className="sc sc-bl" style={{ cursor: 'default' }}>
          <div className="sc-lbl">Sectores únicos</div>
          <div className="sc-val">58</div>
        </div>
        <div className="sc sc-gr" style={{ cursor: 'default' }}>
          <div className="sc-lbl">Rango temporal</div>
          <div className="sc-val" style={{ fontSize: 18 }}>
            1999–2026
          </div>
        </div>
      </div>
      <div className="card mb">
        <div className="ct">
          Continuidad de seguimientos
          <button className="btn b-out b-sm" onClick={() => toast('Abrir (pendiente)', 'blue')}>
            Abrir
          </button>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table className="tbl" id="cont-tbl">
            <thead>
              <tr id="cont-head"></tr>
            </thead>
            <tbody id="cont-body"></tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
