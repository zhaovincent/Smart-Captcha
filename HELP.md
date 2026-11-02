# SmartCaptcha 开发说明

三模块 Maven 工程，功能与接口文档见 [README.zh-CN.md](README.zh-CN.md) / [README.md](README.md)。

```bash
mvn clean install                                  # 构建 + 全部测试
mvn -pl smart-captcha-core test                    # 只测内核（不需要 Spring）
mvn -pl smart-captcha-server spring-boot:run       # 启动 REST 服务（8088）
cd vue3 && pnpm install && pnpm dev                # 演示页（5173，代理 /api → 8088）
```

| 模块 | 内容 |
| --- | --- |
| `smart-captcha-core` | 纯 Java 引擎（JDK 17，无第三方依赖）：生成器、校验、ticket、`CaptchaStore` + 内存实现 |
| `smart-captcha-spring-boot-starter` | 自动装配、`smart-captcha.*` 配置、`@RequireCaptcha` 拦截器（**默认关闭**，`smart-captcha.interceptor.enabled=true` 才生效；校验失败抛 `CaptchaTicketException`）、Redis / Redisson 存储；**不含任何返回包装** |
| `smart-captcha-server` | 可直接运行的 Spring Boot 服务，提供 `/api/captcha/**` 与演示业务接口；`ApiResult` 与统一异常处理在本模块内实现 |

安装到本地仓库的 POM 已扁平化（`flatten-maven-plugin`：去掉 `<parent>`、版本号写字面量），外部项目只依赖 `smart-captcha-spring-boot-starter` 即可，不需要聚合父 POM。若 IDE 仍报 `Unresolved dependency: cn.localtool:smart-captcha-parent`，先确认 IDE 的 Maven 本地仓库路径与本机安装路径一致，再 Reload Maven Projects。

Redis 相关测试需要本地 Redis：`docker compose up -d redis`；连不上时这 18 个用例自动跳过。
