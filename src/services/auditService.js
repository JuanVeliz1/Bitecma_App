const AUDIT_KEY = 'bitecma_audit_v1'

function readAudit() {
  try {
    const raw = localStorage.getItem(AUDIT_KEY)
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function writeAudit(arr) {
  try {
    localStorage.setItem(AUDIT_KEY, JSON.stringify(arr))
  } catch {
    return
  }
}

export function getAuditEntries() {
  return readAudit()
}

export function addAuditEntry(entry) {
  const arr = readAudit()
  arr.unshift(entry)
  if (arr.length > 250) arr.length = 250
  writeAudit(arr)
}
