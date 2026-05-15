<?php

require_once __DIR__ . '/../models/Caleta.php';

function cal_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

$db = getDB();
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

// Solo permitimos GET para caletas en este controlador modular
if ($method !== 'GET') {
    cal_send(405, ['ok' => false, 'error' => 'Método no permitido']);
}

if ($id !== null && $id !== '') {
    $found = Caleta::find($db, $id);
    if (!$found) cal_send(404, ['ok' => false, 'error' => 'No encontrado']);
    cal_send(200, ['ok' => true, 'data' => $found]);
}

cal_send(200, ['ok' => true, 'data' => Caleta::all($db)]);
