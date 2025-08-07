-- Kong自定义限流插件
local redis = require "resty.redis"
local cjson = require "cjson"

local RateLimitHandler = {}

RateLimitHandler.PRIORITY = 900
RateLimitHandler.VERSION = "1.0.0"

-- 配置参数
local config_schema = {
  type = "record",
  fields = {
    {
      second = {
        type = "number",
        gt = 0,
      }
    },
    {
      minute = {
        type = "number",
        gt = 0,
      }
    },
    {
      hour = {
        type = "number",
        gt = 0,
      }
    },
    {
      day = {
        type = "number",
        gt = 0,
      }
    },
    {
      policy = {
        type = "string",
        default = "cluster",
        one_of = { "local", "cluster", "redis" },
      }
    },
    {
      redis_host = {
        type = "string",
        default = "127.0.0.1",
      }
    },
    {
      redis_port = {
        type = "integer",
        between = { 1, 65535 },
        default = 6379,
      }
    },
    {
      redis_database = {
        type = "integer",
        default = 0,
      }
    },
    {
      redis_timeout = {
        type = "number",
        default = 2000,
      }
    },
    {
      fault_tolerant = {
        type = "boolean",
        default = true,
      }
    },
    {
      hide_client_headers = {
        type = "boolean",
        default = false,
      }
    },
    {
      limit_by = {
        type = "string",
        default = "consumer",
        one_of = { "consumer", "credential", "ip", "service", "header", "path" },
      }
    },
    {
      header_name = {
        type = "string",
      }
    },
    {
      path = {
        type = "string",
      }
    }
  }
}

-- Redis连接池
local redis_pool = {}

-- 获取Redis连接
local function get_redis_connection(conf)
  local red = redis:new()
  red:set_timeout(conf.redis_timeout)
  
  local ok, err = red:connect(conf.redis_host, conf.redis_port)
  if not ok then
    kong.log.err("Failed to connect to Redis: ", err)
    return nil, err
  end
  
  if conf.redis_database > 0 then
    local ok, err = red:select(conf.redis_database)
    if not ok then
      kong.log.err("Failed to select Redis database: ", err)
      return nil, err
    end
  end
  
  return red
end

-- 关闭Redis连接
local function close_redis_connection(red)
  if red then
    local ok, err = red:set_keepalive(10000, 100)
    if not ok then
      kong.log.err("Failed to set Redis keepalive: ", err)
    end
  end
end

-- 获取限流标识符
local function get_identifier(conf)
  local identifier
  
  if conf.limit_by == "ip" then
    identifier = kong.client.get_forwarded_ip()
  elseif conf.limit_by == "consumer" then
    local consumer = kong.client.get_consumer()
    if consumer then
      identifier = consumer.id
    else
      identifier = kong.client.get_forwarded_ip()
    end
  elseif conf.limit_by == "credential" then
    local credential = kong.client.get_credential()
    if credential then
      identifier = credential.id
    else
      identifier = kong.client.get_forwarded_ip()
    end
  elseif conf.limit_by == "service" then
    local service = kong.router.get_service()
    if service then
      identifier = service.id
    end
  elseif conf.limit_by == "header" then
    if conf.header_name then
      identifier = kong.request.get_header(conf.header_name)
    end
  elseif conf.limit_by == "path" then
    identifier = kong.request.get_path()
  end
  
  return identifier or kong.client.get_forwarded_ip()
end

-- 生成Redis键
local function get_redis_key(identifier, period, window_start)
  return string.format("rate_limit:%s:%s:%d", identifier, period, window_start)
end

-- 检查限流
local function check_rate_limit(conf, identifier, period, limit, window_size)
  if conf.policy == "redis" then
    local red, err = get_redis_connection(conf)
    if not red then
      if conf.fault_tolerant then
        kong.log.warn("Redis connection failed, allowing request: ", err)
        return true, 0, limit
      else
        return false, 0, limit
      end
    end
    
    local current_time = ngx.time()
    local window_start = math.floor(current_time / window_size) * window_size
    local key = get_redis_key(identifier, period, window_start)
    
    -- 使用Lua脚本保证原子性
    local lua_script = [[
      local key = KEYS[1]
      local limit = tonumber(ARGV[1])
      local window_size = tonumber(ARGV[2])
      local current_time = tonumber(ARGV[3])
      
      local current = redis.call('GET', key)
      if current == false then
        current = 0
      else
        current = tonumber(current)
      end
      
      if current < limit then
        local new_val = redis.call('INCR', key)
        redis.call('EXPIRE', key, window_size)
        return {1, new_val, limit}
      else
        return {0, current, limit}
      end
    ]]
    
    local result, err = red:eval(lua_script, 1, key, limit, window_size, current_time)
    close_redis_connection(red)
    
    if not result then
      kong.log.err("Redis eval failed: ", err)
      if conf.fault_tolerant then
        return true, 0, limit
      else
        return false, 0, limit
      end
    end
    
    local allowed = result[1] == 1
    local current_requests = result[2]
    local remaining = math.max(0, limit - current_requests)
    
    return allowed, remaining, limit
  else
    -- 本地或集群策略的简化实现
    return true, limit, limit
  end
end

-- 设置响应头
local function set_rate_limit_headers(conf, remaining, limit, reset_time)
  if not conf.hide_client_headers then
    kong.response.set_header("X-RateLimit-Limit", limit)
    kong.response.set_header("X-RateLimit-Remaining", remaining)
    kong.response.set_header("X-RateLimit-Reset", reset_time)
  end
end

-- 主要处理函数
function RateLimitHandler:access(conf)
  local identifier = get_identifier(conf)
  local current_time = ngx.time()
  
  -- 检查各个时间窗口的限流
  local periods = {
    { name = "second", limit = conf.second, window = 1 },
    { name = "minute", limit = conf.minute, window = 60 },
    { name = "hour", limit = conf.hour, window = 3600 },
    { name = "day", limit = conf.day, window = 86400 }
  }
  
  for _, period in ipairs(periods) do
    if period.limit then
      local allowed, remaining, limit = check_rate_limit(
        conf, identifier, period.name, period.limit, period.window
      )
      
      if not allowed then
        local reset_time = math.floor(current_time / period.window) * period.window + period.window
        set_rate_limit_headers(conf, 0, limit, reset_time)
        
        return kong.response.exit(429, {
          message = "API rate limit exceeded",
          retry_after = reset_time - current_time
        })
      end
      
      -- 设置最严格的限制的响应头
      if period.name == "minute" or (not conf.minute and period.name == "hour") then
        local reset_time = math.floor(current_time / period.window) * period.window + period.window
        set_rate_limit_headers(conf, remaining, limit, reset_time)
      end
    end
  end
end

-- 日志记录
function RateLimitHandler:log(conf)
  local identifier = get_identifier(conf)
  local status = kong.response.get_status()
  
  if status == 429 then
    kong.log.warn("Rate limit exceeded for identifier: ", identifier)
  end
end

RateLimitHandler.schema = config_schema

return RateLimitHandler