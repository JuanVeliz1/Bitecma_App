import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import { ESPECIES } from '../data/especies.js'
import { OPERACIONES } from '../data/operaciones.js'
import { REGIONES_CHILE, CALETAS_BY_REGION_STATIC } from '../data/sectores.js'
import { SECTORES_AMERB } from '../data/sectores_amerb.js'
import { OPA } from '../data/opa.js'
import { BOTES } from '../data/botes.js'
import { PERFILES } from '../data/perfiles.js'

const DbContext = createContext(null)

function initialDb() {
  return {
    especies: ESPECIES,
    operaciones: OPERACIONES,
    regionesChile: REGIONES_CHILE,
    caletasByRegionStatic: CALETAS_BY_REGION_STATIC,
    sectoresAmerb: SECTORES_AMERB,
    opa: OPA,
    botesMaestro: BOTES,
    perfiles: PERFILES,
  }
}

export function DbProvider({ children }) {
  const [db, setDb] = useState(() => initialDb())

  const upsertOperacion = useCallback((op) => {
    setDb((prev) => {
      const ops = Array.isArray(prev.operaciones) ? prev.operaciones : []
      const idx = ops.findIndex((x) => x.id === op.id)
      const nextOps = idx >= 0 ? ops.map((x, i) => (i === idx ? op : x)) : [op, ...ops]
      return { ...prev, operaciones: nextOps }
    })
  }, [])

  const deleteOperacion = useCallback((opId) => {
    setDb((prev) => {
      const ops = Array.isArray(prev.operaciones) ? prev.operaciones : []
      return { ...prev, operaciones: ops.filter((x) => x.id !== opId) }
    })
  }, [])

  const updateOperacion = useCallback((opId, updater) => {
    setDb((prev) => {
      const ops = Array.isArray(prev.operaciones) ? prev.operaciones : []
      const idx = ops.findIndex((x) => x.id === opId)
      if (idx < 0) return prev
      const cur = ops[idx]
      const next = typeof updater === 'function' ? updater(cur) : { ...cur, ...(updater || {}) }
      return { ...prev, operaciones: ops.map((x, i) => (i === idx ? next : x)) }
    })
  }, [])

  const upsertBoteMaestro = useCallback((bote) => {
    setDb((prev) => {
      const botes = Array.isArray(prev.botesMaestro) ? prev.botesMaestro : []
      const idx = botes.findIndex((x) => x.id === bote.id)
      const nextBotes = idx >= 0 ? botes.map((x, i) => (i === idx ? bote : x)) : [bote, ...botes]
      return { ...prev, botesMaestro: nextBotes }
    })
  }, [])

  const deleteBoteMaestro = useCallback((boteId) => {
    setDb((prev) => {
      const botes = Array.isArray(prev.botesMaestro) ? prev.botesMaestro : []
      return { ...prev, botesMaestro: botes.filter((x) => x.id !== boteId) }
    })
  }, [])

  const value = useMemo(
    () => ({
      db,
      setDb,
      upsertOperacion,
      deleteOperacion,
      updateOperacion,
      upsertBoteMaestro,
      deleteBoteMaestro,
    }),
    [db, upsertOperacion, deleteOperacion, updateOperacion, upsertBoteMaestro, deleteBoteMaestro],
  )

  return <DbContext.Provider value={value}>{children}</DbContext.Provider>
}

export function useDb() {
  const ctx = useContext(DbContext)
  if (!ctx) throw new Error('DbProvider missing')
  return ctx
}
