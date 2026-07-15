# CrossSpire — SDD 迁移任务清单 (Task)

> 完成标记: [x] = done, [ ] = pending, [-] = deferred

## 进度总览

| Phase | 任务数 | 完成 | 待做 | 推迟 |
|-------|--------|------|------|------|
| P1 架构修复 | 5 | 5 | 0 | 0 |
| P2 功能补全 | 10 | 6 | 4 | 0 |
| P3 清理稳定 | 8 | 3 | 5 | 0 |
| 归档 | 6 | 1 | 5 | 0 |
| **合计** | **29** | **15** | **14** | **0** |

---

## P1: 架构修复 — ✅ 全部完成

| ID | 内容 | 状态 |
|----|------|------|
| T1.1 | 删除共享 RNG 子系统 (SyncedRng/RngManager/RngSyncPatches), StageHost.stageRng | [x] |
| T1.2 | 引入 StandardPacket 信封 + PacketOperation 常量 | [x] |
| T1.3 | 网络层去 P2P 化 (P2PManager→StarConnectionManager, 删 onHelloReceived 互连) | [x] |
| T1.4 | 删除 BroadcastManager 冗余层 | [x] |
| T1.5 | P1 集成验证 (48→41→45 tests pass, JAR build, device startup OK) | [x] |

**Commit**: `9df0e44`, `3ce46d7`, `ad8318c`

---

## P2: 功能补全 — 3/7 完成

### T2.1 修复诱导重放管道 ✅

- [x] CombatResultReplayer.inducedUseCard: 始终用 CardStub 不回退真卡
- [x] CardStub 已携带 cardType/rarity/target
- [x] resolvePower fallback 到 PowerStub
- [x] replayInduced 包裹 EffectCapture, 新效果回 host 队列
- [x] EffectCapture.stopCapture 修复: snapshot→clear

**Commit**: `89bbb1e`

### T2.2 EndTurnButton gate ✅

- [x] EndTurnSyncPatches Prefix gate 阻止 queue 非空时 enable
- [x] Postfix 广播 player_end_turn (seq 改为 nextSeq)
- [x] MessageRouter: queue_empty → QueueDisplay.onQueueEmpty + EndTurnButton.enable
- [x] MessageRouter: queue_submit → QueueDisplay.resetEndTurn

**Commit**: `447a216`

### T2.3 怪物回合 HP 增量法 ✅

- [x] MonsterTurnPatches 重写: applyStartOfTurnPowers postfix HP 采样法
- [x] 广播实际 hpDelta/blockDelta (不再 speculative damage)

**Commit**: `447a216`

### T2.4 房主选举与迁移 · priority(medium)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | HeartbeatManager 补全: peerLastSeen Map, pong handler, 5s 超时检测线程, onPeerTimeout callback | `network/HeartbeatManager.java` |
| 2 | RoomHost.onPeerTimeout(): removePlayer → 广播 player_left → 若掉线者是 host → triggerHostElection | `network/RoomHost.java` |
| 3 | MessageRouter 加 pong 处理 + 调用 heartbeatManager.handlePong() | `sync/MessageRouter.java` |
| 4 | 房主选举流程: electHost (按 ID 排序), 广播 host_migration | `network/RoomHost.java` |
| 5 | CrossSpireMod 加 reconnectToNewHost() | `CrossSpireMod.java` |
| 6 | FullSnapshot 收集与分发 | `sync/SyncExecutor.java` |

**测试**:
- [ ] `HeartbeatManagerTest.testTimeoutDetection`
- [ ] `RoomHostTest.testHostMigration`
- [ ] `RoomHostTest.testFullSnapshotRebuild`

### T2.7a 房间标注命令 · priority(medium)

**影响**: `spec.md` FR-4.7, US-4a

| # | 步骤 | 文件 |
|---|------|------|
| 1 | CrossSpireCommand 加 `room` 子命令: 解析 `<index>`, 构造 `room_pin` 消息, 发往房主 | `ui/CrossSpireCommand.java` |
| 2 | MessageRouter 加 `room_pin` → 房主转发到 RoomHost | `sync/MessageRouter.java` |
| 3 | CrossSpireCommand.errorMsg 加 `room <index>` 到帮助文本 | `ui/CrossSpireCommand.java` |

