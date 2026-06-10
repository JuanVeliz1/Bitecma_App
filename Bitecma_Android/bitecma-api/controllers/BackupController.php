<?php

require_once __DIR__ . '/../middleware/auth.php';
require_once __DIR__ . '/../config/database.php';

function bk_send($status, $payload)
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($payload);
    exit;
}

function bk_require_admin()
{
    $payload = require_auth();
    $rol = strtolower(trim((string)($payload['rol'] ?? '')));
    if ($rol !== 'admin') {
        bk_send(403, ['ok' => false, 'error' => 'Acceso restringido']);
    }
    return $payload;
}

function bk_sql_ident($name)
{
    $n = str_replace('`', '``', (string)$name);
    return '`' . $n . '`';
}

function bk_value_to_sql(PDO $db, $value, $colType = '')
{
    if ($value === null) return 'NULL';

    $t = strtolower((string)$colType);
    $isBinary = (strpos($t, 'blob') !== false) || (strpos($t, 'binary') !== false) || (strpos($t, 'varbinary') !== false);
    if ($isBinary) {
        $s = (string)$value;
        return '0x' . bin2hex($s);
    }

    if (is_bool($value)) return $value ? '1' : '0';
    if (is_int($value) || is_float($value)) return (string)$value;

    $s = (string)$value;
    if ($s !== '' && preg_match('//u', $s) !== 1) {
        return '0x' . bin2hex($s);
    }

    return $db->quote($s);
}

function bk_new_unbuffered_primary_pdo()
{
    $cfg = db_config('primary');
    $host = (string)($cfg['host'] ?? DB_HOST);
    $name = (string)($cfg['name'] ?? DB_NAME);
    $user = (string)($cfg['user'] ?? DB_USER);
    $pass = (string)($cfg['pass'] ?? DB_PASS);
    $charset = (string)($cfg['charset'] ?? DB_CHARSET);

    $dsn = 'mysql:host=' . $host . ';dbname=' . $name . ';charset=' . $charset;

    $opts = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ];

    if (defined('PDO::MYSQL_ATTR_USE_BUFFERED_QUERY')) {
        $opts[PDO::MYSQL_ATTR_USE_BUFFERED_QUERY] = false;
    }

    return new PDO($dsn, $user, $pass, $opts);
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
if ($method !== 'GET') {
    bk_send(405, ['ok' => false, 'error' => 'Método no permitido']);
}

bk_require_admin();

$sub = (string)($id ?? '');
if ($sub !== 'sql') {
    bk_send(404, ['ok' => false, 'error' => 'No encontrado']);
}

set_time_limit(0);
@ini_set('zlib.output_compression', '0');
@ini_set('output_buffering', '0');
while (ob_get_level()) {
    @ob_end_flush();
}
@ob_implicit_flush(1);

try {
    $db = bk_new_unbuffered_primary_pdo();
    $dbName = db_name('primary') ?: 'database';
    $charset = DB_CHARSET ?: 'utf8mb4';

    $db->exec("SET SESSION sql_mode = ''");

    $filename = $dbName . '_' . date('Ymd_His') . '.sql';
    header('Content-Type: application/sql; charset=utf-8', true);
    header('Content-Disposition: attachment; filename="' . $filename . '"', true);
    header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0', true);
    header('Pragma: no-cache', true);
    http_response_code(200);

    echo 'SET NAMES ' . $charset . ";\n";
    echo "SET FOREIGN_KEY_CHECKS=0;\n\n";

    $stmt = $db->query("SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'");
    $tables = [];
    while ($r = $stmt->fetch(PDO::FETCH_NUM)) {
        $t = (string)($r[0] ?? '');
        if ($t !== '') $tables[] = $t;
    }
    $stmt->closeCursor();

    foreach ($tables as $t) {
        $tIdent = bk_sql_ident($t);

        $createStmt = $db->query('SHOW CREATE TABLE ' . $tIdent);
        $createRow = $createStmt->fetch(PDO::FETCH_ASSOC);
        $createStmt->closeCursor();
        if (!$createRow) continue;
        $vals = array_values($createRow);
        $createSql = (string)($vals[1] ?? '');
        $createSql = rtrim($createSql, ";\r\n") . ";\n";

        echo 'DROP TABLE IF EXISTS ' . $tIdent . ";\n";
        echo $createSql . "\n";

        $colInfoStmt = $db->query('SHOW COLUMNS FROM ' . $tIdent);
        $cols = [];
        $types = [];
        while ($c = $colInfoStmt->fetch(PDO::FETCH_ASSOC)) {
            $cols[] = (string)($c['Field'] ?? '');
            $types[] = (string)($c['Type'] ?? '');
        }
        $colInfoStmt->closeCursor();

        if (!count($cols)) {
            echo "\n";
            flush();
            continue;
        }

        $colList = implode(',', array_map(function ($c) {
            return bk_sql_ident($c);
        }, $cols));

        $dataStmt = $db->query('SELECT * FROM ' . $tIdent);
        $batch = [];
        $batchSize = 200;
        $n = count($cols);

        while ($row = $dataStmt->fetch(PDO::FETCH_NUM)) {
            $valsSql = [];
            for ($i = 0; $i < $n; $i++) {
                $valsSql[] = bk_value_to_sql($db, $row[$i] ?? null, $types[$i] ?? '');
            }
            $batch[] = '(' . implode(',', $valsSql) . ')';

            if (count($batch) >= $batchSize) {
                echo 'INSERT INTO ' . $tIdent . ' (' . $colList . ") VALUES\n" . implode(",\n", $batch) . ";\n";
                $batch = [];
                flush();
            }
        }
        $dataStmt->closeCursor();

        if (count($batch)) {
            echo 'INSERT INTO ' . $tIdent . ' (' . $colList . ") VALUES\n" . implode(",\n", $batch) . ";\n";
            flush();
        }

        echo "\n";
        flush();
    }

    echo "SET FOREIGN_KEY_CHECKS=1;\n";
    exit;
} catch (Throwable $e) {
    bk_send(500, ['ok' => false, 'error' => 'Error generando respaldo']);
}

