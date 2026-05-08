<?php

class Organizacion
{
    public static function all(PDO $db)
    {
        $stmt = $db->query("SELECT id, nombre, nombrecorto, region_id, comuna FROM organizaciones_opa ORDER BY id ASC");
        $rows = $stmt->fetchAll();
        return array_map(function ($r) {
            return [
                'id' => (int)$r['id'],
                'nombre' => $r['nombre'],
                'nombrecorto' => $r['nombrecorto'],
                'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
                'comuna' => $r['comuna'],
            ];
        }, $rows);
    }

    public static function find(PDO $db, $id)
    {
        $stmt = $db->prepare("SELECT id, nombre, nombrecorto, region_id, comuna FROM organizaciones_opa WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return [
            'id' => (int)$r['id'],
            'nombre' => $r['nombre'],
            'nombrecorto' => $r['nombrecorto'],
            'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'comuna' => $r['comuna'],
        ];
    }

    public static function create(PDO $db, array $data)
    {
        $nombre = trim((string)($data['nombre'] ?? ''));
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $stmt = $db->prepare(
            "INSERT INTO organizaciones_opa (id, nombre, nombrecorto, region_id, comuna)
             VALUES (:id, :nombre, :nombrecorto, :region_id, :comuna)"
        );
        $id = isset($data['id']) && $data['id'] !== '' ? (int)$data['id'] : null;
        if ($id === null) return ['error' => 'id requerido'];

        $stmt->execute([
            ':id' => $id,
            ':nombre' => $nombre,
            ':nombrecorto' => trim((string)($data['nombrecorto'] ?? '')) ?: null,
            ':region_id' => isset($data['region']) && $data['region'] !== '' ? (int)$data['region'] : null,
            ':comuna' => trim((string)($data['comuna'] ?? '')) ?: null,
        ]);

        return self::find($db, $id);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;

        $nombre = array_key_exists('nombre', $data) ? trim((string)$data['nombre']) : $cur['nombre'];
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $nombrecorto = array_key_exists('nombrecorto', $data) ? trim((string)$data['nombrecorto']) : $cur['nombrecorto'];
        $comuna = array_key_exists('comuna', $data) ? trim((string)$data['comuna']) : $cur['comuna'];
        $region = array_key_exists('region', $data) ? $data['region'] : $cur['region'];

        $stmt = $db->prepare(
            "UPDATE organizaciones_opa
             SET nombre = :nombre,
                 nombrecorto = :nombrecorto,
                 region_id = :region_id,
                 comuna = :comuna
             WHERE id = :id"
        );
        $stmt->execute([
            ':id' => (int)$id,
            ':nombre' => $nombre,
            ':nombrecorto' => $nombrecorto !== '' ? $nombrecorto : null,
            ':region_id' => $region !== null && $region !== '' ? (int)$region : null,
            ':comuna' => $comuna !== '' ? $comuna : null,
        ]);

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM organizaciones_opa WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}

