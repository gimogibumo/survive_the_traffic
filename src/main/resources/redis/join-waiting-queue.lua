local existingScore = redis.call(
    'ZSCORE',
    KEYS[2],
    ARGV[1]
)

-- 이미 waiting에 있는 사용자
if existingScore then

    -- active registry가 혹시 유실됐더라도 복구
    redis.call(
        'SADD',
        KEYS[3],
        ARGV[2]
    )

    local rank = redis.call(
        'ZRANK',
        KEYS[2],
        ARGV[1]
    )

    local waitingCount = redis.call(
        'ZCARD',
        KEYS[2]
    )

    return {
        0,
        rank,
        waitingCount
    }
end

-- 신규 사용자
redis.call(
    'SADD',
    KEYS[3],
    ARGV[2]
)

local sequence = redis.call(
    'INCR',
    KEYS[1]
)

redis.call(
    'ZADD',
    KEYS[2],
    sequence,
    ARGV[1]
)

local rank = redis.call(
    'ZRANK',
    KEYS[2],
    ARGV[1]
)

local waitingCount = redis.call(
    'ZCARD',
    KEYS[2]
)

return {
    1,
    rank,
    waitingCount
}