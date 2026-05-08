<?php

class Evaluacion
{
    private static function mapRow($r)
    {
        return [
            'id' => $r['id'],
            'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'sector' => $r['sector'],
            'sectorAmerbId' => $r['sector_amerb_id'] !== null ? (int)$r['sector_amerb_id'] : null,
            'sectorAmerb' => $r['sector_amerb'],
            'tipoOrg' => $r['tipo_org'],
            'opaId' => $r['opa_id'] !== null ? (int)$r['opa_id'] : null,
            'orgNombre' => $r['org_nombre'],
            'numSegEsba' => $r['num_seg_esba'] !== null ? (int)$r['num_seg_esba'] : null,
            'fechaInicio' => $r['fecha_inicio'],
            'fechaFin' => $r['fecha_fin'],
            'createdBy' => $r['created_by'] !== null ? (int)$r['created_by'] : null,
            'createdAt' => $r['created_at'] ?? null,
            'updatedAt' => $r['updated_at'] ?? null,
        ];
    }

    public static function all(PDO $db)
    {
        $stmt = $db->query(
            "SELECT id, region_id, sector, sector_amerb_id, sector_amerb, tipo_org, opa_id, org_nombre, num_seg_esba, fecha_inicio, fecha_fin, created_by, created_at, updated_at
             FROM operaciones
             ORDER BY created_at DESC"
        );
        $rows = $stmt->fetchAll();
        return array_map([self::class, 'mapRow'], $rows);
    }

    public static function find(PDO $db, $id)
    {
        $stmt = $db->prepare(
            "SELECT id, region_id, sector, sector_amerb_id, sector_amerb, tipo_org, opa_id, org_nombre, num_seg_esba, fecha_inicio, fecha_fin, created_by, created_at, updated_at
             FROM operaciones
             WHERE id = :id
             LIMIT 1"
        );
        $stmt->execute([':id' => (string)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }

    public static function create(PDO $db, array $data, $createdBy = null)
    {
        $id = trim((string)($data['id'] ?? ''));
        if ($id === '') return ['error' => 'id requerido'];
        $sector = trim((string)($data['sector'] ?? ''));
        if ($sector === '') return ['error' => 'sector requerido'];

        $stmt = $db->prepare(
            "INSERT INTO operaciones (
                id, region_id, sector, sector_amerb_id, sector_amerb, tipo_org, opa_id, org_nombre, num_seg_esba, fecha_inicio, fecha_fin, created_by
             ) VALUES (
                :id, :region_id, :sector, :sector_amerb_id, :sector_amerb, :tipo_org, :opa_id, :org_nombre, :num_seg_esba, :fecha_inicio, :fecha_fin, :created_by
             )"
        );
        $stmt->execute([
            ':id' => $id,
            ':region_id' => isset($data['region']) && $data['region'] !== '' ? (int)$data['region'] : null,
            ':sector' => $sector,
            ':sector_amerb_id' => isset($data['sectorAmerbId']) && $data['sectorAmerbId'] !== '' ? (int)$data['sectorAmerbId'] : null,
            ':sector_amerb' => trim((string)($data['sectorAmerb'] ?? '')) ?: null,
            ':tipo_org' => trim((string)($data['tipoOrg'] ?? '')) ?: null,
            ':opa_id' => isset($data['opaId']) && $data['opaId'] !== '' ? (int)$data['opaId'] : null,
            ':org_nombre' => trim((string)($data['orgNombre'] ?? '')) ?: null,
            ':num_seg_esba' => isset($data['numSegEsba']) && $data['numSegEsba'] !== '' ? (int)$data['numSegEsba'] : null,
            ':fecha_inicio' => trim((string)($data['fechaInicio'] ?? '')) ?: null,
            ':fecha_fin' => trim((string)($data['fechaFin'] ?? '')) ?: null,
            ':created_by' => $createdBy !== null ? (int)$createdBy : null,
        ]);

        return self::find($db, $id);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;

        $sector = array_key_exists('sector', $data) ? trim((string)$data['sector']) : $cur['sector'];
        if ($sector === '') return ['error' => 'sector requerido'];

        $stmt = $db->prepare(
            "UPDATE operaciones SET
                region_id = :region_id,
                sector = :sector,
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
            ':sector' => $sector,
            ':sector_amerb_id' => array_key_exists('sectorAmerbId', $data) ? ($data['sectorAmerbId'] !== '' ? (int)$data['sectorAmerbId'] : null) : $cur['sectorAmerbId'],
            ':sector_amerb' => array_key_exists('sectorAmerb', $data) ? (trim((string)$data['sectorAmerb']) ?: null) : $cur['sectorAmerb'],
            ':tipo_org' => array_key_exists('tipoOrg', $data) ? (trim((string)$data['tipoOrg']) ?: null) : $cur['tipoOrg'],
            ':opa_id' => array_key_exists('opaId', $data) ? ($data['opaId'] !== '' ? (int)$data['opaId'] : null) : $cur['opaId'],
            ':org_nombre' => array_key_exists('orgNombre', $data) ? (trim((string)$data['orgNombre']) ?: null) : $cur['orgNombre'],
            ':num_seg_esba' => array_key_exists('numSegEsba', $data) ? ($data['numSegEsba'] !== '' ? (int)$data['numSegEsba'] : null) : $cur['numSegEsba'],
            ':fecha_inicio' => array_key_exists('fechaInicio', $data) ? (trim((string)$data['fechaInicio']) ?: null) : $cur['fechaInicio'],
            ':fecha_fin' => array_key_exists('fechaFin', $data) ? (trim((string)$data['fechaFin']) ?: null) : $cur['fechaFin'],
        ]);

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM operaciones WHERE id = :id");
        $stmt->execute([':id' => (string)$id]);
        return $stmt->rowCount() > 0;
    }
}

