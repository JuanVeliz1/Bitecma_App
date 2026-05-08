<?php

require_once __DIR__ . '/../models/Especie.php';

function esp_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function esp_send($status, $payload)
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
        $found = Especie::find($db, $id);
        if (!$found) esp_send(404, ['ok' => false, 'error' => 'No encontrado']);
        esp_send(200, ['ok' => true, 'data' => $found]);
    }
    esp_send(200, ['ok' => true, 'data' => Especie::all($db)]);
}

if ($method === 'POST') {
    $data = esp_json_body();
    $created = Especie::create($db, $data);
    if (is_array($created) && isset($created['error'])) esp_send(400, ['ok' => false, 'error' => $created['error']]);
    esp_send(201, ['ok' => true, 'data' => $created]);
}

if ($method === 'PUT') {
    if ($id === null || $id === '') esp_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = esp_json_body();
    $updated = Especie::update($db, $id, $data);
    if ($updated === null) esp_send(404, ['ok' => false, 'error' => 'No encontrado']);
    if (is_array($updated) && isset($updated['error'])) esp_send(400, ['ok' => false, 'error' => $updated['error']]);
    esp_send(200, ['ok' => true, 'data' => $updated]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') esp_send(400, ['ok' => false, 'error' => 'id requerido']);
    $ok = Especie::delete($db, $id);
    if (!$ok) esp_send(404, ['ok' => false, 'error' => 'No encontrado']);
    esp_send(200, ['ok' => true]);
}

esp_send(405, ['ok' => false, 'error' => 'Método no permitido']);

