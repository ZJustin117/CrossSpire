# 开发基础设施手册

本目录记录在 **SlayTheAmethyst Android** 上调试 CrossSpire 时使用的测试台与诊断入口。内容可供其他贡献者参考与复用，但属于**开发基础设施**，不是 CrossSpire 的运行时配置、网络协议或最终用户文档。

## 本机配置（与文档分离）

| 文件 | 提交 | 内容 |
|------|------|------|
| [`.env.example`](../../.env.example) | ✅ | 环境变量名、含义、占位 |
| `.env.local` | ❌ gitignored | 本机真实路径、ADB serial、端口 |

所有手册中的命令使用 `$CROSSSPIRE_*` / `$STS_*` / `$SLAY_THE_AMETHYST_ROOT`，**不要**把维护者机器的绝对路径或 ADB serial 写回共享文档。

```bash
cp .env.example .env.local   # 首次
# 编辑 .env.local 后：
set -a && source .env.local && set +a
```

OpenCode 项目插件 [`.opencode/plugins/local-env.ts`](../../.opencode/plugins/local-env.ts) 会在启动时加载 `.env.local`（白名单键），注入 shell 环境，并向测试 subagent 的 system 追加配置块。

## OpenCode 测试 subagent

| Agent | 调用 | 职责 |
|-------|------|------|
| `junit-test` | `@junit-test` | `mods/cross-spire` 下 `./gradlew test`；只读 |
| `android-deploy-jar` | `@android-deploy-jar` | `./gradlew jar` + 推送 `CrossSpire.jar` 到 D1/D2 `mods_library` + 默认 `force-stop`；只读源码；依赖 `.env.local` |
| `android-harness` | `@android-harness` | connector + harness console E2E；只读；依赖 `.env.local`（假定设备已有目标 jar） |
| `android-arthas` | `@android-arthas` | 设备 JVM 的 Arthas 线程、类加载、方法与调用链诊断；只读；按 `start → query → stop` 清理 bridge；依赖 `.env.local` |

定义位置：`.opencode/agent/*.md`。修改 agent/plugin 后需**重启 opencode**。

## 文档列表

| 文件 | 内容 |
|------|------|
| [`android-harness.md`](./android-harness.md) | 双设备 Harness、connector daemon、BaseMod console 联机流程 |
| [`android-arthas.md`](./android-arthas.md) | 设备 JVM 上的 Arthas 生命周期与常用诊断 |

## 边界

- ADB serial、connector 端口、game-probe / Arthas 端口、loopback 转发均属测试台约定；**不得**写入生产 mod 默认值或发布 JAR。
- 本机绝对路径（如 Amethyst 安装根、`desktop-1.0.jar`）只通过 `.env.local` / shell 环境变量配置，不要提交。
- CrossSpire 最终用户不需要 Harness、connector、game-probe、Arthas 或 ADB。

## 相关仓库内文档

- [`../console-commands.md`](../console-commands.md) — `crossspire` BaseMod console 语义
- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — 架构与协议（生产路径）
- 模块技术细节：`scripts/tools/arthas/README.md`（经 Amethyst symlink 提供）
