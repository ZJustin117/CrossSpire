# Android Harness 开发运行手册

本文记录 CrossSpire 当前维护者使用的 SlayTheAmethyst Android 双设备测试台。这里的 ADB serial、connector daemon 和端口转发属于开发基础设施，不是 CrossSpire 的运行时配置或网络协议。

## 支持与验证范围

- 当前已验证平台：SlayTheAmethyst 提供的 Android ModTheSpire + BaseMod 兼容运行时。
- 当前设备：D1 `localhost:15555`，D2 `localhost:25555`。
- Desktop 保留标准 ModTheSpire/BaseMod 兼容目标，本阶段不执行 Desktop 验证。
- CrossSpire 最终用户不需要 Harness、connector、game-probe、Arthas 或 ADB。

## 测试台拓扑

```text
开发机
├── ADB serial localhost:15555 → D1（房主）
└── ADB serial localhost:25555 → D2（客户端）

游戏网络
├── D1 监听 127.0.0.1:54321
└── D2 连接 127.0.0.1:54321
    └── 外部测试基础设施自动转发到 D1 127.0.0.1:54321

调试控制
└── Harness → connector daemon (:STS_CONNECTOR_PORT)
           → game-probe :9099 → BaseMod DevConsole → CrossSpireCommand
           → arthas-bridge :8099（可选 JVM 诊断，见 android-arthas.md）
```

`localhost:15555` 和 `localhost:25555` 是开发机看到的 ADB serial。游戏内的 `127.0.0.1:54321` 是 P2P 端点，两者没有端口映射关系。

D2 使用 `127.0.0.1` 只能在当前测试台成立，因为外部环境已经提供 D2 `localhost:54321` 到 D1 `localhost:54321` 的转发。CrossSpire 不创建、检测或恢复该转发。其他网络拓扑必须向 `crossspire join` 传入房主实际可达的地址。

当前 Harness **不直接调用 adb**，也不直接连 game-probe：所有设备 I/O 经 connector daemon（TCP 127.0.0.1）。Harness 不会自动拉起 daemon（`auto_start=False`），必须先手动启动。

## 本地环境

在 shell 中设置本地变量。不要把维护者机器的绝对路径写入仓库：

```bash
export SLAY_THE_AMETHYST_ROOT=/path/to/SlayTheAmethystModded
export STS_CONNECTOR_PORT=39999
export CROSSSPIRE_D1_SERIAL=localhost:15555
export CROSSSPIRE_D2_SERIAL=localhost:25555
export CROSSSPIRE_GAME_PORT=54321
export CROSSSPIRE_STS_JAR=/path/to/desktop-1.0.jar
```

`STS_CONNECTOR_PORT` 为 connector daemon 的本机 TCP 端口（示例 `39999`）。也可在每条 harness 命令上传 `-ConnectorPort`；二者缺一则 harness 会报错退出。

入口约定：

- CrossSpire 仓库的 `scripts/tools` 是指向 `$SLAY_THE_AMETHYST_ROOT/scripts/tools` 的 symlink。
- harness：`python3 scripts/tools/main.py sts-harness ...`（在 CrossSpire 根）或 `python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness ...`。
- connector 管理命令：在 **SlayTheAmethyst 仓库根** 执行 `python3 -m scripts.tools.connector ...`（需能 import `scripts.tools`）。
- harness 的 `repo_root` 解析为 symlink 真实路径下的 Amethyst 根；gradle / `debug-artifacts` 写在 Amethyst 侧。

在当前 SlayTheAmethyst 工作区构建 CrossSpire：

```bash
cd mods/cross-spire
./gradlew clean jar test \
  -PstsJar="$CROSSSPIRE_STS_JAR" \
  -PbaseModJar="$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/BaseMod.jar" \
  -PmodTheSpireJar="$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/ModTheSpire.jar"
```

`desktop-1.0.jar` 的位置取决于本地 STS 安装。CrossSpire 不再猜测 Steam 或 SlayTheAmethyst 的安装位置。

Harness 的 game-probe 默认端口是 `9099`，一般不需要传 `-AgentPort`。只有 game-probe 以非默认端口启动时才使用该覆盖参数。

## 前置条件

1. 两台设备均可由 ADB serial 访问。
2. 已启动 connector daemon，且 `STS_CONNECTOR_PORT` 或 `-ConnectorPort` 指向该端口。
3. SlayTheAmethyst 以 ModTheSpire + BaseMod 模式启动 CrossSpire。
4. 启动时启用 game-probe，例如使用 Harness `-DebugMode`。
5. 等待游戏进入主菜单，并确认 BaseMod 和 CrossSpire 已完成初始化。
6. 确认测试台已建立 D2 `localhost:54321` 到 D1 `localhost:54321` 的转发。
7. 双设备同时在线时，每条 harness 命令必须传 `-DeviceSerial`（connector 按命令 select；多设备且未指定会报错）。

CrossSpire 不再读取 `crossspire_startup.txt` 或 `crossspire_batch.txt`。不要通过向 Android 应用目录写文件来执行命令。

## 启动 connector

