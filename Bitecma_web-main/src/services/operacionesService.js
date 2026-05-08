export function getOperacionMetricas(op) {
  const botes = Array.isArray(op?.botes) ? op.botes : []
  const totalTx = botes.reduce((s, b) => s + ((b?.transectos || []).length || 0), 0)
  const totalLPMuestras = botes.reduce(
    (s, b) =>
      s +
      Object.values(b?.lpMuestras || {}).reduce((s2, entry) => {
        if (Array.isArray(entry)) return s2 + entry.length
        if (entry && typeof entry === 'object') {
          if (Array.isArray(entry.ms)) return s2 + entry.ms.length
          return (
            s2 +
            ['LP', 'L', 'D'].reduce((acc, k) => {
              const arr = entry?.[k]
              return acc + (Array.isArray(arr) ? arr.length : 0)
            }, 0)
          )
        }
        return s2
      }, 0),
    0,
  )
  return { totalTx, totalLPMuestras }
}

function opMatchesText(op, texto) {
  if (!texto) return true
  const botesText = (op?.botes || []).map((b) => `${b?.nombre || ''} ${b?.buzo || ''}`)
  const haystack = [
    op?.id || '',
    op?.sector || '',
    op?.org || '',
    op?.tipoOrg || '',
    ...botesText,
  ]
    .join(' ')
    .toLowerCase()
  return haystack.includes(texto)
}

export function filterOperaciones(operaciones, { sector = '', mes = '', texto = '' } = {}) {
  const ops = Array.isArray(operaciones) ? operaciones : []
  const q = String(texto || '').toLowerCase().trim()
  return ops.filter((op) => {
    if (sector && op?.sector !== sector) return false
    if (mes) {
      const fi = String(op?.fechaInicio || '')
      const ff = String(op?.fechaFin || '')
      if (!fi.startsWith(mes) && !ff.startsWith(mes)) return false
    }
    return opMatchesText(op, q)
  })
}
