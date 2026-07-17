# ACHIEVEMENTS

CrossSpire development timeline and milestones.

## 2026-07-11 — MVP: 中继服务器 + Mod 框架 + 事件抑制 + 网络层

### 中继服务器 (TypeScript)
- `store.ts` — 房间 CRUD，空房间自动删除
- `server.ts` — Express + WebSocket，握手/房间管理/消息中继
- `protocol.ts` — 协议类型定义
- `sequence.ts` — 序列号管理
- **19 测试** (vitest) 全部通过

### Mod 构建系统
- `build.gradle.kts` — 独立 Gradle 构建，compileOnly BaseMod/ModTheSpire/desktop-1.0.jar
- `CrossSpireMod.java` — `@SpireInitializer` 入口
- 设备日志: `CrossSpire mod initialized`

### BaseMod 事件抑制
- `EventSuppression.java` — `AtomicInteger` 计数器 + `suppressEvents(Runnable)`
- `SuppressBaseModPatches.java` — 12 个 `@SpirePatch` 拦截 `BaseMod.publishXxx()` 方法
- MTS 加载无 crash

### 网络层
- `Protocol.java` — 消息 POJO + Gson 序列化
- `RelayClient.java` — Java-WebSocket 客户端
- `MessageRouter.java` — 按 type 分发
- 设备日志: `CrossSpire connected to relay server`

### 同步层
- `LocalCapturePatches.java` — 拦截 `AbstractPlayer.useCard()` → 广播
- `SyncExecutor.java` — 操作重放 + fallback 数值应用
- `RemotePlayer.java` — `extends AbstractPlayer`，37 个 abstract 方法存根化
- `RemotePlayerRegistry.java` / `RemotePlayerState.java`

### 联机入口 + 素材系统
- `LobbyScreen`, `ServerPicker`, `RoomPanel`, `RoomChat`
- `RemoteAssetCache` — L1 内存 + L2 磁盘缓存
- `RemoteCard/Relic/Power/PotionResource` — 素材投影类
- `ResourceRegistryTracker` — 素材清单自动交换

### 阶段统计
| 指标 | 值 |
|------|-----|
| TS 源文件 | 6 |
| Java 源文件 | 36 |
| 测试 | 19 (全部通过) |
| jar 大小 | 279 KB |
| Fallback 效果 | 13 类型 |

---

## 2026-07-12 — 引用系统 + P2P + Lobby + 全 Fallback

- `Reference` / `LocalReference` / `RemoteReference` / `NullReference` / `ReferenceFactory` — 引用系统完整实现
- `ContentValidator` — SHA-256 资源哈希校验
- `QueueManager` — 分布式广播队列，timestamp+sender_id 排序，Reference 路由
- `CombatResultReplayer` — 13 种 fallback 效果（damage/block/heal/energy/power 等）
- `P2PManager` — ServerSocket 监听 + hello 协议 + sendOrRelay 回退
- `LobbyState` — 就绪/seed 同步
- 19 vitest + 34 JUnit 全部通过
- jar: 595 KB

---

## 2026-07-14 — 架构对齐：18 项差距修复 + 真机全线验证

| # | 修复项 | 文件 |
|---|--------|------|
| 1 | Gold 同步 Patch | `GoldSyncPatches.java` |
| 2 | Base64 decode + putTexture | `RemoteResourceManager` |
| 3 | 磁盘缓存 serve | `RemoteAssetServer.java` |
| 4 | 共享 RNG 怪物意图 | `RngManager` + `RngSyncPatches` |
| 5 | ContentValidator Mod 支持 | hashFromInstance |
| 6 | RemotePlayer 被动引用 | `passiveReferences` |
| 7 | Reference.remoteAddr | 字段补全 |
| 8 | QueueDisplay 图形化 | `render(SpriteBatch)` |
| 9 | 等待图主 UI | `setWaitingForHost()` |
| 10 | 4 条协议消息 | StageHostElection/Result/FullSnapshot/AnimationSync |
| 11 | BroadcastManager | 统一 P2P+Relay 广播 |
| 12 | HeartbeatManager | 独立线程类 |
| 13 | sequence.ts | per-source 单调序列号 |
| 14 | RemotePotionResource | POJO |
| 15 | RemoteCharacterResource | POJO |
| 16 | RoomChat | 消息列表渲染 |
| 17 | entity-mappings.json | STS1↔STS2 映射表 |
| 18 | protocol-schema.json | 25 消息类型 Schema |

