function asArray(v) {
  return Array.isArray(v) ? v : []
}

export function serializeOperaciones(operaciones) {
  const ops = asArray(operaciones)
  return JSON.stringify(
    {
      version: 1,
      exportedAt: new Date().toISOString(),
      operaciones: ops,
    },
    null,
    2,
  )
}

export function parseOperacionesPayload(raw) {
  const text = String(raw || '')
  const data = JSON.parse(text)
  if (Array.isArray(data)) return data
  if (data && typeof data === 'object' && Array.isArray(data.operaciones)) return data.operaciones
  throw new Error('Formato inválido')
}

export function mergeOperacionesById(existing, incoming) {
  const cur = asArray(existing)
  const inc = asArray(incoming)
  const byId = new Map(cur.map((o) => [String(o?.id || ''), o]).filter(([id]) => id))
  inc.forEach((o) => {
    const id = String(o?.id || '')
    if (!id) return
    byId.set(id, o)
  })
  return Array.from(byId.values())
}

