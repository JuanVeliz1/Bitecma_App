<?php

require_once __DIR__ . '/../config/database.php';

function out($status, $payload)
{
    if (PHP_SAPI !== 'cli') {
        header('Content-Type: application/json; charset=utf-8');
    }
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    exit;
}

if (PHP_SAPI !== 'cli') {
    out(403, ['ok' => false, 'error' => 'CLI only']);
}

function sql_ident($name)
{
    $n = str_replace('`', '``', (string)$name);
    return '`' . $n . '`';
}

function table_exists(PDO $db, $table)
{
    $stmt = $db->prepare(
        "SELECT 1
         FROM INFORMATION_SCHEMA.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = :t
         LIMIT 1"
    );
    $stmt->execute([':t' => (string)$table]);
    return (bool)$stmt->fetch();
}

function col_meta(PDO $db, $table, $col)
{
    $stmt = $db->prepare(
        "SELECT DATA_TYPE, COLUMN_TYPE, COLLATION_NAME, IS_NULLABLE
         FROM INFORMATION_SCHEMA.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = :t
           AND COLUMN_NAME = :c
         LIMIT 1"
    );
    $stmt->execute([':t' => (string)$table, ':c' => (string)$col]);
    $r = $stmt->fetch(PDO::FETCH_ASSOC);
    return $r ?: null;
}

function fk_exists(PDO $db, $table, $constraintName)
{
    $stmt = $db->prepare(
        "SELECT 1
         FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = :t
           AND CONSTRAINT_NAME = :k
         LIMIT 1"
    );
    $stmt->execute([':t' => (string)$table, ':k' => (string)$constraintName]);
    return (bool)$stmt->fetch();
}

function has_index_on(PDO $db, $table, $col)
{
    $stmt = $db->query("SHOW INDEX FROM " . sql_ident($table));
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($rows as $r) {
        if (strcasecmp((string)($r['Column_name'] ?? ''), (string)$col) === 0) return true;
    }
    return false;
}

function ensure_index(PDO $db, $table, $col, $name)
{
    if (has_index_on($db, $table, $col)) return ['ok' => true, 'created' => false];
    $db->exec("ALTER TABLE " . sql_ident($table) . " ADD INDEX " . sql_ident($name) . " (" . sql_ident($col) . ")");
    return ['ok' => true, 'created' => true];
}

function count_orphans(PDO $db, $childTable, $childCol, $parentTable, $parentCol)
{
    $sql = "SELECT COUNT(*) AS c
            FROM " . sql_ident($childTable) . " c
            LEFT JOIN " . sql_ident($parentTable) . " p
              ON c." . sql_ident($childCol) . " = p." . sql_ident($parentCol) . "
            WHERE c." . sql_ident($childCol) . " IS NOT NULL
              AND p." . sql_ident($parentCol) . " IS NULL";
    $stmt = $db->query($sql);
    $r = $stmt->fetch(PDO::FETCH_ASSOC);
    return (int)($r['c'] ?? 0);
}

function types_compatible($childMeta, $parentMeta)
{
    if (!$childMeta || !$parentMeta) return false;
    $ct = strtolower((string)($childMeta['COLUMN_TYPE'] ?? ''));
    $pt = strtolower((string)($parentMeta['COLUMN_TYPE'] ?? ''));
    if ($ct !== $pt) return false;
    $cd = strtolower((string)($childMeta['DATA_TYPE'] ?? ''));
    $isString = in_array($cd, ['char', 'varchar', 'text', 'tinytext', 'mediumtext', 'longtext'], true);
    if ($isString) {
        $cc = (string)($childMeta['COLLATION_NAME'] ?? '');
        $pc = (string)($parentMeta['COLLATION_NAME'] ?? '');
        if ($cc !== $pc) return false;
    }
    return true;
}

