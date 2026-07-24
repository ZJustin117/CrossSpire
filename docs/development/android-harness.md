# Android Harness 开发运行手册

本文记录 CrossSpire 在 SlayTheAmethyst Android 上的双设备测试台用法。ADB serial、connector daemon 和端口转发属于开发基础设施，不是 CrossSpire 的运行时配置或网络协议。

**语义回归（phase / ownership / induce / queue admit 等）默认用 JUnit，不靠本手册的双机流程。** 逻辑层规范见 [`logic-layer-testing.md`](./logic-layer-testing.md)；OpenCode 用 `@junit-test`。Harness 只验证联机/设备契约与发布级共进 smoke。

**本机取值**见仓库根 [`.env.example`](../../.env.example) / 本地 `.env.local`（gitignored）。下文只使用环境变量名。

## 支持与验证范围

- 当前已验证平台：SlayTheAmethyst 提供的 Android ModTheSpire + BaseMod 兼容运行时。
- 设备角色：D1 = 房主（`$CROSSSPIRE_D1_SERIAL`），D2 = 客户端（`$CROSSSPIRE_D2_SERIAL`）。
- Desktop 保留标准 ModTheSpire/BaseMod 兼容目标，本阶段不执行 Desktop 验证。
- CrossSpire 最终用户不需要 Harness、connector、game-probe、Arthas 或 ADB。

## 测试台拓扑

```text
开发机
├── ADB serial $CROSSSPIRE_D1_SERIAL → D1（房主）
└── ADB serial $CROSSSPIRE_D2_SERIAL → D2（客户端）

游戏网络
├── D1 监听 127.0.0.1:$CROSSSPIRE_GAME_PORT
└── D2 连接 127.0.0.1:$CROSSSPIRE_GAME_PORT
    └── 外部测试基础设施自动转发到 D1 127.0.0.1:$CROSSSPIRE_GAME_PORT

调试控制
└── Harness → connector daemon (:$STS_CONNECTOR_PORT)
           → game-probe :$CROSSSPIRE_GAME_PROBE_PORT → BaseMod DevConsole → CrossSpireCommand
           → arthas-bridge :$CROSSSPIRE_ARTHAS_PORT（可选 JVM 诊断，见 android-arthas.md）
```

`$CROSSSPIRE_D*_SERIAL` 是开发机看到的 ADB serial。游戏内的 `127.0.0.1:$CROSSSPIRE_GAME_PORT` 是 P2P 端点，与 ADB serial 没有端口映射关系。

D2 使用 `127.0.0.1` 只能在当前测试台成立，因为 D2 上的 `127.0.0.1:$CROSSSPIRE_GAME_PORT` 必须被转发到 D1 的游戏监听端口。CrossSpire 不创建、检测或恢复该转发。其他网络拓扑必须向 `crossspire join` 传入房主实际可达的地址。

**若外部隧道只映射 ADB、未映射游戏端口**，可在开发机临时建立 ADB 链路（会话级，勿写入生产代码）：

```bash
# D1 游戏已 host 并监听设备侧 $CROSSSPIRE_GAME_PORT 后：
adb -s "$CROSSSPIRE_D1_SERIAL" forward tcp:15432 tcp:"$CROSSSPIRE_GAME_PORT"
adb -s "$CROSSSPIRE_D2_SERIAL" reverse tcp:"$CROSSSPIRE_GAME_PORT" tcp:15432
# 验证：D2 上 toybox nc -w 2 127.0.0.1 $CROSSSPIRE_GAME_PORT 应成功
```

**推送新 CrossSpire.jar 后**：`SkipInstall` 热重启可能仍加载旧类。应用 `am force-stop io.stamethyst` 再 harness `start`，或用 Arthas `jad` 核对 `LocalReference` 是否含 `ApplyPowerEffects`（见 [`android-arthas.md`](./android-arthas.md)）。

当前 Harness **不直接调用 adb**，也不直接连 game-probe：所有设备 I/O 经 connector daemon（TCP 127.0.0.1）。Harness 不会自动拉起 daemon（`auto_start=False`），必须先手动启动。

## 本地环境

从 `.env.example` 复制并填写 `.env.local`，再载入 shell（或依赖 OpenCode `local-env` 插件注入）：

```bash
set -a && source .env.local && set +a
# 至少需要：
#   SLAY_THE_AMETHYST_ROOT
#   STS_CONNECTOR_PORT
#   CROSSSPIRE_D1_SERIAL / CROSSSPIRE_D2_SERIAL
#   CROSSSPIRE_GAME_PORT
#   CROSSSPIRE_STS_JAR（构建时）
```

`STS_CONNECTOR_PORT` 为 connector daemon 的本机 TCP 端口。也可在每条 harness 命令上传 `-ConnectorPort`；二者缺一则 harness 会报错退出。

入口约定：

