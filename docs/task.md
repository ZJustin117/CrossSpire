# CrossSpire — SDD 迁移任务清单 (Task)

> 历史阶段 30/30 完成。当前测试数和 JAR 大小以构建结果为准。
> 与 `plan.md` 执行状态对齐：2026-07-19。**T5.2–T5.5 ✅（原版 buff）；T5.3 灾厄/mutation 延后**。

## P4 Android 调试基础设施清理 ✅

- [x] T4.1 SDD：正式标注 SlayTheAmethyst Android 支持和 Harness 边界
- [x] T4.2 删除 `crossspire_startup.txt` / `crossspire_batch.txt` 命令执行与轮询线程
- [x] T4.3 删除 Android `crossspire.properties` 路径和隐式 P2P 配置
- [x] T4.4 `host/join` 改为仅接受显式网络参数，并修复 manager 生命周期
- [x] T4.5 Gradle 本地依赖路径改为显式、可移植配置
- [x] T4.6 使用 Harness → BaseMod console 完成 D1/D2 Android E2E
- [x] T4.7 静态审计发布源码/JAR 不含 Android 测试台硬编码和 Harness 依赖

本阶段测试环境：D1/D2 ADB serial 与游戏端口见仓库根 `.env.local`（模板 `.env.example`）；D2 loopback 游戏端口由外部测试基础设施转发到 D1。Desktop 验证暂缓。

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
> 实现与契约对照：`LocalOwnerGate` + Replayer 门控已落地；mutation/attachment 仍为后续。
>
> **范围修订（2026-07-19）**：当前无灾厄等自定义 buff 支持。T5.2 用**原版 buff**验收（优先 `Bash` → `Vulnerable`）。
> 灾厄 / Mod-only power / `monster_mutation_*` 整段延后至 T5.3+，不阻塞 T5.2 交付。

- [x] T5.0 文档 + schema：`logic_owner_id`、mutation/phase 草案；ARCHITECTURE/spec/plan/task
- [x] T5.1 `LocalOwnerGate` + `CombatResultReplayer` AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY
  - 删除 induced 路径上的 `BaseMod.publishOnCardUse`
  - 仅 fire `TriggerRegistry` 中 `ownerId == self` 的条目
  - `executor_id` 在 MessageRouter / QueueComplete / LocalReference 保留
  - induced 副作用带 `origin_owner_id` + `hop_count`（上限 3）
  - 测试：`LocalOwnerGateTest` + Protocol logic_owner 序列化
- [x] T5.2 `apply_power` 全面写入 `logic_owner_id` + ComponentAttachment 注册表 + PowerStub 非 owner no-op 接线（2026-07-19）
  - **验收基准**：原版 `Bash` → `Vulnerable`（`logic_owner_id = 施加者`）
  - 落地：`ApplyPowerEffects`、`ComponentAttachment`/`Registry`、Replayer AUTHORITATIVE_APPLY 登记、`PowerLogicGate`+`PowerStub` 回调 no-op、`EffectCapture.recordApplyPower`
  - REAL effects：`magic_number` → `apply_power`+`logic_owner_id`（LocalReference + MessageRouter）
  - 单测：`ComponentAttachmentRegistryTest`、`ApplyPowerOwnershipTest`、`PowerStubTest`、`LocalReferenceApplyPowerTest`、`EffectCapture` owner 路径
  - **未做（可选增强）**：真实 `ApplyPowerAction` 全量捕获 patch；snapshot 带 attachment
  - **不做（延后）**：灾厄/自定义 power / mutation
- [ ] T5.3 怪物 mutation proposal/commit（图主 revision CAS）— **延后**；依赖自定义/灾厄类 buff 再测
- [x] T5.4 房主显式 `combat_phase`（2026-07-19）
  - `CombatPhase` 枚举 + `CombatPhaseCoordinator` 广播/本地应用
  - 房主：首项 `queue_submit` → `resolving_queue`；队列空 → `queue_empty`（仍发 legacy `queue_empty`）
  - 房主：全员 `player_end_turn` 共识 → `pre_monster_turn`
  - `crossspire phase` / `status` 显示当前阶段；`RoomHost` end-turn 聚合
  - 测试：`CombatPhaseTest` + `RoomHostTest` end-turn 共识
- [x] T5.5 E2E 验收（2026-07-19，原版 Bash/Vulnerable + combat_phase）
  - 前置：`adb force-stop` 冷启动后加载新 JAR（SkipInstall 热重启可能仍跑旧类；Arthas `jad` 可核对）
  - 游戏网：`adb -s D1 forward tcp:15432 tcp:54321` + `adb -s D2 reverse tcp:54321 tcp:15432`（openp2p 仅 ADB 端口，不转游戏 54321）
  - D1 host + D2 join → room size=2
  - D1 `start IRONCLAD` → `fight Cultist` → `play Bash Cultist`
  - **D1 日志**：`queue_complete: Bash [damage=8, apply_power=2]`；`CombatPhaseCoordinator broadcast resolving_queue / queue_empty / pre_monster_turn`
  - **D2 日志**：`INDUCED … apply_power: Vulnerable→Cultist x2 logic_owner=<D1>`；`skip non-owner power logic: Vulnerable logic_owner=<D1>`；`combat_phase: queue_empty`
  - T5.3 后：灾厄跨节点 — 暂无内容支持时跳过

## 归档 ✅

- A1-A6 全部完成
