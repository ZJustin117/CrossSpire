# TODO — CrossSpire 生产环境改造

## 一、架构完整性

- [x] **1.1 统一队列路径** — 删除旧 `QueueManager`，`crossspire play` 改为走 `CentralQueueManager`（Client→host(queue_submit)→invoke→invoke_result→combat_result）
- [x] **1.2 消费 queue_update / queue_empty** — `MessageRouter` 新增路由：`queue_update`→`QueueDisplay` 刷新；`queue_empty`→解锁回合结束按钮
- [x] **1.3 诱导重放升级为深层空函数调用** — 事件步骤改为 `AbstractPlayer.useCard(stubCard, target)`，触发 @SpirePatch + 内部 BaseMod.publishOnCardUse 全量执行
- [x] **1.4 消除重复 combat_result** — `handleInvoke` 只发 `invoke_result`（target=调用方），由房主 `CentralQueueManager.onInvokeResult` 统一广播 `combat_result`
- [x] **1.5 target 接收端校验** — `MessageRouter.handleInvoke` 增加 `if (!msg.target.equals(me)) return` 定向投递保护

## 二、生产质量

- [x] **2.1 MonsterTurnPatches 补 takeTurn** — HP增量法(applyStartOfTurn→applyEndOfTurn)绕过抽象方法限制
- [x] **2.2 骨架传输修复** — 从 Gdx.files.internal 读取原始文件替代 hashcode stub
- [x] **2.3 客户端掉线处理** — RelayClient player_left → RemotePlayerRegistry.remove + onPlayerLeft
- [x] **2.4 日志降噪** — batch 执行后 delete()（已实现）
- [x] **2.5 EventSuppression 封装** — SUPPRESSION package-private + isSuppressed()
- [x] **2.6 HeartbeatManager 接入** — RelayClient 改用 HeartbeatManager.start/stop
- [x] **2.7 ReferenceFactory hostId** — Reference.hostId 字段 + RemoteReference 经 hostId 路由

## 三、功能对齐

- [x] **3.1 怪物意图全量快照** — createIntent 缓冲 + applyStartOfTurnPowers 全量广播
- [x] **3.2 怪物回合三阶段分离** — 当前实现已对齐 ARCHITECTURE §8
- [x] **3.3 RemotePlayer 实例化** — RemotePlayerState.getPlayerInstance() + renderHealth
- [ ] **3.4 图主选举协议** — 暂缓（ARCHITECTURE.md 标注"暂不实现"）

## 四、代码债务

- [x] **4.1 删除死代码** — legacy POJO (`QueueSubmit`, `QueueUpdate`, `InvokeCard`, `InvokeResult`, `RemotePlayerSync`) 全部删除；`QueuePacket` 零引用删除
- [x] **4.2 RemoteAssetCache 校验** — `manifest.json` SHA-256 + `verify()` 方法
- [x] **4.3 RemoteAssetCache 过期清理** — `readDisk` 时检查 `lastModified > 30天` → auto delete
- [x] **4.4 CrossSpireMod.initialize() 拆分** — 已精简至 17 行，加注释分隔
- [x] **4.5 mods/cross-spire/README.md** — 25 个 @SpirePatch 完整清单 + 子包结构

## 五、实施顺序

```
Week 1 : 1.3 → 1.1 → 1.2 → 1.4 → 1.5
Week 2 : 2.1 → 2.2 → 3.1 → 3.2
Week 3 : 2.7 → 3.3 → 2.3 → 2.5 → 2.6
Week 4 : 4.1 → 4.4 → 4.5 → 3.4 → 4.2 → 4.3
```
