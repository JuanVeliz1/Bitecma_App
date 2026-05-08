import { useMemo, useState } from 'react'
import { useDb } from '../context/dbContext.jsx'
import { filterOperaciones } from '../services/operacionesService.js'

export function useOperaciones() {
  const { db } = useDb()
  const ops = db?.operaciones || []

  const [sector, setSector] = useState('')
  const [mes, setMes] = useState('')
  const [texto, setTexto] = useState('')

  const sectores = useMemo(() => {
    const set = new Set()
    ops.forEach((o) => {
      if (o?.sector) set.add(o.sector)
    })
    return Array.from(set).sort((a, b) => a.localeCompare(b))
  }, [ops])

  const meses = useMemo(() => {
    const set = new Set()
    ops.forEach((o) => {
      const fi = String(o?.fechaInicio || '')
      if (/^\d{4}-\d{2}-\d{2}$/.test(fi)) set.add(fi.slice(0, 7))
    })
    return Array.from(set).sort((a, b) => b.localeCompare(a))
  }, [ops])

  const filtered = useMemo(
    () => filterOperaciones(ops, { sector, mes, texto }),
    [ops, sector, mes, texto],
  )

  return {
    operaciones: ops,
    filtered,
    sector,
    setSector,
    mes,
    setMes,
    texto,
    setTexto,
    sectores,
    meses,
  }
}

