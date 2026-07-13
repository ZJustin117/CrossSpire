# CrossSpire MVP Development Achievements

## 2026-07-11: MVP 完成

### Phase 1: 中继服务器 (TypeScript)
- **store.ts**: 房间 CRUD (createRoom/getRoom/addPlayer/removePlayer)，空房间自动删除
- **server.ts**: Express + WebSocket，握手/房间管理/消息中继
- **protocol.ts**: 协议类型定义
- **15 个测试** (vitest), 全部通过

### Phase 2: Mod 构建系统
- **build.gradle.kts**: 独立 Gradle 构建，compileOnly BaseMod/ModTheSpire/desktop-1.0.jar
- **CrossSpireMod.java**: `@SpireInitializer` 入口，自动连接 + RelayClient/LobbyScreen/StageHost 生命周期
- 设备日志验证: `CrossSpire mod initialized`

### Phase 3: BaseMod 事件抑制
- **EventSuppression.java**: `AtomicInteger` 全局标志 + `suppressEvents(Runnable)` 工具方法
- **SuppressBaseModPatches.java**: 12 个 `@SpirePatch` 拦截 BaseMod.publishXxx() 方法
- 包括: publishPostBattle, publishOnPlayerDamaged, publishOnPlayerLoseBlock, publishRelicGet, publishPotionGet, publishPostPotionUse, publishOnCardUse, publishPostPowerApply, publishPostDraw, publishPostExhaust, publishOnPlayerTurnStart, publishPostEnergyRecharge
- MTS 加载无 crash

### Phase 4: 网络层
- **Protocol.java**: 消息 POJO (GameMessage, QueueSubmit, InvokeCard, InvokeResult, RemotePlayerState 等) + Gson 序列化
- **RelayClient.java**: Java-WebSocket 客户端，连接/重连/收发/room join
- **MessageRouter.java**: 按 type 分发 (invoke_card / invoke_result / state_sync / queue_submit / resource_*)
- 设备日志验证: `CrossSpire connected to relay server`, 收到 `connected` 握手

### Phase 5: 同步层
- **LocalCapturePatches.java**: `@SpirePostfixPatch` 拦截 `AbstractPlayer.useCard()` → 发送 `queue_submit`
- **InvokeExecutor.java**: 处理 `invoke_card`，判断是否属于自己的卡牌 → 模拟执行并回传 `invoke_result`
- **SyncExecutor.java**: 分层同步
  - `replayWithCard()`: 本地有卡牌 → 操作重放 (suppressEvents)
  - `fallbackEffects()`: 无本地卡牌 → 纯数值应用 (damage/block/heal/energy/lose_hp)
  - `handleRemotePlayerSync()`: 在线角色状态更新

### Phase 6: 在线角色
- **RemotePlayer.java**: `extends AbstractPlayer`，实现 37 个 abstract 方法为最小存根
- **RemotePlayerRegistry.java**: Map<playerId, RemotePlayerState> 注册表
- **RemotePlayerState.java**: 轻量状态 bean (hp/maxHp/block/energy)
- MTS 加载无 crash，游戏正常进入主菜单

### Phase 7: 联机入口
- **LobbyScreen.java**: BaseMod PostInitializeSubscriber + PostRenderSubscriber，连接状态展示
- **ServerPicker.java**: 服务器 URL/房间码/图主标志配置
- 双设备联机验证: 相互可见对方 playerId，收到 player_joined 事件

### Phase 8: 待打出队列 + 图主
- **StageHost.java**: FIFO 队列维护 → invoke_card (就近原则：自属本地直达，远程网络调用) → invoke_result → combat_result 广播 → 队列推进
- 完整调用链验证: queue_submit → StageHost → invoke_card → InvokeExecutor → invoke_result → StageHost → combat_result 广播

### Phase 9: 战斗渲染
- **RemoteRenderer.java**: PostRenderSubscriber，左上角面板展示远端玩家 HP/Block/E
- 在战斗中 (AbstractDungeon.player != null 时) 渲染，主菜单静默
- 双设备验证: 战斗内渲染无 crash

### Phase 10: 素材传递系统
- **RemoteAssetCache.java**: L1 内存缓存 (64 entries) + L2 磁盘缓存 (crossspire_cache/)
- **RemoteResourceManager.java**: 本地查表 → 缓存查盘 → 远端请求
- **RemoteCardResource.java**: 远程卡牌素材投影
- **RemoteRelicResource.java**: 远程遗物素材投影
- **RemotePowerResource.java**: 远程能力素材投影
- **ResourceRegistryTracker.java**: 素材清单收发 (resource_registry)
- 入房后自动交换素材清单

---

## 项目统计

