<?php

class Operacion
{
    private static function normalizeZonaMuestreo($zona, $fallback = null)
    {
        $z = trim((string)($zona ?? ''));
        if ($z === '' && $fallback !== null) $z = trim((string)$fallback);
        return $z === '' ? null : $z;
    }

    private static function normalizeSubmarealFromBote($b)
    {
        $b = is_array($b) ? $b : [];
        if (array_key_exists('submareal', $b)) {
            $v = $b['submareal'];
            if ($v === true || $v === 1 || $v === '1') return 1;
            if ($v === false || $v === 0 || $v === '0') return 0;
            return $v ? 1 : 0;
        }
        if (array_key_exists('tipoMuestreo', $b)) {
            $t = strtolower(trim((string)$b['tipoMuestreo']));
            if ($t === 'intermareal') return 0;
            if ($t === 'submareal') return 1;
        }
        return 1;
    }

    private static function mapOperacionRow($r)
    {
        $caleta = $r['caleta'] ?? ($r['sector'] ?? null);
        return [
            'id' => $r['id'],
            'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'sector' => $caleta,
            'caleta' => $caleta,
            'caletaId' => array_key_exists('caleta_id', $r) && $r['caleta_id'] !== null ? (int)$r['caleta_id'] : null,
            'sectorAmerbId' => $r['sector_amerb_id'] !== null ? (int)$r['sector_amerb_id'] : null,
            'sectorAmerb' => $r['sector_amerb'] ?? null,
            'tipoOrg' => $r['tipo_org'] ?? null,
            'opaId' => $r['opa_id'] !== null ? (int)$r['opa_id'] : null,
            'org' => $r['org_nombre'] ?? null,
            'numSeg' => $r['num_seg_esba'] !== null ? (int)$r['num_seg_esba'] : null,
            'fechaInicio' => $r['fecha_inicio'] ?? null,
            'fechaFin' => $r['fecha_fin'] ?? null,
            'createdById' => array_key_exists('created_by', $r) && $r['created_by'] !== null ? (int)$r['created_by'] : null,
            'createdByName' => $r['created_by_nombre'] ?? null,
            'botes' => [],
        ];
    }

    private static function normalizeLpMuestras($lpMuestras)
    {
        $map = $lpMuestras && is_array($lpMuestras) ? $lpMuestras : [];
        $out = [];
        foreach ($map as $spIdRaw => $entry) {
            $spId = (int)$spIdRaw;
            if ($spId <= 0) continue;
            $bucket = [];
            if (is_array($entry)) {
                foreach ($entry as $m) {
                    if (!is_array($m)) continue;
                    if (array_key_exists('d', $m)) {
                        if (!isset($bucket['D'])) $bucket['D'] = [];
                        $bucket['D'][] = ['d' => isset($m['d']) ? (float)$m['d'] : null];
                    } else if (array_key_exists('p', $m)) {
                        if (!isset($bucket['LP'])) $bucket['LP'] = [];
                        $bucket['LP'][] = ['l' => isset($m['l']) ? (float)$m['l'] : null, 'p' => isset($m['p']) ? (float)$m['p'] : null];
                    } else {
                        if (!isset($bucket['L'])) $bucket['L'] = [];
                        $bucket['L'][] = ['l' => isset($m['l']) ? (float)$m['l'] : null];
                    }
                }
            } else if (is_array($entry) === false && is_object($entry)) {
                $entry = (array)$entry;
            }

            if (is_array($entry) && array_key_exists('ms', $entry) && is_array($entry['ms'])) {
                $k = strtoupper(trim((string)($entry['type'] ?? 'LP')));
                if ($k !== 'LP' && $k !== 'L' && $k !== 'D') $k = 'LP';
                $bucket[$k] = [];
                foreach ($entry['ms'] as $m) {
                    if (!is_array($m)) continue;
                    if ($k === 'LP') $bucket[$k][] = ['l' => isset($m['l']) ? (float)$m['l'] : null, 'p' => isset($m['p']) ? (float)$m['p'] : null];
                    else if ($k === 'D') $bucket[$k][] = ['d' => isset($m['d']) ? (float)$m['d'] : null];
                    else $bucket[$k][] = ['l' => isset($m['l']) ? (float)$m['l'] : null];
                }
            } else if (is_array($entry)) {
                foreach (['LP', 'L', 'D'] as $k) {
                    if (!array_key_exists($k, $entry) || !is_array($entry[$k])) continue;
                    if (!isset($bucket[$k])) $bucket[$k] = [];
                    foreach ($entry[$k] as $m) {
                        if (!is_array($m)) continue;
                        if ($k === 'LP') $bucket[$k][] = ['l' => isset($m['l']) ? (float)$m['l'] : null, 'p' => isset($m['p']) ? (float)$m['p'] : null];
                        else if ($k === 'D') $bucket[$k][] = ['d' => isset($m['d']) ? (float)$m['d'] : null];
                        else $bucket[$k][] = ['l' => isset($m['l']) ? (float)$m['l'] : null];
                    }
                }
            }

            if (!empty($bucket)) $out[$spId] = $bucket;
        }
        return $out;
    }

