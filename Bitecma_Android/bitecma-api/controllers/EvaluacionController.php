<?php

require_once __DIR__ . '/../models/Evaluacion.php';

function ev_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function ev_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

$db = getDB();
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$authPayload = null;

if ($method !== 'GET') {
    require_once __DIR__ . '/../middleware/auth.php';
    $authPayload = require_auth();
}

if ($method === 'GET') {
    if ($id !== null && $id !== '') {
        $found = Evaluacion::find($db, $id);
        if (!$found) ev_send(404, ['ok' => false, 'error' => 'No encontrado']);
        ev_send(200, ['ok' => true, 'data' => $found]);
    }
    ev_send(200, ['ok' => true, 'data' => Evaluacion::all($db)]);
}

if ($method === 'POST') {
    $data = ev_json_body();
    $createdBy = is_array($authPayload) && isset($authPayload['uid']) ? (int)$authPayload['uid'] : null;
    $created = Evaluacion::create($db, $data, $createdBy);
    if (is_array($created) && isset($created['error'])) ev_send(400, ['ok' => false, 'error' => $created['error']]);
    ev_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') ev_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = ev_json_body();
    $updated = Evaluacion::update($db, $id, $data);
    if ($updated === null) ev_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) ev_send(400, ['ok' => false, 'error' => $updated['error']]);
    ev_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') ev_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Evaluacion::delete($db, $id);
    if (!$ok) ev_send(404, ['ok' => false, 'error' => 'No encontrado']);
    ev_send(200, ['ok' => true]);
}

ev_send(405, ['ok' => false, 'error' => 'Método no permitido']);

