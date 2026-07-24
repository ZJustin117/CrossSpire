# CrossSpire — SDD 迁移任务清单 (Task)

> 历史阶段 30/30 完成。当前测试数和 JAR 大小以构建结果为准。
> 与 `plan.md` 执行状态对齐：2026-07-24。**P5–P15 ✅；T5.3 延后；P-Testing 规范 ✅ / 实现 ⏳**。

## P-Testing 逻辑层测试（规范 + 实现队列）

> 权威写法：[`development/logic-layer-testing.md`](./development/logic-layer-testing.md)。语义默认 `@junit-test`，Harness 仅设备契约。

- [x] **T-Test.0** 文档与 OpenCode agent 规范（logic-layer-testing、AGENTS、junit-test/harness/deploy/arthas、spec NFR-16–18、ARCHITECTURE §22、plan 本章）
- [x] **T-Test.1** combat_result admit / host local-induce → `CombatResultApplyPolicy`；删测试镜像；MessageRouter 接线
- [x] **T-Test.2** hop + own-skip + owner-fire + resolveExecutor 表测；Replayer 接线 pure
- [x] **T-Test.3** monster admit 空 tx / wrong id / reset 边界补全（phase/queue 既有覆盖保留）
- [x] **T-Test.4** `combat.scenario` 首批 S1–S5（own skip / peer induce / personal / owner-fire / hop）
- [ ] **T-Test.5**（可选）schema ↔ Protocol 键校验
- [ ] **T-Test.6** harness 文档持续对齐「语义已迁 JUnit」

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
> 实现与契约对照：`LocalOwnerGate` + Replayer 门控与 `ComponentAttachment` 注册表已落地；mutation 与 snapshot attachment 仍为后续。
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
- [ ] T5.3 怪物 mutation proposal/commit（图主 revision CAS）— **延后**（见文末表）；依赖自定义/灾厄类 buff 再测
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

## P6 战斗回合闭环（2026-07-19）

> 目标：`end_turn` 共识 → `pre_monster_turn` → `monster_turn`（图主权威）→ `post_monster_turn` → `player_turn`。不接 T5.3 mutation。

- [x] T6.0 文档：plan/task/spec 阶段表
- [x] T6.1 `CombatTurnOrchestrator` 状态机 + host 广播 `MONSTER_TURN`（end_turn 共识后）
- [x] T6.2 `MonsterTurnCapture` + `MonsterGroup.applyPreTurnLogic` Prefix/Postfix HP 增量 → combat_result；host 推进 post/player
- [x] T6.4 `CentralQueueManager` 在 pre/monster/post 拒绝 queue_submit
- [x] T6.5 双机 E2E：`pre_monster`→`monster`→`post`→`player` + T5.5 Bash 回归（2026-07-19）
- [ ] T6.3 增强：phase 边界显式 fire owner-only 原版 power（引擎自发为主；可选，见文末表）

## P7 小队化冒险推进与批准式事件 ✅

> 角色边界：RoomHost = 星型网络路由 + 图主/队长目录；StageHost = 阶段地图与房间内容权威；PartyLeader = 本队成员、队列、阶段和地图共识。初始全员属于默认小队；队长恒为成员 ID 字典序最小者。

- [x] T7.0a 战斗权威：非图主联机节点抑制 `MonsterGroup.applyPreTurnLogic`；远端 `combat_phase` 要求房主来源、合法转换和唯一 transaction（JUnit 144/144，Android E2E 未取得子任务摘要）
- [x] T7.0b attachment 生命周期基础：原子 `replaceSnapshot`、`remove_power` 同目标清理、进入新 MonsterRoom 时清空陈旧 metadata（聚焦 JUnit 9/9）
- [x] T7.0c monster-turn transaction gate：仅接受当前图主/当前 transaction 的 completion result，零伤害仍完成回合，拒绝重复/旧回合 result（JUnit 153/153；Android 双机 E2E 两轮闭环通过，2026-07-20）
  - D1 host + D2 join（room size=2）→ Cultist；双端连续两轮均完成 `pre_monster_turn` → `monster_turn` → `post_monster_turn` → `player_turn`
  - 第一轮图主 `damage=0` 仍正确完成阶段；D2 收到与 D1 末阶段一致的 `player_turn` transaction
  - D2 有两条非阻塞 `StarConnectionManager route error: null` 日志；未影响 host/join 或两轮阶段闭环，后续单独诊断
