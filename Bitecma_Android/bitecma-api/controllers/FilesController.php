<?php

function files_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

function files_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function files_storage_dir()
{
    return __DIR__ . '/../storage/files';
}

function files_index_path()
{
    return files_storage_dir() . '/index.json';
}

function files_ensure_storage()
{
    $dir = files_storage_dir();
    if (!is_dir($dir)) {
        @mkdir($dir, 0775, true);
    }
    $idx = files_index_path();
    if (!is_file($idx)) {
        @file_put_contents($idx, json_encode([]));
    }
}

function files_read_index()
{
    files_ensure_storage();
    $raw = @file_get_contents(files_index_path());
    $arr = $raw ? json_decode($raw, true) : [];
    return is_array($arr) ? $arr : [];
}

function files_write_index($arr)
{
    files_ensure_storage();
    @file_put_contents(files_index_path(), json_encode(array_values($arr)));
}

function files_new_id()
{
    try {
        return bin2hex(random_bytes(12));
    } catch (Throwable $e) {
        return bin2hex(openssl_random_pseudo_bytes(12));
    }
}

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

if ($method !== 'GET') {
    require_once __DIR__ . '/../middleware/auth.php';
    require_auth();
}

if ($method === 'GET') {
    $opFilter = isset($_GET['opId']) ? trim((string)$_GET['opId']) : null;
    $all = files_read_index();
    if ($opFilter !== null && $opFilter !== '') {
        $all = array_values(array_filter($all, function ($x) use ($opFilter) {
            return isset($x['opId']) && (string)$x['opId'] === (string)$opFilter;
        }));
    }

    if ($id !== null && $id !== '') {
        $found = null;
        foreach ($all as $x) {
            if ((string)($x['id'] ?? '') === (string)$id) {
                $found = $x;
                break;
            }
        }
        if (!$found) files_send(404, ['ok' => false, 'error' => 'No encontrado']);
        $path = files_storage_dir() . '/' . $found['id'] . '.txt';
        if (!is_file($path)) files_send(404, ['ok' => false, 'error' => 'Archivo no encontrado']);
        $text = (string)@file_get_contents($path);
        files_send(200, ['ok' => true, 'data' => [
            'id' => $found['id'],
            'name' => $found['name'],
            'mime' => $found['mime'],
            'text' => $text,
        ]]);
    }

    files_send(200, ['ok' => true, 'data' => $all]);
}

if ($method === 'POST') {
    $body = files_json_body();
    $name = trim((string)($body['name'] ?? 'archivo.txt'));
    if ($name === '') $name = 'archivo.txt';
    $opId = isset($body['opId']) ? trim((string)$body['opId']) : null;
    $text = (string)($body['text'] ?? '');
    $mime = 'text/plain';

    if (mb_strlen($text, '8bit') > 1024 * 1024) {
        files_send(413, ['ok' => false, 'error' => 'Archivo demasiado grande']);
    }

    $fid = files_new_id();
    files_ensure_storage();
    $path = files_storage_dir() . '/' . $fid . '.txt';
    $ok = @file_put_contents($path, $text);
    if ($ok === false) files_send(500, ['ok' => false, 'error' => 'No se pudo guardar']);

    $entry = [
        'id' => $fid,
        'name' => $name,
        'mime' => $mime,
        'size' => mb_strlen($text, '8bit'),
        'opId' => $opId !== '' ? $opId : null,
        'createdAt' => gmdate('c'),
    ];

    $idx = files_read_index();
    array_unshift($idx, $entry);
    files_write_index($idx);

    files_send(201, ['ok' => true, 'data' => $entry]);
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') files_send(400, ['ok' => false, 'error' => 'id requerido']);
    $idx = files_read_index();
    $kept = [];
    $removed = null;
    foreach ($idx as $x) {
        if ($removed === null && (string)($x['id'] ?? '') === (string)$id) {
            $removed = $x;
            continue;
        }
        $kept[] = $x;
    }
    if (!$removed) files_send(404, ['ok' => false, 'error' => 'No encontrado']);
    $path = files_storage_dir() . '/' . $removed['id'] . '.txt';
    if (is_file($path)) @unlink($path);
    files_write_index($kept);
    files_send(200, ['ok' => true]);
}

files_send(405, ['ok' => false, 'error' => 'Método no permitido']);