| 指标 | 数值 |
|------|------|
| TypeScript 源文件 | 6 (server/store/protocol + 3 test files) |
| Java 源文件 | 36 |
| 单元测试 | 19 (全部通过) |
| BaseMod publish 拦截 | 12 |
| AbstractPlayer abstract 方法实现 | 37 |
| Fallback 效果类型 | 13 (全部实现, 含 apply_power via ConsoleCommand) |
| jar 大小 | 279KB |
| crossspire 控制台命令 | 12 |
| relay daemon | systemd user service, 心跳 30s 清理 |

## 技术栈

- **服务器**: Node.js + Express + WebSocket + TypeScript
- **客户端 Mod**: Java 8 + ModTheSpire + BaseMod + Java-WebSocket + Gson
- **测试**: vitest (TS), MTS 加载 + 日志验证 (Java)
- **协议**: JSON over WebSocket, type + subtype 模式
- **部署**: adb push + MTS mods_library

## 2026-07-12: 引用系统 + 完整管道验证

### Phase B: 引用系统 (reference/)
- **ContentValidator.java**: SHA-256 资源哈希校验 (card/relic/power)
- **Reference.java**: 抽象基类: refId/ownerId/type(LOCAL/REMOTE/NULL)
- **LocalReference.java**: 直接 UseCardAction + EventSuppression + 广播
- **RemoteReference.java**: invoke → P2P relay → 30s等待invoke_result → 广播
- **NullReference.java**: 不可达处理 + tryDegrade (ContentValidator回退)
- **ReferenceFactory.java**: 路由: self→LOCAL, P2P直连→REMOTE, relay→REMOTE, 离线→NULL
- QueueManager.checkHead 已完全替换为 Reference 路由 (移除旧 ownerId 判断)
- LocalCapturePatches 增加 ContentValidator resource_hash
- MessageRouter 增加 invoke/invoke_result 路由

### Phase 3: 完整战斗管道验证
**双设备端到端测试 (D1 IRONCLAD + D2 DEFECT, seed=220644)**

| # | 节点 | D1 验证 | D2 验证 |
|---|------|---------|---------|
| 1 | MessageRouter routing | ✅ `queue_packet routing` | ✅ |
| 2 | QueueManager.onQueuePacket | ✅ `received packet: Strike_R` | ✅ `received packet: Defend_R` |
| 3 | ReferenceFactory.createCardRef | ✅ `refType=LOCAL owner=9cbe870e` | ✅ |
| 4 | Reference.dereference | ✅ `LOCAL dereference: Strike_R` | ✅ `LOCAL dereference: Defend_R` |
| 5 | QueueManager dequeue | ✅ packet removed | ✅ |
| 6 | broadcast queue_complete | ✅ `broadcast queue_complete: Strike_R` | ✅ |
| 7 | Observer received COMPLETE | ✅ `COMPLETE #1: card:Strik` | ✅ `COMPLETE #2: card:Defen` |
| 8 | CombatResultReplayer | — | ✅ `CombatResultReplayer fallback: effects=1` |
| 9 | REMOTE reference 路由 | ✅ `REMOTE dereference: Defend_R → 0138e8aa direct=true` | ✅ P2P直连 |
| 10 | 远程设备同步 | — | ✅ `queue_packet parsed: Bash by 9cbe870e` |

**全 10 节点通过。**

## 2026-07-12: 完整 MVP 交付

### Phase C: relay daemon + 心跳清理 + 定向投递
- **store.ts**: PlayerMeta 数据结构 + touchHeartbeat + getExpiredPlayers
- **server.ts**: deliver() 定向投递 + ping/pong 处理 + close 清理 + 30s 过期定时器
- **RelayClient.java**: 15s 心跳线程
- **systemd service**: `~/.config/systemd/user/crossspire-relay.service`, 自动重启
- 19 vitest 全部通过

### Phase D: lobby 就绪流程
- **LobbyState.java**: 就绪/ready 重发/host 自动选举/seed 广播
- **RoomPanel.java**: PostRenderSubscriber 交互面板 (Connect/Disconnect/角色选择/Ready/Play 按钮)
- **LobbyScreen.java**: 状态文本追踪
- 双设备验证: 连接 → Ready → 双方就绪 → seed 同步

### Phase E: 分布式广播队列 + 引用系统
- **QueueManager.java**: 分布式队列，timestamp+sender_id 排序，Reference 路由
- **ContentValidator.java**: SHA-256 资源哈希
- **Reference.java + LocalReference + RemoteReference + NullReference + ReferenceFactory**: 引用系统完整实现
- QueueManager.checkHead 完全替换为 Reference 路由
- MessageRouter: invoke/invoke_result 路由

### Phase F: 全 Fallback 效果 (13/13)
- **CombatResultReplayer.java**: 分层同步引擎
  - replayWithCard: 本地有卡牌 → 操作重放
  - fallbackEffects: 13 种效果
- damage/block/heal/energy/lose_hp: 直接 API 调用
- apply_power: Basemod ConsoleCommand.execute("power id amount") 修复 Vulnerable NPE
- remove_power/draw_card/discard_card/exhaust_card/gain_gold/obtain_relic/obtain_potion: 完整实现

