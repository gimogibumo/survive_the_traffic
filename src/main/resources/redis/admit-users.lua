local count = tonumber(ARGV[1])
local expiresAt = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local waitingKey = KEYS[1]
local admittedKey = KEYS[2]


-- =========================================================
-- 1. argument validation
-- =========================================================

if not count or count <= 0 then
    return {}
end

if not expiresAt then
    return {}
end

if not now then
    return {}
end


-- =========================================================
-- 2. expired admission cleanup
--
-- admitted ZSET:
--
-- member = userId
-- score  = admission expiresAt epoch millis
--
-- 따라서
--
-- expiresAt <= now
--
-- 인 모든 member는 더 이상 유효한 admission이 아니다.
-- =========================================================

redis.call(
    'ZREMRANGEBYSCORE',
    admittedKey,
    '-inf',
    now
)


-- =========================================================
-- 3. waiting queue에서 이번 batch 대상 조회
-- =========================================================

local users = redis.call(
    'ZRANGE',
    waitingKey,
    0,
    count - 1
)


-- =========================================================
-- 4. 새롭게 admit할 사용자가 없는 경우
--
-- 이미 admitted에 살아 있는 member가 있을 수도 있다.
--
-- 특히 이 코드 배포 이전에 생성된 Redis key는
-- key-level TTL이 없을 수 있으므로,
-- 현재 남아 있는 admission의 가장 늦은 expiresAt을 찾아
-- key expiration을 복구한다.
-- =========================================================

if #users == 0 then

    local latestAdmission = redis.call(
        'ZRANGE',
        admittedKey,
        -1,
        -1,
        'WITHSCORES'
    )

    if #latestAdmission == 2 then

        local latestExpiresAt =
            tonumber(latestAdmission[2])

        if latestExpiresAt then

            redis.call(
                'PEXPIREAT',
                admittedKey,
                latestExpiresAt
            )

        end
    end

    return {}
end


-- =========================================================
-- 5. ZADD argument 생성
--
-- ZADD key score member score member ...
--
-- 모든 사용자는 이번 batch에서 동일한 expiresAt을 가진다.
-- =========================================================

local args = {}

for i, userId in ipairs(users) do

    table.insert(
        args,
        expiresAt
    )

    table.insert(
        args,
        userId
    )

end


-- =========================================================
-- 6. ADMITTED에 먼저 추가
--
-- waiting에서 먼저 제거하면
--
-- waiting 삭제 성공
-- admitted 추가 실패
--
-- 시 사용자가 유실될 수 있다.
--
-- Lua 자체가 atomic하게 실행되므로
-- 먼저 admitted에 추가하고 그 다음 waiting에서 제거한다.
-- =========================================================

redis.call(
    'ZADD',
    admittedKey,
    unpack(args)
)


-- =========================================================
-- 7. WAITING에서 제거
-- =========================================================

redis.call(
    'ZREM',
    waitingKey,
    unpack(users)
)


-- =========================================================
-- 8. admitted key 자체에 expiration 설정
--
-- 이번 batch의 expiresAt은 현재 admission 중
-- 가장 늦은 만료 시각이다.
--
-- 따라서 마지막 admission까지 모두 만료되는 순간
-- Redis가 key 전체를 제거할 수 있다.
--
-- 이후 새로운 batch가 들어오면 PEXPIREAT이 갱신된다.
-- =========================================================

redis.call(
    'PEXPIREAT',
    admittedKey,
    expiresAt
)


return users