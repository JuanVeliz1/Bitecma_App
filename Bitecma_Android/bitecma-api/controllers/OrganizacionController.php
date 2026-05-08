<?php

require_once __DIR__ . '/../models/Organizacion.php';

function org_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function org_send($status, $payload)
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
        $found = Organizacion::find($db, $id);
        if (!$found) org_send(404, ['ok' => false, 'error' => 'No encontrado']);
        org_send(200, ['ok' => true, 'data' => $found]);
    }
    org_send(200, ['ok' => true, 'data' => Organizacion::all($db)]);
}

if ($method === 'POST') {
    $data = org_json_body();
    $created = Organizacion::create($db, $data);
    if (is_array($created) && isset($created['error'])) org_send(400, ['ok' => false, 'error' => $created['error']]);
    org_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') org_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = org_json_body();
    $updated = Organizacion::update($db, $id, $data);
    if ($updated === null) org_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) org_send(400, ['ok' => false, 'error' => $updated['error']]);
    org_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') org_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Organizacion::delete($db, $id);
    if (!$ok) org_send(404, ['ok' => false, 'error' => 'No encontrado']);
    org_send(200, ['ok' => true]);
}

org_send(405, ['ok' => false, 'error' => 'Método no permitido']);