    private static function trxToUnidadInsert($t)
    {
        $t = is_array($t) ? $t : [];
        $tipo = isset($t['tipo']) && $t['tipo'] === 'cuadrante' ? 'cuadrante' : 'transecto';
        return [
            'num' => isset($t['num']) ? (int)$t['num'] : 0,
            'tipo' => $tipo,
            'area_m2' => array_key_exists('area', $t) && $t['area'] !== '' && $t['area'] !== null ? (float)$t['area'] : null,
            'fecha' => isset($t['fecha']) && $t['fecha'] !== '' ? (string)$t['fecha'] : null,
            'sustrato' => isset($t['sustrato']) && trim((string)$t['sustrato']) !== '' ? trim((string)$t['sustrato']) : null,
            'cubierta' => isset($t['cubierta']) && trim((string)$t['cubierta']) !== '' ? trim((string)$t['cubierta']) : null,
            'especie_id' => $tipo === 'cuadrante' && isset($t['especieId']) && $t['especieId'] !== '' ? (int)$t['especieId'] : null,
            'coord_x' => isset($t['coordX']) && $t['coordX'] !== '' ? (float)$t['coordX'] : null,
            'coord_y' => isset($t['coordY']) && $t['coordY'] !== '' ? (float)$t['coordY'] : null,
            'coord_long' => isset($t['coordLong']) && $t['coordLong'] !== '' ? (float)$t['coordLong'] : null,
            'coord_lat' => isset($t['coordLat']) && $t['coordLat'] !== '' ? (float)$t['coordLat'] : null,
            'datum' => isset($t['datum']) && trim((string)$t['datum']) !== '' ? trim((string)$t['datum']) : 'WGS 84',
            'counts' => isset($t['counts']) && is_array($t['counts']) ? $t['counts'] : [],
        ];
    }

    public static function all(PDO $db)
    {
        $stmt = $db->query(
            "SELECT o.id, o.region_id, o.caleta, o.caleta_id, o.sector_amerb_id, o.sector_amerb, o.tipo_org, o.opa_id, o.org_nombre, o.num_seg_esba, o.fecha_inicio, o.fecha_fin,
                    o.created_by, u.nombre AS created_by_nombre
             FROM operaciones o
             LEFT JOIN usuarios u ON u.id = o.created_by
             ORDER BY o.created_at DESC"
        );
        $opsRows = $stmt->fetchAll();
        $ops = [];
        $opIds = [];
        foreach ($opsRows as $r) {
            $op = self::mapOperacionRow($r);
            $ops[$op['id']] = $op;
            $opIds[] = (string)$op['id'];
        }
        if (!$opIds) return [];

        $ph = implode(',', array_fill(0, count($opIds), '?'));
        $stmtB = $db->prepare(
            "SELECT id, operacion_id, zona_muestreo, bote_maestro_id, nombre_bote, buzo, dens_tipo, submareal
             FROM operacion_botes
             WHERE operacion_id IN ($ph)
             ORDER BY operacion_id ASC,
                      (zona_muestreo REGEXP '^[0-9]+$') DESC,
                      CASE WHEN zona_muestreo REGEXP '^[0-9]+$' THEN CAST(zona_muestreo AS UNSIGNED) END ASC,
                      zona_muestreo ASC"
        );
        $stmtB->execute($opIds);
        $boatsRows = $stmtB->fetchAll();

        $boatById = [];
        $boatIds = [];
        foreach ($boatsRows as $b) {
            $bid = (int)$b['id'];
            $opId = (string)$b['operacion_id'];
            $zona = self::normalizeZonaMuestreo($b['zona_muestreo'] ?? null);
            $boat = [
                'id' => 'OB-' . $bid,
                'zona' => $zona,
                'nombre' => $b['nombre_bote'],
                'buzo' => $b['buzo'],
                'densTipo' => $b['dens_tipo'],
                'submareal' => $b['submareal'] !== null ? (int)$b['submareal'] : 1,
                'boteMaestroId' => $b['bote_maestro_id'] !== null ? (int)$b['bote_maestro_id'] : null,
                'lpMuestras' => [],
                'transectos' => [],
            ];
            if (!isset($ops[$opId])) continue;
            $ops[$opId]['botes'][] = $boat;
            $boatById[$bid] = &$ops[$opId]['botes'][count($ops[$opId]['botes']) - 1];
            $boatIds[] = $bid;
        }

        if ($boatIds) {
            $phB = implode(',', array_fill(0, count($boatIds), '?'));

            $stmtU = $db->prepare(
                "SELECT id, operacion_bote_id, num, tipo, area_m2, fecha, sustrato, cubierta, especie_id, coord_x, coord_y, coord_long, coord_lat, datum
                 FROM densidad_unidades
                 WHERE operacion_bote_id IN ($phB)
                 ORDER BY operacion_bote_id ASC, num ASC"
            );
            $stmtU->execute($boatIds);
            $unitsRows = $stmtU->fetchAll();

            $unitIds = [];
            $unitById = [];
            foreach ($unitsRows as $u) {
                $uid = (int)$u['id'];
                $bid = (int)$u['operacion_bote_id'];
                if (!isset($boatById[$bid])) continue;
                $t = [
                    'num' => (int)$u['num'],
                    'tipo' => $u['tipo'],
                    'area' => $u['area_m2'] !== null ? (float)$u['area_m2'] : null,
                    'fecha' => $u['fecha'] ?? null,
                    'sustrato' => $u['sustrato'] ?? null,
                    'cubierta' => $u['cubierta'] ?? null,
                    'counts' => [],
                ];
                if ($u['tipo'] === 'cuadrante' && $u['especie_id'] !== null) {
                    $t['especieId'] = (int)$u['especie_id'];
                }
                if ($u['coord_x'] !== null) $t['coordX'] = (float)$u['coord_x'];
                if ($u['coord_y'] !== null) $t['coordY'] = (float)$u['coord_y'];
                if ($u['coord_long'] !== null) $t['coordLong'] = (float)$u['coord_long'];
                if ($u['coord_lat'] !== null) $t['coordLat'] = (float)$u['coord_lat'];
                if ($u['datum'] !== null) $t['datum'] = $u['datum'];

                $boatById[$bid]['transectos'][] = $t;
                $unitById[$uid] = &$boatById[$bid]['transectos'][count($boatById[$bid]['transectos']) - 1];
                $unitIds[] = $uid;
            }

            if ($unitIds) {
                $phU = implode(',', array_fill(0, count($unitIds), '?'));
                $stmtC = $db->prepare(
                    "SELECT unidad_id, especie_id, cantidad
                     FROM densidad_unidad_counts
                     WHERE unidad_id IN ($phU)"
                );
                $stmtC->execute($unitIds);
                $countsRows = $stmtC->fetchAll();
                foreach ($countsRows as $c) {
                    $uid = (int)$c['unidad_id'];
                    if (!isset($unitById[$uid])) continue;
                    $unitById[$uid]['counts'][(int)$c['especie_id']] = (int)$c['cantidad'];
                }
            }

            $stmtM = $db->prepare(
                "SELECT operacion_bote_id, especie_id, kind, longitud_mm, peso_g, diam_disco_cm
                 FROM muestras
                 WHERE operacion_bote_id IN ($phB)
                 ORDER BY operacion_bote_id ASC, especie_id ASC, id ASC"
            );
            $stmtM->execute($boatIds);
            $mRows = $stmtM->fetchAll();
            foreach ($mRows as $m) {
                $bid = (int)$m['operacion_bote_id'];
                if (!isset($boatById[$bid])) continue;
                $sp = (int)$m['especie_id'];
                $k = strtoupper(trim((string)$m['kind']));
                if ($k !== 'LP' && $k !== 'L' && $k !== 'D') $k = 'L';

                $entry = $boatById[$bid]['lpMuestras'];
                if (!isset($entry[$sp]) || !is_array($entry[$sp])) $entry[$sp] = [];
                if (!isset($entry[$sp][$k]) || !is_array($entry[$sp][$k])) $entry[$sp][$k] = [];
                if ($k === 'LP') {
                    $entry[$sp][$k][] = [
                        'l' => $m['longitud_mm'] !== null ? (float)$m['longitud_mm'] : null,
                        'p' => $m['peso_g'] !== null ? (float)$m['peso_g'] : null,
                    ];
                } else if ($k === 'D') {
                    $entry[$sp][$k][] = [
                        'd' => $m['diam_disco_cm'] !== null ? (float)$m['diam_disco_cm'] : null,
                    ];
                } else {
                    $entry[$sp][$k][] = [
                        'l' => $m['longitud_mm'] !== null ? (float)$m['longitud_mm'] : null,
                    ];
                }
                $boatById[$bid]['lpMuestras'] = $entry;
            }
        }

        return array_values($ops);
    }

