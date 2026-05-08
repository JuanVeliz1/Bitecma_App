<?php

class Especie
{
    private static $colsCache = null;

    private static function cols(PDO $db)
    {
        if (is_array(self::$colsCache)) return self::$colsCache;
        $stmt = $db->query("SHOW COLUMNS FROM especies");
        $rows = $stmt->fetchAll();
        $set = [];
        foreach ($rows as $r) {
            $set[(string)$r['Field']] = true;
        }
        self::$colsCache = $set;
        return $set;
    }

    private static function selectCols(PDO $db)
    {
        $c = self::cols($db);
        $cols = ['id', 'com', 'sci'];
        foreach (['lp', 'dens', 'is_alga', 'activo'] as $k) {
            if (!empty($c[$k])) $cols[] = $k;
        }
        return implode(', ', $cols);
    }

    private static function mapRow($r)
    {
        return [
            'id' => (int)$r['id'],
            'com' => $r['com'],
            'sci' => $r['sci'],
            'lp' => isset($r['lp']) ? (bool)$r['lp'] : false,
            'dens' => isset($r['dens']) ? (bool)$r['dens'] : false,
            'is_alga' => isset($r['is_alga']) ? (bool)$r['is_alga'] : false,
            'activo' => isset($r['activo']) ? (bool)$r['activo'] : true,
        ];
    }

    public static function all(PDO $db)
    {
        $cols = self::selectCols($db);
        $stmt = $db->query("SELECT " . $cols . " FROM especies ORDER BY id ASC");
        $rows = $stmt->fetchAll();
        return array_map([self::class, 'mapRow'], $rows);
    }

    public static function find(PDO $db, $id)
    {
        $cols = self::selectCols($db);
        $stmt = $db->prepare("SELECT " . $cols . " FROM especies WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }

    public static function create(PDO $db, array $data)
    {
        $id = isset($data['id']) && $data['id'] !== '' ? (int)$data['id'] : null;
        if ($id === null) return ['error' => 'id requerido'];
        $com = trim((string)($data['com'] ?? ''));
        if ($com === '') return ['error' => 'com requerido'];

        $cols = self::cols($db);
        $fields = ['id', 'com', 'sci'];
        $params = [':id' => $id, ':com' => $com, ':sci' => trim((string)($data['sci'] ?? '')) ?: null];

        foreach (['lp', 'dens', 'is_alga', 'activo'] as $k) {
            if (empty($cols[$k])) continue;
            $fields[] = $k;
            $params[':' . $k] = $k === 'activo' ? (array_key_exists('activo', $data) ? (!empty($data['activo']) ? 1 : 0) : 1) : (!empty($data[$k]) ? 1 : 0);
        }

        $sql = "INSERT INTO especies (" . implode(', ', $fields) . ") VALUES (" . implode(', ', array_keys($params)) . ")";
        $stmt = $db->prepare($sql);
        $stmt->execute($params);

        return self::find($db, $id);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;

        $com = array_key_exists('com', $data) ? trim((string)$data['com']) : $cur['com'];
        if ($com === '') return ['error' => 'com requerido'];

        $sci = array_key_exists('sci', $data) ? trim((string)$data['sci']) : $cur['sci'];

        $cols = self::cols($db);
        $sets = ['com = :com', 'sci = :sci'];
        $params = [':id' => (int)$id, ':com' => $com, ':sci' => $sci !== '' ? $sci : null];

        foreach (['lp', 'dens', 'is_alga', 'activo'] as $k) {
            if (empty($cols[$k])) continue;
            $sets[] = $k . ' = :' . $k;
            $params[':' . $k] = array_key_exists($k, $data) ? (!empty($data[$k]) ? 1 : 0) : (!empty($cur[$k]) ? 1 : 0);
        }

        $stmt = $db->prepare("UPDATE especies SET " . implode(', ', $sets) . " WHERE id = :id");
        $stmt->execute($params);

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM especies WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}
