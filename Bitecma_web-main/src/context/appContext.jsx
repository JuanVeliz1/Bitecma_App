import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { useDb } from './dbContext.jsx'
import { useUi } from './uiContext.jsx'

const AppContext = createContext(null)

const ACTIVE_PROFILE_KEY = 'bitecma_active_profile'
const PROFILE_DATA_KEY = 'bitecma_profile_data'

function normKey(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
}

function normalizeRole(rol) {
  const r = normKey(rol)
  if (r === 'admin') return 'Admin'
  if (r === 'usuario' || r === 'biologo' || r === 'biologa') return 'Usuario'
  if (r === 'visualizador' || r === 'viewer' || r === 'readonly' || r === 'read-only') return 'Visualizador'
  return String(rol || '').trim() || 'Usuario'
}

function readActiveProfileId() {
  try {
    return localStorage.getItem(ACTIVE_PROFILE_KEY)
  } catch {
    return null
  }
}

function writeActiveProfileId(id) {
  try {
    if (!id) localStorage.removeItem(ACTIVE_PROFILE_KEY)
    else localStorage.setItem(ACTIVE_PROFILE_KEY, String(id))
  } catch {
    return
  }
}

function readProfileMap() {
  try {
    const raw = localStorage.getItem(PROFILE_DATA_KEY)
    const map = raw ? JSON.parse(raw) : {}
    return map && typeof map === 'object' ? map : {}
  } catch {
    return {}
  }
}

function writeProfileMap(map) {
  try {
    localStorage.setItem(PROFILE_DATA_KEY, JSON.stringify(map || {}))
  } catch {
    return
  }
}

export function AppProvider({ children }) {
  const { db } = useDb()
  const { toast } = useUi()

  const [page, setPage] = useState('dashboard')
  const [userId, setUserId] = useState(() => readActiveProfileId())
  const [profileMap, setProfileMap] = useState(() => readProfileMap())

  const user = useMemo(() => {
    const perfiles = db.perfiles || []
    const base = perfiles.find((p) => String(p.id) === String(userId)) || null
    if (!base) return null
    const saved = profileMap?.[String(base.id)]
    return saved && typeof saved === 'object' ? { ...base, ...saved } : base
  }, [db.perfiles, userId, profileMap])

  const role = useMemo(() => normalizeRole(user?.rol), [user?.rol])
  const isAdmin = role === 'Admin'
  const isViewer = role === 'Visualizador'
  const canWrite = !isViewer

  const isAuthed = !!user

  const navigate = useCallback((next) => {
    const p = String(next)
    setPage(p)
  }, [])

  const login = useCallback(
    (email, pass) => {
      const e = String(email || '').trim().toLowerCase()
      const p = String(pass || '').trim()
      if (!e || !p) {
        toast('Completa correo y contraseña', 'red')
        return false
      }
      const perfiles = db.perfiles || []
      const found = perfiles.find((x) => String(x.correo || '').toLowerCase() === e)
      if (!found) {
        toast('Usuario no encontrado', 'red')
        return false
      }
      if (p !== '12345678') {
        toast('Contraseña incorrecta', 'red')
        return false
      }
      setUserId(String(found.id))
      writeActiveProfileId(found.id)
      toast('Bienvenido', 'green')
      navigate('dashboard')
      return true
    },
    [db.perfiles, toast, navigate],
  )

  const logout = useCallback(() => {
    setUserId(null)
    writeActiveProfileId(null)
    toast('Sesión cerrada', 'green')
    setPage('dashboard')
  }, [toast])

  const updateProfile = useCallback(
    ({ nombre, correo, numero, logo }) => {
      if (!user) return false
      const next = {
        ...user,
        nombre: String(nombre ?? user.nombre ?? '').trim(),
        correo: String(correo ?? user.correo ?? '').trim(),
        numero: String(numero ?? user.numero ?? '').trim(),
        logo: String(logo ?? user.logo ?? ''),
      }
      const map = { ...profileMap, [String(user.id)]: next }
      setProfileMap(map)
      writeProfileMap(map)
      toast('Perfil actualizado', 'green')
      return true
    },
    [user, profileMap, toast],
  )

  const changePassword = useCallback(
    ({ oldPass, newPass, confirmPass }) => {
      if (!user) return false
      if (String(oldPass || '') !== String(user.contraseña || '')) {
        toast('Contraseña actual incorrecta', 'red')
        return false
      }
      const np = String(newPass || '')
      const cp = String(confirmPass || '')
      if (np.length < 8) {
        toast('La nueva contraseña debe tener al menos 8 caracteres', 'red')
        return false
      }
      if (np !== cp) {
        toast('Las contraseñas no coinciden', 'red')
        return false
      }
      const map = {
        ...profileMap,
        [String(user.id)]: { ...(profileMap[String(user.id)] || user), contraseña: np },
      }
      setProfileMap(map)
      writeProfileMap(map)
      toast('Contraseña actualizada', 'green')
      return true
    },
    [user, profileMap, toast],
  )

  const value = useMemo(
    () => ({
      page,
      navigate,
      isAuthed,
      user,
      role,
      isAdmin,
      isViewer,
      canWrite,
      login,
      logout,
      updateProfile,
      changePassword,
    }),
    [page, navigate, isAuthed, user, role, isAdmin, isViewer, canWrite, login, logout, updateProfile, changePassword],
  )

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>
}

export function useApp() {
  const ctx = useContext(AppContext)
  if (!ctx) throw new Error('AppProvider missing')
  return ctx
}
