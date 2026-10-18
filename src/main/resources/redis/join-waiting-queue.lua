local sequenceKey = KEYS[1]
local waitingKey = KEYS[2]
local activeConcertsKey = KEYS[3]
local admittedKey = KEYS[4]

local userId = ARGV[1]
local concertId = ARGV[2]
local now = tonumber(ARGV[3])


-- =========================================================
-- 1. ADMITTED 상태 확인
-- =========================================================

local admittedScore =
    redis.call(
        'ZSCORE',
        admittedKey,
        userId
    )

if admittedScore then

    local expiresAt =
        tonumber(admittedScore)

    if expiresAt > now then

        -- 이미 admission 받은 공연이면
        -- active registry가 유실된 경우도 복구한다.
        redis.call(
            'SADD',
            activeConcertsKey,
            concertId
        )

        local waitingCount =
            redis.call(
                'ZCARD',
                waitingKey
            )

        -- 2 = ALREADY_ADMITTED
        -- waiting rank가 없으므로 -1
        return {
            2,
            -1,
            waitingCount
        }
    end


    -- admission이 만료된 stale entry라면 제거한다.
    redis.call(
        'ZREM',
        admittedKey,
        userId
    )
end


-- =========================================================
-- 2. WAITING 상태 확인
-- =========================================================

local existingWaitingScore =
    redis.call(
        'ZSCORE',
        waitingKey,
        userId
    )

if existingWaitingScore then

    -- active registry가 유실된 경우 복구한다.
    redis.call(
        'SADD',
        activeConcertsKey,
        concertId
    )

    local rank =
        redis.call(
            'ZRANK',
            waitingKey,
            userId
        )

    local waitingCount =
        redis.call(
            'ZCARD',
            waitingKey
        )

    -- 1 = ALREADY_WAITING
    return {
        1,
        rank,
        waitingCount
    }
end


-- =========================================================
-- 3. 신규 JOIN
-- =========================================================

redis.call(
    'SADD',
    activeConcertsKey,
    concertId
)


local sequence =
    redis.call(
        'INCR',
        sequenceKey
    )


redis.call(
    'ZADD',
    waitingKey,
    sequence,
    userId
)


local rank =
    redis.call(
        'ZRANK',
        waitingKey,
        userId
    )


local waitingCount =
    redis.call(
        'ZCARD',
        waitingKey
    )


-- 0 = JOINED
return {
    0,
    rank,
    waitingCount
}