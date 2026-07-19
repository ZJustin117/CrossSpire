# Android Arthas 开发运行手册

本文记录 CrossSpire 维护者在 SlayTheAmethyst Android 设备 JVM 上使用 [Arthas](https://arthas.aliyun.com) 做诊断的入口。Arthas、connector 端口转发和设备 serial 属于开发基础设施，不是 CrossSpire 的运行时配置或网络协议。

完整模块架构、Android 适配细节与实现文件表见 [`../../scripts/tools/arthas/README.md`](../../scripts/tools/arthas/README.md)。双设备测试台与 BaseMod console 见 [`android-harness.md`](./android-harness.md)。

## 定位

- Arthas 是阿里巴巴开源的 JVM 诊断工具。本集成经自定义 `arthas-bridge` 绕过 Netty，在设备 JVM 的 `localhost:8099` 暴露纯 socket 接口。
- Python 客户端经 connector daemon 的 `connect_stream` 透传命令。
- CrossSpire 最终用户不需要 Arthas、Harness、connector、game-probe 或 ADB。

## 与 harness / game-probe 分工

| 能力 | game-probe / harness console | Arthas |
|------|------------------------------|--------|
| 游戏状态、BaseMod console、`crossspire` 命令 | ✅ | ❌ |
| 方法参数 / 返回值 | TracingMonitor | `watch`（OGNL） |
| 调用链耗时 | PERF | `trace` |
| 线程 / 面板 / 类搜索 / 反编译 | ❌ | `thread` / `dashboard` / `sc` / `jad` |
| OGNL / 火焰图 / 堆转储 / 热替换 | ❌ | `ognl` / `profiler` / `heapdump` / `retransform` |

game-probe 负责游戏语义；Arthas 负责通用 JVM 诊断。联机 host/join/status 与战斗命令仍走 harness console。

## 拓扑

```text
开发机
├── ADB serial localhost:15555 → D1
└── ADB serial localhost:25555 → D2

调试控制
└── connector daemon (:STS_CONNECTOR_PORT)
    ├── game-probe :9099  → LOAD_AGENT / BaseMod console
    └── arthas-bridge :8099 → shell / query（connect_stream）
```

`8099` 与 `9099` 均为设备侧端口，由 connector 按命令 forward；与游戏 P2P `54321` 无映射关系。

## 前置条件

1. 游戏以 debug 相关模式启动，且 game-probe `:9099` 可用（例如 harness `-DebugMode`，或 `./gradlew :app:stsStart -PlaunchMode=mts -PdebugMode=true`）。
2. 已启动 connector daemon，且设置 `STS_CONNECTOR_PORT`（见 [`android-harness.md`](./android-harness.md)「启动 connector」）。
3. 多设备时必须指定设备：`--device <serial>` 或 `export STS_TEST_DEVICE=<serial>`（解析顺序：`--device` → `STS_TEST_DEVICE` → 仅 1 台在线时自动选择）。
4. 在 **SlayTheAmethyst 仓库根** 执行 `python3 -m scripts.tools.arthas ...`（需能 import `scripts.tools`）。CrossSpire 仓库的 `scripts/tools` 是指向 Amethyst 的 symlink，也可在 CrossSpire 根用同一模块路径。

本地环境变量可与 harness 共用：

```bash
export SLAY_THE_AMETHYST_ROOT=/path/to/SlayTheAmethystModded
export STS_CONNECTOR_PORT=39999
export CROSSSPIRE_D1_SERIAL=localhost:15555
export CROSSSPIRE_D2_SERIAL=localhost:25555
# 可选默认设备（等价于每条命令 --device）
export STS_TEST_DEVICE="$CROSSSPIRE_D1_SERIAL"
```

## 快速开始

```bash
cd "$SLAY_THE_AMETHYST_ROOT"

python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" start
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "version"
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" shell
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" stop
```

| 子命令 | 作用 |
|--------|------|
| `start` | 推送 JAR / 原生库 / 伴生文件 → `LOAD_AGENT` → 准备 bridge |
| `shell` | 交互 shell（`exit` / `quit` / `q` 退出） |
| `query CMD` | 一次性执行一条 Arthas 命令 |
| `stop` | `reset`/`stop` + unforward |

可选参数：`--agent-port`（默认 `9099`）、`--arthas-port`（默认 `8099`）。

`start` 结束后关闭 game-probe 会话；`shell` / `query` 在成功、失败或中断时关闭 stream 并 `unforward`。遇 `TypeNotPresentException` 时 CLI 会自动重连并保持同一 serial。

## 维护者常用场景

以下假设已 `start`，且每条命令带正确 `--device`。

### 线程与概览

```bash
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "thread -n 5"
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "dashboard"
```

### MTS ClassLoader 与类信息

ModTheSpire 为每个 mod 使用独立 `URLClassLoader`。对这类类需先查 hash，再显式 `-c`：

```bash
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "sc -d com.megacrit.cardcrawl.cards.AbstractCard"
# 将 <hash> 换成 sc 输出中的 classLoaderHash
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "jad -c <hash> com.megacrit.cardcrawl.cards.AbstractCard"
```

### 方法观察与调用链

```bash
# watch / trace 示例；目标为 mod 类时补 -c <hash>
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" shell
# 在 shell 内：
#   watch com.example.Foo bar '{params,returnObj}' -x 2
#   trace com.example.Foo bar
```

### 堆转储 / JFR / profiler

路径必须在应用私有目录（SELinux）：

```bash
# shell 内示例
heapdump /data/data/io.stamethyst/files/heap.hprof
jfr start -n rec1 --duration 30s -f /data/data/io.stamethyst/files/rec1.jfr
profiler start -o jfr
```

### 不可用命令

| 命令 | 原因 |
|------|------|
| `mc` | JRE 无 `tools.jar`；本地 `javac` 后 `adb push`，再用 `retransform` / `redefine` |

命令全表与参数见模块离线文档 [`../../scripts/tools/arthas/docs/README.md`](../../scripts/tools/arthas/docs/README.md) 与[官方命令列表](https://arthas.aliyun.com/doc/commands.html)。

## 故障定位

| 症状 | 检查项 |
|------|--------|
| `Multiple Android devices online` | 是否传 `--device` 或设置 `STS_TEST_DEVICE` |
| Connector / 连接拒绝 | daemon 是否已 `start`；`STS_CONNECTOR_PORT` 是否一致 |
| game-probe `available: false` / `LOAD_AGENT` 失败 | 是否 `-DebugMode`（或等价）启动；serial 是否在线 |
| `LOAD_AGENT` → `already bind` | bridge 重复加载；重启游戏后重新 `start` |
| `connect_stream` BrokenPipe | bridge 已 `stop`；重启游戏后重新 `start` |
| `Type xxx not present` | CommonSuperBridge 首次 retransform 未就绪；同 serial 重连（CLI 自动重试） |
| `ognl` 返回 `null` | 方法为 `void` 时正常；改用有返回值方法 |

## 依赖边界

禁止在 CrossSpire 生产源码或资源中加入：

- Arthas bridge、`LOAD_AGENT` 或对 `:8099` 的依赖。
- ADB 命令、设备 serial、应用私有目录硬编码路径作为运行时配置。
- Harness、connector、game-probe 或 SlayTheAmethyst 类依赖。

## 参考

- 模块技术说明：[`../../scripts/tools/arthas/README.md`](../../scripts/tools/arthas/README.md)
- 离线命令文档：[`../../scripts/tools/arthas/docs/README.md`](../../scripts/tools/arthas/docs/README.md)
- 双设备 Harness：[`android-harness.md`](./android-harness.md)
- [Arthas 官方文档](https://arthas.aliyun.com/doc/commands.html)
