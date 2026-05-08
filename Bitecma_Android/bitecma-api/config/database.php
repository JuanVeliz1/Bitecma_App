<?php

define('DB_HOST', getenv('DB_HOST') ?: 'localhost');
define('DB_NAME', getenv('DB_NAME') ?: 'bitecmac_web');
define('DB_USER', getenv('DB_USER') ?: 'bitecmac_web');
define('DB_PASS', getenv('DB_PASS') ?: 'REEMPLAZA_ESTA_CLAVE_EN_CPANEL');
define('DB_CHARSET', getenv('DB_CHARSET') ?: 'utf8mb4');

function db_target()
{
    $t = strtolower(trim((string)(getenv('DB_TARGET') ?: '')));
    return $t === 'backup' ? 'backup' : 'primary';
}

function db_config($target)
{
    $t = $target === 'backup' ? 'backup' : 'primary';
    if ($t === 'backup') {
        return [
            'host' => getenv('DB_HOST_BACKUP') ?: DB_HOST,
            'name' => getenv('DB_NAME_BACKUP') ?: (DB_NAME . '_respaldo'),
            'user' => getenv('DB_USER_BACKUP') ?: DB_USER,
            'pass' => getenv('DB_PASS_BACKUP') ?: DB_PASS,
            'charset' => getenv('DB_CHARSET_BACKUP') ?: DB_CHARSET,
        ];
    }
    return [
        'host' => DB_HOST,
        'name' => DB_NAME,
        'user' => DB_USER,
        'pass' => DB_PASS,
        'charset' => DB_CHARSET,
    ];
}

function db_name($target)
{
    $cfg = db_config($target);
    return (string)($cfg['name'] ?? '');
}

function getDB($target = null)
{
    static $pool = [];
    $t = $target === null ? db_target() : ($target === 'backup' ? 'backup' : 'primary');
    if (isset($pool[$t]) && $pool[$t] instanceof PDO) return $pool[$t];

    $cfg = db_config($t);
    $host = (string)($cfg['host'] ?? 'localhost');
    $name = (string)($cfg['name'] ?? '');
    $user = (string)($cfg['user'] ?? '');
    $pass = (string)($cfg['pass'] ?? '');
    $charset = (string)($cfg['charset'] ?? 'utf8mb4');
    if ($name === '') {
        throw new RuntimeException($t === 'backup' ? 'DB_NAME_BACKUP no configurado' : 'DB_NAME no configurado');
    }

    $dsn = "mysql:host=" . $host . ";dbname=" . $name . ";charset=" . $charset;
    $pdo = new PDO($dsn, $user, $pass, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);

    $pool[$t] = $pdo;
    return $pool[$t];
}

function getBackupDB()
{
    return getDB('backup');
}
