import { useUi } from '../context/uiContext.jsx'

export default function InformePage({ active }) {
  const { toast } = useUi()
  return (
    <div className={`page${active ? ' active' : ''}`} id="pg-informe">
      <div className="ph">
        <div>
          <h2>Generar Informe</h2>
          <p>Plantilla DOCX autorrellenable · Formato SUBPESCA</p>
        </div>
        <div className="ph-a">
          <button className="btn b-teal b-sm" onClick={() => toast('Generación DOCX (pendiente)', 'blue')}>
            Generar DOCX
          </button>
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '310px 1fr', gap: 14 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="card">
            <div className="isec">Operación de referencia</div>
            <div className="ig">
              <label className="il">Sector AMERB</label>
              <select className="is">
                <option>HUAPE SECTOR B — CORRAL</option>
                <option>AMARGOS</option>
              </select>
            </div>
            <div className="ig">
              <label className="il">EVADIR vinculado</label>
              <select className="is">
                <option>OP-2025-033 · Huape B · 17-12-2025</option>
                <option>OP-2026-002 · Amargos · 05-02-2026</option>
              </select>
            </div>
            <div className="i2">
              <div className="ig">
                <label className="il">N° Seguimiento</label>
                <input className="ii" defaultValue="16" />
              </div>
              <div className="ig">
                <label className="il">Período</label>
                <input className="ii" defaultValue="Enero 2026" />
              </div>
            </div>
            <div className="ig">
              <label className="il">Jefe de proyecto</label>
              <select className="is">
                <option>Armando Rosson Villalobos</option>
                <option>Lorena Olmos Palacios</option>
              </select>
            </div>
            <div className="i2">
              <div className="ig">
                <label className="il">Ingresos ($)</label>
                <input className="ii" defaultValue="118.258.200" />
              </div>
              <div className="ig">
                <label className="il">Costos ($)</label>
                <input className="ii" defaultValue="11.500.000" />
              </div>
            </div>
            <button
              className="btn b-teal"
              style={{ width: '100%', marginTop: 4 }}
              onClick={() => toast('Generación DOCX (pendiente)', 'blue')}
            >
              Generar informe DOCX
            </button>
          </div>
        </div>
        <div
          className="card"
          style={{
            padding: 0,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div
            style={{
              background: 'var(--bg)',
              padding: '13px 16px',
              borderBottom: '1px solid var(--border)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <span style={{ fontFamily: 'var(--ff-d)', fontWeight: 700, color: 'var(--navy)' }}>
              Vista previa
            </span>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <span className="pill p-amb" id="inf-status">
                Borrador
              </span>
              <button className="btn b-out b-sm" onClick={() => toast('Abrir (pendiente)', 'blue')}>
                Abrir
              </button>
            </div>
          </div>
          <div style={{ padding: 16, overflowY: 'auto', flex: 1 }}>
            <div
              style={{
                background: 'var(--bg)',
                borderRadius: 9,
                padding: 20,
                border: '1px solid var(--border)',
                fontSize: 12,
                lineHeight: 1.75,
              }}
            >
              <div style={{ textAlign: 'center', marginBottom: 14 }}>
                <div
                  style={{
                    fontFamily: 'var(--ff-m)',
                    fontSize: 9,
                    color: 'var(--teal)',
                    letterSpacing: 2,
                    textTransform: 'uppercase',
                    marginBottom: 5,
                  }}
                >
                  SUBSECRETARÍA DE PESCA Y ACUICULTURA
                </div>
                <div
                  style={{
                    fontFamily: 'var(--ff-d)',
                    fontSize: 15,
                    fontWeight: 900,
                    color: 'var(--navy)',
                  }}
                >
                  INFORME DE SEGUIMIENTO N° 16
                </div>
                <div style={{ fontSize: 13, fontWeight: 700, margin: '5px 0' }}>
                  ÁREA DE MANEJO HUAPE SECTOR B
                </div>
                <div style={{ fontSize: 11, color: 'var(--text3)' }}>
                  Región de Los Ríos · Enero 2026
                </div>
              </div>
              <hr style={{ border: 'none', borderTop: '1px solid var(--border)', margin: '12px 0' }} />
              <div
                style={{
                  fontFamily: 'var(--ff-m)',
                  fontSize: 9,
                  color: 'var(--teal)',
                  letterSpacing: 1.5,
                  textTransform: 'uppercase',
                  marginBottom: 5,
                }}
              >
                Evaluación directa
              </div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '3px 0',
                  borderBottom: '1px solid var(--bg2)',
                  fontSize: 11,
                }}
              >
                <span style={{ color: 'var(--text3)' }}>Operación origen</span>
                <span>OP-2025-033</span>
              </div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '3px 0',
                  borderBottom: '1px solid var(--bg2)',
                  fontSize: 11,
                }}
              >
                <span style={{ color: 'var(--text3)' }}>Stock Loco</span>
                <span style={{ fontWeight: 700 }}>74.641 ind / 18.399 kg</span>
              </div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '3px 0',
                  fontSize: 11,
                }}
              >
                <span style={{ color: 'var(--text3)' }}>B/C</span>
                <span style={{ fontWeight: 700, color: 'var(--green)' }}>10.2</span>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 7, marginTop: 12 }}>
              <button className="btn b-teal" style={{ flex: 1 }} onClick={() => toast('Descarga DOCX (pendiente)', 'blue')}>
                Descargar .DOCX
              </button>
              <button
                className="btn b-out"
                style={{ flex: 1 }}
                onClick={() => toast('Enviado a SUBPESCA', 'green')}
              >
                Enviar SUBPESCA
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