**测试**:
- [ ] `CrossSpireCommandTest.testRoomPinSent` (新建)

### T2.7b 房主聚合 + 共识检测 · priority(medium)

**影响**: `spec.md` FR-4.8, US-4a

| # | 步骤 | 文件 |
|---|------|------|
| 1 | RoomHost 加 `Map<String, Integer> playerPins`, `pinRoom(playerId, index)`, `checkConsensus()` | `network/RoomHost.java` |
| 2 | MessageRouter `room_pin` handler: 房主调 `roomHost.pinRoom()` → checkConsensus → 若一致广播 `room_consensus`; 否则广播 `room_pins` | `sync/MessageRouter.java` |

`checkConsensus()` 逻辑:
```java
boolean checkConsensus() {
    if (playerPins.isEmpty() || playerPins.size() != getPlayerCount()) return false;
    int first = playerPins.values().iterator().next();
    return playerPins.values().stream().allMatch(v -> v == first);
}
```

**测试**:
- [ ] `RoomHostTest.testPinAndConsensus`
- [ ] `RoomHostTest.testNoConsensusWhenDivergent`

### T2.7c 共识触发图主执行 · priority(medium)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | MessageRouter 加 `room_consensus` handler: 图主收到 → 调 SyncExecutor.executeRoomConsensus(roomIndex) | `sync/MessageRouter.java` |
| 2 | SyncExecutor.executeRoomConsensus(): Gdx.app.postRunnable → 图主本地导航到房间 → 触发 CombatSyncPatches 自动广播 room_enter | `sync/SyncExecutor.java` |
| 3 | 导航实现: `AbstractDungeon.getCurrMapNode()` 获取当前节点 → 遍历 connected nodes → 取第 roomIndex 个 → 走现有 `enterRemoteCombat` 或 goToRoom | `sync/SyncExecutor.java` |

**测试**:
- [ ] 手动双设备: D1 host+stageHost, D2 client → 双方 `crossspire room 0` → 检查图主是否进入房间、D2 是否同步

### T2.5 事件系统补全 · priority(medium)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | EventSyncPatches.OnEnterRoom 捕获 event_interface(名称、描述、选项、disabled 状态、图片路径) → 广播 | `combat/EventSyncPatches.java` |
| 2 | MessageRouter 加 event_select → 转发图主 → buttonEffect | `sync/MessageRouter.java` |
| 3 | EventSyncPatches 加 event_result 回调(patch AbstractEvent.buttonEffect 或监听 openMap/transitionKey) | `combat/EventSyncPatches.java` |
| 4 | 新增 RemoteEventDisplay.java (非图主渲染事件 UI) | `ui/RemoteEventDisplay.java` |

**测试**:
- [ ] `EventSyncPatchesTest.testInterfaceCapture`
- [ ] `testEventSelectFlow`
- [ ] `testEventResultBroadcast`

### T2.6 所有者交互选择 · priority(low)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | 新建 InteractionCapturePatches: patch GridCardSelectScreen / HandCardSelectScreen.open() | `combat/InteractionCapturePatches.java` (新) |
| 2 | Protocol 加 InteractRequestPayload / InteractResponsePayload | `network/Protocol.java` |
| 3 | MessageRouter 加 interact_request / interact_response 路由 | `sync/MessageRouter.java` |
| 4 | 超时处理: 30s 无响应 → 默认策略 | `combat/InteractionCapturePatches.java` |

**测试**: `InteractionCapturePatchesTest.testRequestResponse`, `testTimeoutFallback`

### T2.7 Phase 2 集成验证 · priority(high)

- [ ] `./gradlew test` 全量通过 + 新增测试覆盖
- [ ] 手动双设备验证: 诱导重放/回合结束 gate/怪物回合/事件同步

---

## P3: 清理与稳定 — 3/8 完成

