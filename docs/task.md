# CrossSpire — SDD 迁移任务清单 (Task)

> 每个任务粒度控制在 1-3 文件。完成后跑 `./gradlew test` 确认。按依赖顺序排列。

## 进度总览

| Phase | 任务数 | - [ ] | - [x] |
|-------|--------|-------|-------|
| P1 架构修复 | 5 | 5 | 0 |
| P2 功能补全 | 7 | 7 | 0 |
| P3 清理稳定 | 8 | 8 | 0 |
| 归档 | 6 | 6 | 0 |
| **合计** | **26** | **26** | **0** |

---

## P1: 架构修复 (必须先做)

### T1.1 删除共享 RNG 子系统 · priority(high)

**影响**: `spec.md` FR-6.1/6.2/6.3

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 删除文件 | `rng/SyncedRng.java`, `rng/RngManager.java`, `rng/RngSyncPatches.java` |
| 2 | StageHost 加 `private Random stageRng = new Random()` + `getStageRng()` | `remote/StageHost.java` |
| 3 | MonsterIntentBroadcastPatches 改用 `stageHost.getStageRng()` | `combat/MonsterIntentBroadcastPatches.java` |
| 4 | 移除 `CrossSpireMod.syncedSeed` + `rngManager` 字段 | `CrossSpireMod.java` |
| 5 | 移除 SyncExecutor.handleBattleStart 中设置 syncedSeed | `sync/SyncExecutor.java` |
| 6 | 移除 RoomPanel 中 seed 同步门控逻辑 | `ui/RoomPanel.java`, `ui/LobbyState.java` |
| 7 | 移除 GameStarter 的 `seed` 参数 | `remote/GameStarter.java` |

**测试**:
- [ ] 更新 `RngManagerTest.java` → 删除或改为验证 StageHost stageRng 存在
- [ ] 运行 `./gradlew test` — 确保 48 测试全部通过

**依赖**: 无

---

### T1.2 引入 StandardPacket 信封 · priority(high)

**影响**: `spec.md` NFR-7, `ARCHITECTURE.md` §17

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 新建 StandardPacket 类 (packetId, source, seq, timestamp, refId, ownerId, resourceHash, operation, payload) + toJson/fromJson + 便捷构造器 | `network/StandardPacket.java` |
| 2 | 新建 PacketOperation 常量 | `network/PacketOperation.java` |
| 3 | 重写 Protocol.java — 仅保留 payload 类: EffectDescription, OperationStep(新增 cardType/rarity/target), RemotePlayerState, QueueSubmitPayload, QueueUpdatePayload, QueueEntry, InvokePayload, InvokeResultPayload, CombatResultPayload, MonsterIntentEntry, MonsterIntentPayload, EventInterfacePayload, EventSelectPayload, EventResultPayload, InteractRequestPayload, InteractResponsePayload, ResourceRegistryPayload, ResourceRequestPayload, ResourceResponsePayload, AnimationSyncPayload, FullSnapshotPayload。删除旧 GameMessage 子类 | `network/Protocol.java` |
| 4 | 更新 protocol-schema.json | `shared/cross-spire-protocol/protocol-schema.json` |
| 5 | 更新 CrossSpireMod.send() 签名为 `send(StandardPacket)` + 更新 nextSeq() | `CrossSpireMod.java` |
| 6 | 更新 MessageRouter 分发从 msg.type → packet.operation switch | `sync/MessageRouter.java` |
| 7 | 全局搜索替换所有消息构造/解析点 (约30处) | 所有引用 `new Protocol.*Message` / `type=`. 的文件 |
| 8 | 删除 `Protocol.GSON`(移入 StandardPacket) | `network/Protocol.java` |

**测试**:
- [ ] 新建 `StandardPacketTest.java`: testSerialize, testDeserialize, testSeqMonotonic, testOperationEnum
- [ ] 更新 `ProtocolTest.java` → 改为测 payload 序列化
- [ ] `./gradlew test` — 确保 48+ 测试通过

**依赖**: T1.1 完成后(避免在旧 Protocol 上做无用功)

---

### T1.3 网络层去 P2P 化 · priority(high)

