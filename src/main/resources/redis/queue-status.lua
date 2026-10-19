local waitingCount = redis.call(
    'ZCARD',
    KEYS[1]
)

local admittedScore = redis.call(
    'ZSCORE',
    KEYS[2],
    ARGV[1]
)

local now = tonumber(ARGV[2])

-- 아직 만료되지 않은 admission이 존재
if admittedScore
    and tonumber(admittedScore) > now then

    return {
        2,
        -1,
        waitingCount
    }
end

local rank = redis.call(
    'ZRANK',
    KEYS[1],
    ARGV[1]
)

if rank then

    return {
        1,
        rank,
        waitingCount
    }
end

return {
    0,
    -1,
    waitingCount
}