### T3.1 Cache 修复 · priority(medium)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | RemoteAssetCache: 替换 clear-all 为 LRU LinkedHashMap(256 cap, dispose evicted textures) | `resource/RemoteAssetCache.java` |
| 2 | readDisk() 接入 verify() SHA-256 checksum | `resource/RemoteAssetCache.java` |
| 3 | RemoteAssetServer.serveResource 对 card 类型: CardLibrary 查 → Gdx.files.internal() 读 PNG | `resource/RemoteAssetServer.java` |

**测试**: `RemoteAssetCacheTest.testLruEviction`, `testChecksumVerificationRejectsCorrupted`

### T3.2 CentralQueueManager bug 修复 ✅

- [x] dedup: UUID.randomUUID → source+seq
- [x] processNext() 竞态: sync 包裹 get(0)
- [x] markDone 同步化
- [x] onInvokeResult 改用 packetId 匹配
- [x] CentralQueueManagerTest.testDedupPreventsDuplicate

**Commit**: `0975c5a`

### T3.3 seq 全域统一 ✅

- [x] LocalReference, RemoteReference, NullReference, EventSyncPatches, GoldSyncPatches, MessageRouter, LobbyState, StarConnectionManager — 全部改为 nextSeq()

**Commit**: `0975c5a`

### T3.4 Bug 修复清单 ✅ (4/7, 剩余 3 项) — partial

| # | 文件 | 修复 | 状态 |
|---|------|------|------|
| 1 | `SuppressBaseModPatches.java` | SuppressOnPlayerDamaged: `Return(0)` → `Return(__amount)` | [x] |
| 2 | `MessageRouter.java` | 删除 unreachable `combat_result` 第二个分支 | [x] |
| 3 | `RemotePlayerState.java` | syncToPlayerInstance() 同步 powers/energy/gold | [ ] |
| 4 | `LocalCapturePatches.java` | 加 `if EventSuppression.isSuppressed() return;` | [x] |
| 5 | `Reference.java` / `ReferenceFactory.java` | hostId 重选后更新存量 reference | [ ] |
| 6 | `RoomPanel.java` + `StarConnectionManager` | `substring(0, 8)` 加 length guard | [ ] |
| 7 | `MonsterTurnPatches.java` | 重写为 HP 增量法 (已在 T2.3 完成) | [x] |

### T3.5 ResourceRegistryTracker 补齐 · priority(low)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | onRegistryReceived 存储 powers/potions/characters(当前丢弃) | `resource/ResourceRegistryTracker.java` |
| 2 | 加公开查询 API: hasCard(playerId, cardId) | 同上 |

**测试**: `ResourceRegistryTrackerTest.testQueryApi`

### T3.6 Heartbeat seq 补全 · priority(low)

HeartbeatManager ping/pong 加 monotonic seq。

### T3.7 文档同步 · priority(medium)

| # | 步骤 | 文件 |
|---|------|------|
| 1 | 更新 spire-patches.md — 删除 RngSyncPatches; MonsterTurnPatches→HP增量法 | `docs/spire-patches.md` |
| 2 | 更新 README.md — patch 列表 + StandardPacket 说明 | `mods/cross-spire/README.md` |
| 3 | ACHIEVEMENTS.md — 添加 SDD 迁移里程碑 | `docs/ACHIEVEMENTS.md` |

### T3.8 全量回归 · priority(high)

- [ ] `./gradlew test` — 确保所有测试通过
- [ ] 双设备 E2E 验证

---

## 归档: 顺路修

| ID | 内容 | 状态 |
|----|------|------|
| A1 | EffectCapture.stopCapture 修复 (snapshot→clear) | [x] |
| A2 | ReferenceFactory 非 card 前缀修复 | [ ] |
| A3 | TriggerRegistry 确认 fire 端 | [ ] |
| A4 | RemoteResource POJO 注解 | [ ] |
| A5 | RemoteAssetServer 非 GL 线程安全 | [ ] |
| A6 | StageHost.electHost 副作用修复 | [ ] |
