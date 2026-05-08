<?php

require_once __DIR__ . '/../config/database.php';

function out($status, $payload)
{
    if (PHP_SAPI !== 'cli') {
        header('Content-Type: application/json; charset=utf-8');
    }
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

if (PHP_SAPI !== 'cli') {
    out(403, ['ok' => false, 'error' => 'CLI only']);
}

$primaryDbName = db_name('primary');
$backupDbName = db_name('backup');
if ($primaryDbName === '' || $backupDbName === '') {
    out(500, ['ok' => false, 'error' => 'DB_NAME / DB_NAME_BACKUP no configurado']);
}

$p = getDB('primary');
$b = getDB('backup');

$dateKey = date('Ymd');
$prefix = getenv('BACKUP_SNAPSHOT_PREFIX') ?: 'snap_' . $dateKey . '__';

try {
    $p->exec("SET SESSION sql_mode = ''");
    $b->exec("SET SESSION sql_mode = ''");
    $b->exec("SET FOREIGN_KEY_CHECKS = 0");

    $stmt = $p->query("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'");
    $rows = $stmt->fetchAll();
    $tables = [];
    foreach ($rows as $r) {
        $vals = array_values($r);
        $t = (string)($vals[0] ?? '');
        if ($t !== '') $tables[] = $t;
    }

    $copied = [];
    foreach ($tables as $t) {
        $tQ = str_replace('`', '``', $t);
        $snapName = $prefix . $t;
        $snapQ = str_replace('`', '``', $snapName);

        $b->exec("CREATE TABLE IF NOT EXISTS `{$tQ}` LIKE `{$primaryDbName}`.`{$tQ}`");
        $b->exec("TRUNCATE TABLE `{$backupDbName}`.`{$tQ}`");
        $b->exec("INSERT INTO `{$backupDbName}`.`{$tQ}` SELECT * FROM `{$primaryDbName}`.`{$tQ}`");

        $b->exec("CREATE TABLE IF NOT EXISTS `{$backupDbName}`.`{$snapQ}` LIKE `{$primaryDbName}`.`{$tQ}`");
        $b->exec("TRUNCATE TABLE `{$backupDbName}`.`{$snapQ}`");
        $b->exec("INSERT INTO `{$backupDbName}`.`{$snapQ}` SELECT * FROM `{$primaryDbName}`.`{$tQ}`");

        $copied[] = $t;
    }

    $b->exec("SET FOREIGN_KEY_CHECKS = 1");
    out(200, ['ok' => true, 'primary' => $primaryDbName, 'backup' => $backupDbName, 'snapshot_prefix' => $prefix, 'tables' => $copied]);
} catch (Throwable $e) {
    try {
        $b->exec("SET FOREIGN_KEY_CHECKS = 1");
    } catch (Throwable $e2) {
        ;
    }
    out(500, ['ok' => false, 'error' => $e->getMessage()]);
}