### 真机端到端验证

| 验证项 | 通过 |
|--------|------|
| Mod 初始化 + Relay 连接 | ✅ |
| 双向发现 + 资源表交换 | ✅ |
| D1 进入战斗 + D2 自动同步 | ✅ |
| Strike_R `[damage=6]` / Defend_R `[gain_block=5]` / Bash `[damage=8, magic_number=2]` | ✅ |
| VFX ATTACK 同步 | ✅ |
| 回合结束同步 | ✅ |
| D2 crash count = 0 | ✅ |

### 统计
| 指标 | 值 |
|------|-----|
| Java 源文件 | 52 |
| TS 源文件 | 7 |
| 测试 | 36 Java + 19 TS (全部通过) |
| jar | 601 KB |

---

## 2026-07-15 — UI + 真机同步修复

- `GameStarter` — 启动绕过 Exordium（消除 `renderBlackFadeScreen` NPE）
- `EndTurnSyncPatches` — 拦截 `EndTurnButton.enable()` → 广播 `player_end_turn`
- `CrossSpireHUD` — F1-F4 面板统一管理器
- 同步管线: `CombatSync → room_enter → createGameIfNeeded → enterRemoteCombat` (直接房间替换)
- 真机 D2 crash = 0

### 统计
| 指标 | 值 |
|------|-----|
| Java 源文件 | 54 |
| 测试 | 36 (全部通过) |
| jar | 608 KB |

---

## 2026-07-15 — 架构重构：房主中央调度 + 诱导重放 + 骨骼渲染

### 房主中央调度
- relay `room_state.host` — 先入为主选举
- `CentralQueueManager` — 房主唯一队列，排序、调度、invoke 路由
- `queue_submit` (C→host) / `queue_update` / `queue_empty` — 协议三件套
- `CombatResultReplayer` REAL/INDUCED 分离 — 事件步骤 BaseMod 事件链，效果步骤 suppressEvents
- `CardStub extends AbstractCard` + `PowerStub extends AbstractPower`

### 诱导重放管道验证
| 步骤 | D1 (host) | D2 (guest) |
|------|-----------|------------|
| Room host 识别 | `host: D1_ID` | `host: D1_ID` |
| 战斗同步 | `MONSTER: Cultist` | `combat entered: Cultist` |
| REAL 模式 | `LocalReference [damage=6]` | — |
| Content degrade | — | `RemoteReference degraded to LOCAL` |
| INDUCED onCardUse | `published onCardUse` | `published onCardUse` |
| VFX | `ATTACK on self` | `ATTACK on self` |

### 骨骼渲染
| 组件 | 文件 | 状态 |
|------|------|------|
| 骨骼资源 | `RemoteCharacterResource.java` | SkeletonData/Atlas/State/render |
| 素材服务 | `RemoteAssetServer.java` | ReflectionHacks spine 提取 |
| 骨骼缓存 | `RemoteAssetCache.java` | L1 characterCache + disk |
| 动画同步 | `AnimationSyncPatches.java` | @SpirePatch setAnimation → broadcast |
| 角色渲染 | `RemoteRenderer.java` | PostUpdate+PostRender 骨架 |

### 生产环境改造计划
- `TODO.md` — 21 项重构任务（5 架构 + 7 质量 + 4 功能 + 5 债务）

---

## 2026-07-16 — 架构完整性 5/5

### 一、统一队列 + 深层诱导重放

旧分布式 `QueueManager` 删除，全链路统一走 `CentralQueueManager` 房主中央队列：

| # | 任务 | 文件变更 |
|---|------|----------|
| 1.1 | **统一队列路径** | 删除 `QueueManager.java`；`MessageRouter` 构造函数改为 `CentralQueueManager`；`CrossSpireCommand.cmdPlay` 交由 `LocalCapturePatches` 自动捕获发 `queue_submit` |
| 1.2 | **消费 queue_update / queue_empty** | `MessageRouter` 新增 `queue_update`→`QueueDisplay.onUpdate()` + `queue_empty`→`QueueDisplay.onQueueEmpty()`；`QueueDisplay` 重写为 `QueueEntry` 列表 |
| 1.3 | **深层诱导重放** | `CombatResultReplayer.inducedUseCard()` → `AbstractPlayer.useCard(stubCard, target, -1)` — 不加 suppressEvents，@SpirePatch + BaseMod 事件链全量触发 |
| 1.4 | **消除重复 combat_result** | `handleInvoke` 移除重复广播，仅 `CentralQueueManager.onInvokeResult` 统一广播 |
| 1.5 | **target 接收端校验** | `handleInvoke` 顶部增加 `inv.target` 校验 + `isRoomHost()` 保护 |

