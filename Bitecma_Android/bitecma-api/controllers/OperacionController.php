<?php

require_once __DIR__ . '/../models/Operacion.php';

function op_json_body()
{
    $raw = file_get_contents('php://input');
    if (!$raw) return [];
    $data = json_decode($raw, true);
    return $data && is_array($data) ? $data : [];
}

function op_send($status, $payload)
{
    http_response_code($status);
    echo json_encode($payload);
    exit;
}

function op_send_db_error(Throwable $e)
{
    if ($e instanceof PDOException) {
        $info = is_array($e->errorInfo ?? null) ? $e->errorInfo : null;
        $driverCode = is_array($info) ? (int)($info[1] ?? 0) : 0;
        if ($driverCode === 1451) op_send(409, ['ok' => false, 'error' => 'No se puede completar la operación: existen registros relacionados.']);
        if ($driverCode === 1452) op_send(400, ['ok' => false, 'error' => 'Referencia inválida: el registro relacionado no existe.']);
        if ($driverCode === 1062) op_send(409, ['ok' => false, 'error' => 'Conflicto: registro duplicado.']);
    }
    op_send(500, ['ok' => false, 'error' => 'Error guardando operación']);
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
        $found = Operacion::find($db, $id);
        if (!$found) op_send(404, ['ok' => false, 'error' => 'No encontrado']);
        op_send(200, ['ok' => true, 'data' => $found]);
    }
    op_send(200, ['ok' => true, 'data' => Operacion::all($db)]);
}

if ($method === 'POST') {
    $data = op_json_body();
    $createdBy = is_array($authPayload) && isset($authPayload['uid']) ? (int)$authPayload['uid'] : null;
    try {
        $db->beginTransaction();
        $created = Operacion::create($db, $data, $createdBy);
        if (is_array($created) && isset($created['error'])) {
            $db->rollBack();
            op_send(400, ['ok' => false, 'error' => $created['error']]);
        }
        $db->commit();
        op_send(201, ['ok' => true, 'data' => $created]);
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        op_send_db_error($e);
    }
}

if ($method === 'PUT') {
    if ($id === null || $id === '') op_send(400, ['ok' => false, 'error' => 'id requerido']);
    $data = op_json_body();
    try {
        $db->beginTransaction();
        $updated = Operacion::update($db, $id, $data);
        if ($updated === null) {
            $db->rollBack();
            op_send(404, ['ok' => false, 'error' => 'No encontrado']);
        }
        if (is_array($updated) && isset($updated['error'])) {
            $db->rollBack();
            op_send(400, ['ok' => false, 'error' => $updated['error']]);
        }
        $db->commit();
        op_send(200, ['ok' => true, 'data' => $updated]);
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        op_send_db_error($e);
    }
}

if ($method === 'DELETE') {
    if ($id === null || $id === '') op_send(400, ['ok' => false, 'error' => 'id requerido']);
    try {
        $db->beginTransaction();
        $ok = Operacion::delete($db, $id);
        if (!$ok) {
            $db->rollBack();
            op_send(404, ['ok' => false, 'error' => 'No encontrado']);
        }
        $db->commit();
        op_send(200, ['ok' => true]);
    } catch (Throwable $e) {
        if ($db->inTransaction()) $db->rollBack();
        if ($e instanceof PDOException) {
            $info = is_array($e->errorInfo ?? null) ? $e->errorInfo : null;
            $driverCode = is_array($info) ? (int)($info[1] ?? 0) : 0;
            if ($driverCode === 1451) op_send(409, ['ok' => false, 'error' => 'No se puede eliminar: existen registros relacionados.']);
        }
        op_send(500, ['ok' => false, 'error' => 'Error eliminando operación']);
    }
}

op_send(405, ['ok' => false, 'error' => 'Método no permitido']);
