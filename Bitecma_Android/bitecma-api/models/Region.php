<?php

class Region
{
    private static function mapRow($r)
    {
        if (!$r) return null;
        return [
            'id' => (int)$r['id'],
            'rom' => $r['rom'] ?? null,
            'nom' => $r['nombre'] ?? null,
        ];
    }

    public static function all(PDO $db)
    {
        $stmt = $db->query("SELECT id, rom, nombre FROM regiones ORDER BY id ASC");
        $rows = $stmt->fetchAll();
        return array_values(array_filter(array_map([self::class, 'mapRow'], $rows)));
    }

    public static function find(PDO $db, $id)
    {
        $stmt = $db->prepare("SELECT id, rom, nombre FROM regiones WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }
}

