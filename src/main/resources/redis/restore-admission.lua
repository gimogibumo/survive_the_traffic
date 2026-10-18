local userId = ARGV[1]
local now = tonumber(ARGV[2])


-- ============================================================
-- 1. RESERVING 상태 확인
-- ============================================================

local reservingScore = redis.call(
    'ZSCORE',
    KEYS[1],
    userId
)

-- 이미 RESERVING 상태가 아니라면 복구할 것이 없다.
if not reservingScore then

    return 0
end


local expiresAt = tonumber(
    reservingScore
)


-- ============================================================
-- 2. 원래 admission expiration 확인
--
-- Reservation이 실패했더라도 원래 admission이 이미
-- 만료되었다면 다시 ADMITTED 상태로 복구하지 않는다.
-- ============================================================

if expiresAt <= now then

    redis.call(
        'ZREM',
        KEYS[1],
        userId
    )

    return 2
end


-- ============================================================
-- 3. RESERVING -> ADMITTED 복구
--
-- 기존 expiration score를 그대로 사용한다.
--
-- 새로운 5분을 부여하면 Reservation 실패를 반복하면서
-- admission lifetime을 무한히 연장할 수 있기 때문이다.
--
-- user-loss보다 temporary duplicate state가 안전하므로
-- ADMITTED에 먼저 추가한 뒤 RESERVING에서 제거한다.
--
-- 단, Lua script 자체가 atomic하기 때문에 정상 실행 중에는
-- 외부 command가 중간 상태를 관찰할 수 없다.
-- ============================================================

redis.call(
    'ZADD',
    KEYS[2],
    expiresAt,
    userId
)

redis.call(
    'ZREM',
    KEYS[1],
    userId
)


return 1