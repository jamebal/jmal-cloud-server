# STUN 通道地址与 gost 3.x 动态节点

`jmalcloud` 现已内置 `StunBeacon` 风格的通道地址同步能力，所有接口最终路径都带全局 `/api` 前缀。

鉴权说明：

- 这组接口复用了现有认证与权限体系。
- 需要可通过 `AuthInterceptor` 的凭证。
- 推荐给 `gost` 使用 `access-token`，可以放在请求头，也可以直接放到查询参数。
- 当前接口同时要求具备 `cloud:set:sync` 权限。

## 1. 更新某个通道地址

`POST /api/stun/{channelId}/update`

请求体：

```json
{
  "addr": "1.2.3.4:5678"
}
```

行为：

- `addr` 不能为空。
- `addr` 必须是合法 `host:port` 形式。
- 更新成功后立即写入持久层。
- 持久化失败时直接返回错误，不会出现“内存已更新但落库失败”的情况。

示例：

```bash
curl -X POST 'http://127.0.0.1:8088/api/stun/home/update?access-token=YOUR_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"addr":"1.2.3.4:5678"}'
```

成功响应：

```json
{
  "code": 0,
  "message": "true",
  "data": null
}
```

## 2. 获取某个通道最新地址

`GET /api/stun/{channelId}/get`

成功时返回纯文本：

```text
1.2.3.4:5678
```

示例：

```bash
curl 'http://127.0.0.1:8088/api/stun/home/get?access-token=YOUR_TOKEN'
```

如果通道不存在，返回 `404 Not Found`。

## 2.1 获取某个通道最新地址(JSON)

`GET /api/stun/{channelId}/address`

这个接口适合 `jmal-cloud-view` 这类依赖统一 `{ code, message, data }` 返回体的前端调用。

成功响应：

```json
{
  "code": 0,
  "message": "true",
  "data": "1.2.3.4:5678"
}
```

未找到时响应：

```json
{
  "code": -1,
  "message": "动态地址不存在",
  "data": null
}
```

## 3. 获取 gost 3.x 动态节点

`GET /api/stun/{channelId}/gost/nodes`

默认返回一个节点数组：

```json
[
  {
    "name": "home",
    "addr": "1.2.3.4:5678",
    "connector": {
      "type": "socks5"
    },
    "dialer": {
      "type": "tls",
      "tls": {
        "secure": true
      }
    }
  }
]
```

### 支持的查询参数

- `username`
- `password`
- `connector`
- `dialer`
- `name`
- `serverName`
- `caFile`
- `secure`

### 默认值

- `connector=socks5`
- `dialer=tls`
- `secure=true`

### 参数规则

- `username/password` 必须成对出现，否则返回 `400 Bad Request`
- `name=""` 视为未设置，回退到 `channelId`
- `serverName=""` 视为未设置
- `caFile=""` 视为未设置
- `dialer=tls` 时返回 `dialer.tls`
- `dialer!=tls` 时不返回 `dialer.tls`

### 覆盖协议示例

`ss + tcp`：

```bash
curl 'http://127.0.0.1:8088/api/stun/home/gost/nodes?access-token=0ca30c584a57d7c7681edd2a60ac9f83&connector=ss&dialer=tcp&username=username&password=pwd'
```

示例响应：

```json
[
  {
    "name": "home",
    "addr": "1.2.3.4:5678",
    "connector": {
      "type": "ss",
      "auth": {
        "username": "username",
        "password": "pwd"
      }
    },
    "dialer": {
      "type": "tcp"
    }
  }
]
```

## gost 3.x 接入示例

推荐的家用回连场景：`socks5 + tls`

```yaml
services:
  - name: edge
    addr: :1080
    handler:
      type: socks5
    listener:
      type: tcp
    forwarder:
      nodes:
        - name: dynamic-hop
          addr: http://127.0.0.1:8088
          connector:
            type: http
          dialer:
            type: tcp
          metadata:
            url: "http://127.0.0.1:8088/api/stun/home/gost/nodes?access-token=YOUR_TOKEN"
            interval: 10s
```

如果你的 `gost` 配置写法使用 `hop.http` 数据源，也可以直接把 URL 指向：

```text
http://127.0.0.1:8088/api/stun/home/gost/nodes?access-token=YOUR_TOKEN
```

## `serverName` 与 `caFile`

- `serverName`：TLS 握手时使用的目标服务名，通常用于 SNI 与证书主机名校验。比如服务端证书是 `home.example.com`，这里就应传 `serverName=home.example.com`。
- `caFile`：客户端加载的 CA 证书文件路径，用于校验服务端 TLS 证书。
- `caFile` 是 `gost` 客户端所在机器上的本地路径，不是 `jmalcloud` 服务器上的路径。

示例：

```bash
curl 'http://127.0.0.1:8088/api/stun/home/gost/nodes?access-token=YOUR_TOKEN&serverName=home.example.com&caFile=/etc/gost/ca.pem'
```

当 `dialer=tls` 时，返回节点中的 `dialer.tls` 会包含这些字段。