- [x] T7.1 Party 状态与协议基础：schema/DTO、纯 `PartyState`/`PartyManager`、默认 P0、成员最小 ID 队长、leave/join 请求/批准/拒绝/掉线重选（JUnit 159/159；router/命令 wiring 留给 T7.2）
- [x] T7.2a Party snapshot 路由：房主按房间成员建立/广播默认 P0；客户端仅接受房主来源并原子应用目录；`crossspire party status` 诊断（JUnit 159/159；Android 双机 P0 snapshot 通过，2026-07-20）
- [x] T7.2b 将 queue/end-turn/combat_phase/room_pin/可见性按 `party_id` 隔离；RoomHost 不再管理玩法队列
  - 已落地（2026-07-20）：`party_id` 已贯穿 queue submit/update/empty、invoke/result、combat result/phase 与 player end-turn；`CentralQueueManager`、phase transaction 和 end-turn ready 集合均按小队独立，跨队 queue/combat result/UI 更新被拒绝或忽略。
  - 已落地（2026-07-21）：成员的 `queue_submit` / `player_end_turn` 经 RoomHost 定向给 `party.leader_id`；仅队长可调度队列、聚合 end-turn、发送 queue update/empty 与 combat phase。队长的玩法输出由 RoomHost 校验成员资格后仅中继到本队；host 与 leader 分离时不再回退为房主执行。
  - 目录变更不再重置既有拆分：新连接只加入 P0，断连后更新 party snapshot。
  - 已落地（2026-07-21）：`PartyVisibility` 将战斗角色渲染、远程状态 overlay、RoomPanel、控制台状态与怪物目标计数限制为本地小队成员；跨队状态可保留在缓存中但不得进入玩法 UI 或渲染。
  - 已落地（2026-07-22）：T7.3 真实地图/节点目录就绪后关闭收尾：`room_pin` 按 `party_id` 聚合、跨队 pin 互不共享共识；NodeInstance 按 `(map, party, node, visit)` 隔离；共识后 clear 本队 pin。JUnit 覆盖多队同图不同 pin。
- [x] T7.3 图主地图 snapshot、房间目录、按小队 normal navigation/room consensus（主路径 ✅；商店库存/篝火 UI 同步见 T9 类型-only 合约）
  - 已落地（2026-07-21）：schema-first `MapNode` / `MapDefinition` / `NodeInstanceInfo` DTO；RoomHost 持有纯 `MapRegistry` 与 `NodeInstanceRegistry`。MapDefinition 拓扑不可变，注册只接受首次或等价重传；节点实例以 `(map_instance_id, party_id, node_id, visit_id)` 幂等分配并校验相邻边。
  - 已落地（2026-07-21）：`PartyHostElectionTracker` 按 `party_id` 分别聚合 MapHost 与 NodeInstanceHost 全员一致投票，拒绝跨队投票人和候选人，允许改票。
  - 已落地（2026-07-21）：`map_host_vote` / `map_host_result` 与 `map_register` / `map_registered` 标准包路由；仅本队一致获选的 MapHost 可登记不可变地图；控制台 `maphost` / `mapreg` 诊断入口。
  - 已落地（2026-07-21）：`PartyState` map binding（phase/map_instance/start/revision）；`map_register` 成功后 bind + party_snapshot；`node_instance_host_vote`/`result` 路由与 `crossspire nodehost`；NIH 仅在已绑定地图的小队可选举。
  - 已落地（2026-07-21）：小队 `room_pin`（index→outgoing `node_id`）、队长 pin 聚合、`room_consensus`→RoomHost `NodeEntryCoordinator.allocate`→`node_instance_allocate` 定向 NIH；`party.enterNode` 更新 pos/active instance。
  - 已落地（2026-07-21）：NIH 处理 allocate（host loopback）→ `node_generation_commit`；RoomHost `NodeOpenCoordinator` 一次性授权后 `node_instance_opened` 本队同步。`MapNode.room_type` 经 map register/allocate 保留，纯 `NodeGenerationPlanner` 为 `monster`/`event` 生成类型化结果；event 的已提交 `event_interface` 只在实例打开后由 NIH 经标准包发布（JUnit 全量通过）。
  - 已落地（2026-07-22）：权威地图捕获/重建：`MapNode` 保留 x/y/icon/burning_elite；`map_registered` 携带完整 `MapDefinition`；`StsMapDefinitionCapture`/`StsMapDefinitionApplier`；MapHost 选举后自动捕获（含 `0:-1` 起点出边修复）；RoomHost/客户端 apply；`MapPatches.ReapplyAuthoritativeMap` 在成员 `generateMap` 后重放拓扑；`PartyMemberIdResolver` 支持短 ID 投票；`node_instance_opened` 走权威 MonsterRoom 转场（成员可 bootstrap GAMEPLAY）。
  - 验证（2026-07-21）：Android D1/D2 完成 map host → map register/bind → node host → `room 0` → allocate → generation commit → node opened；两端最终目录为 `active_node`、`pos=node1`。
  - 验证（2026-07-22）：Android D1/D2（JAR `a4416b66…`）无 `mapreg`：MapHost 自动捕获 → 单次 `map_registered` → 双端 apply → NIH → `room 0` → 双端 `gamestate` 均为 `MonsterRoom Floor 1`；JUnit 214 pass。
  - 已落地（2026-07-22）：`NodeGenerationResult` 扩展 elite/shop/rest/treasure/boss；planner 产出 encounter/占位字段；`?`/`unknown` 确定性解析；NodeOpenCoordinator 与 SyncExecutor 打开路径覆盖非 monster/event 房间。
  - 已落地（2026-07-22）：队长 `room_pin` 达成共识后立即 `clear` 本队 pin 表，避免并发二次 pin 用陈旧成员集重复共识。
  - 已落地（2026-07-23）：generation 内容确定性增强 — shopSeed/restOptions/treasureTier/elite encounter 由 nodeInstanceId 稳定哈希派生（无共享 RNG）；同 instance 重算一致。
  - 保留后续：商店库存明细/商人物价安装、篝火选项实际 UI 同步、宝箱开箱奖励实例化（当前为权威描述字段）。