- CrossSpire 不再包含 `scripts/` symlink；共享工具位于 `$CROSSSPIRE_AMETHYST_TOOLS_DIR`。
- harness：在 CrossSpire 根执行 `python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness ...`。
- connector 管理命令：在 **SlayTheAmethyst 仓库根** 执行 `python3 -m scripts.tools.connector ...`（需能 import `scripts.tools`）。
- harness 的 `repo_root` 是入口脚本所在的 Amethyst 根；其 Gradle 操作仍在 Amethyst 侧。
- CrossSpire 调试必须设置绝对路径 `$CROSSSPIRE_HARNESS_OUT_DIR`，并在每条 harness 命令上传 `-OutDir "$CROSSSPIRE_HARNESS_OUT_DIR"`。`-OutDir` 必须是绝对路径；相对路径按 Amethyst `repo_root` 解析。每次运行写入 `$CROSSSPIRE_HARNESS_OUT_DIR/<timestamp>/result.json`，不清空既有产物。

构建 CrossSpire：

```bash
cd mods/cross-spire
./gradlew clean jar test \
  -PstsJar="$CROSSSPIRE_STS_JAR" \
  -PbaseModJar="${CROSSSPIRE_BASEMOD_JAR:-$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/BaseMod.jar}" \
  -PmodTheSpireJar="${CROSSSPIRE_MODTHESPIRE_JAR:-$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/ModTheSpire.jar}"
```

`desktop-1.0.jar` 的位置取决于本地 STS 安装。CrossSpire 不再猜测 Steam 或 SlayTheAmethyst 的安装位置。

### 推送 `CrossSpire.jar` 到设备

OpenCode：改 mod 后、联机 E2E 前，委派 `@android-deploy-jar`（`./gradlew jar` → 双机 `mods_library` → 默认 `am force-stop io.stamethyst`）。**不要**把构建/推送塞进 `@android-harness`。

手动等价（仓库根，已 `source .env.local`）：

```bash
cd mods/cross-spire && ./gradlew jar \
  -PstsJar="$CROSSSPIRE_STS_JAR" \
  -PbaseModJar="${CROSSSPIRE_BASEMOD_JAR:-$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/BaseMod.jar}" \
  -PmodTheSpireJar="${CROSSSPIRE_MODTHESPIRE_JAR:-$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/ModTheSpire.jar}"

JAR="mods/cross-spire/build/libs/CrossSpire.jar"
REMOTE="/sdcard/Android/data/io.stamethyst/files/sts/mods_library/CrossSpire.jar"
for s in "$CROSSSPIRE_D1_SERIAL" "$CROSSSPIRE_D2_SERIAL"; do
  adb -s "$s" shell mkdir -p /sdcard/Android/data/io.stamethyst/files/sts/mods_library
  adb -s "$s" push "$JAR" "$REMOTE"
  adb -s "$s" shell am force-stop io.stamethyst
done
```

推送后用 harness `start` 冷启动；勿依赖仅 `SkipInstall` 热重启加载新类。

Harness 的 game-probe 默认端口见 `$CROSSSPIRE_GAME_PROBE_PORT`（常见 `9099`），一般不需要传 `-AgentPort`。

## 前置条件

1. 两台设备均可由 ADB serial（`$CROSSSPIRE_D1_SERIAL` / `$CROSSSPIRE_D2_SERIAL`）访问。
2. 已启动 connector daemon，且 `STS_CONNECTOR_PORT` 或 `-ConnectorPort` 指向该端口。
3. 已加载 `.env.local`（或等价 export）。
4. SlayTheAmethyst 以 ModTheSpire + BaseMod 模式启动 CrossSpire。
5. 启动时启用 game-probe，例如使用 Harness `-DebugMode`。
6. 等待游戏进入主菜单，并确认 BaseMod 和 CrossSpire 已完成初始化。
7. 确认测试台已建立 D2 `127.0.0.1:$CROSSSPIRE_GAME_PORT` 到 D1 游戏端口的转发。
8. 双设备同时在线时，每条 harness 命令必须传 `-DeviceSerial`。
9. 推送新 `CrossSpire.jar` 后建议 `am force-stop io.stamethyst` 再 `start`，避免 MTS 仍持有旧类。

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

Harness 只是调用标准 BaseMod console；命令本身与直接在 BaseMod console 输入时相同。以下示例假设已 `source .env.local`，并在 CrossSpire 仓库根使用 `$CROSSSPIRE_AMETHYST_TOOLS_DIR` 入口。

```bash
test -n "$CROSSSPIRE_HARNESS_OUT_DIR"
```

每条命令均传 `-OutDir "$CROSSSPIRE_HARNESS_OUT_DIR"`，使 `result.json`、logcat、截图和命令诊断产物留在 CrossSpire 的 gitignored `debug-artifacts/` 下；以 stdout 打印的 `Harness result:` 路径为准。

先通过 Harness 启动并等待两端达到 `READY`：

```bash
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command start -LaunchMode mts_basemod -DebugMode \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" -SkipInstall \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR"

python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command status -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR"
```

D2 使用相同命令并替换为 `$CROSSSPIRE_D2_SERIAL`。`start` 只表示启动请求已发送，必须以 `status` 的 `READY` 为准。

