-- 滑动窗口限流（ZSet 实现）
-- KEYS[1] 限流 key（user:{id}:path 或 api:path）
-- ARGV[1] 窗口大小（毫秒） ARGV[2] 容量 ARGV[3] 当前时间戳（毫秒）
-- 返回 1 放行 / 0 拒绝
local key      = KEYS[1]
local window   = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

-- 1. 移除窗口外的旧请求
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
-- 2. 统计窗口内请求数
local count = redis.call('ZCARD', key)
if count >= capacity then
    return 0
end
-- 3. 记录本次请求（member 用 now 加随机后缀防覆盖）
redis.call('ZADD', key, now, now .. '-' .. math.random(100000))
redis.call('PEXPIRE', key, window)
return 1