- [x] T7.4 事件批准协议：`event_choice_request` → `approved/rejected` → 本地原生执行 → `event_player_result` ✅
  - 状态（2026-07-24）：individual + voting 路径、native gate/fallback、Grid `cardSelect`、Hand `targetSelect`、personal gold/HP/relic/potion/card 差量均已落地。
  - 已落地（2026-07-21）：schema/DTO/StandardPacket 路由与 `EventApprovalCoordinator`。individual 请求检查 event/party/member/hash/可用选项/request ID；有效请求只批准一次，结果也只接受一次；voting 见 T7.5。
  - 已落地（2026-07-21）：`eventopen` / `eventchoice` / `eventresult` 诊断命令，RoomHost 自身选择走本地回环；成员结果按本队 relay。
  - 已落地（2026-07-21）：`NativeEventApprovalPatches` 在 `AbstractEvent.update()` 的共享 `buttonEffect(int)` 调用点前门控已绑定且 class/hash 匹配的原生事件。首次选择发送 `event_choice_request` 并冻结输入；只有 event/request/step/option 全匹配的批准放行一次，拒绝恢复输入且不产生副作用。
  - 已落地（2026-07-21）：event 节点打开走 `SyncExecutor.enterRemoteEvent`：class/hash 匹配则本地实例化 `EventRoom` 并 bind gate；否则 `RemoteEventDisplay` fallback。打开期间抑制旧 `EventSyncPatches` interface 广播。
  - 已落地（2026-07-21）：fallback UI 选择改为 `event_choice_request`（`FallbackEventChoiceSession`）；批准关闭 UI、拒绝可重选；NIH 代执行见 T7.4b。`mapreg ... event` 诊断参数可生成 event 节点。
  - 验证（2026-07-21）：Android D1/D2 room size=2；`eventopen`→registered/fallback UI（diagnostic class）、`eventchoice`→approved、`eventresult`→本队 relay。
  - 验证（2026-07-21）：`mapreg … event` + `room 0` → generation `type=event`（非 Cultist）；双端 `native event opened` + D2 `bound native event approval`；JUnit 205/205。
  - 已落地（2026-07-22）：T7.4b NIH fallback 个人结果 — `FallbackNihResultPlanner` 在批准后由本队 NIH 生成 `event_player_result`（诊断性 gain_gold）；`EventPlayerResultApplyPlanner` 仅本机 chooser 应用 effects；RoomHost 仍 one-shot accept + 本队中继。
  - 已落地（2026-07-22）：`acceptPlayerResult` 允许本队 NIH 代 chooser 提交结果（source=NIH, player_id=chooser）。
  - 验证（2026-07-22）：Android D2 eventopen+eventchoice → approved → NIH result chooser=D2 → D2 接受 `event_player_result`（JAR `ed8b7125…`）；JUnit 223 pass。
  - 已落地（2026-07-22）：`NativeEventApprovalGate.beforeChoice` 支持 multi-step `ui_step`（buttonEffect/cardSelect/targetSelect/confirm）与 `selected_cards`；选牌 step 要求非空牌表。
  - 已落地（2026-07-22）：T7.4c 原生个人差量 — 获批 `buttonEffect` 执行前后快照 gold/HP/maxHP/block/deck → `PersonalEventDeltaPlanner` → `event_player_result`；fallback 仍走 NIH 诊断 gold；native 不再双发 NIH 诊断结果。
  - 验证（2026-07-23）：Android native BigFish — open + eventchoice → skip NIH diagnostic + personal result；Donut 后 HP 80→85/85、effects=2、D2 收到 result（JAR `daaa4974…`）；console 批准可 arm gate 并 reflect 重放 buttonEffect。
  - 已落地（2026-07-23）：T7.4d `GateGridConfirm` — 绑定原生事件时拦截 `GridCardSelectScreen.update` 确认点击，先发 `cardSelect` 请求（含 selected_cards），批准后才放行。
  - [x] T7.4e `selected_targets` + `targetSelect` 校验 + `HandCardSelectScreen` 门控（2026-07-24）
  - [x] T7.4f personal delta 扩展 relic/potion/card 身份差量（2026-07-24）；schema 扩展 obtain/remove_*；Replayer 可 apply
  - 验证（2026-07-24）：JUnit 260/260；Android 共进 smoke host/join→ready→start→map/node→room 0 同 node_instance PASS（JAR `39ea9887…`）
  - 保留后续：图像事件字段
