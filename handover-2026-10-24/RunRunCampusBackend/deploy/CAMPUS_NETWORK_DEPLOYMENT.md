# 跑跑代取公网网络部署

目标是让 Android 客户端通过无线网或移动数据访问生产 API。生产 API 通过公网 HTTPS 提供服务，不限制客户端的网络传输类型。

## 上线前必须取得的信息

上线前确认：

- 生产域名 `api.runruncampus.cn` 指向 ECS 的有效 A 记录；
- 与 `api.runruncampus.cn` 匹配、Android 系统信任的 HTTPS 证书；
- Android 正式构建使用生产 HTTPS 地址；
- API 的 8080 端口只绑定服务器回环地址，数据库不对公网监听。

## 部署步骤

1. 使用 `compose.prod.yml` 启动 API。它只把 8080 绑定到服务器回环地址，客户端不能绕过 Nginx。
2. 在阿里云 DNS 为 `api.runruncampus.cn` 添加指向生产 ECS 公网 IP 的 A 记录。
3. 安装 Nginx 和 Certbot，使用 `/var/www/letsencrypt` 完成 HTTP-01 验证并签发受信任证书。
4. 把 `nginx-campus.conf.example` 复制到服务器的 Nginx配置目录。
5. 执行 `nginx -t`，通过后重载 Nginx。
6. 验证证书自动续期，确认续期后会安全重载 Nginx。
7. Android 正式构建继续使用 `https://api.runruncampus.cn/`。

## 验收

### HTTPS 安全配置的本地回归

模板仅允许 TLS 1.2/1.3，TLS 1.2 限定为 ECDHE 与 AES-GCM/ChaCha20-Poly1305；TLS 1.3 使用 OpenSSL 默认套件。HTTPS 正常页和错误页均提供 HSTS、禁止 MIME 嗅探、禁止嵌入框架、禁止发送 Referrer 及 CSP。CSP 允许同源脚本、样式和接口，禁止内联脚本、eval、插件、修改 base URI 及被外部页面嵌入。当前后台静态构建适用此策略；如将来引入其他来源的资源，应先验证再调整。

在后端目录运行以下命令，需要 Docker Desktop、PowerShell 7.2+、Microsoft Edge、Codex 附带 Node/Playwright 及相邻后台工程：

```powershell
pwsh -NoProfile -File tools/test-nginx-security.ps1 -ReportDirectory 'D:/NginxQaResults/run-1'
```

报告目录必须不存在，测试固定使用本机 28443 端口及专用容器名，请串行执行。脚本构建独立前端和 Nginx 测试镜像，在临时容器中生成一天有效的自签名证书，验证公网业务入口、Actuator 来源限制、响应头、ACME、TLS 和浏览器 CSP 行为，最后按资源标签核对并清理容器。首次构建需要下载官方 Nginx 镜像和测试用 OpenSSL/curl。自签名证书只在测试中忽略信任检查，不安装到系统证书库，也不替代生产受信任证书。

结果保存在 `security-tests.log`、`browser-acceptance.json`、`login-csp.png` 和 `run-summary.json`。浏览器验证覆盖真实静态构建、登录表单交互及 CSV 下载机制；测试上游是夹具，不代表真实账号登录或生产接口验收。当前源码回归通过后，还需在生产实际 Nginx 版本上执行 `nginx -t` 与下列网络验收。

### 生产网络验收

- 服务器本机访问健康检查成功；
- Android 在校园、家庭等可正常上网的 Wi-Fi 下可以访问；
- Android 切换到移动数据后仍可以访问；
- 外部 HTTPS 请求可以进入公开业务接口，但 `/actuator/` 仍拒绝外部访问；
- 8080 和 PostgreSQL 5432 均不能被公网客户端直接访问。
