[OPEN] Debug session: sync-operaciones

# Resumen
- Sintoma: Android Studio muestra 14 operaciones, la web 11 y la app instalada en celular 28.
- Objetivo: identificar de donde salen las operaciones extra, por que se agregan y dejar sincronizada la app Android con la BD y la web.
- Restriccion: en la fase inicial no se modifica logica de negocio; primero se recolecta evidencia.

# Hipotesis iniciales
1. La app Android mezcla datos remotos con un cache local persistente y no limpia registros antiguos.
2. La app Android arranca con datos seed o demo y luego tambien sincroniza datos reales, duplicando o acumulando operaciones.
3. La web y Android consumen fuentes distintas: la web usa un dataset local o un endpoint diferente al de Android.
4. El proceso de sincronizacion Android inserta operaciones sin una clave de unicidad estable, por lo que cada sync agrega filas repetidas.
5. La app instalada en el celular conserva una base local de una instalacion previa o de pruebas y por eso muestra mas operaciones que Android Studio.

# Evidencia pendiente
- Revisar flujo de carga de operaciones en web.
- Revisar almacenamiento local y sync en Android.
- Revisar si existen datos semilla en ambos clientes.
- Revisar API y modelo de operaciones.

# Evidencia recolectada
- Web: actualmente no consulta la API; carga `OPERACIONES` desde `src/data/operaciones.js` via `src/context/dbContext.jsx`.
- Android: carga cache persistente al iniciar desde Room/SharedPreferences mediante `DataManager.loadCache`.
- Android: guarda cache persistente en la base `bitecma_local_cache.db`.
- Android: al sincronizar, `mergeServerOperations()` vuelve a agregar operaciones previas de BD que ya no vienen del servidor (`id !in serverIds`), por lo que conserva operaciones obsoletas en cache.
- Android: la URL base por defecto del APK es `https://bitecma.cl/api/`, salvo que Android Studio compile con la propiedad `BITECMA_API_BASE_URL`.
- Backend publico consultado en esta sesion: `https://bitecma.cl/api/operaciones` responde `{"ok":true,"data":[]}`.

# Hipotesis evaluadas
1. Cache local persistente en Android: compatible con la evidencia.
2. Datos seed/demo en Android para operaciones: no aparece una fuente seed activa para operaciones; solo hay catalogos estaticos legacy. Hipotesis debilitada.
3. Web y Android consumen fuentes distintas: confirmada para la web.
4. Sync Android agrega o conserva operaciones obsoletas: compatible con la evidencia en `mergeServerOperations`.
5. La app instalada conserva datos previos de otra instalacion o backend distinto: compatible con la evidencia.

# Verificacion por evidencia
| ID | Hipotesis | Estado | Evidencia |
|----|-----------|--------|-----------|
| A | Android mezcla cache persistente con datos remotos | CONFIRMADA | El log carga `operacionesBd: 14`, `operacionesLc: 0`, `dirtyOperacionIds: 0`, `pendingTextFiles: 0` desde cache local antes del sync. |
| B | Android usa datos seed/demo de operaciones | RECHAZADA | No hay fuente seed activa de operaciones en Android; las 14 vienen del cache persistido, no de datos demo. |
| C | Web y Android usan fuentes distintas | CONFIRMADA | La web lee `src/data/operaciones.js`, mientras Android usa API + cache local. |
| D | El merge Android preserva operaciones obsoletas que ya no vienen del servidor | CONFIRMADA | El log del merge indica `serverCount: 1`, `currentBdCount: 14` y preserva 13 IDs ausentes del snapshot remoto, dejando `finalBdCount: 14`. |
| E | La persistencia deberia desaparecer luego de sincronizar exitosamente | CONFIRMADA COMO REQUERIMIENTO | El comportamiento esperado del usuario es que la app quede alineada con BD/web y no retenga operaciones ya sincronizadas si el servidor no las devuelve. |

# Evidencia puntual
- Log `B`: `operacionesBd: 14`, `operacionesLc: 0`, `dirtyOperacionIds: 0`, `pendingTextFiles: 0`.
- Log `C`: `serverCount: 1`, `currentBdCount: 14`, `currentLcCount: 0`, `dirtyCount: 0`.
- Log `D`: se preservan los IDs `OP-2024-001`, `OP-2023-001`, `OP-2026-003`, `OP-2026-002`, `OP-2026-45`, `OP-2026-61`, `OP-2026-65`, `OP-2026-52`, `OP-2026-99`, `OP-2026-69`, `OP-2024-004`, `OP-2024-003`, `OP-2024-002` aun cuando el servidor solo devuelve `OP-2026-001`.

# Estado
- Estado actual: fix minimo aplicado en Android; pendiente verificacion post-fix con logs limpios.

# Fix aplicado
- `mergeServerOperations()` ya no reinyecta operaciones sincronizadas viejas que no aparecen en el snapshot remoto.
- Solo se preservan operaciones ausentes del servidor si siguen marcadas como `dirty` localmente.
- Se mantiene la instrumentacion para comparar el comportamiento post-fix.

# Iteracion actual
- El usuario reporta que post-fix la app aun muestra operaciones de mas.
- No se recibieron logs `post-fix` en la primera verificacion, por lo que se agrego instrumentacion extra sobre entrada/salida de sync, respuesta remota y abortos por sesion/red.
- Logs limpiados nuevamente para una nueva corrida.
