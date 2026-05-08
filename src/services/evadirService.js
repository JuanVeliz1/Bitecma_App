export function fmtDMY(iso) {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return ''
  return iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4)
}

export function getEvadirResumenOperacion(op) {
  const botes = Array.isArray(op?.botes) ? op.botes : []
  const totalBotes = botes.length
  const totalTx = botes.reduce(
    (s, b) => s + ((b?.transectos || []).filter((t) => t?.tipo !== 'cuadrante').length || 0),
    0,
  )
  const totalCq = botes.reduce(
    (s, b) => s + ((b?.transectos || []).filter((t) => t?.tipo === 'cuadrante').length || 0),
    0,
  )

  let sumAreaLoco = 0
  let sumNLoco = 0
  let sumAreaErizo = 0
  let sumNErizo = 0
  botes.forEach((b) => {
    ;(b?.transectos || []).forEach((t) => {
      if (t?.counts?.[1] != null) {
        sumAreaLoco += t.area || 0
        sumNLoco += t.counts[1] || 0
      }
      if (t?.counts?.[5] != null) {
        sumAreaErizo += t.area || 0
        sumNErizo += t.counts[5] || 0
      }
    })
  })
  const denLoco = sumAreaLoco > 0 ? (sumNLoco / sumAreaLoco).toFixed(3) : '0.000'
  const denErizo = sumAreaErizo > 0 ? (sumNErizo / sumAreaErizo).toFixed(3) : '0.000'

  return {
    totalBotes,
    totalTx,
    totalCq,
    denLoco,
    denErizo,
  }
}

export function getEvadirRegistradosRows(operaciones) {
  const ops = Array.isArray(operaciones) ? operaciones : []
  return ops.map((op) => {
    const { totalBotes, totalTx, totalCq, denLoco, denErizo } = getEvadirResumenOperacion(op)
    return {
      id: op?.id,
      sector: op?.sector,
      numSeg: op?.numSeg,
      fechaInicio: op?.fechaInicio,
      totalBotes,
      totalTx,
      totalCq,
      denLoco,
      denErizo,
      estado: 'Borrador',
    }
  })
}

