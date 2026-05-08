<?php

class Bote
{
    private static function mapRow($r)
    {
        if (!$r) return null;
        $regionRom = $r['region_rom'] ?? null;
        return [
            'id' => isset($r['id']) ? (int)$r['id'] : null,
            'region_rom' => $regionRom,
            'region' => $regionRom,
            'nombre' => $r['nombre'] ?? null,
            'nrpa' => $r['nrpa'] ?? null,
            'nmatricula' => $r['nmatricula'] ?? null,
            'caleta' => $r['caleta'] ?? null,
            'created_at' => $r['created_at'] ?? null,
            'updated_at' => $r['updated_at'] ?? null,
        ];
    }

    public static function all(PDO $db)
    {
        $stmt = $db->query("SELECT id, region_rom, nombre, nrpa, nmatricula, caleta, created_at, updated_at FROM botes_maestro ORDER BY nombre ASC");
        $rows = $stmt->fetchAll();
        return array_values(array_filter(array_map([self::class, 'mapRow'], $rows)));
    }

    public static function find(PDO $db, $id)
    {
        $stmt = $db->prepare("SELECT id, region_rom, nombre, nrpa, nmatricula, caleta, created_at, updated_at FROM botes_maestro WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        if (!$r) return null;
        return self::mapRow($r);
    }

    private static function existsByRegionNrpa(PDO $db, $regionRom, $nrpa, $excludeId = null)
    {
        if ($regionRom === null || $regionRom === '' || $nrpa === null || $nrpa === '') return false;
        if ($excludeId !== null) {
            $stmt = $db->prepare("SELECT id FROM botes_maestro WHERE region_rom = :region_rom AND nrpa = :nrpa AND id <> :id LIMIT 1");
            $stmt->execute([':region_rom' => (string)$regionRom, ':nrpa' => (string)$nrpa, ':id' => (int)$excludeId]);
            return (bool)$stmt->fetch();
        }
        $stmt = $db->prepare("SELECT id FROM botes_maestro WHERE region_rom = :region_rom AND nrpa = :nrpa LIMIT 1");
        $stmt->execute([':region_rom' => (string)$regionRom, ':nrpa' => (string)$nrpa]);
        return (bool)$stmt->fetch();
    }

    public static function create(PDO $db, array $data)
    {
        $nombre = trim((string)($data['nombre'] ?? ''));
        $caleta = trim((string)($data['caleta'] ?? ''));
        if ($nombre === '') return ['error' => 'nombre requerido'];
        $regionRom = strtoupper(trim((string)($data['region_rom'] ?? $data['region'] ?? ''))) ?: null;
        $nrpa = trim((string)($data['nrpa'] ?? '')) ?: null;
        $nmatricula = trim((string)($data['nmatricula'] ?? '')) ?: null;
        $caleta = $caleta !== '' ? $caleta : null;

        if ($regionRom !== null && strlen($regionRom) > 255) return ['error' => 'region_rom inválido'];

        if (self::existsByRegionNrpa($db, $regionRom, $nrpa)) return ['error' => 'ya existe un bote con esa región y NRPA'];

        $stmt = $db->prepare(
            "INSERT INTO botes_maestro (region_rom, nombre, nrpa, nmatricula, caleta)
             VALUES (:region_rom, :nombre, :nrpa, :nmatricula, :caleta)"
        );
        $stmt->execute([
            ':region_rom' => $regionRom,
            ':nombre' => $nombre,
            ':nrpa' => $nrpa,
            ':nmatricula' => $nmatricula,
            ':caleta' => $caleta,
        ]);

        $newId = (int)$db->lastInsertId();
        return $newId ? self::find($db, $newId) : self::all($db);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::find($db, $id);
        if (!$cur) return null;
        $nombre = array_key_exists('nombre', $data) ? trim((string)$data['nombre']) : (string)($cur['nombre'] ?? '');
        if ($nombre === '') return ['error' => 'nombre requerido'];

        $regionRom = $cur['region_rom'] ?? null;
        if (array_key_exists('region_rom', $data) || array_key_exists('region', $data)) {
            $regionRom = strtoupper(trim((string)($data['region_rom'] ?? $data['region'] ?? ''))) ?: null;
        }

        $nrpa = array_key_exists('nrpa', $data) ? (trim((string)$data['nrpa']) ?: null) : ($cur['nrpa'] ?? null);
        $nmatricula = array_key_exists('nmatricula', $data) ? (trim((string)$data['nmatricula']) ?: null) : ($cur['nmatricula'] ?? null);
        $caleta = array_key_exists('caleta', $data) ? (trim((string)$data['caleta']) ?: null) : ($cur['caleta'] ?? null);

        if (self::existsByRegionNrpa($db, $regionRom, $nrpa, $id)) return ['error' => 'ya existe un bote con esa región y NRPA'];

        $stmt = $db->prepare(
            "UPDATE botes_maestro
             SET region_rom = :region_rom,
                 nombre = :nombre,
                 nrpa = :nrpa,
                 nmatricula = :nmatricula,
                 caleta = :caleta
             WHERE id = :id"
        );
        $stmt->execute([
            ':id' => (int)$id,
            ':region_rom' => $regionRom,
            ':nombre' => $nombre,
            ':nrpa' => $nrpa,
            ':nmatricula' => $nmatricula,
            ':caleta' => $caleta,
        ]);

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM botes_maestro WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}
