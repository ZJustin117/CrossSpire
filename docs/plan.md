# CrossSpire — SDD 迁移技术方案 (Plan)

> 本文档是 SDD 迁移的实施设计方案。现状审计结论见 codebase audit，需求见 `spec.md`，架构设计见 `ARCHITECTURE.md`。

## 执行状态

> 与 `task.md` 对齐（2026-07-19）。历史 commit 见各阶段实施记录；测试数以 `./gradlew test` 为准。

| Phase | 进度 | 说明 |
|-------|------|------|
| P1 架构修复 | ✅ 完成 | T1.1–T1.4 |
| P2 功能补全 | ✅ 完成 | T2.1–T2.7 + 事件/房间/投票等 SDD 驱动项 |
| P3 清理稳定 | ✅ 完成 | T3.1–T3.8 |
| P4 Android 调试清理 | ✅ 完成 | T4.1–T4.7（见 `task.md` / `development/`） |
| 归档 | ✅ 完成 | A1–A6 |
| P5 Buff 所有权契约 | ✅ 原版完成 | T5.0–T5.2 + T5.4–T5.5 ✅；**T5.3 灾厄/mutation 延后** |
| P6 战斗回合闭环 | ✅ 核心完成 | T6.0–T6.2/T6.4/T6.5 ✅；T6.3 可选增强 |
| **总** | **P5 原版完成 + P6 进行中** | 以构建结果为准 |

## 目录

