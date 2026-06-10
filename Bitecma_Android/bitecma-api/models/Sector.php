<?php

class Sector
{
    private static $colCache = [];

    private static function hasColumn(PDO $db, $table, $col)
    {
        $k = strtolower($table . '.' . $col);
        if (array_key_exists($k, self::$colCache)) return self::$colCache[$k];
        $stmt = $db->prepare(
            "SELECT 1
             FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = :t
               AND COLUMN_NAME = :c
             LIMIT 1"
        );
        $stmt->execute([':t' => $table, ':c' => $col]);
        $ok = $stmt->fetch() ? true : false;
        self::$colCache[$k] = $ok;
        return $ok;
    }

    private static function mapRow($r)
    {
        if (!$r) return null;
        $nombre = $r['nombre'] ?? null;
        return [
            'id' => isset($r['id']) ? (int)$r['id'] : null,
            'nombre' => $nombre,
            'nombreamerb' => $nombre,
            'region_id' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'comuna' => $r['comuna'] ?? null,
            'activo' => array_key_exists('activo', $r) ? (bool)$r['activo'] : true,
        ];
    }

    public static function all(PDO $db)
    {
        $cols = "id, nombre, region_id, comuna";
        if (self::hasColumn($db, 'sectores_amerb', 'activo')) $cols .= ", activo";
        $stmt = $db->query("SELECT $cols FROM sectores_amerb ORDER BY id ASC");
        $rows = $stmt->fetchAll();
        return array_values(array_filter(array_map([self::class, 'mapRow'], $rows)));
    }

    public static function find(PDO $db, $id)
    {
        $cols = "id, nombre, region_id, comuna";
        if (self::hasColumn($db, 'sectores_amerb', 'activo')) $cols .= ", activo";
        $stmt = $db->prepare("SELECT $cols FROM sectores_amerb WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }

    public static function create(PDO $db, array $data)
    {
        $id = isset($data['id']) && $data['id'] !== '' ? (int)$data['id'] : null;
        if ($id === null) return ['error' => 'id requerido'];

        $nombre = trim((string)($data['nombre'] ?? $data['nombreamerb'] ?? ''));
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $regionId = isset($data['region_id']) ? $data['region_id'] : ($data['region'] ?? null);
        $regionId = $regionId !== null && $regionId !== '' ? (int)$regionId : null;

        $comuna = trim((string)($data['comuna'] ?? '')) ?: null;
        $useActivo = self::hasColumn($db, 'sectores_amerb', 'activo');
        $activo = $useActivo ? (isset($data['activo']) ? ((int)!!$data['activo']) : 1) : null;

        if ($useActivo) {
            $stmt = $db->prepare(
                "INSERT INTO sectores_amerb (id, nombre, region_id, comuna, activo)
                 VALUES (:id, :nombre, :region_id, :comuna, :activo)"
            );
            $stmt->execute([
                ':id' => $id,
                ':nombre' => $nombre,
                ':region_id' => $regionId,
                ':comuna' => $comuna,
                ':activo' => $activo,
            ]);
        } else {
            $stmt = $db->prepare(
                "INSERT INTO sectores_amerb (id, nombre, region_id, comuna)
                 VALUES (:id, :nombre, :region_id, :comuna)"
            );
            $stmt->execute([
                ':id' => $id,
                ':nombre' => $nombre,
                ':region_id' => $regionId,
                ':comuna' => $comuna,
            ]);
        }

        return self::find($db, $id);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;

        $nombre = array_key_exists('nombre', $data) || array_key_exists('nombreamerb', $data)
            ? trim((string)($data['nombre'] ?? $data['nombreamerb'] ?? ''))
            : (string)($cur['nombre'] ?? '');
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $regionId = $cur['region_id'];
        if (array_key_exists('region_id', $data) || array_key_exists('region', $data)) {
            $v = $data['region_id'] ?? $data['region'] ?? null;
            $regionId = $v !== null && $v !== '' ? (int)$v : null;
        }

        $comuna = array_key_exists('comuna', $data) ? (trim((string)$data['comuna']) ?: null) : ($cur['comuna'] ?? null);
        $useActivo = self::hasColumn($db, 'sectores_amerb', 'activo');
        $hasActivo = $useActivo && array_key_exists('activo', $data);
        $activo = $hasActivo ? (int)!!$data['activo'] : null;

        if ($useActivo) {
            if ($hasActivo) {
                $stmt = $db->prepare(
                    "UPDATE sectores_amerb
                     SET nombre = :nombre,
                         region_id = :region_id,
                         comuna = :comuna,
                         activo = :activo
                     WHERE id = :id"
                );
                $stmt->execute([
                    ':id' => (int)$id,
                    ':nombre' => $nombre,
                    ':region_id' => $regionId,
                    ':comuna' => $comuna,
                    ':activo' => $activo,
                ]);
            } else {
                $stmt = $db->prepare(
                    "UPDATE sectores_amerb
                     SET nombre = :nombre,
                         region_id = :region_id,
                         comuna = :comuna
                     WHERE id = :id"
                );
                $stmt->execute([
                    ':id' => (int)$id,
                    ':nombre' => $nombre,
                    ':region_id' => $regionId,
                    ':comuna' => $comuna,
                ]);
            }
        } else {
            $stmt = $db->prepare(
                "UPDATE sectores_amerb
                 SET nombre = :nombre,
                     region_id = :region_id,
                     comuna = :comuna
                 WHERE id = :id"
            );
            $stmt->execute([
                ':id' => (int)$id,
                ':nombre' => $nombre,
                ':region_id' => $regionId,
                ':comuna' => $comuna,
            ]);
        }

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM sectores_amerb WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}