**影响**: `ARCHITECTURE.md` §16

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 重命名 P2PManager → StarConnectionManager | `network/P2PManager.java` → `network/StarConnectionManager.java` |
| 2 | 删除 sendHello() 中携带 peers 列表；删除 onHelloReceived() 中自动互连 peer 循环 | StarConnectionManager 内部 |
| 3 | 补充 RoomHost: broadcastToAll(rawMessage), relayToOwner(ownerId, packet) | `network/RoomHost.java` |
| 4 | 更新 CrossSpireMod: 字段 `p2pManager` → `connectionManager`, 类型更新 | `CrossSpireMod.java` |
| 5 | 全局替换 `p2pManager` → `connectionManager` 引用 | 约 15 文件 |

**测试**:
- [ ] 更新 `RoomHostTest.java`: testBroadcastToAll, testRelayToOwner
- [ ] `./gradlew test`

**依赖**: T1.2 完成后(新的 RoomHost API 需要 StandardPacket)

---

### T1.4 删除 BroadcastManager 冗余层 · priority(medium)

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 删除文件 | `network/BroadcastManager.java` |
| 2 | 替换引用为 `CrossSpireMod.send()` | 搜索 `BroadcastManager.broadcast` 的所有文件 |

**测试**:
- [ ] `./gradlew test` — 无编译错误

**依赖**: T1.3 完成后

---

### T1.5 Phase 1 集成验证 · priority(high)

| 步骤 | 动作 |
|------|------|
| 1 | `./gradlew test` 全量通过 |
| 2 | git diff 审查所有改动 |
| 3 | 手动验证: D1 host → D2 join → 进入战斗 → 确认无 SyncedRng 日志、消息使用 StandardPacket 格式 |

**依赖**: T1.1-T1.4 全部完成

---

## P2: 核心功能补全

### T2.1 修复诱导重放管道 · priority(high)

**影响**: `spec.md` FR-2.4, FR-3.4, US-3

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | CombatResultReplayer.inducedUseCard() 删除 CardLibrary 分支，始终用 CardStub。从 operationSequence 步骤读取 cardType/rarity/target | `combat/CombatResultReplayer.java` |
| 2 | CardStub 加 cardType/rarity/target 构造参数 | `combat/CardStub.java` |
| 3 | Protocol.OperationStep 加 cardType/cardRarity/cardTarget 字段 | `network/Protocol.java` |
| 4 | PublishPostPowerApply 用真实 ApplyPowerAction；resolvePower fallback 到 PowerStub | `combat/CombatResultReplayer.java` |
| 5 | replayInduced() 包裹在 EffectCapture.startCapture 中，捕获新效果 → 回 host 队列 | `combat/CombatResultReplayer.java`, `combat/EffectCapture.java` |
| 6 | EffectCapture.stopCapture(): 修复先清后返 bug，改为 snapshot → clear | `combat/EffectCapture.java` |
| 7 | LocalCapturePatches 同时记录 operationSequence(不仅是 effects) | `sync/LocalCapturePatches.java`, `combat/EffectCapture.java` |

**测试**:
- [ ] 新建 `CombatResultReplayerTest.testStubPreventsDoubleExecution`
- [ ] `testInducedReplayCapturesNewEffects`
- [ ] `testApplyPowerUsesPowerStubFallback`
- [ ] 更新 `EffectCaptureTest` 验证 stopCapture 返回 captured 再清空

**依赖**: T1.5 完成

---

### T2.2 实现 queue_empty → EndTurnButton gate · priority(high)

**影响**: `spec.md` FR-2.5, US-2

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | EndTurnSyncPatches 新增 @SpirePrefixPatch 在 EndTurnButton.enable() 上: isConnected() && !queueEndTurnAllowed → Return(null) | `sync/EndTurnSyncPatches.java` |
| 2 | MessageRouter 处理 queue_empty: QueueDisplay.onQueueEmpty() + EndTurnButton.enable() (需包 suppressEndTurn flag) | `sync/MessageRouter.java` |
| 3 | queue_submit 时调用 EndTurnButton.disable() | `sync/MessageRouter.java` |
| 4 | sendStandardPacket Consumer → 构造后自动 seq | `network/StandardPacket.java` |

**测试**:
- [ ] 新建 `EndTurnSyncPatchesTest.testButtonDisabledUntilQueueEmpty`
- [ ] `testButtonEnabledOnQueueEmpty`

**依赖**: T2.1 完成(queue_submit 路径需新 Protocol 格式)

---

### T2.3 怪物回合正确 patch · priority(high)

