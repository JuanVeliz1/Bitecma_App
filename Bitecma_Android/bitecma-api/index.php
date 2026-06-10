<?php

header('Access-Control-Allow-Origin: *');
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(200);
    exit;
}

require_once __DIR__ . '/config/database.php';

function api_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

function api_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function api_exception_payload(Throwable $e)
{
    if ($e instanceof PDOException) {
        $info = is_array($e->errorInfo ?? null) ? $e->errorInfo : null;
        $sqlState = is_array($info) ? (string)($info[0] ?? '') : '';
        $driverCode = is_array($info) ? (int)($info[1] ?? 0) : 0;

        if ($sqlState === '23000' || $driverCode === 1451 || $driverCode === 1452 || $driverCode === 1062) {
            if ($driverCode === 1451) {
                return [
                    'status' => 409,
                    'payload' => ['ok' => false, 'error' => 'No se puede completar la operación: existen registros relacionados.'],
                ];
            }
            if ($driverCode === 1452) {
                return [
                    'status' => 400,
                    'payload' => ['ok' => false, 'error' => 'Referencia inválida: el registro relacionado no existe.'],
                ];
            }
            if ($driverCode === 1062) {
                return [
                    'status' => 409,
                    'payload' => ['ok' => false, 'error' => 'Conflicto: registro duplicado.'],
                ];
            }

            return [
                'status' => 409,
                'payload' => ['ok' => false, 'error' => 'Conflicto de integridad en base de datos.'],
            ];
        }
    }

    return [
        'status' => 500,
        'payload' => ['ok' => false, 'error' => 'Error interno del servidor.'],
    ];
}

set_exception_handler(function (Throwable $e) {
    $out = api_exception_payload($e);
    api_send((int)$out['status'], $out['payload']);
});

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
$scriptDir = str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? ''));
$scriptDir = rtrim($scriptDir, '/');
if ($scriptDir !== '' && $scriptDir !== '/' && strpos($path, $scriptDir) === 0) {
    $path = substr($path, strlen($scriptDir));
}
$path = trim($path, '/');
$parts = $path !== '' ? explode('/', $path) : [];
if (($parts[0] ?? '') === 'api') {
    array_shift($parts);
}

$resource = $parts[0] ?? '';
$id = $parts[1] ?? null;

if ($resource === '' || $resource === 'ping') {
    api_send(200, ['ok' => true, 'service' => 'bitecma-api']);
}