- [x] T7.5 事件内房间 instance、同选项成员路径、按小队 voting（主路径 ✅；跨队相遇记录见 T7.5c）
  - 已落地（2026-07-22）：小队 voting 聚合 — 成员可改票；全员同 `option_index` 后一次性批准全部 pending request；`MessageRouter` 向每位 chooser 定向投递 `event_choice_approved` 并触发 NIH 个人结果。
  - 已落地（2026-07-23）：`EventRoomInstanceRegistry` — 同 `(party,event,option)` 复用 instance 并收集成员；`EventRoomOutcomePlanner` 启发式 fight→event_room、leave map→leave_party；`event_player_result.shared_outcome` schema/DTO；RoomHost 在 accept result 时附加 shared_outcome。
  - 验证（2026-07-23）：Android BigFish Fight（eventchoice 1）→ `event_room instance=er:P0:…:1` + D1/D2 日志 `shared_outcome type=event_room`（JAR `8e927e04…`）；JUnit 235 pass。
  - 已落地（2026-07-24）：T7.5b `shared_outcome` 带 `encounter` + `member_ids`；成员收到 event_room 后 `SyncExecutor.enterEventRoomCombat` 装 MonsterRoom 壳；BigFish Fight → Cultist
  - [x] T7.5c 跨小队同 `node_id` 仅记录（不 merge 战斗）— `CrossPartyNodeEncounterRecorder` on first allocate
  - 保留后续：event_votes 快照广播字段（可选）；更丰富 encounter 映射
- [x] T7.6 schema-aligned full snapshot（parties + attachments）及 Android 双机 smoke 验证
  - 已落地（2026-07-22）：`FullSnapshotBuilder` 组装 parties/maps/attachments/active_node_instances；`FullSnapshotSender` 合并本地 stage/player/monsters；schema `full_snapshot` 更新。
  - 验证（2026-07-22）：Android 双机 smoke — host/join → maphost/nodehost → `snapshot`（D2 收到 full_snapshot）→ room 0 双端 MonsterRoom（JAR `4c587ab3…` size 648538）；JUnit 225 pass。
  - 保留后续：Desktop smoke。

## T7.7 真共进开局 / 选房 / 同房可玩 ✅

> 验收主路径：双方 ready → play → 同队 P0 → 投票同房自动进入 → 互相可见可打牌 phase 统一 → 胜后完整奖励屏（个人金币）→ 再投票下一房。
> 历史「D1 start + fight + D2 INDUCED」仅为 host-spawn projection，不作为共进 pass。

- [x] T7.7a Ready+Play 双机开局：schema `party_run_start`；全员 ready 后 start 广播；双端 `GameStarter`；共进路径不用 `createGameIfNeeded(IRONCLAD)` 作主开局
- [x] T7.7b 同队 P0 + map/NIH 门控：仅 GAMEPLAY 后允许 maphost 捕获；reward 期拒绝 room_pin
- [x] T7.7c room_pin 共识双端自动进房；status 输出 `ActiveNodeTracker`；map-bound 抑制 legacy room_enter
- [x] T7.7d 诊断/文档共进脚本；战斗队列/phase/可见性沿用既有实现（真机 E2E 另跑 harness）