**影响**: `spec.md` FR-2.6, US-2, `ARCHITECTURE.md` §8

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 重写 MonsterTurnPatches: BeforeTurn patch (applyStartOfTurnPowers postfix 记录 preHp/preBlock) + AfterMonsterTurns patch (applyStartOfTurnPowers prefix 计算 delta) | `combat/MonsterTurnPatches.java` |
| 2 | IntentRenderer.show() / showSnapshot() 实际应用传输的 intent/damage/hits 数据 | `sync/IntentRenderer.java` |
| 3 | MonsterIntentBroadcastPatches 分离 intent snapshot 到 applyStartOfTurnPowers 触发 | `combat/MonsterIntentBroadcastPatches.java` |

**测试**:
- [ ] 新建 `MonsterTurnPatchesTest.testHpDeltaAfterMonsterTurns`
- [ ] `testIntentRendererAppliesTransmittedData`
- [ ] `testBlockDeltaRecorded`

**依赖**: T2.1 完成

---

### T2.4 房主选举与迁移 · priority(medium)

**影响**: `spec.md` FR-1.5, US-6

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | HeartbeatManager 补全: peerLastSeen Map, pong handler, 5s 超时检测线程, onPeerTimeout callback | `network/HeartbeatManager.java` |
| 2 | RoomHost.onPeerTimeout(): removePlayer → 广播 player_left → 若掉线者是 host → triggerHostElection | `network/RoomHost.java` |
| 3 | MessageRouter 加 pong 处理 + 调用 heartbeatManager.onPong() | `sync/MessageRouter.java` |
| 4 | 房主选举流程: electHost (按 ID 排序), 广播 host_migration, 新房主重建路由 | `network/RoomHost.java` |
| 5 | CrossSpireMod 加 reconnectToNewHost() | `CrossSpireMod.java` |
| 6 | FullSnapshot 收集与分发 | `sync/SyncExecutor.java` |

**测试**:
- [ ] 新建 `HeartbeatManagerTest.testTimeoutDetection`
- [ ] 更新 `RoomHostTest` + testHostMigration, testElectNewHost
- [ ] 新建 `RoomHostTest.testFullSnapshotRebuild`

**依赖**: T2.2 完成(状态一致性依赖 gate 机制)

---

### T2.5 事件系统补全 · priority(medium)

**影响**: `spec.md` FR-4.3~4.5, US-4

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | EventSyncPatches.OnEnterRoom 捕获 event_interface(名称、描述、选项、disabled 状态、图片路径) → 广播 | `combat/EventSyncPatches.java` |
| 2 | MessageRouter 加 event_select → 转发图主 → buttonEffect | `sync/MessageRouter.java` |
| 3 | EventSyncPatches 加 event_result 回调(patch 所有 AbstractEvent.buttonEffect 或监听 openMap/transitionKey) | `combat/EventSyncPatches.java` |
| 4 | 新增 RemoteEventDisplay.java (非图主渲染事件 UI) | `ui/RemoteEventDisplay.java` |
| 5 | Protocol 加 EventInterfacePayload, EventSelectPayload | `network/Protocol.java` |

**测试**:
- [ ] 新建 `EventSyncPatchesTest.testInterfaceCapture`
- [ ] `testEventSelectFlow`
- [ ] `testEventResultBroadcast`

**依赖**: T2.1 完成

---

### T2.6 所有者交互选择 · priority(low)

**影响**: `spec.md` FR-4.6, US-7

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 新建 InteractionCapturePatches: patch GridCardSelectScreen.open() / HandCardSelectScreen.open() → 捕获参数 | `combat/InteractionCapturePatches.java` (新) |
| 2 | Protocol 加 InteractRequestPayload / InteractResponsePayload | `network/Protocol.java` |
| 3 | MessageRouter 加 interact_request / interact_response 路由 | `sync/MessageRouter.java` |
| 4 | 超时处理: 30s 无响应 → 默认策略 | `combat/InteractionCapturePatches.java` |

**测试**:
- [ ] 新建 `InteractionCapturePatchesTest.testRequestResponse`
- [ ] `testTimeoutFallback`

**依赖**: T2.5 完成(事件交互选择用同一条路径)

---

### T2.7 Phase 2 集成验证 · priority(high)

| 步骤 | 动作 |
|------|------|
| 1 | `./gradlew test` 全量通过 + 新增测试覆盖 |
| 2 | 手动验证: 诱导重放不产生双倍伤害、回合结束按钮 gate 生效、怪物回合 HP 变化同步、事件同步 |