if ($resource === 'auth') {
    require_once __DIR__ . '/middleware/auth.php';
    $sub = $parts[1] ?? '';

    if ($sub === 'login') {
        if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
            api_send(405, ['ok' => false, 'error' => 'Método no permitido']);
        }
        $body = api_json_body();
        $correo = trim((string)($body['correo'] ?? $body['email'] ?? ''));
        $pass = (string)($body['password'] ?? $body['pass'] ?? '');
        if ($correo === '' || $pass === '') {
            api_send(400, ['ok' => false, 'error' => 'correo y password requeridos']);
        }

        $db = getDB();
        $stmt = $db->prepare("SELECT id, correo, nombre, numero, rol, activo, avatar_url, password_hash FROM usuarios WHERE LOWER(correo) = LOWER(:correo) LIMIT 1");
        $stmt->execute([':correo' => $correo]);
        $u = $stmt->fetch();
        if (!$u || empty($u['activo'])) {
            api_send(401, ['ok' => false, 'error' => 'Credenciales inválidas']);
        }
        $stored = (string)($u['password_hash'] ?? '');
        $looksHashed = preg_match('/^(\$2[aby]\$|\$argon2)/i', $stored) === 1;
        $okPass = false;

        if ($looksHashed) {
            $okPass = password_verify($pass, $stored);
        } else {
            $okPass = $stored !== '' && hash_equals($stored, $pass);
            if ($okPass) {
                $newHash = password_hash($pass, PASSWORD_DEFAULT);
                $upd = $db->prepare('UPDATE usuarios SET password_hash = :h, updated_at = NOW() WHERE id = :id');
                $upd->execute([':h' => $newHash, ':id' => (int)$u['id']]);
            }
        }

        if (!$okPass) {
            api_send(401, ['ok' => false, 'error' => 'Credenciales inválidas']);
        }

        $tokenPayload = [
            'uid' => (int)$u['id'],
            'id' => (int)$u['id'],
            'correo' => $u['correo'],
            'nombre' => $u['nombre'],
            'rol' => $u['rol'],
        ];
        $token = jwt_encode($tokenPayload, 60 * 60 * 24 * 30);

        $user = [
            'id' => (int)$u['id'],
            'uid' => (int)$u['id'],
            'correo' => $u['correo'],
            'nombre' => $u['nombre'],
            'numero' => $u['numero'] ?? null,
            'rol' => $u['rol'],
            'avatar_url' => $u['avatar_url'] ?? null,
        ];

        api_send(200, [
            'ok' => true,
            'token' => $token,
            'user' => $user,
        ]);
    }

    if ($sub === 'me') {
        if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
            api_send(405, ['ok' => false, 'error' => 'Método no permitido']);
        }
        $payload = require_auth();
        $uid = (int)($payload['uid'] ?? $payload['id'] ?? 0);
        if ($uid <= 0) api_send(401, ['ok' => false, 'error' => 'Token inválido']);

        $db = getDB();
        $stmt = $db->prepare('SELECT id, correo, nombre, numero, rol, activo, avatar_url FROM usuarios WHERE id = :id LIMIT 1');
        $stmt->execute([':id' => $uid]);
        $u = $stmt->fetch();
        if (!$u || empty($u['activo'])) api_send(401, ['ok' => false, 'error' => 'No autorizado']);

        api_send(200, [
            'ok' => true,
            'user' => [
                'id' => (int)$u['id'],
                'uid' => (int)$u['id'],
                'correo' => $u['correo'],
                'nombre' => $u['nombre'],
                'numero' => $u['numero'] ?? null,
                'rol' => $u['rol'],
                'avatar_url' => $u['avatar_url'] ?? null,
            ],
        ]);
    }

    if ($sub === 'profile') {
        $method2 = $_SERVER['REQUEST_METHOD'] ?? 'GET';
        if ($method2 !== 'PUT') {
            api_send(405, ['ok' => false, 'error' => 'Método no permitido']);
        }
        $payload = require_auth();
        $uid = (int)($payload['uid'] ?? $payload['id'] ?? 0);
        if ($uid <= 0) api_send(401, ['ok' => false, 'error' => 'Token inválido']);

        $body = api_json_body();
        $nombre = trim((string)($body['nombre'] ?? ''));
        $correo = strtolower(trim((string)($body['correo'] ?? $body['email'] ?? '')));
        $numero = trim((string)($body['numero'] ?? ''));

        if ($nombre === '') api_send(400, ['ok' => false, 'error' => 'nombre requerido']);
        if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) api_send(400, ['ok' => false, 'error' => 'correo inválido']);
        $numeroDb = $numero !== '' ? $numero : null;

        $db = getDB();
        $curStmt = $db->prepare('SELECT id, correo, activo FROM usuarios WHERE id = :id LIMIT 1');
        $curStmt->execute([':id' => $uid]);
        $cur = $curStmt->fetch();
        if (!$cur || empty($cur['activo'])) api_send(401, ['ok' => false, 'error' => 'No autorizado']);

        if (strtolower((string)$cur['correo']) !== $correo) {
            $chk = $db->prepare('SELECT id FROM usuarios WHERE LOWER(correo) = LOWER(:correo) LIMIT 1');
            $chk->execute([':correo' => $correo]);
            $ex = $chk->fetch();
            if ($ex && (int)$ex['id'] !== $uid) api_send(400, ['ok' => false, 'error' => 'correo ya existe']);
        }

        $upd = $db->prepare('UPDATE usuarios SET nombre = :nombre, correo = :correo, numero = :numero, updated_at = NOW() WHERE id = :id');
        $upd->execute([':id' => $uid, ':nombre' => $nombre, ':correo' => $correo, ':numero' => $numeroDb]);

        $stmt = $db->prepare('SELECT id, correo, nombre, numero, rol, activo, avatar_url FROM usuarios WHERE id = :id LIMIT 1');
        $stmt->execute([':id' => $uid]);
        $u = $stmt->fetch();
        if (!$u || empty($u['activo'])) api_send(401, ['ok' => false, 'error' => 'No autorizado']);

        api_send(200, [
            'ok' => true,
            'user' => [
                'id' => (int)$u['id'],
                'uid' => (int)$u['id'],
                'correo' => $u['correo'],
                'nombre' => $u['nombre'],
                'numero' => $u['numero'] ?? null,
                'rol' => $u['rol'],
                'avatar_url' => $u['avatar_url'] ?? null,
            ],
        ]);
    }

    if ($sub === 'avatar') {
        $method2 = $_SERVER['REQUEST_METHOD'] ?? 'GET';
        if ($method2 !== 'POST') {
            api_send(405, ['ok' => false, 'error' => 'Método no permitido']);
        }
        $payload = require_auth();
        $uid = (int)($payload['uid'] ?? $payload['id'] ?? 0);
        if ($uid <= 0) api_send(401, ['ok' => false, 'error' => 'Token inválido']);

        $file = $_FILES['avatar'] ?? $_FILES['file'] ?? null;
        if (!$file || !is_array($file)) api_send(400, ['ok' => false, 'error' => 'archivo requerido']);
        if (!isset($file['error']) || (int)$file['error'] !== UPLOAD_ERR_OK) api_send(400, ['ok' => false, 'error' => 'no se pudo subir archivo']);

        $size = (int)($file['size'] ?? 0);
        if ($size <= 0) api_send(400, ['ok' => false, 'error' => 'archivo inválido']);
        if ($size > 5 * 1024 * 1024) api_send(400, ['ok' => false, 'error' => 'archivo demasiado grande (máx 5MB)']);

        $tmp = (string)($file['tmp_name'] ?? '');
        if ($tmp === '' || !is_uploaded_file($tmp)) api_send(400, ['ok' => false, 'error' => 'archivo inválido']);

        $finfo = new finfo(FILEINFO_MIME_TYPE);
        $mime = (string)($finfo->file($tmp) ?: '');
        $ext = '';
        if ($mime === 'image/jpeg') $ext = 'jpg';
        if ($mime === 'image/png') $ext = 'png';
        if ($mime === 'image/webp') $ext = 'webp';
        if ($ext === '') api_send(400, ['ok' => false, 'error' => 'formato no soportado (jpg/png/webp)']);

        $uploadDir = __DIR__ . '/uploads/avatars';
        if (!is_dir($uploadDir)) {
            if (!mkdir($uploadDir, 0775, true) && !is_dir($uploadDir)) {
                api_send(500, ['ok' => false, 'error' => 'no se pudo crear carpeta de uploads']);
            }
        }

        $rand = bin2hex(random_bytes(8));
        $ts = date('YmdHis');
        $filename = 'u' . $uid . '_' . $ts . '_' . $rand . '.' . $ext;
        $dest = $uploadDir . '/' . $filename;
        if (!move_uploaded_file($tmp, $dest)) api_send(500, ['ok' => false, 'error' => 'no se pudo guardar archivo']);

        $rel = '/uploads/avatars/' . $filename;

        $db = getDB();
        $curStmt = $db->prepare('SELECT avatar_url, activo FROM usuarios WHERE id = :id LIMIT 1');
        $curStmt->execute([':id' => $uid]);
        $cur = $curStmt->fetch();
        if (!$cur || empty($cur['activo'])) api_send(401, ['ok' => false, 'error' => 'No autorizado']);

        $old = trim((string)($cur['avatar_url'] ?? ''));
        if ($old !== '' && strpos($old, '/uploads/avatars/') === 0) {
            $oldName = basename($old);
            $oldPath = $uploadDir . '/' . $oldName;
            if (is_file($oldPath)) {
                @unlink($oldPath);
            }
        }

        $upd = $db->prepare('UPDATE usuarios SET avatar_url = :a, updated_at = NOW() WHERE id = :id');
        $upd->execute([':a' => $rel, ':id' => $uid]);

        api_send(200, ['ok' => true, 'avatar_url' => $rel]);
    }

    api_send(404, ['ok' => false, 'error' => 'No encontrado']);
}

if ($resource === 'organizaciones') {
    require __DIR__ . '/controllers/OrganizacionController.php';
}

if ($resource === 'especies') {
    require __DIR__ . '/controllers/EspecieController.php';
}

if ($resource === 'files') {
    require __DIR__ . '/controllers/FilesController.php';
}

if ($resource === 'evaluaciones') {
    require __DIR__ . '/controllers/EvaluacionController.php';
}

if ($resource === 'usuarios') {
    require __DIR__ . '/controllers/UsuarioController.php';
}

if ($resource === 'sectores') {
    require __DIR__ . '/controllers/SectorController.php';
}

if ($resource === 'botes') {
    require __DIR__ . '/controllers/BoteController.php';
}

if ($resource === 'regiones') {
    require __DIR__ . '/controllers/RegionController.php';
}

if ($resource === 'caletas') {
    require __DIR__ . '/controllers/CaletaController.php';
}

if ($resource === 'caletas') {
    require __DIR__ . '/controllers/CaletaController.php';
}

if ($resource === 'operaciones') {
    require __DIR__ . '/controllers/OperacionController.php';
}

if ($resource === 'backup') {
    require __DIR__ . '/controllers/BackupController.php';
}

api_send(404, ['ok' => false, 'error' => 'No encontrado']);
