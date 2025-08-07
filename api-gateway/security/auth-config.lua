-- Kong自定义认证插件
local jwt = require "resty.jwt"
local cjson = require "cjson"

local AuthHandler = {}

AuthHandler.PRIORITY = 1000
AuthHandler.VERSION = "1.0.0"

-- 配置参数
local config_schema = {
  type = "record",
  fields = {
    {
      secret_key = {
        type = "string",
        required = true,
        encrypted = true,
        referenceable = true,
      }
    },
    {
      algorithm = {
        type = "string",
        default = "HS256",
        one_of = { "HS256", "HS384", "HS512", "RS256" },
      }
    },
    {
      header_names = {
        type = "set",
        elements = { type = "string" },
        default = { "authorization" },
      }
    },
    {
      uri_param_names = {
        type = "set",
        elements = { type = "string" },
        default = { "jwt" },
      }
    },
    {
      cookie_names = {
        type = "set",
        elements = { type = "string" },
        default = { "jwt" },
      }
    },
    {
      claims_to_verify = {
        type = "set",
        elements = {
          type = "string",
          one_of = { "exp", "nbf", "iat" },
        },
        default = { "exp" },
      }
    },
    {
      anonymous = {
        type = "string",
        uuid = true,
      }
    },
    {
      run_on_preflight = {
        type = "boolean",
        default = true,
      }
    }
  }
}

-- 从请求中提取JWT token
local function retrieve_token(request, conf)
  local token
  local args = request.get_uri_args and request.get_uri_args() or {}
  
  -- 从URI参数中获取
  for _, v in ipairs(conf.uri_param_names) do
    if args[v] then
      token = args[v]
      break
    end
  end
  
  -- 从请求头中获取
  if not token then
    local headers = request.get_headers()
    for _, name in ipairs(conf.header_names) do
      local header_value = headers[name]
      if header_value then
        if name:lower() == "authorization" then
          local iterator, iter_err = ngx.re.gmatch(header_value, "\\s*[Bb]earer\\s+(.+)")
          if not iterator then
            kong.log.err("Failed to retrieve token: ", iter_err)
            return nil
          end
          
          local m, err = iterator()
          if err then
            kong.log.err("Failed to retrieve token: ", err)
            return nil
          end
          
          if m and #m > 0 then
            token = m[1]
            break
          end
        else
          token = header_value
          break
        end
      end
    end
  end
  
  -- 从Cookie中获取
  if not token then
    local cookie = request.get_header("cookie")
    if cookie then
      for _, name in ipairs(conf.cookie_names) do
        local pattern = name .. "=([^;]+)"
        local match = ngx.re.match(cookie, pattern)
        if match then
          token = match[1]
          break
        end
      end
    end
  end
  
  return token
end

-- 验证JWT token
local function verify_token(token, conf)
  local jwt_obj = jwt:verify(conf.secret_key, token, {
    alg = conf.algorithm
  })
  
  if not jwt_obj.valid then
    return false, jwt_obj.reason
  end
  
  local claims = jwt_obj.payload
  local now = ngx.time()
  
  -- 验证过期时间
  if conf.claims_to_verify.exp and claims.exp then
    if now >= claims.exp then
      return false, "Token expired"
    end
  end
  
  -- 验证生效时间
  if conf.claims_to_verify.nbf and claims.nbf then
    if now < claims.nbf then
      return false, "Token not valid yet"
    end
  end
  
  -- 验证签发时间
  if conf.claims_to_verify.iat and claims.iat then
    if now < claims.iat then
      return false, "Token issued in the future"
    end
  end
  
  return true, claims
end

-- 设置用户上下文
local function set_consumer(consumer_id, claims)
  kong.client.authenticate({
    consumer_id = consumer_id,
    credential_id = claims.sub or consumer_id
  })
  
  -- 设置上游请求头
  kong.service.request.set_header("X-Consumer-ID", consumer_id)
  kong.service.request.set_header("X-Consumer-Username", claims.username or "")
  kong.service.request.set_header("X-Consumer-Custom-ID", claims.custom_id or "")
  kong.service.request.set_header("X-Anonymous-Consumer", false)
  
  -- 设置JWT声明到请求头
  if claims.role then
    kong.service.request.set_header("X-User-Role", claims.role)
  end
  if claims.permissions then
    kong.service.request.set_header("X-User-Permissions", cjson.encode(claims.permissions))
  end
end

-- 处理匿名用户
local function set_anonymous_consumer(conf)
  if conf.anonymous then
    kong.client.authenticate({
      consumer_id = conf.anonymous
    })
    kong.service.request.set_header("X-Anonymous-Consumer", true)
    return true
  end
  return false
end

-- 主要处理函数
function AuthHandler:access(conf)
  -- 处理预检请求
  if not conf.run_on_preflight and kong.request.get_method() == "OPTIONS" then
    return
  end
  
  local token = retrieve_token(kong.request, conf)
  
  if not token then
    if set_anonymous_consumer(conf) then
      return
    end
    return kong.response.exit(401, {
      message = "Unauthorized: No token provided"
    })
  end
  
  local is_valid, claims_or_error = verify_token(token, conf)
  
  if not is_valid then
    if set_anonymous_consumer(conf) then
      return
    end
    return kong.response.exit(401, {
      message = "Unauthorized: " .. claims_or_error
    })
  end
  
  local claims = claims_or_error
  local consumer_id = claims.sub or claims.consumer_id
  
  if not consumer_id then
    if set_anonymous_consumer(conf) then
      return
    end
    return kong.response.exit(401, {
      message = "Unauthorized: Invalid token claims"
    })
  end
  
  set_consumer(consumer_id, claims)
end

-- 响应处理
function AuthHandler:header_filter(conf)
  -- 移除敏感响应头
  kong.response.clear_header("X-Kong-Upstream-Latency")
  kong.response.clear_header("X-Kong-Proxy-Latency")
  
  -- 添加安全响应头
  kong.response.set_header("X-Content-Type-Options", "nosniff")
  kong.response.set_header("X-Frame-Options", "DENY")
  kong.response.set_header("X-XSS-Protection", "1; mode=block")
end

AuthHandler.schema = config_schema

return AuthHandler