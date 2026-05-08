<?php

require_once __DIR__ . '/../models/Usuario.php';
require_once __DIR__ . '/../middleware/auth.php';

function usr_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function usr_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

function usr_require_admin()
{
    $payload = require_auth();
    $rol = strtolower(trim((string)($payload['rol'] ?? '')));
    if ($rol !== 'admin') {
        usr_send(403, ['ok' => false, 'error' => 'Acceso restringido']);
    }
    return $payload;
}

$db = getDB();
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
usr_require_admin();

if ($method === 'GET') {
    if ($id !== null && $id !== '') {
        $found = Usuario::find($db, $id);
        if (!$found) usr_send(404, ['ok' => false, 'error' => 'No encontrado']);
        usr_send(200, ['ok' => true, 'data' => $found]);
    }
    usr_send(200, ['ok' => true, 'data' => Usuario::all($db)]);
}

if ($method === 'POST') {
    $data = usr_json_body();
    $created = Usuario::create($db, $data);
    if (is_array($created) && isset($created['error'])) usr_send(400, ['ok' => false, 'error' => $created['error']]);
    usr_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') usr_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = usr_json_body();
    $updated = Usuario::update($db, $id, $data);
    if ($updated === null) usr_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) usr_send(400, ['ok' => false, 'error' => $updated['error']]);
    usr_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') usr_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Usuario::delete($db, $id);
    if (!$ok) usr_send(404, ['ok' => false, 'error' => 'No encontrado']);
    usr_send(200, ['ok' => true]);
}

usr_send(405, ['ok' => false, 'error' => 'Método no permitido']);

