local userId = ARGV[1]
local now = tonumber(ARGV[2])

-- ============================================================
-- 1. 이미 RESERVING 상태인지 먼저 확인
--
-- 같은 admission을 두 요청이 동시에 사용하려 할 경우
-- 첫 번째 요청이 ADMITTED -> RESERVING으로 이동한 뒤
-- 두 번째 요청은 여기에서 차단된다.
-- ============================================================

local reservingScore = redis.call(
    'ZSCORE',
    KEYS[2],
    userId
)

if reservingScore then

    local reservingExpiresAt = tonumber(
        reservingScore
    )

    -- 아직 유효한 RESERVING 상태
    if reservingExpiresAt > now then

        return {
            2,
            reservingExpiresAt
        }
    end

    -- RESERVING 상태 자체가 이미 만료된 경우
    -- stale state를 제거한다.
    redis.call(
        'ZREM',
        KEYS[2],
        userId
    )
end


-- ============================================================
-- 2. ADMITTED 상태 확인
-- ============================================================

local admittedScore = redis.call(
    'ZSCORE',
    KEYS[1],
    userId
)

-- admission 자체가 없음
if not admittedScore then

    return {
        0,
        -1
    }
end


local expiresAt = tonumber(
    admittedScore
)


-- ============================================================
-- 3. admission 만료 확인
-- ============================================================

if expiresAt <= now then

    redis.call(
        'ZREM',
        KEYS[1],
        userId
    )

    return {
        0,
        -1
    }
end


-- ============================================================
-- 4. ADMITTED -> RESERVING
--
-- Lua script 전체가 Redis에서 atomic하게 실행되므로
-- 다른 요청이 ZADD와 ZREM 사이에 끼어들 수 없다.
--
-- admission의 기존 expiration score를 그대로 유지한다.
-- Reservation 실패 시 동일 expiration으로 복구하기 위함이다.
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


return {
    1,
    expiresAt
}