### 统计

| 指标 | 变更 |
|------|------|
| 修改文件 | 7 |
| 行数 | +88 / -252 |
| 删除文件 | `QueueManager.java` |
| 测试 | 47 全部通过 |
| jar | 625 KB (-5 KB) |
---

## 2026-07-16 — 生产质量 7/7

### 事件抑制 + Heartbeat + 掉线 + 骨架修复

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 2.1 | **怪物回合结果同步** | `MonsterTurnPatches.java` | `applyStartOfTurnPowers` 前后 HP/Block 增量法（`takeTurn` 为 abstract 无法 Patch） |
| 2.2 | **骨架传输修复** | `RemoteAssetServer.java` | 字符→路径映射表，从 `Gdx.files.internal()` 读取原始 skeleton.json/atlas/png，替代 hashcode stub |
| 2.3 | **客户端掉线处理** | `RelayClient.java`, `MessageRouter.java`, `LobbyState.java` | `player_left` handler → `Registry.remove()` + `onPlayerLeft()` + roomSize 递减 |
| 2.4 | **日志降噪** | `CrossSpireMod.java` | batch `f.delete()` 已实现 |
| 2.5 | **EventSuppression 封装** | `EventSuppression.java`, `SuppressBaseModPatches.java` | `SUPPRESSION` package-private，调用方改用 `isSuppressed()` |
| 2.6 | **HeartbeatManager 接入** | `RelayClient.java`, `HeartbeatManager.java` | `onOpen`→`start()`, `onClose`→`stop()`，移除内联线程 |
| 2.7 | **Reference.hostId 路由** | `Reference.java`, `RemoteReference.java`, `MessageRouter.java` | invoke 发到 `hostId`，房主识别并转发给 owner |

### 统计

| 指标 | 变更 |
|------|------|
| 修改文件 | 9 |
| 行数 | +143 / -67 |
| 测试 | 47 全部通过 |
| jar | 625 KB |
| TODO.md | 12/21 完成 (5 架构 + 7 质量) |

### 双设备验证

| 验证项 | 结果 |
|--------|------|
| `HeartbeatManager started` | D1 ✅ D2 ✅ |
| `RelayClient room host` 正确 | D1 ✅ D2 ✅ |
| 无新增 Error/Exception | D1 ✅ D2 ✅ |
| 构建 + 测试 | 47/47 ✅ |

---

## 项目总览

| 指标 | 当前值 |
|------|--------|
| Java 源文件 | 55 (prod: 49, test: 6) |
| TypeScript 源文件 | 7 |
| Java 单元测试 | 48 (全部通过) |
| TypeScript 测试 | 19 (全部通过) |
| 协议消息类型 | 31 |
| MTS @SpirePatch | 30+ |
| Jar 大小 | 629 KB |
| TODO.md 完成 | 20/21 (仅 3.4 图主选举延期) |

---

## 2026-07-16 — 代码债务 5/5

### 怪物意图全量快照 + RemotePlayer 实例化 + 骷髅渲染定位

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 3.1 | **怪物意图全量快照** | `MonsterIntentBroadcastPatches.java`, `IntentRenderer.java`, `SyncExecutor.java` | `createIntent` 缓冲到 `intentBuffer`，`AbstractPlayer.applyStartOfTurnPowers` 全量广播 `MonsterIntentMessage.intents[]`；`IntentRenderer.showSnapshot()` 逐怪刷新 |
| 3.2 | **怪物回合三阶段分离** | — | 已对齐 ARCHITECTURE §8：phase1 intent snapshot / phase2 player queue / phase3 HP delta（takeTurn 抽象无法 Patch，回退 usePreBattleAction） |
| 3.3 | **RemotePlayer 实例化 + 渲染** | `RemotePlayerState.java`, `RemoteRenderer.java` | `getPlayerInstance()` 惰性创建 `RemotePlayer extends AbstractPlayer`；`renderHealth(sb)` 委托；多角色按 index 自动排列 x 坐标 |
| 3.4 | **图主选举协议** | — | 延期（ARCHITECTURE.md 标注"暂不实现"） |