## T7.8 战斗胜利奖励屏 ✅

- [x] T7.8 schema + `reward_phase_enter` / `reward_done` / `reward_phase_complete` 路由与聚合
- [x] `CombatRewardPatches` 胜后 enter；`rewarddone` 命令；金币仅 `player_state` 投影（个人经济）
- [x] 胜后 pin 门控修复：completed node 不重开 reward；capture/repair 出边 + `MapNavigation` 下一行 fallback；E2E `0:0`→`2:1` ShopRoom
- [x] T8.0 战斗 end-turn：仅 `disable(true)` 广播 `player_end_turn`（不再挂在 `enable`）

## T9 统一房间实例 + 地图解锁跟随 ✅

> 全类型 RoomInstance；SHOP/REST 只同步类型；`room_exit_unlocked` 由房间实例主广播，队员跟随；解锁后仍全员 pin；opened 强制 follow。

- [x] T9.0 文档 + schema：`room_exit_unlocked`；shop/rest 非库存 SoT
- [x] T9.1 `RoomNavigationGate`；pin 仅 exit_unlocked；reward_complete → unlock
- [x] T9.1b opened force-follow + status 诊断
- [x] T9.2 shop/rest 类型-only 注释 + console `mapunlock`（打开壳沿用 SyncExecutor）
- [x] T9.3 RIH 打开 `DungeonMapScreen` / 事件 `openMap` 自动 `room_exit_unlocked`（console `mapunlock` 保留）

## T8 战斗稳定 + 文档 ✅

- [x] T8.0 end-turn 仅 `disable(true)`；E2E play 不自动进 monster_turn
- [x] T8.1 plan/task/harness 共进 smoke 对齐

## 归档 ✅

- A1-A6 全部完成

## P15 双倍 INDUCED + 实际伤害快照

> 日志：同一 combat_result 两次 INDUCED（毒 5→10）；Perfected Strike 用 baseDamage=6。

- [x] P15.1 host 中继 combat_result 排除 source（防二次 apply）
- [x] P15.2 prepareCardForSnapshot(calculateCardDamage) 同步实际伤害
- [x] P15.3 毒直接 stackPower（suppress 下可挂层）
- [x] P15.4 双机 E2E：Deadly Poison 单次 INDUCED + Poison×5 一次；Strike 双向各 1 次（JAR 747123）

## P14 毒/易伤映射 + 个人防御归属

> 毒被写成 Vulnerable；gain_block self 对端误加到自己；Poison 构造 owner 错误。

- [x] P14.1 cardId 推断 Poison/Weak/Vulnerable；block 目标=executor
- [x] P14.2 INDUCED 个人效果仅 executor 本机 apply；PoisonPower(owner,source,amount)
- [x] P14.3 双机 E2E：Deadly Poison→Poison×5；Defend gain_block skip non-local；Strike 双向（JAR 745573）

## P13 队长出牌 + map 后默认 NIH

> 日志：队长 CapturePatches 只 send 无 peer 结果；无 NIH 时原生 2 Louse；无 node_instance。

- [x] P13.1 队长 useCard 后 publish combat_result（不二次 queue）
- [x] P13.2 map_register 后默认 NIH=MapHost；client 应用 payload
- [x] P13.3 拦截 nextRoomTransition；native combat 门控用 connectionManager
- [x] P13.4 双机 E2E：auto NIH + 同 Cultist + 双向 INDUCED；无 CME（JAR 743711）
- [x] P13.5 combat_result 主线程 postRunnable（修 D2 ConcurrentModificationException）

## P12 防原生进房 + 关 legacy room_enter

> 会话 A：无 NIH 时拦截关闭→直接 Next Room；共进前 room_enter 投影战。

- [x] P12.1 map 绑定即拦截；无 NIH 点图拒绝 pin（不原生进）
- [x] P12.2 map-bound 无 active node 时挡 MonsterRoom.onPlayerEntry
- [x] P12.3 共进目录/已 start/已 map 时忽略 legacy room_enter
- [x] P12.4 双机 E2E：room 0 同 node + Strike INDUCED；无 legacy room_enter（JAR 738565）

## P11 地图选房早拦截（防不同房）

