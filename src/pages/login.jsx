import logoUrl from '../img/logo.png'
import { useState } from 'react'
import { useApp } from '../context/appContext.jsx'
import { useUi } from '../context/uiContext.jsx'

export default function LoginScreen({ active }) {
  const { login } = useApp()
  const { toast } = useUi()
  const [email, setEmail] = useState('')
  const [pass, setPass] = useState('')

  return (
    <div id="scr-login" className={`screen${active ? ' active' : ''}`}>
      <div className="lcard">
        <div className="lc-logo">
          <div className="lc-icon">
            <img src={logoUrl} alt="BITECMA" />
          </div>
          <div className="lc-brand">
            <h1>BITECMA</h1>
            <span>Sistema AMERB - V1.0.4</span>
          </div>
        </div>
        <div className="lc-title">Iniciar sesión</div>
        <div className="lc-sub">
          Sistema de gestión de operaciones bentónicas y generación de Documentos.
        </div>
        <div className="lf-g">
          <label className="lf-lbl">Correo electrónico</label>
          <input
            className="lf-inp"
            id="login-email"
            type="email"
            placeholder="bitecma@bitecma.cl"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="lf-g">
          <label className="lf-lbl">Contraseña</label>
          <input
            className="lf-inp"
            id="login-pass"
            type="password"
            placeholder="12345678"
            value={pass}
            onChange={(e) => setPass(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') login(email, pass)
            }}
          />
        </div>
        <button className="btn-login" onClick={() => login(email, pass)}>
          Ingresar
        </button>
        <div className="lc-foot">
          <a onClick={() => toast('Correo enviado')}>¿Olvidaste tu contraseña?</a> · Bitecma
          Ltda. © 1995
        </div>
      </div>
    </div>
  )
}