**依赖**: T2.1-T2.6 全部完成

---

## P3: 清理与稳定

### T3.1 Cache 修复 · priority(medium)

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | RemoteAssetCache: 替换 clear-all 为 LRU LinkedHashMap(256 cap, dispose evicted textures) | `resource/RemoteAssetCache.java` |
| 2 | readDisk() 接入 verify() SHA-256 checksum | `resource/RemoteAssetCache.java` |
| 3 | RemoteAssetServer.serveResource 对 card 类型: CardLibrary 查 → Gdx.files.internal() 读原始 PNG | `resource/RemoteAssetServer.java` |

**测试**:
- [ ] 新建 `RemoteAssetCacheTest.testLruEviction`
- [ ] `testChecksumVerificationRejectsCorrupted`

**依赖**: T2.7 完成

---

### T3.2 CentralQueueManager bug 修复 · priority(high)

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 修复 dedup: 移除 UUID.randomUUID → 用 source+seq 做 packetId; 查重只需 packetId | `combat/CentralQueueManager.java` |
| 2 | 修复 processNext() 竞态: synchronized(queue) 包裹 get(0) | 同上 |
| 3 | markDone 同步化 | 同上 |
| 4 | onInvokeResult 改用 packetId 精确匹配 | 同上 |

**测试**:
- [ ] 更新 `CentralQueueManagerTest`: testDedupPreventsDuplicate, testConcurrentProcessNext, testInvokeResultExactMatch

**依赖**: T2.7 完成

---

### T3.3 seq 全域统一 · priority(medium)

搜索所有硬编码 seq=1 或 timestamp%100000 的点，全部改为 `CrossSpireMod.nextSeq()`:

| 文件 | 当前 |
|------|------|
| `reference/LocalReference.java` | `complete.seq = 1` |
| `reference/RemoteReference.java` | `complete.seq = 1` |
| `reference/NullReference.java` | `seq = System.currentTimeMillis() % 100000` |
| `sync/CombatSyncPatches.java` | `seq = 1` |
| `sync/EndTurnSyncPatches.java` | `seq = System.currentTimeMillis() % 100000` |
| `sync/GoldSyncPatches.java` | `seq = System.currentTimeMillis() % 100000` |
| `combat/EventSyncPatches.java` | `seq = System.currentTimeMillis() % 100000` |
| `sync/MessageRouter.java` | `handleInvoke → result.seq = 1` |

**测试**:
- [ ] `StandardPacketTest.testSeqMonotonicAcrossCalls`

**依赖**: T2.7 完成

---

### T3.4 Bug 修复清单 · priority(high)

| # | 文件 | 修复 |
|---|------|------|
| 1 | `SuppressBaseModPatches.java` | SuppressOnPlayerDamaged: `Return(0)` → `Return(__amount)` |
| 2 | `MessageRouter.java` | 删除 unreachable `combat_result` 第二个分支(line 96) |
| 3 | `RemotePlayerState.java` | syncToPlayerInstance() 同步 powers/energy/gold |
| 4 | `LocalCapturePatches.java` | 加 `if EventSuppression.isSuppressed() return;` |
| 5 | `Reference.java` / `ReferenceFactory.java` | hostId 重选后调用 `ref.updateHostId()` 更新存量 reference |
| 6 | `RoomPanel.java` | `playerId.substring(0, 8)` 加 length guard |
| 7 | `MonsterTurnPatches.java` | 确保 opSeq 不空(或 COMBAT_RESULT opSeq 处理不依赖它) |

**测试**:
- [ ] 更新已有测试覆盖修复点; `SuppressBaseModPatchesTest.testDamageReturnPreservesAmount`
- [ ] `./gradlew test`

**依赖**: T2.7 完成

---

### T3.5 ResourceRegistryTracker 补齐 · priority(low)

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | onRegistryReceived 存储 powers/potions/characters(当前丢弃) | `resource/ResourceRegistryTracker.java` |
| 2 | 加公开查询 API: hasCard(playerId, cardId), hasRelic, hasCharacter | 同上 |

**测试**:
- [ ] `ResourceRegistryTrackerTest.testQueryApi` (新建)

**依赖**: T3.4 完成

---

### T3.6 seq 同步到心跳 · priority(low)

