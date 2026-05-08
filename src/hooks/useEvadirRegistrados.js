import { useMemo } from 'react'
import { getEvadirRegistradosRows } from '../services/evadirService.js'
import { useDb } from '../context/dbContext.jsx'

export function useEvadirRegistrados() {
  const { db } = useDb()
  const rows = useMemo(
    () => getEvadirRegistradosRows(db?.operaciones || []),
    [db?.operaciones],
  )
  return { rows }
}