> 根因：`nextRoomTransitionStart` 拦截过晚，首房已 `setCurrMapNode`；无 MapHost/NIH 时原生独自进房。

- [x] P11.1 `MapRoomNode.update` 在 setCurr 前提前 pin + 挡 transition/setCurr
- [x] P11.2 pin by `node_id` + host 接受 node_id
- [x] P11.3 双机 E2E：console room 共识同 node PASS（JAR 737292）；地图点击早拦截需手测 `MapRoomSelectPatches early pin`

## P10 双向战斗 + 结束回合 + 图主时机

> 日志：D2→D1 仅 `broadcast combat_result` 无 INDUCED（sendToParty 跳过 self）；end_turn 零条；Vote 过早。

- [x] P10.1 `broadcastCombatResult` 本地 INDUCED + LocalReference/queue_submit 填 party_id
- [x] P10.2 end_turn 全员上线（含 host+leader sendToParty）+ mirror
- [x] P10.3 VotePanel 仅 GAMEPLAY 可投 + 时机文案
- [x] P10.4 双机 E2E：D1→D2 与 **D2→D1 INDUCED** PASS（JAR 732877）；end_turn 仅代码路径（无 console 按钮）

## P9 战斗同源 + 地图点击选房

> latest.log：`encounter=Cultist` 后 STS 本地 roll 出 `2 Louse`；`remoteCombatActive` 抑制 BattleStart 致血条异常。去掉独立 Pin 面板。

- [x] P9.1 权威 encounter 预装（`setMonster` + `MonsterGroup.init`，禁止 re-roll）
- [x] P9.2 co-op 进房不 suppress BattleStart；显式 `showHealthBar`
- [x] P9.3 移除 RoomPinPanel；`MapRoomSelectPatches` 拦截 `nextRoomTransitionStart` → room_pin
- [x] P9.4 双机 E2E：Cultist 双端 50/50、无 Louse、Strike→D2 INDUCED PASS（JAR 731033，2026-07-24）

## P8 游戏 UI 可玩性（控制台 → 手操）

> 目标：主路径可不写 console；投票行显示角色选择屏按钮缩略图。诊断命令仍保留 console。

- [x] T8-UI.1 Connection/Lobby/Vote/Map actions 抽取（与 console 同路径）
- [x] T8-UI.2 `CharacterIconPaths`/`CharacterIconCache` + Lobby 角色图标
- [x] T8-UI.3 RoomPanel Host/Join/Disconnect/Port 切换
- [x] T8-UI.4 VotePanel MapHost/NIH + 成员角色图标
- [x] T8-UI.5 RoomPinPanel 出边 pin + pins 快照图标
- [x] T8-UI.6 HUD 集成 + `room_pins`/选举结果写入 `PartyUiState`
- [x] T8-UI.0 文档补全（plan/task/spec US-9/console 标注）
- [x] T8-UI.7 双机 Full E2E（console=actions 同路径）：host/join→ready/start→maphost/nodehost→room 0 同 node_instance PASS（JAR 727827，2026-07-24）

## 显式未开 / 可选（非 P7 阻塞）

| 项 | 优先级 | 说明 |
|----|--------|------|
| T5.3 monster mutation proposal/commit | 延后 | 需灾厄/改怪物核心状态内容；schema 草案 only |
| T6.3 phase 边界显式 fire owner-only power | 可选 | 引擎自发 + LocalOwnerGate 已够 |
| reward_offer 候选池协议接线 | 可选 | 本地 STS 奖励屏已可用 |
| Desktop smoke | 暂缓 | 平台约束；Android 共进 smoke 已通过 |
| 图像事件字段 / event_votes 广播 / 更丰富 encounter | 小 polish | 不阻塞主路径 |
| Snapshot 带 attachment；ApplyPowerAction 全量捕获 | 可选增强 | T5.2 已用原版 Vulnerable 验收 |
| party leave/join 控制台 wiring | 可选 | PartyManager 状态机已有；console 目前仅 `party status` |
| US-7 卡牌 invoke 交互（非事件） | 另开阶段 | 事件已用批准协议；卡牌选牌/目标非本轮 |
| 商店库存 / 篝火选项 / 宝箱完整同步 | 不做 | T9 类型-only 合约 |
| StarConnectionManager `route error: null` | 诊断债 | 非阻塞 |
| P-Testing T-Test.1–4 逻辑层提取与 scenario | 进行中 | 规范 T-Test.0 已完成；见上节 |