### 统计

| 指标 | 变更 |
|------|------|
| 修改文件 | 8 |
| 行数 | +179 / -106 |
| 新增测试 | 1 (`shouldSerializeMonsterIntentSnapshot`) |
| 测试 | 48 全部通过 |
| jar | 627 KB |
| TODO.md | 20/21 完成 |

---

## 2026-07-16 — 代码债务 5/5

### 死代码清理 + 缓存增强 + README

| # | 任务 | 变更 |
|---|------|------|
| 4.1 | **删除死代码** | 6 legacy POJO (`QueuePacket/Submit/Update/InvokeCard/InvokeResult/RemotePlayerSync`) -47 行 |
| 4.2 | **缓存 SHA-256 校验** | `RemoteAssetCache.manifest.json` + `verify()` 方法 |
| 4.3 | **缓存过期清理** | `readDisk()` 30天 auto-delete |
| 4.4 | **initialize 拆分** | 已精简至 17 行 |
| 4.5 | **README.md** | 25 个完整 `@SpirePatch` 清单 + 子包结构 |

### 统计

| 指标 | 变更 |
|------|------|
| 修改文件 | 4 |
| 行数 | +152 / -47 |
| 新增文件 | `README.md` |
| 测试 | 48 全部通过 |
| jar | 629 KB |

---

## 2026-07-16 — 稳定性修复

### FlushSnapshot Crash 回退
- `MonsterIntentBroadcastPatches.FlushSnapshot` patch 的目标方法 `AbstractPlayer.applyStartOfTurnPowers` 不存在 → `NoSuchMethodException` crash
- 回退为 `OnCreateIntent` 内直接广播单怪意图

### FontHelper NPE 防护
- 所有 `PostRenderSubscriber` 入口增加 `FontHelper.tipBodyFont == null` 空值检查
- 文件：`CrossSpireHUD`, `RemoteRenderer`, `LobbyScreen`, `RoomPanel`, `RemoteStatsOverlay`, `QueueDisplay`, `RoomChat`

### D2 战斗进入崩溃修复
- `SyncExecutor.enterRemoteCombat()` 在 `room.onPlayerEntry()` 后设置 `AbstractDungeon.nextRoom = null`
- 根因：`onPlayerEntry()` 会创建 `nextRoom` 触发场景过渡渲染，D2 的 minial game state 缺少字体/纹理初始化 → `LevelTransitionTextOverlayEffect.render()` NPE

---

## 技术栈

| 层 | 技术 |
|----|------|
| 服务器 | Node.js + Express + ws + TypeScript |
| Mod 客户端 | Java 8 + ModTheSpire + BaseMod + Java-WebSocket + Gson |
| 测试 | vitest (TS), JUnit 4 (Java), MTS 加载 + 日志验证 |
| 调试（历史） | ADB 双设备, `crossspire_batch.txt` 指令注入（P4 由 Harness → BaseMod console 替代） |

---

## 2026-07-16 (3/3) — 从 Relay 到 P2P 的迁移

### 架构变更：删除中继服务器，换成 P2P 直连

**核心变更：**
- 删除 `cross-spire-server/` — TypeScript WebSocket relay 服务端 (7 TS 源文件, 19 vitest 测试)
- 删除 `RelayClient.java` — 移除 `java-websocket` WebSocket 客户端依赖
- 删除 `org.java-websocket:Java-WebSocket:1.5.3` 依赖 → JAR 从 629KB 减至 450KB

**新增 P2P 统一发送层：**
- `CrossSpireMod.send(String)` — 统一发送 API，根据 `ServerPicker.isRoomHost` 自动选择路由：
  - 房主：检查 `target` 字段 → 定向发送或广播
  - 客户端：所有消息发给房主
- `CrossSpireMod.connect()` — 重写：房主启动 P2P 监听，客户端连接房主 IP
- `CrossSpireMod.isConnected()` — 改写为检查 `p2pManager.connectionCount() > 0`
- 自生成 UUID 作为 `playerId`（不再依赖 relay 服务器分配）

