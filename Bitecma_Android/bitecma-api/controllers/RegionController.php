<?php

require_once __DIR__ . '/../models/Region.php';

function reg_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

$db = getDB();
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method !== 'GET') {
    reg_send(405, ['ok' => false, 'error' => 'Método no permitido']);
}

if ($id !== null && $id !== '') {
    $found = Region::find($db, $id);
    if (!$found) reg_send(404, ['ok' => false, 'error' => 'No encontrado']);
    reg_send(200, ['ok' => true, 'data' => $found]);
}

reg_send(200, ['ok' => true, 'data' => Region::all($db)]);

