<?php

class Organizacion
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

    public static function all(PDO $db)
    {
        $cols = "id, nombre, nombrecorto, region_id, comuna";
        if (self::hasColumn($db, 'organizaciones_opa', 'activo')) $cols .= ", activo";
        $stmt = $db->query("SELECT $cols FROM organizaciones_opa ORDER BY id ASC");
        $rows = $stmt->fetchAll();
        return array_map(function ($r) {
            return [
                'id' => (int)$r['id'],
                'nombre' => $r['nombre'],
                'nombrecorto' => $r['nombrecorto'],
                'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
                'comuna' => $r['comuna'],
                'activo' => array_key_exists('activo', $r) ? (bool)$r['activo'] : true,
            ];
        }, $rows);
    }

    public static function find(PDO $db, $id)
    {
        $cols = "id, nombre, nombrecorto, region_id, comuna";
        if (self::hasColumn($db, 'organizaciones_opa', 'activo')) $cols .= ", activo";
        $stmt = $db->prepare("SELECT $cols FROM organizaciones_opa WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return [
            'id' => (int)$r['id'],
            'nombre' => $r['nombre'],
            'nombrecorto' => $r['nombrecorto'],
            'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'comuna' => $r['comuna'],
            'activo' => array_key_exists('activo', $r) ? (bool)$r['activo'] : true,
        ];
    }

    public static function create(PDO $db, array $data)
    {
        $nombre = trim((string)($data['nombre'] ?? ''));
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $id = isset($data['id']) && $data['id'] !== '' ? (int)$data['id'] : null;
        if ($id === null) return ['error' => 'id requerido'];

        $useActivo = self::hasColumn($db, 'organizaciones_opa', 'activo');
        $activo = $useActivo ? (isset($data['activo']) ? ((int)!!$data['activo']) : 1) : null;

        if ($useActivo) {
            $stmt = $db->prepare(
                "INSERT INTO organizaciones_opa (id, nombre, nombrecorto, region_id, comuna, activo)
                 VALUES (:id, :nombre, :nombrecorto, :region_id, :comuna, :activo)"
            );
            $stmt->execute([
                ':id' => $id,
                ':nombre' => $nombre,
                ':nombrecorto' => trim((string)($data['nombrecorto'] ?? '')) ?: null,
                ':region_id' => isset($data['region']) && $data['region'] !== '' ? (int)$data['region'] : null,
                ':comuna' => trim((string)($data['comuna'] ?? '')) ?: null,
                ':activo' => $activo,
            ]);
        } else {
            $stmt = $db->prepare(
                "INSERT INTO organizaciones_opa (id, nombre, nombrecorto, region_id, comuna)
                 VALUES (:id, :nombre, :nombrecorto, :region_id, :comuna)"
            );
            $stmt->execute([
                ':id' => $id,
                ':nombre' => $nombre,
                ':nombrecorto' => trim((string)($data['nombrecorto'] ?? '')) ?: null,
                ':region_id' => isset($data['region']) && $data['region'] !== '' ? (int)$data['region'] : null,
                ':comuna' => trim((string)($data['comuna'] ?? '')) ?: null,
            ]);
        }

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

        $useActivo = self::hasColumn($db, 'organizaciones_opa', 'activo');
        $hasActivo = $useActivo && array_key_exists('activo', $data);
        $activo = $hasActivo ? (int)!!$data['activo'] : null;

        if ($useActivo) {
            if ($hasActivo) {
                $stmt = $db->prepare(
                    "UPDATE organizaciones_opa
                     SET nombre = :nombre,
                         nombrecorto = :nombrecorto,
                         region_id = :region_id,
                         comuna = :comuna,
                         activo = :activo
                     WHERE id = :id"
                );
                $stmt->execute([
                    ':id' => (int)$id,
                    ':nombre' => $nombre,
                    ':nombrecorto' => $nombrecorto !== '' ? $nombrecorto : null,
                    ':region_id' => $region !== null && $region !== '' ? (int)$region : null,
                    ':comuna' => $comuna !== '' ? $comuna : null,
                    ':activo' => $activo,
                ]);
            } else {
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
            }
        } else {
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
        }

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM organizaciones_opa WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}
