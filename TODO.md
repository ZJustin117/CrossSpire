# TODO — CrossSpire 生产环境改造

## 一、架构完整性

- [x] **1.1 统一队列路径** — 删除旧 `QueueManager`，`crossspire play` 改为走 `CentralQueueManager`（Client→host(queue_submit)→invoke→invoke_result→combat_result）
- [x] **1.2 消费 queue_update / queue_empty** — `MessageRouter` 新增路由：`queue_update`→`QueueDisplay` 刷新；`queue_empty`→解锁回合结束按钮
- [x] **1.3 诱导重放升级为深层空函数调用** — 事件步骤改为 `AbstractPlayer.useCard(stubCard, target)`，触发 @SpirePatch + 内部 BaseMod.publishOnCardUse 全量执行
- [x] **1.4 消除重复 combat_result** — `handleInvoke` 只发 `invoke_result`（target=调用方），由房主 `CentralQueueManager.onInvokeResult` 统一广播 `combat_result`
- [x] **1.5 target 接收端校验** — `MessageRouter.handleInvoke` 增加 `if (!msg.target.equals(me)) return` 定向投递保护

## 二、生产质量

- [ ] **2.1 MonsterTurnPatches 补 takeTurn** — `@SpirePatch` `AbstractMonster.takeTurn` → 房主捕获 → 广播 `combat_result`
- [ ] **2.2 骨架传输修复** — `RemoteAssetServer` 发送真实 skeleton JSON（ReflectionHacks 提取 SkeletonData → 序列化 → Base64），替代 hashcode stub
- [ ] **2.3 客户端掉线处理** — relay `player_left` → `MessageRouter` 路由 → 清除 `RemotePlayerState` + `CentralQueueManager` 移除待处理项
- [ ] **2.4 日志降噪** — batch 执行后删除 `crossspire_batch.txt` 避免重复执行
- [ ] **2.5 EventSuppression 封装** — `AtomicInteger` 计数器从 `public static final` 改为 package-private，仅通过 `suppressEvents()` API 访问
- [ ] **2.6 HeartbeatManager 接入** — 当前已存在独立线程类但未被 `CrossSpireMod` 启动，补上并接入掉线检测
- [ ] **2.7 ReferenceFactory 使用 hostId** — `Reference` 基类加 `hostId` 字段，`RemoteReference.dereference` 将 invoke 发到 hostId 而非 ownerId

## 三、功能对齐

- [ ] **3.1 怪物意图全量快照** — `MonsterIntentBroadcastPatches` 改为回合开始时广播**所有**怪物意图
- [ ] **3.2 怪物回合三阶段分离** — ARCHITECTURE §8：phase1 intent / phase2 player turns / phase3 monster actions
- [ ] **3.3 RemotePlayer 实例化并接入渲染** — `RemoteRenderer` 创建 `RemotePlayer extends AbstractPlayer` 实例，委托 `renderPlayerBattle()` + `renderHealth()`
- [ ] **3.4 图主选举协议** — `stage_host_election` / `stage_host_result` 从 reserved 变为实际实现（房主广播候选者→成员回复选票→宣布结果）

## 四、代码债务

- [ ] **4.1 删除死代码** — legacy POJO (`QueueSubmit`, `QueueUpdate`, `InvokeCard`, `InvokeResult`, `RemotePlayerSync`)；旧 `QueueManager`；P2PManager 未用直连方法
- [ ] **4.2 RemoteAssetCache 校验** — `manifest.json` 记录 SHA-256；收到 `resource_response` 时校验 vs 本地
- [ ] **4.3 RemoteAssetCache 过期清理** — 上次访问 > 30 天自动清理；LRU 淘汰替代 `clear()` 全丢弃
- [ ] **4.4 CrossSpireMod.initialize() 拆分** — 拆为 `initNetwork()` / `initCombat()` / `initUI()` / `initResources()`
- [ ] **4.5 mods/cross-spire/README.md** — 新建：列出所有 `@SpirePatch` 及其目标方法和功能说明

## 五、实施顺序

```
Week 1 : 1.3 → 1.1 → 1.2 → 1.4 → 1.5
Week 2 : 2.1 → 2.2 → 3.1 → 3.2
Week 3 : 2.7 → 3.3 → 2.3 → 2.5 → 2.6
Week 4 : 4.1 → 4.4 → 4.5 → 3.4 → 4.2 → 4.3
```
