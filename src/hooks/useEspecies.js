import { useMemo } from 'react'
import { useDb } from '../context/dbContext.jsx'

export function useEspecies() {
  const { db } = useDb()
  const especies = useMemo(() => {
    const arr = db?.especies
    return Array.isArray(arr) ? arr : []
  }, [db?.especies])
  return { especies }
}