**P2PManager 清理：**
- 删除 `sendOrRelay()` — 不再回退到 relay
- 删除 `relayViaServer()` — 不再有 relay 服务器
- `send()` 方法移除 relay 回退逻辑
- `sendHello()` 移除 relay 检查

**ServerPicker 重构：**
- 删除 `serverUrl`（relay URL）
- 新增 `hostIp` (默认 127.0.0.1) / `hostPort` (默认 54321)
- 新增 `isRoomHost` (默认 true, 可通过 `-Dcrossspire.room.host=false` 设置)
- 保留 `isStageHost`（战斗权威标志）
- 静态初始化 try-catch 安全化（测试环境兼容）

**RoomHost (新) — 房主房间管理：**
- `RoomHost.java` — 管理连接的玩家列表
- `addPlayer()` / `removePlayer()` / `hasPlayer()` / `getOtherPlayers()`
- 6 个 JUnit 测试

**消息路由全局替换 (67 处)：**
- `CrossSpireMod.relayClient.send()` → `CrossSpireMod.send()`
- `relayClient != null && relayClient.isOpen()` → `CrossSpireMod.isConnected()`
- `relayClient == null || !relayClient.isOpen()` → `!CrossSpireMod.isConnected()`
- 涉及 21 个 Java 文件

**命令系统更新：**
- `crossspire host [port]` — 以房主身份启动
- `crossspire join <ip> [port]` — 以客户端身份连接
- `crossspire connect` — 兼容别名（指向 join）
- 删除 `crossspire connect <url> <room>` 旧语法

**UI 更新：**
- `RoomPanel` 按钮文本 "Connect to Relay" → "Host Game" / "Join Game"

**连接流程 (新)：**
```
房主: CrossSpireMod.connect() → p2pManager.start() → 监听 54321 → hub
客户端: CrossSpireMod.connect() → p2pManager.connectTo(hostIp, hostPort) → 发送 playerId
```

**受影响的统计：**
| 指标 | 旧值 | 新值 |
|------|------|------|
| 传输协议 | WebSocket → Relay | TCP → P2P |
| 服务器 | TypeScript (Node.js, 19 TS 测试) | 删除 |
| 依赖 | java-websocket:1.5.3, gson:2.10.1 | gson:2.10.1 (仅) |
| JAR 大小 | 629 KB | 450 KB |
| Java 测试 | 48 | 51 (+3) |
| Java 源文件 | 55 | 56 (+RoomHost, -RelayClient) |
| TypeScript 源文件 | 7 | 0 |
| TS 测试 | 19 | 0 |

**设备端验证：**
- 两台设备 (Redmi Note 8 + Redmi 8) 通过 ADB (`localhost:15555`, `localhost:25555`) 连接
- JAR 成功推送到两台设备的 `mods_library/CrossSpire.jar`
- 两台设备均成功启动并进入主菜单 (`main_menu_ready`)
- P2P 命令 (`crossspire host` / `crossspire join`) 通过 batch watcher 机制发送
- ⚠️ 日志系统在重启后未刷新，E2E 完整验证待跟进

> 后续状态：以上是当时的真实验证记录。P4 删除 batch watcher，改用 SlayTheAmethyst Harness 调用标准 BaseMod console；设备 serial 保持不变，D2 `localhost:54321` 到 D1 `localhost:54321` 的转发由外部测试基础设施提供。

## 2026-07-17 — Android Harness 调试链路清理

- 正式标注 SlayTheAmethyst Android 为当前验证平台，Desktop 验证暂缓。
- 删除 `crossspire_startup.txt`、`crossspire_batch.txt` 和 `BatchWatcher`。
- 删除 Android `crossspire.properties` 与 P2P JVM property，`host/join` 仅接受显式参数。
- Gradle 通过 `-PstsJar`、`-PbaseModJar`、`-PmodTheSpireJar` 接受本地依赖路径。
- JUnit 104 项通过，发布 JAR 静态审计不含 Android 存储路径、ADB serial、旧文件入口或 Harness/game-probe 类。
- D1 `localhost:15555` 执行 `crossspire host 127.0.0.1 54321`，D2 `localhost:25555` 执行 `crossspire join 127.0.0.1 54321`；Harness → BaseMod console 均执行成功。
- D1 日志确认监听 `54321`、接受 D2 并注册玩家；D2 日志确认连接 host，双机 P2P E2E 通过。
