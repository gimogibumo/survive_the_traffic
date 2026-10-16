local existingScore = redis.call(
    'ZSCORE',
    KEYS[2],
    ARGV[1]
)

if existingScore then
    redis.call(
        'SADD',
        KEYS[3],
        ARGV[2]
    )

    return {
        0,
        existingScore
    }
end

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

return {
    1,
    sequence
}