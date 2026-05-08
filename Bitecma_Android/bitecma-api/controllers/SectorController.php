<?php

require_once __DIR__ . '/../models/Sector.php';

function sec_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function sec_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

$db = getDB();
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method !== 'GET') {
    require_once __DIR__ . '/../middleware/auth.php';
    require_auth();
}

if ($method === 'GET') {
    if ($id !== null && $id !== '') {
        $found = Sector::find($db, $id);
        if (!$found) sec_send(404, ['ok' => false, 'error' => 'No encontrado']);
        sec_send(200, ['ok' => true, 'data' => $found]);
    }
    sec_send(200, ['ok' => true, 'data' => Sector::all($db)]);
}

if ($method === 'POST') {
    $data = sec_json_body();
    $created = Sector::create($db, $data);
    if (is_array($created) && isset($created['error'])) sec_send(400, ['ok' => false, 'error' => $created['error']]);
    sec_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') sec_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = sec_json_body();
    $updated = Sector::update($db, $id, $data);
    if ($updated === null) sec_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) sec_send(400, ['ok' => false, 'error' => $updated['error']]);
    sec_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') sec_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Sector::delete($db, $id);
    if (!$ok) sec_send(404, ['ok' => false, 'error' => 'No encontrado']);
    sec_send(200, ['ok' => true]);
}

sec_send(405, ['ok' => false, 'error' => 'Método no permitido']);

