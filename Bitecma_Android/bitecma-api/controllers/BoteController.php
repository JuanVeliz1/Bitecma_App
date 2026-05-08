<?php

require_once __DIR__ . '/../models/Bote.php';

function bote_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function bote_send($status, $payload)
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
        $found = Bote::find($db, $id);
        if (!$found) bote_send(404, ['ok' => false, 'error' => 'No encontrado']);
        bote_send(200, ['ok' => true, 'data' => $found]);
    }
    bote_send(200, ['ok' => true, 'data' => Bote::all($db)]);
}

if ($method === 'POST') {
    $data = bote_json_body();
    $created = Bote::create($db, $data);
    if (is_array($created) && isset($created['error'])) bote_send(400, ['ok' => false, 'error' => $created['error']]);
    bote_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') bote_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = bote_json_body();
    $updated = Bote::update($db, $id, $data);
    if ($updated === null) bote_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) bote_send(400, ['ok' => false, 'error' => $updated['error']]);
    bote_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') bote_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Bote::delete($db, $id);
    if (!$ok) bote_send(404, ['ok' => false, 'error' => 'No encontrado']);
    bote_send(200, ['ok' => true]);
}

bote_send(405, ['ok' => false, 'error' => 'Método no permitido']);

