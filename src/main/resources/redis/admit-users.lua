local count = tonumber(ARGV[1])

if not count or count <= 0 then
    return {}
end

local expiresAt = ARGV[2]

local users = redis.call(
    'ZRANGE',
    KEYS[1],
    0,
    count - 1
)

if #users == 0 then
    return {}
end

local args = {}

for i, userId in ipairs(users) do
    table.insert(args, expiresAt)
    table.insert(args, userId)
end

redis.call(
    'ZADD',
    KEYS[2],
    unpack(args)
)

redis.call(
    'ZREM',
    KEYS[1],
    unpack(users)
)

return users