一次调试会话启动一次 daemon 即可：

```bash
cd "$SLAY_THE_AMETHYST_ROOT"
python3 -m scripts.tools.connector start --port "$STS_CONNECTOR_PORT"
python3 -m scripts.tools.connector status
```

停止：

```bash
python3 -m scripts.tools.connector stop
```

已设置 `STS_CONNECTOR_PORT` 时，后续示例可省略 `-ConnectorPort`。若未 export，每条 harness 命令需加 `-ConnectorPort "$STS_CONNECTOR_PORT"`。

## BaseMod Console 调用

Harness 只是调用标准 BaseMod console；命令本身与直接在 BaseMod console 输入时相同。以下示例假设已 `export STS_CONNECTOR_PORT`，并在 CrossSpire 仓库根使用 symlink 入口（也可换成 `$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py`）。

先通过 Harness 启动并等待两端达到 `READY`：

```bash
python3 scripts/tools/main.py sts-harness \
  -Command start -LaunchMode mts_basemod -DebugMode \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" -SkipInstall

python3 scripts/tools/main.py sts-harness \
  -Command status -DeviceSerial "$CROSSSPIRE_D1_SERIAL"
```

D2 使用相同命令并替换 serial。`start` 只表示启动请求已发送，必须以 `status` 的 `READY` 为准。

### D1 启动房主

```bash
python3 scripts/tools/main.py sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -ConsoleCommand "crossspire host 127.0.0.1 $CROSSSPIRE_GAME_PORT"
```

### D2 加入房间

```bash
python3 scripts/tools/main.py sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D2_SERIAL" \
  -ConsoleCommand "crossspire join 127.0.0.1 $CROSSSPIRE_GAME_PORT"
```

### 查询状态

```bash
python3 scripts/tools/main.py sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -ConsoleCommand "crossspire status"
```

将 `CROSSSPIRE_D1_SERIAL` 替换为 `CROSSSPIRE_D2_SERIAL` 可查询 D2。

### 战斗与事件命令

使用同一入口执行其他 BaseMod console 命令：

```bash
python3 scripts/tools/main.py sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -ConsoleCommand "crossspire start IRONCLAD"

python3 scripts/tools/main.py sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -ConsoleCommand "fight Cultist"

python3 scripts/tools/main.py sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -ConsoleCommand "crossspire play Strike_R"
```

完整命令语义见 [`../console-commands.md`](../console-commands.md)。

## JVM 诊断（Arthas）

联机与战斗命令走上方 BaseMod console。需要线程热点、方法 `watch`/`trace`、类搜索/反编译、火焰图或堆转储时，使用 Arthas（经同一 connector，设备端口 `8099`）：

```bash
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" start
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "thread -n 5"
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" stop
```

前置条件与 harness 相同：debug 模式（game-probe）、connector 已启动、多设备时指定 serial。完整手册见 [`android-arthas.md`](./android-arthas.md)。

## 验证要求

Harness 能连接 game-probe 不等于多人状态已经稳定。每次 E2E 至少检查：

1. connector 在线；Harness 没有报告 console、connector 或协议错误。
2. D1 和 D2 日志均包含 CrossSpire 初始化信息。
3. `crossspire status` 显示预期 peer 数量。
4. D1 实际监听命令传入的端口。
5. 日志中不存在 `BatchWatcher`，也没有 startup/batch 文件访问。
6. 旧 `crossspire_startup.txt` 和 `crossspire_batch.txt` 即使存在也不会被执行或删除。

## 故障定位

| 症状 | 检查项 |
|------|--------|
| Connector port is required | 是否设置 `STS_CONNECTOR_PORT` 或传入 `-ConnectorPort` |
| Harness connector is not initialized / 连接拒绝 | daemon 是否已 `start`；端口是否与 export 一致；`python3 -m scripts.tools.connector status` |
| Multiple Android devices are online | 双设备时是否每条命令都传了 `-DeviceSerial` |
| Harness 无法连接 game-probe | 是否 `-DebugMode`（或等价）启动；serial 是否在线；默认端口 `9099`；connector 是否已 select 正确设备 |
| `crossspire` 是未知命令 | BaseMod 和 CrossSpire 是否已完成初始化 |
| D2 无法连接 D1 | D2 → D1 的 `54321` 转发是否存在；D1 是否已执行 `host` |
| 命令返回但状态未变化 | 查询两端 `crossspire status` 并检查游戏日志，不使用固定 `sleep` 判断完成 |
| 重启后连接失效 | 重新确认 connector、外部转发和 game-probe；CrossSpire 不负责恢复测试基础设施 |

## 依赖边界

禁止在 CrossSpire 生产源码或资源中加入：

- ADB 命令或设备 serial。
- `/storage/emulated/0/Android/data/...` 路径。
- Harness、connector、game-probe、Arthas bridge（`:8099`）或 SlayTheAmethyst 类依赖。
- D2 → D1 转发配置。
- 文件轮询命令入口。

构建时通过 `-PstsJar`、`-PbaseModJar` 和 `-PmodTheSpireJar` 指向本地 JAR，但本机绝对路径不得提交。
