# 开发基础设施手册

本目录记录维护者在 **SlayTheAmethyst Android** 上调试 CrossSpire 时使用的测试台与诊断入口。内容可供其他贡献者参考与复用，但属于**开发基础设施**，不是 CrossSpire 的运行时配置、网络协议或最终用户文档。

## 文档列表

| 文件 | 内容 |
|------|------|
| [`android-harness.md`](./android-harness.md) | 双设备 Harness、connector daemon、BaseMod console 联机流程 |
| [`android-arthas.md`](./android-arthas.md) | 设备 JVM 上的 Arthas 生命周期与常用诊断 |

## 边界

- ADB serial、connector 端口、game-probe / Arthas 端口、loopback 转发均属测试台约定；**不得**写入生产 mod 默认值或发布 JAR。
- 本机绝对路径（如 Amethyst 安装根、`desktop-1.0.jar`）只通过 shell 环境变量配置，不要提交。
- CrossSpire 最终用户不需要 Harness、connector、game-probe、Arthas 或 ADB。

## 相关仓库内文档

- [`../console-commands.md`](../console-commands.md) — `crossspire` BaseMod console 语义
- [`../ARCHITECTURE.md`](../ARCHITECTURE.md) — 架构与协议（生产路径）
- 模块技术细节：`scripts/tools/arthas/README.md`（经 Amethyst symlink 提供）
