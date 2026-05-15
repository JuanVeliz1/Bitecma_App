<?php

class Caleta
{
    private static function mapRow($r)
    {
        if (!$r) return null;
        return [
            'id' => (int)$r['id'],
            'nombre' => $r['nombre'] ?? null,
            'region_id' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'region' => $r['region_id'] !== null ? (int)$r['region_id'] : null,
            'sectorAmerbId' => isset($r['sector_amerb_id']) ? (int)$r['sector_amerb_id'] : null,
        ];
    }

    public static function all(PDO $db)
    {
        // Se asume que la tabla caletas existe y tiene relación con sectores_amerb
        // Si no existe columna sector_amerb_id, se usará el esquema actual
        $stmt = $db->query("SELECT id, nombre, region_id, sector_amerb_id FROM caletas ORDER BY region_id ASC, nombre ASC, id ASC");
        $rows = $stmt->fetchAll();
        return array_values(array_filter(array_map([self::class, 'mapRow'], $rows)));
    }

    public static function find(PDO $db, $id)
    {
        $stmt = $db->prepare("SELECT id, nombre, region_id FROM caletas WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }
}