    public static function find(PDO $db, $id)
    {
        $stmt = $db->prepare(
            "SELECT o.id, o.region_id, o.caleta, o.caleta_id, o.sector_amerb_id, o.sector_amerb, o.tipo_org, o.opa_id, o.org_nombre, o.num_seg_esba, o.fecha_inicio, o.fecha_fin,
                    o.created_by, u.nombre AS created_by_nombre
             FROM operaciones o
             LEFT JOIN usuarios u ON u.id = o.created_by
             WHERE o.id = :id
             LIMIT 1"
        );
        $stmt->execute([':id' => (string)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        $op = self::mapOperacionRow($r);

        $stmtB = $db->prepare(
            "SELECT id, zona_muestreo, bote_maestro_id, nombre_bote, buzo, dens_tipo, submareal
             FROM operacion_botes
             WHERE operacion_id = :id
             ORDER BY (zona_muestreo REGEXP '^[0-9]+$') DESC,
                      CASE WHEN zona_muestreo REGEXP '^[0-9]+$' THEN CAST(zona_muestreo AS UNSIGNED) END ASC,
                      zona_muestreo ASC"
        );
        $stmtB->execute([':id' => (string)$id]);
        $boatsRows = $stmtB->fetchAll();

        $boatIds = [];
        $boatIndexById = [];
        foreach ($boatsRows as $b) {
            $bid = (int)$b['id'];
            $zona = self::normalizeZonaMuestreo($b['zona_muestreo'] ?? null);
            $op['botes'][] = [
                'id' => 'OB-' . $bid,
                'zona' => $zona,
                'nombre' => $b['nombre_bote'],
                'buzo' => $b['buzo'],
                'densTipo' => $b['dens_tipo'],
                'submareal' => $b['submareal'] !== null ? (int)$b['submareal'] : 1,
                'boteMaestroId' => $b['bote_maestro_id'] !== null ? (int)$b['bote_maestro_id'] : null,
                'lpMuestras' => [],
                'transectos' => [],
            ];
            $boatIndexById[$bid] = count($op['botes']) - 1;
            $boatIds[] = $bid;
        }

        if (!$boatIds) return $op;
        $phB = implode(',', array_fill(0, count($boatIds), '?'));

        $stmtU = $db->prepare(
            "SELECT id, operacion_bote_id, num, tipo, area_m2, fecha, sustrato, cubierta, especie_id, coord_x, coord_y, coord_long, coord_lat, datum
             FROM densidad_unidades
             WHERE operacion_bote_id IN ($phB)
             ORDER BY operacion_bote_id ASC, num ASC"
        );
        $stmtU->execute($boatIds);
        $unitsRows = $stmtU->fetchAll();

        $unitIds = [];
        $unitRef = [];
        foreach ($unitsRows as $u) {
            $uid = (int)$u['id'];
            $bid = (int)$u['operacion_bote_id'];
            if (!isset($boatIndexById[$bid])) continue;
            $idx = $boatIndexById[$bid];
            $t = [
                'num' => (int)$u['num'],
                'tipo' => $u['tipo'],
                'area' => $u['area_m2'] !== null ? (float)$u['area_m2'] : null,
                'fecha' => $u['fecha'] ?? null,
                'sustrato' => $u['sustrato'] ?? null,
                'cubierta' => $u['cubierta'] ?? null,
                'counts' => [],
            ];
            if ($u['tipo'] === 'cuadrante' && $u['especie_id'] !== null) $t['especieId'] = (int)$u['especie_id'];
            if ($u['coord_x'] !== null) $t['coordX'] = (float)$u['coord_x'];
            if ($u['coord_y'] !== null) $t['coordY'] = (float)$u['coord_y'];
            if ($u['coord_long'] !== null) $t['coordLong'] = (float)$u['coord_long'];
            if ($u['coord_lat'] !== null) $t['coordLat'] = (float)$u['coord_lat'];
            if ($u['datum'] !== null) $t['datum'] = $u['datum'];

            $op['botes'][$idx]['transectos'][] = $t;
            $unitRef[$uid] = [$idx, count($op['botes'][$idx]['transectos']) - 1];
            $unitIds[] = $uid;
        }

        if ($unitIds) {
            $phU = implode(',', array_fill(0, count($unitIds), '?'));
            $stmtC = $db->prepare(
                "SELECT unidad_id, especie_id, cantidad
                 FROM densidad_unidad_counts
                 WHERE unidad_id IN ($phU)"
            );
            $stmtC->execute($unitIds);
            $countsRows = $stmtC->fetchAll();
            foreach ($countsRows as $c) {
                $uid = (int)$c['unidad_id'];
                if (!isset($unitRef[$uid])) continue;
                [$bIdx, $tIdx] = $unitRef[$uid];
                $op['botes'][$bIdx]['transectos'][$tIdx]['counts'][(int)$c['especie_id']] = (int)$c['cantidad'];
            }
        }

        $stmtM = $db->prepare(
            "SELECT operacion_bote_id, especie_id, kind, longitud_mm, peso_g, diam_disco_cm
             FROM muestras
             WHERE operacion_bote_id IN ($phB)
             ORDER BY operacion_bote_id ASC, especie_id ASC, id ASC"
        );
        $stmtM->execute($boatIds);
        $mRows = $stmtM->fetchAll();
        foreach ($mRows as $m) {
            $bid = (int)$m['operacion_bote_id'];
            if (!isset($boatIndexById[$bid])) continue;
            $bIdx = $boatIndexById[$bid];
            $sp = (int)$m['especie_id'];
            $k = strtoupper(trim((string)$m['kind']));
            if ($k !== 'LP' && $k !== 'L' && $k !== 'D') $k = 'L';

            if (!isset($op['botes'][$bIdx]['lpMuestras'][$sp])) $op['botes'][$bIdx]['lpMuestras'][$sp] = [];
            if (!isset($op['botes'][$bIdx]['lpMuestras'][$sp][$k])) $op['botes'][$bIdx]['lpMuestras'][$sp][$k] = [];

            if ($k === 'LP') {
                $op['botes'][$bIdx]['lpMuestras'][$sp][$k][] = [
                    'l' => $m['longitud_mm'] !== null ? (float)$m['longitud_mm'] : null,
                    'p' => $m['peso_g'] !== null ? (float)$m['peso_g'] : null,
                ];
            } else if ($k === 'D') {
                $op['botes'][$bIdx]['lpMuestras'][$sp][$k][] = [
                    'd' => $m['diam_disco_cm'] !== null ? (float)$m['diam_disco_cm'] : null,
                ];
            } else {
                $op['botes'][$bIdx]['lpMuestras'][$sp][$k][] = [
                    'l' => $m['longitud_mm'] !== null ? (float)$m['longitud_mm'] : null,
                ];
            }
        }

        return $op;
    }

    public static function create(PDO $db, array $data, $createdBy = null)
    {
        $id = trim((string)($data['id'] ?? ''));
        if ($id === '') return ['error' => 'id requerido'];
        $caleta = trim((string)($data['caleta'] ?? $data['sector'] ?? ''));
        if ($caleta === '') return ['error' => 'caleta requerida'];

        $stmtE = $db->prepare("SELECT id FROM operaciones WHERE id = :id LIMIT 1");
        $stmtE->execute([':id' => $id]);
        if ($stmtE->fetch()) return ['error' => 'id ya existe'];

        $stmt = $db->prepare(
            "INSERT INTO operaciones (
                id, region_id, caleta_id, caleta, sector_amerb_id, sector_amerb, tipo_org, opa_id, org_nombre, num_seg_esba, fecha_inicio, fecha_fin, created_by
             ) VALUES (
                :id, :region_id, :caleta_id, :caleta, :sector_amerb_id, :sector_amerb, :tipo_org, :opa_id, :org_nombre, :num_seg_esba, :fecha_inicio, :fecha_fin, :created_by
             )"
        );
        $stmt->execute([
            ':id' => $id,
            ':region_id' => isset($data['region']) && $data['region'] !== '' ? (int)$data['region'] : null,
            ':caleta_id' => isset($data['caletaId']) && $data['caletaId'] !== '' ? (int)$data['caletaId'] : (isset($data['caleta_id']) && $data['caleta_id'] !== '' ? (int)$data['caleta_id'] : null),
            ':caleta' => $caleta,
            ':sector_amerb_id' => isset($data['sectorAmerbId']) && $data['sectorAmerbId'] !== '' && $data['sectorAmerbId'] !== 'custom' ? (int)$data['sectorAmerbId'] : null,
            ':sector_amerb' => trim((string)($data['sectorAmerb'] ?? '')) ?: null,
            ':tipo_org' => trim((string)($data['tipoOrg'] ?? '')) ?: null,
            ':opa_id' => isset($data['opaId']) && $data['opaId'] !== '' && $data['opaId'] !== 'custom' ? (int)$data['opaId'] : null,
            ':org_nombre' => trim((string)($data['org'] ?? $data['orgNombre'] ?? '')) ?: null,
            ':num_seg_esba' => isset($data['numSeg']) && $data['numSeg'] !== '' ? (int)$data['numSeg'] : null,
            ':fecha_inicio' => trim((string)($data['fechaInicio'] ?? '')) ?: null,
            ':fecha_fin' => trim((string)($data['fechaFin'] ?? '')) ?: null,
            ':created_by' => $createdBy !== null ? (int)$createdBy : null,
        ]);

        self::upsertChildren($db, $id, $data);
        return self::find($db, $id);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;

        $caleta = array_key_exists('caleta', $data) || array_key_exists('sector', $data)
            ? trim((string)($data['caleta'] ?? $data['sector'] ?? ''))
            : (string)($cur['caleta'] ?? $cur['sector'] ?? '');
        if ($caleta === '') return ['error' => 'caleta requerida'];

        $stmt = $db->prepare(
            "UPDATE operaciones SET
                region_id = :region_id,
                caleta_id = :caleta_id,
                caleta = :caleta,
                sector_amerb_id = :sector_amerb_id,
                sector_amerb = :sector_amerb,
                tipo_org = :tipo_org,
                opa_id = :opa_id,
                org_nombre = :org_nombre,
                num_seg_esba = :num_seg_esba,
                fecha_inicio = :fecha_inicio,
                fecha_fin = :fecha_fin
             WHERE id = :id"
        );
        $stmt->execute([
            ':id' => (string)$id,
            ':region_id' => array_key_exists('region', $data) ? ($data['region'] !== '' ? (int)$data['region'] : null) : $cur['region'],
            ':caleta_id' => array_key_exists('caletaId', $data) || array_key_exists('caleta_id', $data)
                ? ((isset($data['caletaId']) && $data['caletaId'] !== '' ? (int)$data['caletaId'] : (isset($data['caleta_id']) && $data['caleta_id'] !== '' ? (int)$data['caleta_id'] : null)))
                : ($cur['caletaId'] ?? null),
            ':caleta' => $caleta,
            ':sector_amerb_id' => array_key_exists('sectorAmerbId', $data)
                ? ($data['sectorAmerbId'] !== '' && $data['sectorAmerbId'] !== 'custom' ? (int)$data['sectorAmerbId'] : null)
                : $cur['sectorAmerbId'],
            ':sector_amerb' => array_key_exists('sectorAmerb', $data) ? (trim((string)$data['sectorAmerb']) ?: null) : $cur['sectorAmerb'],
            ':tipo_org' => array_key_exists('tipoOrg', $data) ? (trim((string)$data['tipoOrg']) ?: null) : $cur['tipoOrg'],
            ':opa_id' => array_key_exists('opaId', $data)
                ? ($data['opaId'] !== '' && $data['opaId'] !== 'custom' ? (int)$data['opaId'] : null)
                : $cur['opaId'],
            ':org_nombre' => array_key_exists('org', $data) || array_key_exists('orgNombre', $data)
                ? (trim((string)($data['org'] ?? $data['orgNombre'] ?? '')) ?: null)
                : $cur['org'],
            ':num_seg_esba' => array_key_exists('numSeg', $data) ? ($data['numSeg'] !== '' ? (int)$data['numSeg'] : null) : $cur['numSeg'],
            ':fecha_inicio' => array_key_exists('fechaInicio', $data) ? (trim((string)$data['fechaInicio']) ?: null) : $cur['fechaInicio'],
            ':fecha_fin' => array_key_exists('fechaFin', $data) ? (trim((string)$data['fechaFin']) ?: null) : $cur['fechaFin'],
        ]);

        self::syncChildren($db, $id, $data);

        return self::find($db, $id);
    }

    private static function deleteBoatUnidadesYCounts(PDO $db, array $boatIds)
    {
        $boatIds = array_values(array_filter(array_map(function ($x) {
            return (int)$x;
        }, $boatIds), function ($x) {
            return $x > 0;
        }));

        if (!$boatIds) return;

        $phB = implode(',', array_fill(0, count($boatIds), '?'));
        $stmtU = $db->prepare("SELECT id FROM densidad_unidades WHERE operacion_bote_id IN ($phB)");
        $stmtU->execute($boatIds);
        $unitIds = array_map(function ($r) {
            return (int)$r['id'];
        }, $stmtU->fetchAll());
        $unitIds = array_values(array_filter($unitIds, function ($x) {
            return $x > 0;
        }));

        if ($unitIds) {
            $phU = implode(',', array_fill(0, count($unitIds), '?'));
            $stmtDelCounts = $db->prepare("DELETE FROM densidad_unidad_counts WHERE unidad_id IN ($phU)");
            $stmtDelCounts->execute($unitIds);
        }

        $stmtDelUnidades = $db->prepare("DELETE FROM densidad_unidades WHERE operacion_bote_id IN ($phB)");
        $stmtDelUnidades->execute($boatIds);
    }

    private static function deleteBoatMuestras(PDO $db, array $boatIds)
    {
        $boatIds = array_values(array_filter(array_map(function ($x) {
            return (int)$x;
        }, $boatIds), function ($x) {
            return $x > 0;
        }));

        if (!$boatIds) return;

        $phB = implode(',', array_fill(0, count($boatIds), '?'));
        $stmtDelMuestras = $db->prepare("DELETE FROM muestras WHERE operacion_bote_id IN ($phB)");
        $stmtDelMuestras->execute($boatIds);
    }

    private static function deleteBoatDetails(PDO $db, array $boatIds)
    {
        self::deleteBoatUnidadesYCounts($db, $boatIds);
        self::deleteBoatMuestras($db, $boatIds);
    }

    private static function deleteChildren(PDO $db, $opId)
    {
        $opId = (string)$opId;
        if ($opId === '') return;

        $stmtB = $db->prepare("SELECT id FROM operacion_botes WHERE operacion_id = :id");
        $stmtB->execute([':id' => $opId]);
        $boatIds = array_map(function ($r) {
            return (int)$r['id'];
        }, $stmtB->fetchAll());

        self::deleteBoatDetails($db, $boatIds);

        $db->prepare("DELETE FROM operacion_botes WHERE operacion_id = :id")->execute([':id' => $opId]);
    }

    private static function syncChildren(PDO $db, $opId, array $data)
    {
        if (!array_key_exists('botes', $data)) return;
        $botes = isset($data['botes']) && is_array($data['botes']) ? $data['botes'] : [];

        $stmtSel = $db->prepare("SELECT id, zona_muestreo FROM operacion_botes WHERE operacion_id = :id");
        $stmtSel->execute([':id' => (string)$opId]);
        $rows = $stmtSel->fetchAll();
        $existentesPorZona = [];
        foreach ($rows as $r) {
            $zona = self::normalizeZonaMuestreo($r['zona_muestreo'] ?? null);
            if ($zona === null) continue;
            $existentesPorZona[$zona] = (int)$r['id'];
        }

        $stmtUpdB = $db->prepare(
            "UPDATE operacion_botes
             SET zona_muestreo = :zona,
                 bote_maestro_id = :bote_maestro_id,
                 nombre_bote = :nombre_bote,
                 buzo = :buzo,
                 dens_tipo = :dens_tipo,
                 submareal = :submareal
             WHERE id = :id"
        );
        $stmtInsB = $db->prepare(
            "INSERT INTO operacion_botes (operacion_id, zona_muestreo, bote_maestro_id, nombre_bote, buzo, dens_tipo, submareal)
             VALUES (:operacion_id, :zona, :bote_maestro_id, :nombre_bote, :buzo, :dens_tipo, :submareal)"
        );

        $stmtU = $db->prepare(
            "INSERT INTO densidad_unidades (operacion_bote_id, num, tipo, area_m2, fecha, sustrato, cubierta, especie_id, coord_x, coord_y, coord_long, coord_lat, datum)
             VALUES (:operacion_bote_id, :num, :tipo, :area_m2, :fecha, :sustrato, :cubierta, :especie_id, :coord_x, :coord_y, :coord_long, :coord_lat, :datum)"
        );
        $stmtC = $db->prepare(
            "INSERT INTO densidad_unidad_counts (unidad_id, especie_id, cantidad)
             VALUES (:unidad_id, :especie_id, :cantidad)"
        );
        $stmtM = $db->prepare(
            "INSERT INTO muestras (operacion_bote_id, especie_id, kind, longitud_mm, peso_g, diam_disco_cm)
             VALUES (:operacion_bote_id, :especie_id, :kind, :longitud_mm, :peso_g, :diam_disco_cm)"
        );

        $zonasEntrantes = [];

        foreach ($botes as $i => $b) {
            $b = is_array($b) ? $b : [];
            $zona = self::normalizeZonaMuestreo($b['zona'] ?? null, (string)($i + 1));
            if ($zona === null) continue;
            $zonasEntrantes[$zona] = true;

            $submareal = self::normalizeSubmarealFromBote($b);
            $nombreBote = trim((string)($b['nombre'] ?? $b['nombre_bote'] ?? ''));
            if ($submareal === 0) $nombreBote = 'Intermareal';
            if ($submareal === 1 && $nombreBote === '') continue;
            $buzo = trim((string)($b['buzo'] ?? ''));
            $densTipo = isset($b['densTipo']) && $b['densTipo'] === 'cuadrante' ? 'cuadrante' : 'transecto';
            $boteMaestroId = $submareal === 1 && isset($b['boteMaestroId']) && $b['boteMaestroId'] !== '' ? (int)$b['boteMaestroId'] : null;

            $opBoteId = null;
            if (isset($existentesPorZona[$zona])) {
                $opBoteId = (int)$existentesPorZona[$zona];
                $stmtUpdB->execute([
                    ':id' => $opBoteId,
                    ':zona' => $zona,
                    ':bote_maestro_id' => $boteMaestroId,
                    ':nombre_bote' => $nombreBote,
                    ':buzo' => $buzo,
                    ':dens_tipo' => $densTipo,
                    ':submareal' => $submareal,
                ]);
            } else {
                $stmtInsB->execute([
                    ':operacion_id' => (string)$opId,
                    ':zona' => $zona,
                    ':bote_maestro_id' => $boteMaestroId,
                    ':nombre_bote' => $nombreBote,
                    ':buzo' => $buzo,
                    ':dens_tipo' => $densTipo,
                    ':submareal' => $submareal,
                ]);
                $opBoteId = (int)$db->lastInsertId();
            }

            if (array_key_exists('transectos', $b)) {
                self::deleteBoatUnidadesYCounts($db, [$opBoteId]);
                $transectos = isset($b['transectos']) && is_array($b['transectos']) ? $b['transectos'] : [];
                foreach ($transectos as $tRaw) {
                    $t = self::trxToUnidadInsert(is_array($tRaw) ? $tRaw : []);
                    if ($t['num'] <= 0) continue;
                    $stmtU->execute([
                        ':operacion_bote_id' => $opBoteId,
                        ':num' => $t['num'],
                        ':tipo' => $t['tipo'],
                        ':area_m2' => $t['area_m2'],
                        ':fecha' => $t['fecha'],
                        ':sustrato' => $t['sustrato'],
                        ':cubierta' => $t['cubierta'],
                        ':especie_id' => $t['especie_id'],
                        ':coord_x' => $t['coord_x'],
                        ':coord_y' => $t['coord_y'],
                        ':coord_long' => $t['coord_long'],
                        ':coord_lat' => $t['coord_lat'],
                        ':datum' => $t['datum'],
                    ]);
                    $unidadId = (int)$db->lastInsertId();
                    foreach ($t['counts'] as $spIdRaw => $cantRaw) {
                        $spId = (int)$spIdRaw;
                        if ($spId <= 0) continue;
                        $cant = (int)$cantRaw;
                        $stmtC->execute([
                            ':unidad_id' => $unidadId,
                            ':especie_id' => $spId,
                            ':cantidad' => $cant,
                        ]);
                    }
                }
            }

            if (array_key_exists('lpMuestras', $b)) {
                $stmtDelM = $db->prepare("DELETE FROM muestras WHERE operacion_bote_id = :id");
                $stmtDelM->execute([':id' => $opBoteId]);

                $lp = self::normalizeLpMuestras(isset($b['lpMuestras']) ? $b['lpMuestras'] : null);
                foreach ($lp as $spId => $entry) {
                    foreach ($entry as $kind => $arr) {
                        if (!is_array($arr)) continue;
                        $k = strtoupper(trim((string)$kind));
                        if ($k !== 'LP' && $k !== 'L' && $k !== 'D') continue;
                        foreach ($arr as $m) {
                            $m = is_array($m) ? $m : [];
                            $l = array_key_exists('l', $m) && $m['l'] !== null && $m['l'] !== '' ? (float)$m['l'] : null;
                            $p = array_key_exists('p', $m) && $m['p'] !== null && $m['p'] !== '' ? (float)$m['p'] : null;
                            $d = array_key_exists('d', $m) && $m['d'] !== null && $m['d'] !== '' ? (float)$m['d'] : null;

                            if ($k === 'LP' && ($l === null || $p === null)) continue;
                            if ($k === 'L' && $l === null) continue;
                            if ($k === 'D' && $d === null) continue;

                            $stmtM->execute([
                                ':operacion_bote_id' => $opBoteId,
                                ':especie_id' => (int)$spId,
                                ':kind' => $k,
                                ':longitud_mm' => $k === 'D' ? null : $l,
                                ':peso_g' => $k === 'LP' ? $p : null,
                                ':diam_disco_cm' => $k === 'D' ? $d : null,
                            ]);
                        }
                    }
                }
            }
        }

        $idsAEliminar = [];
        foreach ($existentesPorZona as $zona => $boteId) {
            if (!isset($zonasEntrantes[$zona])) $idsAEliminar[] = (int)$boteId;
        }

        if ($idsAEliminar) {
            self::deleteBoatDetails($db, $idsAEliminar);
            $ph = implode(',', array_fill(0, count($idsAEliminar), '?'));
            $stmtDelB = $db->prepare("DELETE FROM operacion_botes WHERE id IN ($ph)");
            $stmtDelB->execute($idsAEliminar);
        }
    }

    private static function upsertChildren(PDO $db, $opId, array $data)
    {
        $botes = isset($data['botes']) && is_array($data['botes']) ? $data['botes'] : [];

        $stmtB = $db->prepare(
            "INSERT INTO operacion_botes (operacion_id, zona_muestreo, bote_maestro_id, nombre_bote, buzo, dens_tipo, submareal)
             VALUES (:operacion_id, :zona, :bote_maestro_id, :nombre_bote, :buzo, :dens_tipo, :submareal)"
        );
        $stmtU = $db->prepare(
            "INSERT INTO densidad_unidades (operacion_bote_id, num, tipo, area_m2, fecha, sustrato, cubierta, especie_id, coord_x, coord_y, coord_long, coord_lat, datum)
             VALUES (:operacion_bote_id, :num, :tipo, :area_m2, :fecha, :sustrato, :cubierta, :especie_id, :coord_x, :coord_y, :coord_long, :coord_lat, :datum)"
        );
        $stmtC = $db->prepare(
            "INSERT INTO densidad_unidad_counts (unidad_id, especie_id, cantidad)
             VALUES (:unidad_id, :especie_id, :cantidad)"
        );
        $stmtM = $db->prepare(
            "INSERT INTO muestras (operacion_bote_id, especie_id, kind, longitud_mm, peso_g, diam_disco_cm)
             VALUES (:operacion_bote_id, :especie_id, :kind, :longitud_mm, :peso_g, :diam_disco_cm)"
        );

        foreach ($botes as $i => $b) {
            $b = is_array($b) ? $b : [];
            $zona = self::normalizeZonaMuestreo($b['zona'] ?? null, (string)($i + 1));
            $submareal = self::normalizeSubmarealFromBote($b);
            $nombreBote = trim((string)($b['nombre'] ?? $b['nombre_bote'] ?? ''));
            if ($submareal === 0) $nombreBote = 'Intermareal';
            if ($submareal === 1 && $nombreBote === '') continue;
            $buzo = trim((string)($b['buzo'] ?? ''));
            $densTipo = isset($b['densTipo']) && $b['densTipo'] === 'cuadrante' ? 'cuadrante' : 'transecto';
            $boteMaestroId = $submareal === 1 && isset($b['boteMaestroId']) && $b['boteMaestroId'] !== '' ? (int)$b['boteMaestroId'] : null;

            $stmtB->execute([
                ':operacion_id' => (string)$opId,
                ':zona' => $zona,
                ':bote_maestro_id' => $boteMaestroId,
                ':nombre_bote' => $nombreBote,
                ':buzo' => $buzo,
                ':dens_tipo' => $densTipo,
                ':submareal' => $submareal,
            ]);
            $opBoteId = (int)$db->lastInsertId();

            $transectos = isset($b['transectos']) && is_array($b['transectos']) ? $b['transectos'] : [];
            foreach ($transectos as $tRaw) {
                $t = self::trxToUnidadInsert(is_array($tRaw) ? $tRaw : []);
                if ($t['num'] <= 0) continue;
                $stmtU->execute([
                    ':operacion_bote_id' => $opBoteId,
                    ':num' => $t['num'],
                    ':tipo' => $t['tipo'],
                    ':area_m2' => $t['area_m2'],
                    ':fecha' => $t['fecha'],
                    ':sustrato' => $t['sustrato'],
                    ':cubierta' => $t['cubierta'],
                    ':especie_id' => $t['especie_id'],
                    ':coord_x' => $t['coord_x'],
                    ':coord_y' => $t['coord_y'],
                    ':coord_long' => $t['coord_long'],
                    ':coord_lat' => $t['coord_lat'],
                    ':datum' => $t['datum'],
                ]);
                $unidadId = (int)$db->lastInsertId();
                foreach ($t['counts'] as $spIdRaw => $cantRaw) {
                    $spId = (int)$spIdRaw;
                    if ($spId <= 0) continue;
                    $cant = (int)$cantRaw;
                    $stmtC->execute([
                        ':unidad_id' => $unidadId,
                        ':especie_id' => $spId,
                        ':cantidad' => $cant,
                    ]);
                }
            }

            $lp = self::normalizeLpMuestras(isset($b['lpMuestras']) ? $b['lpMuestras'] : null);
            foreach ($lp as $spId => $entry) {
                foreach ($entry as $kind => $arr) {
                    if (!is_array($arr)) continue;
                    $k = strtoupper(trim((string)$kind));
                    if ($k !== 'LP' && $k !== 'L' && $k !== 'D') continue;
                    foreach ($arr as $m) {
                        $m = is_array($m) ? $m : [];
                        $l = array_key_exists('l', $m) && $m['l'] !== null && $m['l'] !== '' ? (float)$m['l'] : null;
                        $p = array_key_exists('p', $m) && $m['p'] !== null && $m['p'] !== '' ? (float)$m['p'] : null;
                        $d = array_key_exists('d', $m) && $m['d'] !== null && $m['d'] !== '' ? (float)$m['d'] : null;

                        if ($k === 'LP' && ($l === null || $p === null)) continue;
                        if ($k === 'L' && $l === null) continue;
                        if ($k === 'D' && $d === null) continue;

                        $stmtM->execute([
                            ':operacion_bote_id' => $opBoteId,
                            ':especie_id' => (int)$spId,
                            ':kind' => $k,
                            ':longitud_mm' => $k === 'D' ? null : $l,
                            ':peso_g' => $k === 'LP' ? $p : null,
                            ':diam_disco_cm' => $k === 'D' ? $d : null,
                        ]);
                    }
                }
            }
        }
    }

    public static function delete(PDO $db, $id)
    {
        $opId = (string)$id;
        if ($opId === '') return false;

        $stmtExists = $db->prepare("SELECT id FROM operaciones WHERE id = :id LIMIT 1");
        $stmtExists->execute([':id' => $opId]);
        $found = $stmtExists->fetch();
        if (!$found) return false;

        self::deleteChildren($db, $opId);

        $stmtDelOp = $db->prepare("DELETE FROM operaciones WHERE id = :id");
        $stmtDelOp->execute([':id' => $opId]);
        return $stmtDelOp->rowCount() > 0;
    }
}
