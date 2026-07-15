# CrossSpire — SDD 迁移技术方案 (Plan)

> 本文档是 SDD 迁移的实施设计方案。现状审计结论见 codebase audit，需求见 `spec.md`，架构设计见 `ARCHITECTURE.md`。

## 执行状态

| Phase | 进度 | Commit |
|-------|------|--------|
| P1 架构修复 | ✅ 5/5 完成 | `9df0e44` `3ce46d7` `ad8318c` |
| P2 功能补全 | 🟡 3/10 完成 (T2.1-T2.3, T2.7a-c 新增) | `89bbb1e` `447a216` |
| P3 清理稳定 | 🟡 3/8 完成 (T3.2-T3.4) | `0975c5a` |
| 归档 | 🟡 1/6 完成 (A1) | — |
| **总** | **12/29** | 45 tests pass |

## 目录

1. [迁移策略总览](#迁移策略总览)
2. [Phase 1: 架构修复](#phase-1-架构修复)
3. [Phase 2: 核心功能补全](#phase-2-核心功能补全)
4. [Phase 3: 清理与稳定](#phase-3-清理与稳定)
5. [归档: 不影响运行的顺路修](#归档-不影响运行的顺路修)

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

---

### 2.6 所有者交互选择 (FR-4.6, US-7)

**现状**: 完全未实现。SDD 要求 invoke 执行中触发选牌/选目标时回传给调用方。

**方案**:

1. **Patch GridCardSelectScreen / HandCardSelectScreen**:
   - `@SpirePatch` 拦截 `open()` → 若当前在 invoke 执行线程中 → 捕获选择参数
   - 所有者构造 `interact_request` StandardPacket → 房主 → 调用方

2. **调用方渲染选择 UI** — `MessageRouter` 收到 `interact_request` → 本地渲染 GridCardSelectScreen

3. **选择回传** — 选择完毕 → `interact_response` → 房主 → 所有者继续执行

4. **超时处理** — 30s 无响应 → 默认策略(选第一个/随机/取消)

**文件清单**:
- 新建: `combat/InteractionCapturePatches.java`
- 新增 payload: `Protocol.InteractRequestPayload`, `Protocol.InteractResponsePayload`
- 修改: `sync/MessageRouter.java`

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

### 当前状态 (12/26)

**已完成 SDD 需求**:
- FR-6.1/6.2/6.3 — 独立本地 RNG 策略 ✅
- NFR-7 — StandardPacket 信封引入 (基础设施就位, 全量迁移待 T2+) ✅
- ARCH §16 — 星型拓扑 (P2P 互连代码已清) ✅
- FR-2.4/3.4 — 诱导重放: stub-only + PowerStub fallback + 效果回收集 ✅
- FR-2.5 — EndTurnButton gate ✅
- FR-2.6 — 怪物回合 HP 增量法 ✅
- Protocol Design Rules — 全域 monotonic seq ✅
- AGENTS/Code — EventSuppression guard 全覆盖 ✅

**剩余 SDD 需求**:
- FR-1.5 — 房主迁移 (T2.4)
- FR-4.3~4.5 — 事件系统补全 (T2.5)
- FR-4.6 — 所有者交互选择 (T2.6)
- FR-4.7/4.8 — 房间标注与共识 (T2.7a/b/c) [新]
- FR-5.2 — Cache LRU + SHA-256 校验 (T3.1)
- FR-5.1 — ResourceRegistry 查询 API (T3.5)
- 文档同步 (T3.7)