1. [迁移策略总览](#迁移策略总览)
2. [Phase 1: 架构修复](#phase-1-架构修复)
3. [Phase 2: 核心功能补全](#phase-2-核心功能补全)
4. [Phase 3: 清理与稳定](#phase-3-清理与稳定)
5. [归档: 不影响运行的顺路修](#归档-不影响运行的顺路修)
6. [Phase 5: Buff 所有权与诱导重放门控](#phase-5-buff-所有权与诱导重放门控)
7. [Phase 6: 战斗回合闭环](#phase-6-战斗回合闭环)

---

## 迁移策略总览

### 原则

1. **架构先行** — 破坏 SDD 合约的改动(P1)必须最先做，否则后续补全建立在错误地基上
2. **合同驱动** — 每项改动对照 `spec.md` 的 FR/验收标准 + `ARCHITECTURE.md` 的技术描述
3. **测试同步** — 每项功能修改/新增需同步写测试，不得先写代码后补测试
4. **小步确认** — 每个 task 粒度控制在 1-3 个文件，完成后跑 `./gradlew test`
5. **git 增量** — 每个 task 完成后单独 commit

### 影响量级

| Phase | 新建文件 | 修改文件 | 删除文件 | 测试新增 |
|-------|---------|---------|---------|---------|
| P1 架构 | 2 | 10 | 3 | 8 |
| P2 功能 | 5 | 12 | 0 | 12 |
| P3 清理 | 0 | 10 | 0 | 5 |
| 归档 | 0 | 5 | 0 | 2 |
| **合计** | **7** | **37** | **3** | **27** |

---

## Phase 1: 架构修复

> 必须先做。这些改动改变了核心合约(StandardPacket envelope, RNG策略, 网络拓扑)，后续所有功能补全都依赖它们。

### 1.1 删除共享 RNG 子系统

**现状**: `SyncedRng` + `RngManager` + `RngSyncPatches` 实现共享种子模型，拦截 `AbstractDungeon.generateSeeds()` 注入网络同步种子。FR-6.1 明确禁止。

**方案**:

1. **删除** 三个文件: `SyncedRng.java`, `RngManager.java`, `RngSyncPatches.java`
2. **修改 `MonsterIntentBroadcastPatches.OnCreateIntent`** — 当前用 `CrossSpireMod.rngManager.get("monster_intent_" + monster.id)` 获取 SyncedRng。改为直接用 `new java.util.Random(System.nanoTime())` (host 本地) 或复用 `AbstractDungeon.aiRng` (但可能破坏怪物AI)。**决定**: 新增 `StageHost` 的一个字段 `private final Random stageRng = new Random()`，host 通过 `StageHost.getStageRng()` 获取。
3. **修改 `CrossSpireMod`** — 移除 `syncedSeed` 字段和 `rngManager` 字段。移除 `SyncExecutor.handleBattleStart` 中设置 `syncedSeed` 的逻辑。
4. **修改 `LobbyState`/`RoomPanel`** — 移除 seed 同步门控逻辑(当前 `RoomPanel` 在 seed 未同步时禁用 "Play")。
5. **修改 `GameStarter`** — 移除 `seed` 参数(不再需要同步种子给他人)。

**文件清单**:
- 删除: `rng/SyncedRng.java`, `rng/RngManager.java`, `rng/RngSyncPatches.java`
- 修改: `combat/MonsterIntentBroadcastPatches.java`, `CrossSpireMod.java`, `sync/SyncExecutor.java`, `ui/LobbyState.java`, `ui/RoomPanel.java`, `remote/GameStarter.java`, `remote/StageHost.java`

**验证**: 运行现有 48 测试，确保无 SyncedRng 引用。手动验证: host 启动战斗后怪物意图正常生成。

---

### 1.2 引入 StandardPacket 信封

**现状**: `Protocol.java` 使用扁平 `GameMessage {type, subtype, source, seq, target}` 作为基础信封，各消息子类在自身字段中重复携带 `packetId/ownerId/resourceHash`。`protocol-schema.json` 同理。

**SDD 要求** (`ARCHITECTURE.md §17`):

```java
// 伪代码
class StandardPacket {
    String packetId;       // source + "-" + seq
    String source;         // 发送方 ID
    int seq;               // 单调递增序列号
    long timestamp;         // 发送时间戳
    String refId;           // 引用唯一标识 "card:Strike_R@playerA"
    String ownerId;         // 引用所有者 ID (房主据此路由)
    String resourceHash;    // SHA-256 (可选)
    PacketOperation operation;  // 枚举
    JsonNode payload;       // 操作特定载荷
}
```

**方案**:

1. **新建 `StandardPacket.java`** — 定义标准包结构:
   ```java
   public class StandardPacket {
       public String packetId;   // source + "-" + seq
       public String source;
       public int seq;
       public long timestamp;
       public String refId;
       public String ownerId;
       public String resourceHash;
       public String operation;  // 先 String，后续可换 enum
       public JsonObject payload;
       
       // 便捷构造器
       public static StandardPacket create(String source, String operation, ...) { ... }
       public String toJson();  // GSON 序列化
       public static StandardPacket fromJson(String raw);  // 反序列化
   }
   ```

2. **定义 `PacketOperation` 常量类** — 所有操作枚举值:
   ```
   queue_submit, queue_update, queue_empty,
   invoke, invoke_result, reference_register, reference_migrate,
   combat_result, monster_intent, player_state, full_snapshot,
   event_interface, event_select, event_result,
   interact_request, interact_response,
   resource_registry, resource_request, resource_response, animation_sync
   ```

3. **重写 `Protocol.java`** — 仅保留 **payload 定义**(纯数据类)，不再携带信封字段:
   - `QueueSubmitPayload {cardId, target}`
   - `QueueUpdatePayload {entries: QueueEntry[]}`
   - `QueueEntry {entryId, cardId, target, status}`
   - `InvokePayload {refId?, trigger, args}`
   - `InvokeResultPayload {effects[], operationSequence[]}`
   - `CombatResultPayload {effects[], operationSequence[], senderId}`
   - `EffectDescription` — 保留
   - `OperationStep` — 保留
   - `RemotePlayerState` — 保留
   - `MonsterIntentPayload {intents: MonsterIntentEntry[]}` / `MonsterIntentEntry`
   - `EventInterfacePayload / EventSelectPayload / EventResultPayload`
   - `InteractRequestPayload / InteractResponsePayload`
   - `ResourceRegistryPayload / ResourceRequestPayload / ResourceResponsePayload`
   - `AnimationSyncPayload`
   - `FullSnapshotPayload`
   
   删除旧有的完整消息类: `QueueSubmitMessage`, `QueueUpdateMessage`, `QueueEmptyMessage`, `InvokeMessage`, `InvokeResultMessage`, `QueueComplete`, `CombatResultMessage`, `EventResultMessage`, `MonsterIntentMessage`, `PlayerStateMessage`, `AnimationSyncMessage`, `PlayerReady`, `PlayerEndTurnMessage`, `StageSync`, `HelloMessage`, `RoomInfoMessage`, `ReferenceRegisterMessage`, `ReferenceMigrateMessage`, `StageHostElectionMessage`, `StageHostResultMessage`, `FullSnapshotMessage`, `MemberInfo`。

4. **修改 `CrossSpireMod`**:
   - `send(String rawJson)` → `send(StandardPacket packet)`
   - `nextSeq()` — 保留，用于 packet.seq 赋值
   - 所有调用点更新为新 API

5. **修改 `MessageRouter`** — 分发逻辑从 `msg.type` switch 改为 `packet.operation` switch:
   - 先解析 `StandardPacket.fromJson(rawMessage)`
   - 再按 `packet.operation` 分发

6. **更新 `protocol-schema.json`** — 改为 StandardPacket envelope + 各 operation 的 payload schema

7. **更新所有消息构造/解析点** — 约 30+ 处。搜索 `new Protocol.QueueSubmitMessage`, `new Protocol.InvokeMessage`, `new Protocol.QueueComplete` 等，改为构造 StandardPacket + payload。

**文件清单**:
- 新建: `network/StandardPacket.java`, `network/PacketOperation.java`
- 重写: `network/Protocol.java`
- 修改: `shared/cross-spire-protocol/protocol-schema.json`, `CrossSpireMod.java`, `sync/MessageRouter.java`, 以及所有 30+ 处消息构造/解析点

**验证**: 所有 48 测试通过。手动验证 D1 发消息 D2 能收到并正确解析。

---

### 1.3 网络层去 P2P 化

**现状**: `P2PManager.onHelloReceived()` 包含活跃的全互联自动连接代码 (O(n²))。实际运行时 CrossSpireMod 只用单连接到 host，但 hello 消息链路上的自动互连逻辑仍然存活。

**方案**:

1. **重命名**: `P2PManager` → `StarConnectionManager`
2. **修改 `StarConnectionManager`**:
   - 删除 `sendHello()` 中携带 peers 列表的逻辑(不再做 gossip 发现)
   - 删除 `onHelloReceived()` 中的自动互连 peer 循环
   - `hello` handler 仅存接收方注册(路由到 `MessageRouter.handleRoomInfo`)
   - 线程名改为 `Star-Accept`
3. **修改 `RoomHost.java`** — 补充 host 职责:
   - `broadcastToAll(String rawMessage)` — 遍历 `p2pManager.connections()` 发送
   - `relayToOwner(String ownerId, StandardPacket packet)` — 转发到指定所有者
4. **修改 `MessageRouter`** — 将 host 转发逻辑从 MessageRouter 移入 StarConnectionManager/RoomHost，MessageRouter 只做纯分发
5. **修改 `CrossSpireMod`** — 引用从 `p2pManager` 改为 `connectionManager`

**文件清单**:
- 重命名+修改: `network/P2PManager.java` → `network/StarConnectionManager.java`
- 修改: `network/RoomHost.java`, `sync/MessageRouter.java`, `CrossSpireMod.java`, 所有引用 `p2pManager` 的文件

**验证**: 运行测试。手动验证两台设备星型连接: D2 只连 D1(host)，D3 只连 D1，D2/D3 之间无直连。

---

### 1.4 Cleanup: 删除 BroadcastManager 冗余层

`BroadcastManager` 是一个一行 facade (`CrossSpireMod.send(message)`)，不提供任何额外语义。删除此类，调用处直接改为 `CrossSpireMod.send()`。

**文件清单**:
- 删除: `network/BroadcastManager.java`
- 修改: 所有引用 `BroadcastManager.broadcast()` 的文件(约 3-5 处)

---

## Phase 2: 核心功能补全

> 基于 P1 后的新基础设施补全 SDD 要求的功能。

### 2.1 修复诱导重放管道 (FR-3.4)

**现状**: `CombatResultReplayer.inducedUseCard()` 优先使用 `CardLibrary.getCard(cardId).makeCopy()` (真实卡牌) 而非 `CardStub`，导致重复产生卡牌效果。PowerStub 正确但从未被使用(`resolvePower` 不回退到它)。

**方案**:

1. **始终使用 Stub** — 删除 `CombatResultReplayer.inducedUseCard()` 中 `CardLibrary` 分支，始终构造 `new CardStub(cardId, type, ...)`。`cardType/rarity/target` 从 `operation_sequence` 的步骤参数中获取(需要在 `CombatResultPayload.operationSequence` 条目中增加 `cardType/cardRarity/cardTarget` 字段)。

2. **PowerStub 接线** — `CombatResultReplayer.publishPostPowerApply()` 改为调真实的 `ApplyPowerAction` 或直接 `AbstractPower.onInitialApplication` + `BaseMod.publishPostPowerApply`。当 `resolvePower` 反射失败时 fallback 到 `new PowerStub(powerId, amount)`。

3. **诱导重放效果收集** — `replayInduced()` 包裹在 `EffectCapture.startCapture()` 中，捕获新效果 → 构造 `queue_submit` → 回 host 队列尾:
   ```java
   EffectCapture.startCapture();
   try { replayInduced(opSeq, effects); }
   finally {
       Protocol.EffectDescription[] newEffects = EffectCapture.stopCapture();
       if (newEffects.length > 0) {
           StandardPacket pkt = StandardPacket.create(source, "queue_submit", ...);
           CrossSpireMod.send(pkt);
       }
   }
   ```
   注意: 需要修复 `stopCapture()` 的 API(先清缓冲导致 `getCaptured()` 返回空 → 改为只在 `startCapture` 清)。

4. **OperationStep 补全** — `LocalCapturePatches` / `EffectCapture` 需同时记录 `operation_sequence` (type, cardId, powerId, target, amount, vfxKind + cardType/cardRarity/cardTarget)，不仅记录 effects。

**文件清单**:
- 修改: `combat/CombatResultReplayer.java`, `combat/EffectCapture.java`, `combat/CardStub.java`(加 type/rarity/target 字段), `combat/PowerStub.java`(完善构造器)
- 修改: `network/Protocol.java`(OperationStep 加字段), `sync/LocalCapturePatches.java`(记录 opSeq)

**验证**: 新增测试 `CombatResultReplayerTest.testStubPreventsDoubleExecution`, `testInducedReplayCapturesNewEffects`。

---

### 2.2 实现 queue_empty → EndTurnButton gate (FR-2.5)

**现状**: `QueueDisplay.onQueueEmpty()` 只设一个 `endTurnAllowed` flag，无消费者。`EndTurnSyncPatches` 做的是反向(按钮启用时广播结束回合)。

**方案**:

1. **EndTurnButton patch 改造** — 新增 `@SpirePatch` 在 `EndTurnButton.update()` 或 `EndTurnButton.enable()` 上:
   ```java
   @SpirePrefixPatch
   public static SpireReturn<Void> prefix(EndTurnButton __instance) {
       if (!CrossSpireMod.isConnected()) return SpireReturn.Continue();  // 单人
       if (!QueueDisplay.isEndTurnAllowed()) return SpireReturn.Return(null);  // 阻止启用
       return SpireReturn.Continue();
   }
   ```

2. **`MessageRouter` 处理 `queue_empty`** — 收到后调用 `QueueDisplay.onQueueEmpty()` 设 flag + `EndTurnButton.enable()` 启用按钮。需注意 `enable()` 会触发 `EndTurnSyncPatches` → 需 suppress 或增加 isQueueEmpty flag 防护。

3. **`queue_submit` 时禁用** — 首次 `queue_submit` 后调用 `EndTurnButton.disable()`。

**文件清单**:
- 修改: `sync/EndTurnSyncPatches.java`(新增 prefix), `sync/MessageRouter.java`, `ui/QueueDisplay.java`

**验证**: 测试 forward: `EndTurnSyncPatchesTest.testButtonDisabledUntilQueueEmpty` + 真机验证。

---

### 2.3 怪物回合正确 patch (FR-2.6)

**现状**: `MonsterTurnPatches` patch 的是 `usePreBattleAction` 而非 `takeTurn`。广播 speculative damage 给所有 remote 且 target="self"。

**方案**: 采用 ARCHITECTURE.md §8 的 **HP 增量法**:

1. **BeforeTurn** — `@SpirePostfixPatch` on `AbstractPlayer.applyStartOfTurnPowers()`:
   - 记录 `preTurnHp = AbstractDungeon.player.currentHealth`
   - 记录 `preTurnBlock = AbstractDungeon.player.currentBlock`

2. **AfterMonsterTurns** — `@SpirePostfixPatch` on `AbstractPlayer.applyStartOfTurnPowers()`:
   - 计算 `hpDelta = preTurnHp - currentHealth`
   - 计算 `blockDelta = currentBlock - preTurnBlock`
   - 若 delta 非零 → 构造 `combat_result` StandardPacket 广播 (effects: damage + gain_block, empty opSeq)
   - 重置 preTurn 记录

3. **分离 IntentSnapshot** — `MonsterIntentBroadcastPatches` 在回合开始时(非 preBattle) 广播全量 intent 快照:
   - 延迟 snapshot 到 `AbstractPlayer.applyStartOfTurnPowers()` 触发

**文件清单**:
- 重写: `combat/MonsterTurnPatches.java`
- 修改: `combat/MonsterIntentBroadcastPatches.java`(分离 intent snapshot 时机), `sync/IntentRenderer.java`(修复传递数据不用的bug)

**验证**: 新增 `MonsterTurnPatchesTest.testHpDeltaAfterMonsterTurns`。

---

### 2.4 房主选举与迁移 (FR-1.5, US-6)

**现状**: MessageRouter 中 `stage_host_election/stage_host_result/full_snapshot` 是桩。HeartbeatManager 无超时检测。

**方案**:

1. **HeartbeatManager 补全** — 增加:
   - 最后收到 pong 时间戳 `Map<String, Long> peerLastSeen`
   - 每 15s ping 所有连接
   - 检测线程(每 5s)检查超时(30s无响应)→ 触发 `onPeerTimeout(peerId)`
   - 收到 pong → 更新 timestamp

2. **Host 掉线流程** (`RoomHost`):
   ```java
   sync void onPeerTimeout(String peerId) {
       removePlayer(peerId);
       // 广播 player_left
       // 若掉线者是 host → 触发选举
       if (peerId.equals(hostPlayerId)) {
           triggerHostElection();
       }
   }
   
   void triggerHostElection() {
       // 按 memberId 排序，选第一名为新 host
       String newHost = electHost(getAllMemberIds());
       // 广播 host_migration
       // 更新自身状态
   }
   ```

3. **新增 `HostElectionHandler`** (可在 RoomHost 或独立文件):
   - 发送 `host_election` (非标准包，control 消息)
   - 收集投票 → 返回结果
   - 新房主重建路由连接 + 中央队列

4. **FullSnapshot** — 房主迁移后新房主请求全部 client 发 full_snapshot，合并后广播给全员。

**注意**: SDD 约束中「图主掉线暂不迁移」是当前版本不做。房主迁移要做。

**文件清单**:
- 修改: `network/HeartbeatManager.java`, `network/RoomHost.java`, `sync/MessageRouter.java`, `CrossSpireMod.java`
- 可能新建: `network/HostElectionHandler.java`

**验证**: 新增 `RoomHostTest.testHostMigration`, `HeartbeatManagerTest.testTimeoutDetection`。

---

### 2.5 事件系统补全 (FR-4.3~4.5)

**现状**: `EventSyncPatches` 只广播 `eventId` + 空 effects。无 `event_interface/event_select` 处理。

**方案**:

1. **EventInterface 捕获** — `EventSyncPatches.OnEnterRoom` 改为:
   - 捕获 `AbstractEvent` 的名称、描述（DESCRIPTIONS）、选项文本（OPTIONS）
   - 捕获每个选项的 `disabled` 状态(金币不足等)
   - 捕获事件图片路径(素材系统按需请求)
   - 构造 `event_interface` StandardPacket → 广播

2. **EventSelect 处理** — MessageRouter 新增 `event_select` 路由:
   - 非图主的 client 收到 `event_interface` → 本地渲染事件 UI
   - Client 选中选项 → 发 `event_select` 到房主 → 转发给图主
   - 图主调用 `event.buttonEffect(optionIndex)`

3. **EventResult 广播** — 图主执行后捕获结果(产物)，广播 `event_result`。

4. **素材系统集成** — 事件图片通过 `resource_request/response` 按需获取。

**文件清单**:
- 修改: `combat/EventSyncPatches.java`, `sync/MessageRouter.java`
- 新增 payload: `Protocol.EventInterfacePayload`, `Protocol.EventSelectPayload`
- 新增 UI: `ui/RemoteEventDisplay.java` (非图主渲染事件 UI)

**验证**: 新增 `EventSyncPatchesTest.testInterfaceCapture`, `testEventSelectFlow`。

**2026-07-16 修订**: 此方案已被「事件就近原则」设计取代（见下）。

---

### 2.5 revised — 事件就近原则 + 沙盒转录 (FR-4.3~4.5, US-4)

**思路**: 事件回到本地执行。D2 通过 `Class.forName` 创建与图主同名的事件实例，在 suppressEvents + snapshot/restore 包裹中运行完整的事件逻辑（包括 Gremlin Wheel、Match Game、选牌等特殊 UI）。执行过程被自动捕获为 `event_transcript { actions[] }`，发送给图主重播以产出实际效果。向后兼容：若 D2 无该事件类，降级到 `RemoteEventDisplay + event_select`。

**架构** (五层):

1. **接口广播** — OnEnterRoom 广播 `event_class`（全限定名） + `OPTIONS` + `mode`
2. **沙盒执行** — `@SpirePatch` on `AbstractEvent.buttonEffect` Prefix 挡截：snapshot → suppress → buttonEffect → 收集 transcript steps → restore
3. **event_transcript** — 替代 `event_select` + `interact_request/response` 三条消息，统一为一步
4. **图主重播** — stage host 逐 transcript step 重播 → 产出真实 effects → 广播
5. **Fallback** — `Class.forName` 失败 → 降级到 `RemoteEventDisplay + event_select`

**沙盒 Key API**:

```java
// EventCapture.java (新)
class EventStateSnapshot {
    int playerHp, playerMaxHp, playerBlock, playerGold;
    Map<String,Integer> powerAmounts;
    int floorNum;
    // ... 备份 → restore()
}

static EventStateSnapshot takeSnapshot();
static void restoreSnapshot(EventStateSnapshot snap);
static List<String> drainSelectedCards();  // 从 gridSelectScreen 收集选卡
static void appendTranscript(String actionType, int index, List<String> cards, EffectDescription[] effects);
static JsonObject buildTranscript(String eventId);
```

**Transcript 消息**:

```json
{
  "type": "event_transcript",
  "source": "D2",
  "event_id": "LivingWall",
  "actions": [
    {"type": "buttonEffect", "index": 0},
    {"type": "cardSelect",    "cards": ["Strike_R"]},
    {"type": "confirm"}
  ]
}
```

**Voting 模式**: `event_interface.mode="voting"` → 图主收到 transcript 后聚合 → 全员一致才执行 → 复用 `RoomHost.castVote/checkConsensus`。

**文件清单**:
- 新建: `combat/EventCapture.java`
- 修改: `combat/EventSyncPatches.java` (+ `Sandbox` 内部类), `sync/MessageRouter.java` (删 `event_select`, 加 `event_transcript` handler), `ui/CrossSpireCommand.java` (`eventsel` → transcript)
- 删除: `network/InteractMessageSender.java`, `combat/InteractionCapturePatches.java` (已被沙盒统一捕获替代)
- 保留: `ui/RemoteEventDisplay.java` (作为 fallback), `network/EventMessageSender.java` (精简为 `event_interface` + `event_transcript`)

**验证**:
- 单元测试: `EventCaptureTest.testSnapshotRestore`, `testTranscriptBuild`
- E2E: (1) BigFish 沙盒验证; (2) LivingWall card-select transcript; (3) CursedTome/KnowingSkull 复杂事件

---

### 2.6 所有者交互选择 (FR-4.6, US-7) — 已被 §2.5 覆盖

事件类型的交互选择（GridCardSelectScreen、HandCardSelectScreen 等）由 §2.5 的沙盒执行 + event_transcript 统一处理。卡牌/遗物效果触发的交互选择（非事件上下文）保留 `invoke_result.extraData` 扩展作为未来需求。

**验证**: 真机验证(较复杂，需要触发选牌效果的卡牌)。

---

### 2.7 房间标注与共识 (FR-4.7/FR-4.8, US-4a)

**现状**: 地图导航完全未实现。当前只有 `CombatSyncPatches` 在 `MonsterRoom.onPlayerEntry()` 时广播 `room_enter`。

**方案**:

1. **房间索引**: 用整数 index（0-based），对应当前地板 `DungeonMap` 中当前节点可达房间列表的第 N 个。

2. **消息定义** (普通 JSON，非标准包):
   ```
   room_pin      C→房主      {"type":"room_pin","source":"<id>","room":1}
   room_pins     房主→全员   {"type":"room_pins","source":"<host>","pins":{"a":1,"b":1}}
   room_consensus 房主→图主   {"type":"room_consensus","source":"<host>","room":1}
   ```

3. **命令**: `crossspire room <index>` — CrossSpireCommand 新增子命令，构造 `room_pin` 发往房主。

4. **房主聚合**: `RoomHost` 新增 `Map<String, Integer> playerPins`：
   ```java
   void pinRoom(String playerId, int roomIndex) {
       playerPins.put(playerId, roomIndex);
   }
   int checkConsensus() {
       if (playerPins.size() != getPlayerCount()) return -1; // 不完整
       int first = playerPins.values().iterator().next();
       return playerPins.values().stream().allMatch(v -> v == first) ? first : -1;
   }
   ```

5. **共识触发**: 房主每次收 pin 后检测 — 若 return >=0 → 发送 `room_consensus` 给图主。

6. **图主执行**: MessageRouter 收到 `room_consensus` → `SyncExecutor.executeRoomConsensus(roomIndex)`:
   - `Gdx.app.postRunnable` 中操作
   - 获取 `AbstractDungeon.getCurrMapNode()` 的 connected nodes
   - 取第 roomIndex 个 → 调 `goToRoom(key)` 或直接设 node.room
   - 触发 `MonsterRoom.onPlayerEntry()` → CombatSyncPatches 自动广播 `room_enter`

7. **再标注**: 覆盖旧值。每次标注后广播最新 `room_pins`。

8. **无超时**: 不采用投票或默认选择——走全员一致，玩家自行协商。

**文件清单**:
- 修改: `ui/CrossSpireCommand.java`, `network/RoomHost.java`, `sync/MessageRouter.java`, `sync/SyncExecutor.java`

**验证**:
- 单元测试: `RoomHostTest.testPinAndConsensus`, `testNoConsensusWhenDivergent`
- 双设备: D1 host, D2 client → 双方 `crossspire room 0` → D2 同步进入同一房间

---

## Phase 3: 清理与稳定

### 3.1 Cache 修复 (FR-5.2)

**现状**: `RemoteAssetCache` L1 是 64-entry 满即全清，非 128MB LRU。SHA-256 verify 从不调用。

**方案**:

1. **LRU 改造** — `LinkedHashMap<String, Texture>(32, 0.75f, true)` + `removeEldestEntry`:
   ```java
   private static final Map<String, Texture> textureCache = Collections.synchronizedMap(
       new LinkedHashMap<String, Texture>(32, 0.75f, true) {
           @Override protected boolean removeEldestEntry(Map.Entry<String, Texture> eldest) {
               if (size() > 64) {
                   eldest.getValue().dispose();  // clean GPU resource
                   return true;
               }
               return false;
           }
   });
   ```
   128MB 精确限制在 LibGDX/Java side 不现实(Texture 的 native 内存不暴露给 JVM)。折中: 增加 size cap 到 256 entries 并对 `putTexture` 做估算 `width * height * 4` 累计计数。

2. **verify 接入 readDisk** — 在 `readDisk` 中做 SHA-256 checksum 校验，不匹配则删除文件 + 返回 null。

3. **卡片素材 serving 修复** — `RemoteAssetServer.serveResource` 对于 card 类型:
   - 先读 `CardLibrary.getCard(cardId)` → 获取 Texture 的路径(通过 `img` 字段或 card texture)
   - 若本地有 → 通过 `Gdx.files.internal()` 读取原始 .png 文件 → Base64 编码 → response
   - 若本地无 → 返回 "not_found"

**文件清单**:
- 修改: `resource/RemoteAssetCache.java`, `resource/RemoteAssetServer.java`

---

### 3.2 CentralQueueManager bug 修复

| Bug | 修复 |
|-----|------|
| dedup broken (packetId 被 UUID 覆盖) | 移除 `UUID.randomUUID()`，直接用 `pkt.packetId = pkt.source + "-" + pkt.seq`；dedup 改为 `packetId` 匹配 |
| `processNext()` 读到 queue 外 | `synchronized(queue) { if (queue.isEmpty()) ...; queue.get(0); }` |
| `onInvokeResult` 匹配弱 | 改用 `packetId` 匹配而非 owner+cardId |
| `markDone` 非同步 | 包裹在 `synchronized(queue)` 中 |

**文件清单**:
- 修改: `combat/CentralQueueManager.java`

---

### 3.3 `seq` 全域统一

当前问题: `seq=1` 硬编码出现于 `LocalReference`, `RemoteReference`(QueueComplete), `CombatSyncPatches`, `EventSyncPatches`(timestamp%100000), `EndTurnSyncPatches`(timestamp%100000), `NullReference`(timestamp%100000)。

**方案**: 所有非控制消息构造处统一改为 `packet.seq = CrossSpireMod.nextSeq()`。

**文件清单**:
- 修改: `reference/LocalReference.java`, `reference/RemoteReference.java`, `reference/NullReference.java`, `sync/CombatSyncPatches.java`, `sync/EndTurnSyncPatches.java`, `sync/GoldSyncPatches.java`, `combat/EventSyncPatches.java`
- 确保 `MessageRouter.handleInvoke` → `result.seq = CrossSpireMod.nextSeq()`(当前 `result.seq = 1`)

---

### 3.4 Bug 修复清单

| 文件 | Bug | 修复 |
|------|-----|------|
| `SuppressBaseModPatches.java:SuppressOnPlayerDamaged` | 返回 0 归零伤害 | 改为 `return SpireReturn.Return(__amount)` |
| `MessageRouter.java` | 重复 `combat_result` 分支(dead code) | 删除 line 96 的 unreachable branch |
| `IntentRenderer.java` | 忽略传输的 intent 数据 | 应用 `intent/damage/hits` |
| `RemotePlayerState.java` | powers/energy/gold 不同步 | `syncToPlayerInstance()` 同步全部字段 |
| `LocalCapturePatches.java` | 无 EventSuppression 守卫 | 加 `if (EventSuppression.isSuppressed()) return;` |
| `Reference.java` | hostId 在重选后过期 | `ReferenceFactory` 中存量 Ref 的 hostId 更新机制 |

---

### 3.5 第 2.3 步: 图主本地 RNG 接入

**方案**(已在 1.1 部分做，这里是清理留下的消费点):
- `MonsterIntentBroadcastPatches` 中 `new Random(System.nanoTime())` → 提取为 `StageHost.getStageRng()` 实例。
- 图主本身就是 host → 使用同一个 `stageRng` 实例。

**文件清单**:
- 修改: `combat/MonsterIntentBroadcastPatches.java`(确保使用 stageRng)，`remote/StageHost.java`(加 getStageRng)

---

## 归档: 不影响运行的顺路修

| 位置 | 问题 | 修改 |
|------|------|------|
| `ReferenceFactory.java` | 非 card 资源 refId 被标 `card:` | `createRef` 中 `LocalReference`/`RemoteReference` 去掉硬编码前缀 |
| `TriggerRegistry.java` | 无 fire 端 | 在 `CombatResultReplayer.inducedUseCard` 调用后补充 trigger fire (若已有需确认) |
| `RemoteCard/Relic/Potion/PowerResource.java` | POJO 未使用 | 保留(为素材系统未来使用)，但加上注解说明用途 |
| `ResourceRegistryTracker.java` | 无查询 API | 加 `hasCard(playerId, cardId)` 等 getter |
| `EffectCapture.stopCapture()` | 先清后返 | 改为先 `getCaptured()` snapshot 再 `clear()` |
| `StageHost.electHost()` | 排序输入数组 | 改为排序副本 `playerIds.clone()` |

---

## Phase 依赖关系

```
P1 (1.1 RNG删除, 1.2 StandardPacket, 1.3 去P2P)
 │
 ├──→ P1.4 (BroadcastManager 删除) [可与 1.1-1.3 并行]
 │
 ▼
P2.1 (InducedReplay修复) ──┐
P2.2 (EndTurn gate)        ├── 可并行, 均依赖 P1 完成
P2.3 (MonsterTurn重写)     │
 │                         │
 ├── P2.4 (Host迁移) ──────┘ [需要 P2.2 完成来保证状态一致性]
 │
P2.5 (Event补全) ──┐
P2.6 (Interactive) ┤ 可并行
 │                 │
 ▼                 ▼
P3 (清理收尾 — 依赖所有 P2 完成后做)
P3.1 Cache修复
P3.2 CentralQueue bugs
P3.3 seq统一
P3.4 bug修复清单
```

---

## 不做什么 (显式排除)

以下 SDD 需求在当前版本显式不做，与 `spec.md` 约束一致:

1. ❌ **图主在线迁移**(SDD constraint: "图主掉线 → 暂停等待，不迁移")
2. ❌ **塔2 实际联机实现**(SDD constraint: "仅预留架构")
3. ❌ **P2P 全互联**(已明确淘汰)
4. ❌ **事件投票**(constraint: "一人选择即生效")
5. ❌ **跨回合存档分享**(constraint: "仅本地保存")
6. ❌ **>4 人房间**(constraint: "集中在 4 人场景")

以下 SDD 需求由现有代码基本完成，无需改造:

1. ✅ `RemotePlayer` + `RemoteRenderer` 渲染管线(FR-2.7) — 可用
2. ✅ `SuppressBaseModPatches` 12 个独立拦截(修一个 bug 即可) — 可用
3. ✅ `ContentValidator` SHA-256 校验 — 可用
4. ✅ `RemoteCharacterResource` 骨骼渲染 — 可用
5. ✅ 13 种 fallback 效果 `applyEffect()` — 可用
6. ✅ UI 面板(F1-F4, Lobby, RoomPanel, Chat, ServerPicker) — 可用

---

## 实施记录

### 2026-07-15 — P1 架构修复完成

| Task | 变更 | 文件 |
|------|------|------|
| T1.1 | 删除 SyncedRng/RngManager/RngSyncPatches + 对应测试 | -3 main, -2 test |
| | StageHost 加 stageRng, MonsterIntentBroadcastPatches 改用 | +2 main |
| | 清除 syncedSeed/rngManager 所有引用 (7 文件) | ~7 main |
| T1.2 | 新建 StandardPacket + PacketOperation | +2 main, +1 test |
| | Protocol.java 新增 payload 类, 保留 legacy compat 类 | ~1 main |
| T1.3 | P2PManager → StarConnectionManager, 删 onHelloReceived 互连逻辑 | rename |
| | 全局引用重命名 (10 处, 6 文件) | ~6 main |
| T1.4 | 删除 BroadcastManager | -1 main |
| | P1 验证: 41 tests pass, JAR 454KB, 双设备启动 OK |

### 2026-07-15 — P2/T2.1-T2.3 完成

| Task | 变更 | 文件 |
|------|------|------|
| T2.1 | CombatResultReplayer: 始终用 CardStub, PowerStub fallback, 新效果回 host 队列 | 2 main |
| | EffectCapture.stopCapture: snapshot→clear → return | 1 main + 1 test |
| T2.2 | EndTurnSyncPatches: Prefix gate + Postfix broadcast | 1 main |
| | MessageRouter: queue_empty → enable EndTurnButton | 1 main |
| T2.3 | MonsterTurnPatches: usePreBattleAction → applyStartOfTurnPowers HP delta | 1 main |
| |
| 附带修复 | SuppressBaseModPatches: publishOnPlayerDamaged Return(0)→Return(__amount) | |
| | LocalCapturePatches: +EventSuppression guard | |
| | MessageRouter: 删除 duplicate combat_result branch | |
| |
| P2.1 验证: 45 tests pass | |

### 2026-07-15 — P3/T3.2-T3.4 完成

| Task | 变更 | 文件 |
|------|------|------|
| T3.2 | CentralQueueManager: dedup→source+seq, sync processNext/markDone | 1 main + 1 test |
| T3.3 | seq 统一: 9 文件 seq=1/time%→nextSeq() | 9 main |
| |
| P3 验证: 45 tests pass, JAR 454KB, 双设备 game_ready 无 crash | |

### 当前状态（2026-07-19 文档对齐）

**历史阶段（已完成）**:
- P1–P3、归档 A1–A6、P4 Android Harness 边界清理 — 见 `task.md`
- FR-6.1/6.2/6.3 — 独立本地 RNG ✅
- StandardPacket / 星型拓扑 / 全域 seq ✅
- FR-2.1–2.5、2.7 — 中央队列、诱导门控基础、EndTurn gate、RemotePlayer ✅
- FR-1.5 / 事件就近+transcript / 房间标注共识 / Cache LRU / ResourceRegistry — 已在 P2–P3 落地 ✅

**P5（原版 buff 路径已完成；灾厄延后）**:
| Task | 状态 | 对应 FR |
|------|------|---------|
| T5.0 文档 + schema 草案 | ✅ | FR-2.8–2.10 草案 |
| T5.1 LocalOwnerGate + Replayer 门控 | ✅ | FR-2.4 / FR-3.4 |
| T5.2 ComponentAttachment + apply_power logic_owner（原版） | ✅ | FR-2.9 / FR-3.3 |
| T5.3 monster mutation proposal/commit | **延后** | FR-2.10 / FR-2.6 剩余 |
| T5.4 显式 combat_phase | ✅ | FR-2.8 |
| T5.5 E2E 原版 Bash/Vulnerable + phase | ✅ 2026-07-19 | US-2 原版；灾厄待 T5.3 |

> 旧进度表中的「15/29」「51 tests」已废弃。测试类数量与 pass 数以 `./gradlew test` 为准。

### 2026-07-16 — 事件就近原则设计（ARCHITECTURE.md §9 重写）

事件处理从「图主独占 + event_select 指令」改为「就近实例化 + 沙盒转录」模型：

- **ARCHITECTURE.md §9**: 重写整个事件处理章节 — 5 层架构（接口广播 / 沙盒执行 / event_transcript / 图主重播 / fallback）
- **spec.md US-4**: 更新事件验收标准 — 所有玩家本地实例化事件，沙盒执行，transcript 回传图主重播
- **plan.md §2.5/2.6**: 取代旧 event_interface/event_select + interact_request/response 方案
- **协议 §17**: 删除 event_select / interact_request / interact_response，新增 event_transcript / event_votes

框架要点：
- D2 通过 Class.forName(event_class) 创建本地事件实例 → 兼容原版转轮/对对碰/头部 UI 及 Mod 事件
- @SpirePatch 沙盒：snapshot → suppressEvents → buttonEffect → 收集 transcript + selectedCards → restore
- event_transcript 替代三条独立消息，图主逐 step 重播产出 effects
- voting 模式复用 RoomHost.castVote 基础设施
- fallback: ClassNotFound → RemoteEventDisplay + event_select 旧版兼容

### 2026-07-15 — P2/T2.7d/e/f 完成

| Task | 变更 | 文件 |
|------|------|------|
| T2.7d | cmdPlay 不再调 UseCardAction；改为 construct QueueSubmitMessage → host: centralQueue.onQueueSubmit / client: send(host) | `CrossSpireCommand.java` |
| T2.7e | LocalCapturePatches: AtomicInteger suppressDepth counter + pushSuppress()；CentralQueueManager.handleOwnItem + MessageRouter.handleInvoke 包裹 pushSuppress | `LocalCapturePatches.java`, `CentralQueueManager.java`, `MessageRouter.java` |
| T2.7f | QueueSubmitBuilder 提取公共 builder；LocalReference type→combat_result；buildVfxOps 加 cardType/rarity/target | `QueueSubmitBuilder.java`(新), `LocalReference.java` |
| |
| 异步防回弹: pushSuppress() 在 addToBottom 前 → Postfix 中 decAndGet 释放 |
| 测试: QueueSubmitBuilderTest(4) + LocalCapturePatchesTest(2) → 51 total |

### 卡牌执行 SDD 链路 (已实现)

```
cmdPlay("Strike_R")
  host:  → QueueSubmitBuilder.build → centralQueue.onQueueSubmit → processNext
            → handleOwnItem → pushSuppress → dereference → UseCardAction(排队)
            → buildEffects(baseDamage) + buildVfxOps → combat_result(广播)
            → markDone → queue_empty(广播)

  client: → QueueSubmitBuilder.build → send(host)
            → host.MessageRouter.handleQueueSubmit → centralQueue.onQueueSubmit
            → sendInvoke → client.MessageRouter.handleInvoke
            → pushSuppress → UseCardAction(排队) → invoke_result → host
            → broadcastCombatResult → D2 CombatResultReplayer → INDUCED 扣血

  UseCardAction 异步执行 (下一帧):
    → AbstractPlayer.useCard → BaseMod hooks + Strike_R.use → DamageAction → monster.damage(6)
    → LocalCapturePatches.Postfix: suppressDepth>0 → decAndGet → return (不重复 capture) ✓
```

### E2E 测试状态

JAR 已推送到两台设备 (localhost:15555, 25555)，JUnit 51/51 通过。
自动 E2E 受阻于 Amethyst launcher 需要手动点击"Play"启动 STS 引擎。
手动验证流程: launcher 点 Play → 等 main_menu → batch 命令自动执行。

> 历史说明：以上记录描述当时的 batch 文件注入方案。P4 将删除该方案，改由 SlayTheAmethyst Harness 调用标准 BaseMod console。当前维护者测试台仍使用 D1 `localhost:15555` 和 D2 `localhost:25555`，游戏连接依赖外部预置的 D2 `localhost:54321` → D1 `localhost:54321` 转发；CrossSpire 不维护该转发。Desktop 验证在 P4 暂缓。

---

## Phase 5: Buff 所有权与诱导重放门控

> **Breaking contract 文档修订（2026-07-18）**。旧 FR-2.4/3.4「全量深层诱导」与「图主 INDUCED 自动跑怪物 power」已否决。以 `spec.md` FR-2.4/2.8–2.10/3.3–3.5 与 `ARCHITECTURE.md` §8–10 为准。

### 契约摘要

| 规则 | 定义 |
|------|------|
| Buff 逻辑所有者 | 施加者优先；掉线 content-hash → 宿主权威 |
| 触发 | 房主同步阶段后，logic_owner **本地自发**执行；非 owner 投影无效果 |
| 诱导重放 | AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY；禁止无门控全量 hook |
| 怪物核心状态 | 图主单写；非图主 → `monster_mutation_proposal` → commit |
| 阶段协调 | **房主**（非图主点名 invoke） |

### 任务

| Task | 内容 | 状态 |
|------|------|------|
| T5.0 | 文档 + `protocol-schema.json`：logic_owner、mutation、combat_phase 草案 | ✅ |
| T5.1 | `LocalOwnerGate` + Replayer AUTHORITATIVE_APPLY / LOCAL_OWNER_ONLY；保留 executor_id；hop 限制 | ✅ |
| T5.2 | `apply_power` + `logic_owner_id`；ComponentAttachment；PowerStub no-op（原版 Bash） | ✅ |
| T5.3 | 怪物 mutation proposal/commit 路径 + 图主 revision | **延后** |
| T5.4 | 房主显式 `combat_phase` + end-turn 共识 | ✅ |
| T5.5 | 双机 E2E：Bash→Vulnerable + phase（原版）；灾厄待 T5.3 | ✅ 2026-07-19 |

### 文件触点（实现时）

- Schema: 已 additive 扩展（本轮 T5.0）
- `CombatResultReplayer.java`, `TriggerRegistry.java`, `PowerStub.java`
- `MessageRouter.java`, `CentralQueueManager.java`, `Protocol.java`, `PacketOperation.java`
- 计划新类见 ARCHITECTURE §20

### 与历史 T2.1 的关系

T2.1 实现了 stub-only 与效果回收集，但验收仍写「全量 hook」。P5 **收紧**诱导语义；T5.1 已改 Replayer（AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY），T5.2+ 补全 attachment / mutation / phase。

### 依赖与入口

```
P1–P4 + 归档 ✅
        │
        ▼
P5.T5.0–T5.2 + T5.4–T5.5 ✅（原版 Bash/Vulnerable）
        │
        └─→ T5.3 mutation / 灾厄（内容就绪后再做）
```

### 2026-07-19 — T5.5 双机 E2E（原版）

| 项 | 结果 |
|----|------|
| 连接 | D1 host + D2 join（ADB forward/reverse 游戏 54321） |
| Bash | D1 `queue_complete: [damage=8, apply_power=2]` |
| D2 诱导 | `apply_power: Vulnerable→Cultist x2 logic_owner=<D1>`；`skip non-owner power logic` |
| phase | `resolving_queue` → `queue_empty` → `pre_monster_turn` 双端一致 |
| 诊断 | Arthas `jad` 核对新类；冷启动后加载新 JAR |

---

## Phase 6: 战斗回合闭环

> 在 P5 原版 buff 契约之上，打通 end_turn → 怪物回合权威 → 回玩家回合。**T5.3 mutation 仍延后。**

### 状态机

```
queue_empty / player_turn
  --all end_turn--> pre_monster_turn --> monster_turn
  --stage host HP delta combat_result--> post_monster_turn --> player_turn
```

### 任务

| Task | 内容 | 状态 |
|------|------|------|
| T6.0 | 文档 + 阶段表对齐 | ✅ |
| T6.1 | `CombatTurnOrchestrator` 合法转换 + host 广播 `monster_turn` | ✅ |
| T6.2 | 图主 `MonsterGroup.applyPreTurnLogic` HP 增量 → combat_result | ✅ |
| T6.3 | logic_owner 原版 power 自发（随引擎；gate 已有） | 部分 |
| T6.4 | monster 阶段拒绝 queue_submit；end_turn 门控 | ✅ |
| T6.5 | JUnit + 双机 E2E 一回合闭环 | ✅ |

### 文件

- `CombatTurnOrchestrator.java`, `MonsterTurnCapture.java`, `MonsterTurnPatches.java`
- `CombatPhaseCoordinator`, `MessageRouter`, `EndTurnSyncPatches`, `CentralQueueManager`

---

## Phase 7: 小队化冒险推进与批准式事件

> P7 将房间级玩法状态收敛为小队域：房主保留网络路由与房间目录，图主保留阶段地图和房间内容权威，队长负责本队队列、阶段和地图共识。事件从 sandbox transcript 改为“原生选择先请求房主批准，获批后选择者本地执行”。详见 `ARCHITECTURE.md` §6、§7、§11、§19 与 `spec.md` US-4/US-4a/US-4c。

### 依赖顺序

```
P7.0 战斗权威/快照硬化
  → P7.1 PartyState + party_id 路由
    → P7.2 队列、阶段、可见性和地图标注按小队隔离
      → P7.3 图主地图/房间目录与正常导航
        → P7.4 批准式原生事件 + RemoteEventDisplay fallback
          → P7.5 事件内房间与 voting
```

### 任务

| Task | 内容 | 状态 |
|------|------|------|
| T7.0a | 战斗权威：非图主禁止本地怪物 AI；phase transaction 去重/授权；明确 monster-turn result 关联 | ✅ JUnit 144/144；Android E2E 待重试 |
| T7.0b | attachment 生命周期基础：原子替换、remove_power 清理、战斗边界清空 | ✅ 聚焦 JUnit 9/9 |
| T7.0c | monster-turn transaction gate：连续回合、零伤害、host≠stage-host 的图主来源验证、重复/延迟 result 拒绝 | ✅ JUnit 153/153；Android 双机 E2E 两轮闭环通过（2026-07-20） |
| T7.1 | Schema-first `PartyState`/`PartyManager`、默认 P0、成员最小 ID 队长、leave/join 请求与批准/拒绝状态 | ✅ JUnit 159/159；router/命令 wiring 留给 T7.2 |
| T7.2a | Party snapshot 路由：房主默认 P0、标准包广播、客户端原子应用与 status 诊断 | ✅ JUnit 159/159；Android 双机 P0 snapshot 通过（2026-07-20） |
| T7.2b | `party_id` 隔离中央队列、end-turn、combat phase、room pins 与同队渲染；RoomHost 仅路由/目录 | 进行中：queue/end-turn/phase 的数据与接收 UI 已按 party 隔离（2026-07-20）；成员→RoomHost→队长的定向路由和队长→RoomHost→本队的玩法中继已完成（2026-07-21）。`PartyVisibility` 已限制远程角色渲染/状态 UI/怪物目标计数为同队成员；room pins 仍依赖 T7.3 |
| T7.3 | 图主地图快照、房间目录与正常相邻节点导航；按小队 room consensus | 进行中：MapHost/NIH + pin/allocate + NIH open/`node_instance_opened`（诊断 Cultist）已完成，Android D1/D2 全链路通过（2026-07-21）。真实节点生成 RNG 与多房间类型待实现 |
| T7.4 | `event_choice_request/approved/rejected/player_result`；hash 匹配时原生 UI，不用 sandbox；fallback 由图主定向执行 | 进行中：individual 审批状态机、StandardPacket 路由、诊断命令与 Android D1/D2 approval/result relay 通过（2026-07-21）；原生 buttonEffect 门控、fallback NIH 执行与 voting 待实现 |
| T7.5 | event-room instance、同选项成员路径与按小队 voting；跨队相遇仅记录 | 待开始 |
| T7.6 | Schema/FullSnapshot 对齐 attachment 与 parties；Android 双机和 Desktop smoke 验证 | 待开始 |

### 事件约束

1. 非 voting 事件的个人收益允许每名成员独立选择；每个有效请求由房主独立批准，获批选择者本地执行并同步自己的状态差量。
2. 内容类和 hash 匹配才可执行本地原生事件；缺类或 hash 不匹配时用 `RemoteEventDisplay`，由图主执行并定向应用结果。
3. 事件不得隐式让同一小队成员走向不同地图节点。产生事件内房间时，图主创建稳定 instance，选择相同选项者走同一小队路径；直接离开去地图节点前必须显式离队。
4. 投票仅按 `party_id` 聚合；房间全员不再是默认玩法共识范围。
