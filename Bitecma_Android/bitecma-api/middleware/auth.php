<?php

function jwt_secret()
{
    return getenv('JWT_SECRET') ?: 'cambia_este_secreto_en_cpanel';
}

function b64url_encode($data)
{
    return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}

function b64url_decode($data)
{
    $remainder = strlen($data) % 4;
    if ($remainder) {
        $data .= str_repeat('=', 4 - $remainder);
    }
    return base64_decode(strtr($data, '-_', '+/'));
}

function jwt_encode($payload, $ttlSeconds = 86400)
{
    $now = time();
    $header = ['alg' => 'HS256', 'typ' => 'JWT'];
    $payload = is_array($payload) ? $payload : [];
    $payload['iat'] = $now;
    $payload['exp'] = $now + (int)$ttlSeconds;

    $h = b64url_encode(json_encode($header));
    $p = b64url_encode(json_encode($payload));
    $sig = b64url_encode(hash_hmac('sha256', $h . '.' . $p, jwt_secret(), true));
    return $h . '.' . $p . '.' . $sig;
}

function jwt_decode($token)
{
    $parts = explode('.', (string)$token);
    if (count($parts) !== 3) return null;

    [$h, $p, $s] = $parts;
    $expected = b64url_encode(hash_hmac('sha256', $h . '.' . $p, jwt_secret(), true));
    if (!hash_equals($expected, $s)) return null;

    $payload = json_decode(b64url_decode($p), true);
    if (!$payload || !is_array($payload)) return null;

    $exp = isset($payload['exp']) ? (int)$payload['exp'] : 0;
    if ($exp && time() > $exp) return null;

    return $payload;
}

function get_bearer_token()
{
    $hdr = '';

    if (isset($_SERVER['HTTP_AUTHORIZATION']) && $_SERVER['HTTP_AUTHORIZATION']) $hdr = $_SERVER['HTTP_AUTHORIZATION'];
    if (!$hdr && isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION']) && $_SERVER['REDIRECT_HTTP_AUTHORIZATION']) $hdr = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    if (!$hdr && isset($_SERVER['Authorization']) && $_SERVER['Authorization']) $hdr = $_SERVER['Authorization'];

    if (!$hdr && function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        if (is_array($headers)) {
            foreach ($headers as $k => $v) {
                if (strtolower((string)$k) === 'authorization') {
                    $hdr = (string)$v;
                    break;
                }
            }
        }
    }

    $hdr = trim((string)$hdr);
    if (!$hdr) return null;
    if (stripos($hdr, 'Bearer ') !== 0) return null;
    return trim(substr($hdr, 7));
}

function require_auth()
{
    $token = get_bearer_token();
    if (!$token) {
        http_response_code(401);
        echo json_encode(['ok' => false, 'error' => 'No autorizado']);
        exit;
    }
    $payload = jwt_decode($token);
    if (!$payload) {
        http_response_code(401);
        echo json_encode(['ok' => false, 'error' => 'Token inválido']);
        exit;
    }
    return $payload;
}