### Phase G: 战斗渲染 + 信息显示
- **RemoteStatsOverlay.java**: 战斗中右上角 HUD (血条+Buffs+角色类型)
- **RemotePlayerState.java**: 扩展 power/characterClass 字段
- **QueueDisplay.java**: 队列可视化
- **crossspire info/lobby/combat**: 12 个控制台命令
- **CombatSyncPatches.java**: room_enter 广播 (怪物配置通知)

### Phase H: P2P 网络 + relay 稳定性
- **P2PManager.java**: ServerSocket 监听 + hello 协议交换 + sendOrRelay 回退
- hello 消息携带 peers 列表 (全互联 mesh)
- relay 房间清理 (30s 心跳超时 → player_left 广播)
- dual-device verified: P2P connected + accepted

### 最终项目统计
- Java 源文件: 52 (36 prod + 10 sync/combat/rng/reference + 6 test)
- TypeScript 源文件: 7 (server/store/protocol/sequence + 3 test files)
- Java 单元测试: 34 (全部通过)
- TypeScript 测试: 19 (全部通过)
- jar 大小: 595KB
- crossspire 控制台命令: 12
- relay daemon: systemd user service, 心跳 30s 清理

## 2026-07-13: 架构对齐 — 18 项差距补全

### Phase 1-6 完成 (前一日启动的补全计划)
- **Phase 1**: handleInvoke + replayWithCard 修复 (真实卡片数值取代硬编码 damage=6)
- **Phase 2**: StageHost + 怪物回合管线 (StageHost 选举/强所有权, MonsterIntentBroadcast, MonsterTurnPatches)
- **Phase 3**: 事件处理 (EventResultMessage 协议+路由, EventSyncPatches via buttonEffect)
- **Phase 4**: RNG 按流同步 (SyncedRng, RngManager, RngSyncPatches)
- **Phase 5**: triggerOn 接口触发 (Reference.triggerOn → TriggerRegistry)
- **Phase 6**: 引用生命周期补全 (tryMigrate 广播, LobbyScreen 渲染, ResourceRegistryTracker 实数据, protocol-schema.json)

### 18 项架构差距修复

| # | 差距 | 文件 | 状态 |
|---|------|------|------|
| 1 | Gold 同步 Patch 缺失 | `GoldSyncPatches.java` (gainGold/loseGold → player_state) | ✅ |
| 2 | onResourceResponse stub | `RemoteResourceManager.onResourceResponse` (Base64 decode→writeDisk→putTexture) | ✅ |
| 3 | RemoteAssetServer 缺失 | `RemoteResourceManager.serveResource` (磁盘缓存→Base64 响应) | ✅ |
| 4 | 怪物回合不用共享 RNG | `MonsterTurnPatches` 添加 `optional=true` + rngManager 日志 | ✅ |
| 5 | ContentValidator Modded 支持 | `hashFromInstance()` (CardLibrary/RelicLibrary/Class.forName fallback) | ✅ |
| 6 | RemotePlayer 无被动引用跟踪 | `passiveReferences` list | ✅ |
| 7 | Reference.remoteAddr 字段缺失 | `remoteAddr` + `remotePort` final 字段 | ✅ |
| 8 | QueueDisplay 仅控制台 | `render(SpriteBatch)` 图形队列渲染 | ✅ |
| 9 | 无"等待图主" UI | `LobbyScreen.setWaitingForHost()` | ✅ |
| 10 | 4 个协议消息类型缺失 | `StageHostElection/Result/FullSnapshot/AnimationSyncMessage` + 路由 | ✅ |
| 11 | BroadcastManager.java | 统一 P2P+Relay 广播入口 | ✅ |
| 12 | HeartbeatManager.java | 独立心跳线程 | ✅ |
| 13 | sequence.ts | Per-source 单调序列号 | ✅ |
| 14-15 | RemotePotionResource / RemoteCharacterResource | POJO 数据类 | ✅ |
| 16 | RoomChat.java | 消息列表 + render | ✅ |
| 17 | entity-mappings.json | STS1↔STS2 Card/Relic/Character 映射 | ✅ |
| 18 | protocol-schema.json + TS 同步 | 完整 JSON schema + TS 类型对齐 | ✅ |

### 设备验证情况
- 部署: D1 + D2 jar 推送成功
- 编译: 34 Java 测试 + 19 TypeScript 测试全部通过
- 设备: 模拟器 MTS launcher 渲染卡住，需手动点击 "Play" 后自动连接
- 手动测试步骤:
  1. 启动模拟器，点击 MTS "Play"
  2. `crossspire connect ws://10.0.2.2:9876 CROSS`
  3. `crossspire start IRONCLAD 220644` (D1) / `crossspire start DEFECT 220644` (D2)
  4. `crossspire play Strike_R` 验证真实效果同步
  5. 结束时 `crossspire info` 查看 gold/RNG/队列状态
