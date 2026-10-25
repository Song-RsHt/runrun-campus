# 跑跑代取官网

本目录是 runruncampus.cn 的静态官网，包含：

- index.html：应用与服务介绍
- privacy.html：隐私政策
- terms.html：用户协议
- site.css：响应式样式
- app-icon.png：与 Android 正式应用一致的图标

## 已确认的公开资料

- 运营主体：射阳县合德镇轩轩生活服务部
- 网站ICP备案号：苏ICP备2026064718号-2
- 公安备案号：苏公网安备32092402000486号
- APP备案号：苏ICP备2026064718号-1A
- APP服务名称：跑跑代取
- 客服电话：15151050678

网站备案与公安备案于2026年9月16日完成。页面同时展示网站ICP备案号、公安备案号和APP备案号，三者分别链接至对应的官方备案查询入口。

## 正式发布前必须完成

为 runruncampus.cn 添加指向生产ECS的A记录，并完成生产部署与公网HTTPS验收。

检查命令：rg -n "请填写|待补充" deploy/site

## 生产部署

1. 为 runruncampus.cn 添加指向当前生产 ECS 的 A 记录。
2. 将本目录中的文件复制到服务器 /var/www/runruncampus-site/。
3. 首次签发证书前，将 deploy/nginx-site-acme-bootstrap.conf 复制到 Nginx 配置目录。
4. 执行 nginx -t，通过后重载 Nginx。
5. 使用现有 Certbot 和 /var/www/letsencrypt Webroot 为 runruncampus.cn 签发证书。
6. 将临时配置替换为 deploy/nginx-site.conf.example，再次执行 nginx -t 并重载 Nginx。
7. 验证首页、隐私政策和用户协议均使用可信 HTTPS 并返回 200，HTTP 请求统一跳转 HTTPS。
8. 核对三个页面均展示苏ICP备2026064718号-2、苏公网安备32092402000486号和APP备案号苏ICP备2026064718号-1A。

该站点与 api.runruncampus.cn 使用独立域名和 Nginx server block，不改变现有 API 反向代理。
