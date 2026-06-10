<?php

class Caleta
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
        return [
            'id' => (int)$r['id'],
            'nombre' => $r['nombre'] ?? null,
            'region_id' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'activo' => array_key_exists('activo', $r) ? (bool)$r['activo'] : true,
        ];
    }

    public static function all(PDO $db)
    {
        $cols = "id, nombre, region_id";
        if (self::hasColumn($db, 'caletas', 'activo')) $cols .= ", activo";
        $stmt = $db->query("SELECT $cols FROM caletas ORDER BY region_id ASC, nombre ASC, id ASC");
        $rows = $stmt->fetchAll();
        return array_values(array_filter(array_map([self::class, 'mapRow'], $rows)));
    }

    public static function find(PDO $db, $id)
    {
        $cols = "id, nombre, region_id";
        if (self::hasColumn($db, 'caletas', 'activo')) $cols .= ", activo";
        $stmt = $db->prepare("SELECT $cols FROM caletas WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }

    public static function create(PDO $db, array $data)
    {
        $id = isset($data['id']) && $data['id'] !== '' ? (int)$data['id'] : null;
        if ($id === null) return ['error' => 'id requerido'];

        $nombre = trim((string)($data['nombre'] ?? ''));
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $regionId = isset($data['region_id']) && $data['region_id'] !== '' ? (int)$data['region_id'] : null;
        if ($regionId === null) return ['error' => 'region_id requerido'];

        $useActivo = self::hasColumn($db, 'caletas', 'activo');
        $activo = $useActivo ? (isset($data['activo']) ? ((int)!!$data['activo']) : 1) : null;

        if ($useActivo) {
            $stmt = $db->prepare(
                "INSERT INTO caletas (id, nombre, region_id, activo)
                 VALUES (:id, :nombre, :region_id, :activo)"
            );
            $stmt->execute([
                ':id' => $id,
                ':nombre' => $nombre,
                ':region_id' => $regionId,
                ':activo' => $activo,
            ]);
        } else {
            $stmt = $db->prepare(
                "INSERT INTO caletas (id, nombre, region_id)
                 VALUES (:id, :nombre, :region_id)"
            );
            $stmt->execute([
                ':id' => $id,
                ':nombre' => $nombre,
                ':region_id' => $regionId,
            ]);
        }

        return self::find($db, $id);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;

        $nombre = array_key_exists('nombre', $data) ? trim((string)$data['nombre']) : (string)($cur['nombre'] ?? '');
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $regionId = $cur['region_id'];
        if (array_key_exists('region_id', $data)) {
            $v = $data['region_id'];
            $regionId = $v !== null && $v !== '' ? (int)$v : null;
        }
        if ($regionId === null) return ['error' => 'region_id requerido'];

        $useActivo = self::hasColumn($db, 'caletas', 'activo');
        $hasActivo = $useActivo && array_key_exists('activo', $data);
        $activo = $hasActivo ? (int)!!$data['activo'] : null;

        if ($useActivo) {
            if ($hasActivo) {
                $stmt = $db->prepare(
                    "UPDATE caletas
                     SET nombre = :nombre,
                         region_id = :region_id,
                         activo = :activo
                     WHERE id = :id"
                );
                $stmt->execute([
                    ':id' => (int)$id,
                    ':nombre' => $nombre,
                    ':region_id' => $regionId,
                    ':activo' => $activo,
                ]);
            } else {
                $stmt = $db->prepare(
                    "UPDATE caletas
                     SET nombre = :nombre,
                         region_id = :region_id
                     WHERE id = :id"
                );
                $stmt->execute([
                    ':id' => (int)$id,
                    ':nombre' => $nombre,
                    ':region_id' => $regionId,
                ]);
            }
        } else {
            $stmt = $db->prepare(
                "UPDATE caletas
                 SET nombre = :nombre,
                     region_id = :region_id
                 WHERE id = :id"
            );
            $stmt->execute([
                ':id' => (int)$id,
                ':nombre' => $nombre,
                ':region_id' => $regionId,
            ]);
        }

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM caletas WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}