HeartbeatManager ping/pong 加 monotonic seq(非 StandardPacket，但协议要求所有消息有 seq)

**依赖**: T3.3 完成

---

### T3.7 文档同步 · priority(medium)

| 步骤 | 动作 | 文件 |
|------|------|------|
| 1 | 更新 spire-patches.md — 删除 RngSyncPatches; MonsterTurnPatches 更新为 HP 增量法; 新增 InteractionCapturePatches | `docs/spire-patches.md` |
| 2 | 更新 README.md — 新增 patch 列表 + StandardPacket 说明 | `mods/cross-spire/README.md` |
| 3 | 标记 ACHIEVEMENTS.md — 添加本次 SDD 迁移里程碑 | `docs/ACHIEVEMENTS.md` |

**测试**: 无

**依赖**: T3.4 完成

---

### T3.8 全量回归 · priority(high)

| 步骤 | 动作 |
|------|------|
| 1 | `./gradlew test` — 确保所有测试通过 |
| 2 | 检查 git diff 无意外文件(agent-tmp/debug-artifacts 不在 commit 中) |
| 3 | 最终 commit |

**依赖**: T3.1-T3.7 全部完成

---

## 归档: 顺路修 (低优, 单独迭代)

### A1 ExpressionCapture API 修复

| 文件 | 修复 |
|------|------|
| `combat/EffectCapture.java` | stopCapture: 先 snapshot → 再 clear(避免调用者看不到结果) |

---

### A2 ReferenceFactory 前缀修复

| 文件 | 修复 |
|------|------|
| `reference/ReferenceFactory.java` | createRef 中非 card 类型去掉 `card:` 硬编码前缀(让 LocalReference/RemoteReference 接受 resourceType 参数) |
| `reference/LocalReference.java` | refId 改为 `resourceType + ":" + resourceId + "@" + ownerId` |
| `reference/RemoteReference.java` | 同上 |

---

### A3 TriggerRegistry 确认 fire 端

搜 `TriggerRegistry.getTriggers` 调用点 → 若不存在则加 fire 逻辑在 CombatResultReplayer 和 SyncExecutor 中。

---

### A4 RemoteResource POJO 注解

给 `RemoteCardResource`, `RemoteRelicResource`, `RemotePotionResource`, `RemotePowerResource` 加 JavaDoc 注明用途和当前使用状态。

---

### A5 RemoteAssetServer 非 GL 线程安全

serveResource 中 PNG 编解码移到线程安全路径: 读取 Gdx.files.internal() 原始字节(不经过 Pixmap) → 直接 Base64 → response。

---

### A6 StageHost.electHost 副作用修复

```java
public static String electHost(String[] playerIds) {
    String[] sorted = playerIds.clone(); // 不改原数组
    Arrays.sort(sorted);
    return sorted.length > 0 ? sorted[0] : null;
}
```

---

## 执行顺序

```
T1.1 [RNG删除]
  ↓
T1.2 [StandardPacket]  ← 最大改动
  ↓
T1.3 [去P2P化]
  ↓
T1.4 [删BroadcastManager] ─── 可与 T1.3 并行
  ↓
T1.5 [P1集成验证]

T2.1 [InducedReplay] ──┐
T2.2 [EndTurnGate]     ├── 可并行开始
T2.3 [MonsterTurn]     │
T2.5 [Event补全]       │
  ↓                    │
T2.4 [Host迁移] ◄──────┘  [需要 T2.2 的 gate 机制]
  ↓
T2.6 [交互选择]
  ↓
T2.7 [P2集成验证]

T3.1 [Cache修复] ──┐
T3.2 [Queue Bugs]  ├── 可并行
T3.3 [seq统一]     │
T3.4 [Bug修复]     │
  ↓                │
T3.5 [Registry补齐]┤
T3.6 [心跳seq]     │
  ↓                │
T3.7 [文档同步] ◄──┘
  ↓
T3.8 [全量回归]

A1-A6 [归档顺路修] → 随时可做, 建议 P3 后单独提交
```

---

## 每个 Task 的标准 Checklist

- [ ] 批量修改 > 2 文件时, 先 `./gradlew test` 确认基线通过
- [ ] 代码修改完成
- [ ] 新增/更新测试完成
- [ ] `./gradlew test` — 全部通过
- [ ] git add + commit (feat/fix/perf/chores prefix)
- [ ] 在本文档勾选 ☑️
