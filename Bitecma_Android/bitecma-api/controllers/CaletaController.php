<?php

require_once __DIR__ . '/../models/Caleta.php';

function cal_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function cal_send($status, $payload)
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

if ($id !== null && $id !== '') {
    if ($method === 'GET') {
        $found = Caleta::find($db, $id);
        if (!$found) cal_send(404, ['ok' => false, 'error' => 'No encontrado']);
        cal_send(200, ['ok' => true, 'data' => $found]);
    }
}

if ($method === 'GET') {
    cal_send(200, ['ok' => true, 'data' => Caleta::all($db)]);
}

if ($method === 'POST') {
    $data = cal_json_body();
    $created = Caleta::create($db, $data);
    if (is_array($created) && isset($created['error'])) cal_send(400, ['ok' => false, 'error' => $created['error']]);
    cal_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') cal_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = cal_json_body();
    $updated = Caleta::update($db, $id, $data);
    if ($updated === null) cal_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) cal_send(400, ['ok' => false, 'error' => $updated['error']]);
    cal_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') cal_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Caleta::delete($db, $id);
    if (!$ok) cal_send(404, ['ok' => false, 'error' => 'No encontrado']);
    cal_send(200, ['ok' => true]);
}

cal_send(405, ['ok' => false, 'error' => 'Método no permitido']);
