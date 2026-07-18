# CrossSpire — SDD 迁移任务清单 (Task)

> 历史阶段 30/30 完成。当前测试数和 JAR 大小以构建结果为准。

## P4 Android 调试基础设施清理 ✅

- [x] T4.1 SDD：正式标注 SlayTheAmethyst Android 支持和 Harness 边界
- [x] T4.2 删除 `crossspire_startup.txt` / `crossspire_batch.txt` 命令执行与轮询线程
- [x] T4.3 删除 Android `crossspire.properties` 路径和隐式 P2P 配置
- [x] T4.4 `host/join` 改为仅接受显式网络参数，并修复 manager 生命周期
- [x] T4.5 Gradle 本地依赖路径改为显式、可移植配置
- [x] T4.6 使用 Harness → BaseMod console 完成 D1/D2 Android E2E
- [x] T4.7 静态审计发布源码/JAR 不含 Android 测试台硬编码和 Harness 依赖

本阶段测试环境：D1 `localhost:15555`、D2 `localhost:25555`；D2 的 `localhost:54321` 由外部测试基础设施自动转发到 D1 的 `localhost:54321`。Desktop 验证暂缓。

## P1 架构修复 ✅

- T1.1 删除共享 RNG → StageHost.stageRng
- T1.2 StandardPacket + PacketOperation
- T1.3 P2PManager → StarConnectionManager
- T1.4 删除 BroadcastManager

## P2 功能补全 ✅ (all done)

- T2.1 修复诱导重放管道 (CardStub/PowerStub/EffectCapture回收集)
- T2.2 EndTurnButton gate (Prefix+Postfix)
- T2.3 怪物回合 HP 增量法
- T2.4 HeartbeatManager pong/timeout/detection + host migration
- T2.5 EventMessageSender + EventSyncPatches (openMap) + event_select route + RemoteEventDisplay
- T2.7a/b/c 房间标注+共识+图主导航

## P3 清理稳定 ✅

- T3.1 Cache LRU + SHA-256 verify
- T3.2 CentralQueueManager 竞态修复
- T3.3 seq 全域统一
- T3.4 Bug 修复 (7项)
- T3.5 ResourceRegistry 查询 API
- T3.6 Heartbeat seq
- T3.7 spire-patches.md 更新
- T3.8 回归验证 (每轮 E2E)

## 新增 (SDD 驱动)

- StageVoteSender + RoomHost.castVote + crossspire vote 命令
- RoomPinSender + consensus + crossspire room 命令
- PlayerStatePatches.OnDamage → 实时 HP 同步
- RenderSafetyPatches → 远程战斗渲染兼容
- FullSnapshotSender → game state 序列化
- RemoteEventDisplay → 非图主事件 UI

## P5 Buff 所有权契约（2026-07-18）

> Breaking：否决全量深层诱导与图主点名触发 buff。见 `plan.md` Phase 5、`ARCHITECTURE.md` §8–10、`spec.md` FR-2.4/2.8–2.10。

- [x] T5.0 文档 + schema：`logic_owner_id`、mutation/phase 草案；ARCHITECTURE/spec/plan/task
- [ ] T5.1 `LocalOwnerGate` + `CombatResultReplayer` local-owner-only
- [ ] T5.2 `apply_power` / ComponentAttachment + 非 owner no-op
- [ ] T5.3 怪物 mutation proposal/commit
- [ ] T5.4 房主阶段同步 / 可选 `combat_phase`
- [ ] T5.5 验收测试：无二次被动 combat_result；灾厄跨节点

## 归档 ✅

- A1-A6 全部完成
