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
        $stmt = $db->prepare("SELECT id, correo, nombre, rol, activo, password_hash FROM usuarios WHERE LOWER(correo) = LOWER(:correo) LIMIT 1");
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

        $payload = [
            'uid' => (int)$u['id'],
            'correo' => $u['correo'],
            'nombre' => $u['nombre'],
            'rol' => $u['rol'],
        ];
        $token = jwt_encode($payload, 60 * 60 * 24 * 30);
        api_send(200, [
            'ok' => true,
            'token' => $token,
            'user' => $payload,
        ]);
    }

    if ($sub === 'me') {
        if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'GET') {
            api_send(405, ['ok' => false, 'error' => 'Método no permitido']);
        }
        $payload = require_auth();
        api_send(200, ['ok' => true, 'user' => $payload]);
    }

    api_send(404, ['ok' => false, 'error' => 'No encontrado']);
}

if ($resource === 'organizaciones') {
    require __DIR__ . '/controllers/OrganizacionController.php';
}

if ($resource === 'especies') {
    require __DIR__ . '/controllers/EspecieController.php';
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

if ($resource === 'caletas') {
    require __DIR__ . '/controllers/CaletaController.php';
}

if ($resource === 'botes') {
    require __DIR__ . '/controllers/BoteController.php';
}

if ($resource === 'regiones') {
    require __DIR__ . '/controllers/RegionController.php';
}

if ($resource === 'operaciones') {
    require __DIR__ . '/controllers/OperacionController.php';
}

if ($resource === 'files') {
    require __DIR__ . '/controllers/FilesController.php';
}

api_send(404, ['ok' => false, 'error' => 'No encontrado']);