### D1 启动房主

```bash
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR" \
  -ConsoleCommand "crossspire host 127.0.0.1 $CROSSSPIRE_GAME_PORT"
```

### D2 加入房间

```bash
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D2_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR" \
  -ConsoleCommand "crossspire join 127.0.0.1 $CROSSSPIRE_GAME_PORT"
```

### 查询状态

```bash
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command console \
  -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR" \
  -ConsoleCommand "crossspire status"
```

将 `$CROSSSPIRE_D1_SERIAL` 替换为 `$CROSSSPIRE_D2_SERIAL` 可查询 D2。

### 真共进主路径 smoke（T7.7+ / T8 / T9 验收）

**定位：** 设备路径与 console 联机是否仍通。induce/phase 等规则变更应先有 JUnit（[`logic-layer-testing.md`](./logic-layer-testing.md)）；本 smoke 不替代逻辑层断言。

唯一共进 pass 标准（**不是** D1-only `fight Cultist`）：

```text
host/join → 双方 ready → start (party_run_start) → 双 GAMEPLAY
  → maphost/nodehost → 双方 room 0 → 同 node_instance（nav locked）
  → 开战无自动 player_end_turn；play 进 resolving_queue（T8.0）
  → 胜后 rewarddone 或 RIH mapunlock / 打开地图 → exit_unlocked
  → 双方 room <next> → 新节点 force-follow
```

```bash
# 双方 ready（host/join 已完成）
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command console -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR" \
  -ConsoleCommand "crossspire ready IRONCLAD"
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command console -DeviceSerial "$CROSSSPIRE_D2_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR" \
  -ConsoleCommand "crossspire ready IRONCLAD"
python3 "$CROSSSPIRE_AMETHYST_TOOLS_DIR/main.py" sts-harness \
  -Command console -DeviceSerial "$CROSSSPIRE_D1_SERIAL" \
  -OutDir "$CROSSSPIRE_HARNESS_OUT_DIR" \
  -ConsoleCommand "crossspire start"
# maphost/nodehost（用 status 中的 player id）→ 双方 room 0
# 日志：nav locked；无 "EndTurnSync broadcast" 于开战瞬间
# D1: crossspire play Strike_R  → queue_empty / D2 INDUCED
# 解锁：crossspire mapunlock  或 RIH 本地打开地图（T9.3）
# 双方再 room 0 → 新 node_instance
```

通过条件（日志）：
1. 双端 `party_run_start` + GAMEPLAY  
2. 同 `node_instance_id`；pin 在 unlock 前 `exit_locked`  
3. 开战无自动 `player_end_turn`；`play` 不因 `monster_turn` 拒绝  
4. unlock 后 pin 共识进入下一房  

**非共进回归（deprecated）：** 仅 D1 `start` + BaseMod `fight Cultist` = host-spawn projection，**不得**作为共进 pass。

完整命令语义见 [`../console-commands.md`](../console-commands.md)。

## JVM 诊断（Arthas）

联机与战斗命令走上方 BaseMod console。需要线程热点、方法 `watch`/`trace`、类搜索/反编译、火焰图或堆转储时，使用 Arthas：

```bash
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" start
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" query "thread -n 5"
python3 -m scripts.tools.arthas --device "$CROSSSPIRE_D1_SERIAL" stop
```

前置条件与 harness 相同。完整手册见 [`android-arthas.md`](./android-arthas.md)。

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
| Connector port is required | 是否设置 `STS_CONNECTOR_PORT` 或传入 `-ConnectorPort`；是否 `source .env.local` |
| Harness connector is not initialized / 连接拒绝 | daemon 是否已 `start`；端口是否与 export 一致；`python3 -m scripts.tools.connector status` |
| Multiple Android devices are online | 双设备时是否每条命令都传了 `-DeviceSerial` |
| Harness 无法连接 game-probe | 是否 `-DebugMode`；serial 是否在线；`$CROSSSPIRE_GAME_PROBE_PORT`；connector 是否已 select 正确设备 |
| `crossspire` 是未知命令 | BaseMod 和 CrossSpire 是否已完成初始化 |
| D2 无法连接 D1 | D2 → D1 的游戏端口转发是否存在；D1 是否已执行 `host` |
| 命令返回但状态未变化 | 查询两端 `crossspire status` 并检查游戏日志，不使用固定 `sleep` 判断完成 |
| 重启后连接失效 | 重新确认 connector、外部转发和 game-probe；CrossSpire 不负责恢复测试基础设施 |

## 依赖边界

禁止在 CrossSpire 生产源码或资源中加入：

- ADB 命令或设备 serial。
- `/storage/emulated/0/Android/data/...` 路径。
- Harness、connector、game-probe、Arthas bridge 或 SlayTheAmethyst 类依赖。
- D2 → D1 转发配置。
- 文件轮询命令入口。

构建时通过 `-PstsJar`、`-PbaseModJar` 和 `-PmodTheSpireJar` 指向本地 JAR，但本机绝对路径不得提交。
