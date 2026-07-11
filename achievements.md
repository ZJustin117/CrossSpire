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
| TypeScript 源文件 | 4 (server/store/protocol + tests) |
| Java 源文件 | 22 |
| 单元测试 | 15 (全部通过) |
| BaseMod publish 拦截 | 12 |
| AbstractPlayer abstract 方法实现 | 37 |
| Fallback 效果类型 | 5 (damage/block/heal/energy/lose_hp) |
| jar 大小 | 217KB |

## 技术栈

- **服务器**: Node.js + Express + WebSocket + TypeScript
- **客户端 Mod**: Java 8 + ModTheSpire + BaseMod + Java-WebSocket + Gson
- **测试**: vitest (TS), MTS 加载 + 日志验证 (Java)
- **协议**: JSON over WebSocket, type + subtype 模式
- **部署**: adb push + MTS mods_library
