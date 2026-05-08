export const OPERACIONES = [
  {
    id: 'OP-2026-003',
    region: 4,
    sectorAmerb: 'LOS VILOS SECTOR C',
    sectorAmerbId: '259',
    sector: 'LAS CONCHAS',
    tipoOrg: 'STI',
    org: 'S.T.I. DE PESCADORES ARTESANALES Y BUZOS MARISCADORES EXTRACTORES DE PRODUCTOS DEL MAR, PROVINCIA DEL CHOAPA, CALETA LAS CONCHAS',
    opaId: '128',
    numSeg: 3,
    fechaInicio: '2026-03-12',
    fechaFin: '2026-03-12',
    botes: [
      {
        id: 'B1',
        nombre: 'PABLO FELIPE',
        buzo: 'C. MEDINA',
        zona: 1,
        densTipo: 'transecto',
        lpMuestras: {
          4: [{ l: 104, p: 88 }, { l: 97, p: 72 }, { l: 112, p: 95 }],
          13: [{ l: 52 }, { l: 49 }, { l: 55 }, { l: 50 }]
        },
        transectos: [
          { num: 1, tipo: 'transecto', area: 120, fecha: '2026-03-12', sustrato: 'ROCA', cubierta: 'ALGAS', counts: { 4: 7, 13: 18 } },
          { num: 2, tipo: 'transecto', area: 120, fecha: '2026-03-12', sustrato: 'PIEDRA ARENA', cubierta: '', counts: { 4: 5, 13: 11 } },
          { num: 3, tipo: 'transecto', area: 120, fecha: '2026-03-12', sustrato: 'ROCA', cubierta: 'BIVALVOS', counts: { 4: 9, 13: 6 } }
        ]
      },
      {
        id: 'B2',
        nombre: 'HAWAII',
        buzo: 'L. OLMOS',
        zona: 2,
        densTipo: 'transecto',
        lpMuestras: {
          4: [{ l: 101, p: 80 }, { l: 99, p: 77 }],
          1: [{ l: 118, p: 410 }, { l: 121, p: 435 }]
        },
        transectos: [
          { num: 1, tipo: 'transecto', area: 120, fecha: '2026-03-12', sustrato: 'ROCA', cubierta: '', counts: { 4: 3, 1: 2 } },
          { num: 2, tipo: 'transecto', area: 120, fecha: '2026-03-12', sustrato: 'ROCA', cubierta: 'ALGAS', counts: { 4: 6, 1: 1 } }
        ]
      }
    ]
  },
  {
    id: 'OP-2025-007',
    region: 10,
    sectorAmerb: 'BAHIA CHINCUI',
    sectorAmerbId: '912',
    sector: 'CALBUCO - LA VEGA',
    tipoOrg: 'ASOC',
    org: 'A.G. DE PESCADORES ARTESANALES FUERZA DEL SUR DE CALETA ANAHUAC (FUERZA DEL SUR A.G.)',
    opaId: '341',
    numSeg: 7,
    fechaInicio: '2025-11-08',
    fechaFin: '2025-11-08',
    botes: [
      {
        id: 'B1',
        nombre: 'MAR BRAVA',
        buzo: 'C. ROMERO',
        zona: 1,
        densTipo: 'cuadrante',
        lpMuestras: {
          14: [{ d: 92 }, { d: 88 }, { d: 96 }],
          3: [{ l: 74, p: 55 }, { l: 78, p: 61 }]
        },
        transectos: [
          { num: 1, tipo: 'cuadrante', area: 1, fecha: '2025-11-08', sustrato: 'ARENA', cubierta: '', counts: { 3: 14 }, especieId: 3 },
          { num: 2, tipo: 'cuadrante', area: 1, fecha: '2025-11-08', sustrato: 'ROCA', cubierta: 'ALGAS', counts: { 3: 9 }, especieId: 3 },
          { num: 3, tipo: 'cuadrante', area: 0.25, fecha: '2025-11-08', sustrato: 'ROCA', cubierta: '', counts: { 3: 6 }, especieId: 3 }
        ]
      }
    ]
  }
];

export default OPERACIONES
