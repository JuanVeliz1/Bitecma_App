<?php

class Usuario
{
    private static function normKey($s)
    {
        $s = strtolower(trim((string)$s));
        $s = preg_replace('/[^a-z0-9]+/', '', (string)$s);
        return $s ?: '';
    }

    private static function normalizeRole($rol)
    {
        $r = self::normKey($rol);
        if ($r === 'admin') return 'Admin';
        if ($r === 'usuario' || $r === 'biologo' || $r === 'biologa') return 'Usuario';
        if ($r === 'visualizador' || $r === 'viewer' || $r === 'readonly') return 'Visualizador';
        $t = trim((string)$rol);
        return $t !== '' ? $t : 'Usuario';
    }

    private static function toPublicRow($r)
    {
        if (!$r) return null;
        return [
            'id' => (int)$r['id'],
            'correo' => $r['correo'],
            'nombre' => $r['nombre'],
            'numero' => $r['numero'] ?? null,
            'rol' => $r['rol'],
            'activo' => (bool)$r['activo'],
            'avatar_url' => $r['avatar_url'] ?? null,
            'created_at' => $r['created_at'] ?? null,
            'updated_at' => $r['updated_at'] ?? null,
        ];
    }

    private static function findRow(PDO $db, $id)
    {
        $stmt = $db->prepare("SELECT id, correo, nombre, numero, rol, activo, avatar_url, created_at, updated_at, password_hash FROM usuarios WHERE id = :id LIMIT 1");
        $stmt->execute([':id' => (int)$id]);
        $r = $stmt->fetch();
        return $r ?: null;
    }

    private static function findByCorreo(PDO $db, $correo)
    {
        $stmt = $db->prepare("SELECT id, correo, nombre, numero, rol, activo, avatar_url, created_at, updated_at, password_hash FROM usuarios WHERE LOWER(correo) = LOWER(:correo) LIMIT 1");
        $stmt->execute([':correo' => (string)$correo]);
        $r = $stmt->fetch();
        return $r ?: null;
    }

    public static function all(PDO $db)
    {
        $stmt = $db->query("SELECT id, correo, nombre, numero, rol, activo, avatar_url, created_at, updated_at FROM usuarios ORDER BY id ASC");
        $rows = $stmt->fetchAll();
        return array_values(array_filter(array_map(function ($r) {
            return self::toPublicRow($r);
        }, $rows)));
    }

    public static function find(PDO $db, $id)
    {
        $r = self::findRow($db, $id);
        return self::toPublicRow($r);
    }

    public static function create(PDO $db, array $data)
    {
        $nombre = trim((string)($data['nombre'] ?? ''));
        $correo = strtolower(trim((string)($data['correo'] ?? $data['email'] ?? '')));
        $numero = trim((string)($data['numero'] ?? '')) ?: null;
        $avatarUrl = trim((string)($data['avatar_url'] ?? '')) ?: null;
        $rol = self::normalizeRole($data['rol'] ?? 'Usuario');
        $activo = array_key_exists('activo', $data) ? (bool)$data['activo'] : true;
        $password = (string)($data['password'] ?? '');

        if ($nombre === '') return ['error' => 'nombre requerido'];
        if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) return ['error' => 'correo inválido'];
        if ($password === '' || strlen($password) < 8) return ['error' => 'password inválido'];

        $existing = self::findByCorreo($db, $correo);
        if ($existing) return ['error' => 'correo ya existe'];

        $hash = password_hash($password, PASSWORD_DEFAULT);
        if (!$hash) return ['error' => 'no se pudo generar hash'];

        $stmt = $db->prepare(
            "INSERT INTO usuarios (correo, nombre, numero, rol, activo, avatar_url, password_hash)
             VALUES (:correo, :nombre, :numero, :rol, :activo, :avatar_url, :password_hash)"
        );
        $stmt->execute([
            ':correo' => $correo,
            ':nombre' => $nombre,
            ':numero' => $numero,
            ':rol' => $rol,
            ':activo' => $activo ? 1 : 0,
            ':avatar_url' => $avatarUrl,
            ':password_hash' => $hash,
        ]);

        $newId = (int)$db->lastInsertId();
        if ($newId > 0) return self::find($db, $newId);
        $r = self::findByCorreo($db, $correo);
        return self::toPublicRow($r);
    }

    public static function update(PDO $db, $id, array $data)
    {
        $cur = self::findRow($db, $id);
        if (!$cur) return null;

        $nombre = array_key_exists('nombre', $data) ? trim((string)$data['nombre']) : $cur['nombre'];
        $correo = array_key_exists('correo', $data) ? strtolower(trim((string)$data['correo'])) : $cur['correo'];
        $numero = array_key_exists('numero', $data) ? (trim((string)$data['numero']) ?: null) : ($cur['numero'] ?? null);
        $avatarUrl = array_key_exists('avatar_url', $data) ? (trim((string)$data['avatar_url']) ?: null) : ($cur['avatar_url'] ?? null);
        $rol = array_key_exists('rol', $data) ? self::normalizeRole($data['rol']) : $cur['rol'];
        $activo = array_key_exists('activo', $data) ? (bool)$data['activo'] : (bool)$cur['activo'];
        $password = array_key_exists('password', $data) ? (string)$data['password'] : '';

        if ($nombre === '') return ['error' => 'nombre requerido'];
        if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) return ['error' => 'correo inválido'];
        if ($password !== '' && strlen($password) < 8) return ['error' => 'password inválido'];

        if (strtolower((string)$correo) !== strtolower((string)$cur['correo'])) {
            $existing = self::findByCorreo($db, $correo);
            if ($existing && (int)$existing['id'] !== (int)$cur['id']) return ['error' => 'correo ya existe'];
        }

        $hash = (string)$cur['password_hash'];
        if ($password !== '') {
            $newHash = password_hash($password, PASSWORD_DEFAULT);
            if (!$newHash) return ['error' => 'no se pudo generar hash'];
            $hash = $newHash;
        }

        $stmt = $db->prepare(
            "UPDATE usuarios
             SET correo = :correo,
                 nombre = :nombre,
                 numero = :numero,
                 rol = :rol,
                 activo = :activo,
                 avatar_url = :avatar_url,
                 password_hash = :password_hash
             WHERE id = :id"
        );
        $stmt->execute([
            ':id' => (int)$id,
            ':correo' => $correo,
            ':nombre' => $nombre,
            ':numero' => $numero,
            ':rol' => $rol,
            ':activo' => $activo ? 1 : 0,
            ':avatar_url' => $avatarUrl,
            ':password_hash' => $hash,
        ]);

        return self::find($db, $id);
    }

    public static function delete(PDO $db, $id)
    {
        $stmt = $db->prepare("DELETE FROM usuarios WHERE id = :id");
        $stmt->execute([':id' => (int)$id]);
        return $stmt->rowCount() > 0;
    }
}