try {
    $db = getDB('primary');
    $dbName = db_name('primary');
    if ($dbName === '') out(500, ['ok' => false, 'error' => 'DB_NAME no configurado']);

    $tables = [];
    $stmt = $db->query(
        "SELECT TABLE_NAME, ENGINE
         FROM INFORMATION_SCHEMA.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_TYPE = 'BASE TABLE'
         ORDER BY TABLE_NAME ASC"
    );
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);
    foreach ($rows as $r) {
        $t = (string)($r['TABLE_NAME'] ?? '');
        if ($t !== '') $tables[] = ['name' => $t, 'engine' => (string)($r['ENGINE'] ?? '')];
    }

    $converted = [];
    $skipped = [];
    foreach ($tables as $t) {
        $name = $t['name'];
        $engine = strtoupper((string)($t['engine'] ?? ''));
        if ($engine === 'INNODB') continue;
        try {
            $db->exec("ALTER TABLE " . sql_ident($name) . " ENGINE=InnoDB");
            $converted[] = $name;
        } catch (Throwable $e) {
            $skipped[] = ['table' => $name, 'error' => $e->getMessage()];
        }
    }

    $fks = [
        ['t' => 'caletas', 'c' => 'region_id', 'rt' => 'regiones', 'rc' => 'id', 'name' => 'fk_caletas_region_id'],
        ['t' => 'sectores_amerb', 'c' => 'region_id', 'rt' => 'regiones', 'rc' => 'id', 'name' => 'fk_sectores_amerb_region_id'],
        ['t' => 'organizaciones_opa', 'c' => 'region_id', 'rt' => 'regiones', 'rc' => 'id', 'name' => 'fk_organizaciones_opa_region_id'],
        ['t' => 'operaciones', 'c' => 'region_id', 'rt' => 'regiones', 'rc' => 'id', 'name' => 'fk_operaciones_region_id'],
        ['t' => 'operaciones', 'c' => 'caleta_id', 'rt' => 'caletas', 'rc' => 'id', 'name' => 'fk_operaciones_caleta_id'],
        ['t' => 'operaciones', 'c' => 'sector_amerb_id', 'rt' => 'sectores_amerb', 'rc' => 'id', 'name' => 'fk_operaciones_sector_amerb_id'],
        ['t' => 'operaciones', 'c' => 'opa_id', 'rt' => 'organizaciones_opa', 'rc' => 'id', 'name' => 'fk_operaciones_opa_id'],
        ['t' => 'operaciones', 'c' => 'created_by', 'rt' => 'usuarios', 'rc' => 'id', 'name' => 'fk_operaciones_created_by'],

        ['t' => 'operacion_botes', 'c' => 'operacion_id', 'rt' => 'operaciones', 'rc' => 'id', 'name' => 'fk_operacion_botes_operacion_id', 'on_delete' => 'CASCADE'],
        ['t' => 'operacion_botes', 'c' => 'bote_maestro_id', 'rt' => 'botes_maestro', 'rc' => 'id', 'name' => 'fk_operacion_botes_bote_maestro_id'],

        ['t' => 'densidad_unidades', 'c' => 'operacion_bote_id', 'rt' => 'operacion_botes', 'rc' => 'id', 'name' => 'fk_densidad_unidades_operacion_bote_id', 'on_delete' => 'CASCADE'],
        ['t' => 'densidad_unidades', 'c' => 'especie_id', 'rt' => 'especies', 'rc' => 'id', 'name' => 'fk_densidad_unidades_especie_id'],

        ['t' => 'densidad_unidad_counts', 'c' => 'unidad_id', 'rt' => 'densidad_unidades', 'rc' => 'id', 'name' => 'fk_densidad_unidad_counts_unidad_id', 'on_delete' => 'CASCADE'],
        ['t' => 'densidad_unidad_counts', 'c' => 'especie_id', 'rt' => 'especies', 'rc' => 'id', 'name' => 'fk_densidad_unidad_counts_especie_id'],

        ['t' => 'muestras', 'c' => 'operacion_bote_id', 'rt' => 'operacion_botes', 'rc' => 'id', 'name' => 'fk_muestras_operacion_bote_id', 'on_delete' => 'CASCADE'],
        ['t' => 'muestras', 'c' => 'especie_id', 'rt' => 'especies', 'rc' => 'id', 'name' => 'fk_muestras_especie_id'],
    ];

    $fkResults = [];
    foreach ($fks as $fk) {
        $t = $fk['t'];
        $c = $fk['c'];
        $rt = $fk['rt'];
        $rc = $fk['rc'];
        $name = $fk['name'];
        $onDelete = strtoupper((string)($fk['on_delete'] ?? 'RESTRICT'));
        $onUpdate = 'RESTRICT';

        if (!table_exists($db, $t) || !table_exists($db, $rt)) {
            $fkResults[] = ['name' => $name, 'status' => 'skipped', 'reason' => 'missing_table'];
            continue;
        }

        $childMeta = col_meta($db, $t, $c);
        $parentMeta = col_meta($db, $rt, $rc);
        if (!$childMeta || !$parentMeta) {
            $fkResults[] = ['name' => $name, 'status' => 'skipped', 'reason' => 'missing_column'];
            continue;
        }

        if (!types_compatible($childMeta, $parentMeta)) {
            $fkResults[] = ['name' => $name, 'status' => 'skipped', 'reason' => 'type_mismatch'];
            continue;
        }

        if (fk_exists($db, $t, $name)) {
            $fkResults[] = ['name' => $name, 'status' => 'exists'];
            continue;
        }

        $nullable = strtoupper((string)($childMeta['IS_NULLABLE'] ?? 'YES')) === 'YES';
        if ($onDelete === 'RESTRICT' && $nullable) {
            $onDelete = 'SET NULL';
        }

        $orphans = count_orphans($db, $t, $c, $rt, $rc);
        if ($orphans > 0) {
            $fkResults[] = ['name' => $name, 'status' => 'skipped', 'reason' => 'orphans', 'orphans' => $orphans];
            continue;
        }

        try {
            $idxName = 'idx_' . $t . '__' . $c;
            ensure_index($db, $t, $c, $idxName);
            $db->exec(
                "ALTER TABLE " . sql_ident($t) .
                " ADD CONSTRAINT " . sql_ident($name) .
                " FOREIGN KEY (" . sql_ident($c) . ") REFERENCES " . sql_ident($rt) . " (" . sql_ident($rc) . ")" .
                " ON DELETE " . $onDelete .
                " ON UPDATE " . $onUpdate
            );
            $fkResults[] = ['name' => $name, 'status' => 'created', 'on_delete' => $onDelete];
        } catch (Throwable $e) {
            $fkResults[] = ['name' => $name, 'status' => 'failed', 'error' => $e->getMessage()];
        }
    }

    out(200, [
        'ok' => true,
        'db' => $dbName,
        'converted_tables' => $converted,
        'convert_skipped' => $skipped,
        'foreign_keys' => $fkResults,
    ]);
} catch (Throwable $e) {
    out(500, ['ok' => false, 'error' => $e->getMessage()]);
}

