# CrossSpire — 杀戮尖塔联机Mod架构设计

## 目录

1. [设计目标](#设计目标)
2. [关键术语定义](#关键术语定义)
3. [核心思想：引用模型与双角色投影](#核心思想引用模型与双角色投影)
4. [引用系统](#引用系统)
5. [就近原则](#就近原则)
6. [图主权威模型](#图主权威模型)
7. [房主中央队列：战斗流程](#房主中央队列战斗流程)
8. [怪物回合](#怪物回合)
9. [事件处理](#事件处理)
10. [Mod 兼容性设计：内容校验与分层引用](#mod-兼容性设计内容校验与分层引用)
11. [BaseMod 事件抑制机制](#basemod-事件抑制机制)
12. [素材传递系统](#素材传递系统)
13. [在线角色：独立 AbstractPlayer 实例与渲染集成](#在线角色独立-abstractplayer-实例与渲染集成)
14. [RNG 同步策略](#rng-同步策略)
15. [网络拓扑](#网络拓扑)
16. [协议消息定义](#协议消息定义)
17. [项目结构](#项目结构)
18. [未来塔2兼容性预留](#未来塔2兼容性预留)

---

## 设计目标

CrossSpire 是一个开源的杀戮尖塔1联机Mod，目标：

1. **塔1多人同步战斗** — 多个玩家在同一场战斗中操作各自的角色
2. **Mod 兼容性** — 每个玩家可以使用自己的Mod，不要求所有人安装相同Mod
3. **跨游戏连接预留** — 架构预留与塔2互联的能力（塔1 Java/LibGDX ↔ 塔2 C#/Godot）
4. **开源** — 全栈开源的替代方案，对标闭源的 Together in Spire

## 关键术语定义

| 术语 | 英文 | 定义 |
|------|------|------|
| 引用 | Reference | 对任意可执行对象（卡牌/遗物/怪物/玩家/地图等）的统一抽象，封装了【所有者是谁】和【如何到达它】。`本地引用` = 对象在本地且内容校验通过，可直接执行；`远程引用` = 对象在远端机器上，解引用时经房主路由调用所有者。上层 API 对本地/远程引用透明 |
| 解引用 | Dereference | 调用引用指向的对象，执行其逻辑，并同步结果。本地解引用退化为直接方法调用；远程解引用走 `调用方 → 房主 → 所有者 → 房主 → 调用方` 路由 |
| 本地引用 | Local Reference | 对象在本地存在且内容校验通过。解引用 = 直接调用，无网络开销 |
| 远程引用 | Remote Reference | 对象在远端机器上。解引用 = 经房主路由调用所有者 → 执行 → 回传结果 |
| 空引用 | Null Reference | 远程引用不再可达（所有者掉线）。解引用失败，需引用退化或引用转移 |
| 引用退化 | Reference Degradation | 远程引用 → 本地引用。当本地获得对象完整定义（下载完成、内容校验通过）后降级为本地执行 |
| 引用转移 | Reference Migration | 改变远程引用的指向源（如原所有者掉线后切换到备份源），用于处理空引用 |
| 内容校验 | Content Validation | 同名类/方法不等同于相同类/方法。通过内容哈希比对判定两个实体是否真正一致。对同 Mod 不同版本隔离有关键意义 |
| 房主 | Room Host | **网络路由角色**。维护到所有客户端的连接（星型拓扑，O(n) 连接）。所有客户端间消息经房主转发。房主维护中央待打出队列并负责调度。房主**不执行游戏逻辑**（除非房主本人恰好是该逻辑的所有者） |
| 图主 | Stage Host | **游戏逻辑角色**。当前阶段（Act + Floor）地图/事件/怪物的**强所有者**。其他人只能对此建立远程引用（不允许本地引用）。由所有成员投票选定。图主可以就是房主，但两类角色可分离；房主负责把消息路由到位，图主负责在自己的机器上执行地图/怪物逻辑 |
| 标准包 | Standard Packet | 待打出卡牌的提交包，包含 `{packet_id, sender_id, owner_id, card_id, resource_hash, target}`。客户端发往房主，由房主插入中央队列并管理生命周期 |

### 操作关系

```
远程引用解引用（全经房主路由）:
  A 持有指向 C 对象的远程引用
    → A 调用 ref.dereference(args)
    → 引用系统识别为 REMOTE
    → A → 房主: invoke(refId, ownerId=C, args)
    → 房主 → C: invoke(refId, args)
    → C 本地执行，触发钩子
    → C → 房主: invoke_result(refId, effects)
    → 房主 → A: invoke_result(refId, effects)
    → A 渲染结果（不触发钩子）

本地引用解引用:
  A 持有指向自己对象的本地引用
    → A 调用 ref.dereference(args)
    → 引用系统识别为 LOCAL
    → 直接调用，零网络往返

房主就是所有者时（最短路径）:
  A 持有指向房主/B 对象的远程引用
    → A → 房主: invoke(refId, ownerId=B)
    → 房主识别 ownerId=B → 发现 B 就是自己
    → 房主本地执行，触发钩子
    → 房主 → A: invoke_result(refId, effects)
    → 仅 1 次网络往返（而非 A→房主→B→房主→A 的 2 次）
```

## 核心思想：引用模型与双角色投影

每台机器上有**两种角色**：

```
机器A                                    机器B
┌──────────────────────────┐            ┌──────────────────────────┐
│ 本地角色 (Player A)       │            │ 本地角色 (Player B)       │
│ • 玩家操作的实体           │            │ • 玩家操作的实体           │
│ • 对自身卡牌/遗物持有      │            │ • 对自身卡牌/遗物持有      │
│   本地引用                │            │   本地引用                │
├──────────────────────────┤            ├──────────────────────────┤
│ 在线角色 (Player B投影)    │◄──引用───►│ 在线角色 (Player A投影)    │
│ • 纯渲染镜像              │            │ • 纯渲染镜像              │
│ • 持有指向B对象的远程引用   │            │ • 持有指向A对象的远程引用   │
│ • 解引用经房主路由        │            │ • 解引用经房主路由        │
└──────────────────────────┘            └──────────────────────────┘
           │                                       │
           └────────── 房主（网络路由）──────────────┘
```

**核心原则**：
- 在线角色是渲染和状态镜像——状态由远端所有者通过解引用写入，不自行触发钩子
- 执行权归属所有者——解引用永远在所有者机器上执行逻辑，路由由房主负责
- 引用系统封装网络+路由复杂性——上层代码不区分本地/远程，只调用 `ref.dereference()`

## 引用系统

### 引用数据结构

```typescript
interface Reference<T> {
  refId: string;             // 对象唯一标识 (如 "card:Strike_R@playerA")
  ownerId: string;           // 所有者机器 ID
  hostId: string;            // 房主 ID（路由目标）
  type: "LOCAL" | "REMOTE" | "NULL";
  resourceHash?: string;     // 内容校验哈希
  dereference(args: any): Result<T>;           // 执行逻辑(跨网络透明)
  tryDegrade(): boolean;     // 尝试引用退化
  tryMigrate(): boolean;     // 尝试引用转移
}
```

### 解引用流程

```
ref.dereference(args):
  │
  ├─ type == LOCAL
  │     → 本地直接调用对象逻辑
  │     → 触发本地钩子 (BaseMod 事件等)
  │     → 返回 Result
  │
  ├─ type == REMOTE
  │     → 发 invoke(refId, args, ownerId) 到 hostId（房主）
  │     → 房主将 invoke 转发到 ownerId
  │     → owner 本地执行 → 触发钩子 → 回传 invoke_result 给房主
  │     → 房主转发 invoke_result 给调用方
  │     → 调用方渲染结果，不触发钩子
  │     → 返回 Result
  │
  └─ type == NULL
        → tryDegrade()     // 本地是否已有完整定义？
        → tryMigrate()     // 是否有其他节点持有副本？
        → 都失败 → 抛出空引用异常，界面显示不可达
```

### 房主即所有者时的短路路径

```
A.ownerId == A.hostId 时:
  A → 房主: invoke(refId, ownerId=A, args)
  房主识别 ownerId == 自己 → 本地执行
  房主 → A: invoke_result(refId, effects)
  // 1 次往返，而非 A→房主→A→房主→A
```

### 引用生命周期

```
创建 → 远程引用 (经房主路由)
   │
   ├─ 下载完整定义 → 内容校验通过 → 引用退化 → 本地引用 (直接调用)
   │
   ├─ 所有者掉线 → 空引用
   │     ├─ 引用转移 → 新的远程引用 (指向备份源)
   │     └─ 引用退化 → 本地引用 (若本地已缓存)
   │
   └─ 对象销毁 (卡牌消耗/怪物死亡) → 引用析构
```

### 接口触发

引用可以指定在什么接口（事件钩子）下触发：

```
Reference<Card> cardRef;
cardRef.triggerOn("onCardUse");     // 当卡牌被打出时触发解引用
cardRef.triggerOn("onExhaust");     // 当卡牌被消耗时触发解引用

Reference<Relic> relicRef;
relicRef.triggerOn("atBattleStart"); // 战斗开始时触发
relicRef.triggerOn("onPlayerDamaged"); // 玩家受伤时触发
```

基于 BaseMod 事件体系注册引用触发器。远程遗物可被本地卡牌触发（本地卡牌打出发出事件 → 远程遗物引用收到事件 → 解引用 → 经房主路由调用所有者），远程卡牌也可触发仅在本地的遗物（本地遗物是本地引用，直接在本地生效）。

## 就近原则

引用系统自动保证就近执行：

```
解引用路径选择:

  reference.dereference()
    │
    ├─ 对象在本地且内容校验通过 → 本地引用 → 直接执行
    │   例: A 打出自己拥有的 Strike_R
    │
    ├─ 对象在远端 → 远程引用 → 经房主路由调用所有者
    │   例: A 打出 B 的 Mod 卡牌 → A→房主→B→房主→A
    │   例: A 打出房主的 Mod 卡牌 → A→房主(自己执行)→A (短路，1次往返)
    │
    └─ 远程引用不可达 → 空引用
         ├─ 尝试引用退化 (本地是否已有完整定义?)
         └─ 尝试引用转移 (是否有备份源?)
```

交叉引用就近执行优势：
- B 的远程卡牌触发 A 的本地遗物 → A 本地遗物是本地引用 → A 直接生效，无网络往返
- A 打出 B 的远程卡牌 → 解引用 → 调用 B → B 本地钩子触发（B 的 Mod 正常运作）
- 引用的所有者就是执行者：计算永远发生在所有者的机器上

## 图主权威模型

图主按阶段投票选定，仅掌管三类对象：**地图、事件、怪物**。图主**不等于**房主——房主负责路由，图主负责游戏逻辑。

### 角色分离

```
┌─────────────────────────────────────────┐
│              房主 (网络路由)              │
│  • 维护到所有客户端的星型连接              │
│  • 转发所有 invoke / state_sync 消息      │
│  • 管理中央待打出队列                      │
│  • 心跳检测、掉线处理                      │
│  • 不执行游戏逻辑（除非自己是所有者）        │
├─────────────────────────────────────────┤
│              图主 (游戏逻辑)              │
│  • 地图生成、房间类型决定                  │
│  • 怪物初始状态、AI、意图                  │
│  • 事件类型与选项                          │
│  • 可就是房主，但不是必须                   │
└─────────────────────────────────────────┘
```

### 图主职责

```
1. 地图权威
   • 用共享种子生成当前阶段地图 → 经房主转发给所有人
   • 进入房间时决定房间类型和内容
     - 战斗 → 图主统一怪物配置
     - 事件 → 图主决定具体事件

2. 怪物权威
   • 怪物初始状态由图主提供
   • 怪物 AI/意图/行为由图主解引用（图主=怪物所有者，始终本地执行）
   • 结果经房主转发给全员
   • 其他人对怪物只能建立远程引用 → 渲染走 fallback 或操作重放

3. 事件权威
   • 图主决定事件选项（未来可扩展投票）
```

### 强所有权

图主持有的对象**只允许远程引用**，其他玩家不允许建立本地引用：

```
其他玩家想渲染图主的怪物:
  → 建立远程引用指向图主
  → 本地卡池有该怪物? → 用本地引擎操作重放 + suppressEvents
  → 本地卡池无该怪物? → 用 fallback 数值渲染
```

### 房主/图主掉线

- **普通客户端掉线**：房主检测心跳超时 → 广播 player_left → 移除待打出队列中该客户端的项
- **图主掉线**（由图主持有的对象全部变空引用）：
  - 自动保存进度，等待图主重连
  - 可尝试投票选新图主，引用转移到新图主（暂不实现）
- **房主掉线**：检测心跳超时 → 投票选新房主 → 新房主拉取所有在线角色完整状态快照 → 重建中央队列和网络路由

## 房主中央队列：战斗流程

### 核心设计

房主维护唯一中央队列，负责调度所有卡牌执行：

```
1. 客户端提交卡牌 → 发 queue_submit 到房主
2. 房主收到 → 根据 timestamp + sender_id 排序插入中央队列
3. 房主广播 queue_update（当前队列快照）→ 全员更新 UI
4. 房主从队列头部取出一项：
   a. 发 invoke 到卡牌所有者（如果所有者=房主自己，直接本地执行）
   b. 等待所有者回传 invoke_result
   c. 将执行结果广播给全员（combat_result）
   d. 重复直到队列为空
5. 队列清空后才允许结束回合
```

### 标准包结构

```typescript
interface QueueSubmit {
  type: "queue_submit";
  sender_id: string;       // 提交者（打出卡牌的玩家）
  owner_id: string;        // 卡牌所有者（谁负责解引用）
  seq: number;             // 提交者序列号
  card_id: string;         // 卡牌 ID
  resource_hash: string;   // 内容校验用哈希
  target: string;          // 游戏内目标 "monster_0" | "player_b" | "self"
  timestamp: number;       // 出牌时间戳
}

interface QueueUpdate {
  type: "queue_update";
  source: string;          // 房主 ID
  entries: QueueEntry[];   // 完整队列快照
}

interface QueueEntry {
  packet_id: string;       // sender_id + seq
  sender_id: string;
  owner_id: string;
  card_id: string;
  target: string;
  status: "pending" | "executing" | "done";
}
```

### 调度流程

```
房主中央队列调度:

  队列: [Strike_R(A), Defend_G(B), Bash_R(host)]

  1. 取出 Strike_R(A):
     owner_id = A，不是房主
     → 房主 → A: invoke(Strike_R)
     → A 本地执行，触发钩子
     → A → 房主: invoke_result(effects)
     → 房主 → 全员: combat_result(effects)

  2. 取出 Defend_G(B):
     owner_id = B，不是房主
     → 房主 → B: invoke(Defend_G)
     → B 本地执行，触发钩子
     → B → 房主: invoke_result(effects)
     → 房主 → 全员: combat_result(effects)

  3. 取出 Bash_R(host):
     owner_id = host == 房主自己
     → 房主本地执行，触发钩子
     → 房主 → 全员: combat_result(effects)
```

### 玩家视角

1. **提交卡牌**：卡牌从手牌悬浮至队列区域 → 发 `queue_submit` 到房主 → 等待
2. **继续操作**：卡牌已离手，可继续打牌加入队列
3. **等待执行**：房主逐个调度队列中的项，处理到自己的项时播放动画
4. **回合结束**：队列全部清空后房主广播 `queue_empty`，所有人可结束回合

### 队列可视化

房主广播 `queue_update`，每个人显示相同队列 UI：
- 队列项按顺序排列，显示提交者、卡牌名、状态（等待中/执行中/已完成）
- 执行中的项高亮
- 自己的项排队时可以看自己在第几个

## 怪物回合

怪物由图主持有（所有者 = 图主），流程：

```
1. 图主本地遍历所有未死亡怪物
2. 对每个怪物:
   a. 图主用共享种子 RNG 确定怪物意图
   b. 图主对自己的怪物引用解引用（始终本地，图主有怪物完整定义）
   c. 图主 → 房主: monster_intent → 房主转发全员（渲染意图图标）
   d. 怪物执行动作（AI 逻辑，触发图主本地钩子）
   e. 收集效果结果 → 图主 → 房主: combat_result
   f. 房主转发 combat_result → 全员同步:
      - 有该怪物定义者 → 操作重放 + suppressEvents
      - 无该怪物定义者 → fallback 数值渲染
3. 所有怪物动作完成后，回合结束
```

怪物动作由图主解引用 → 始终本地执行，无网络往返。图主和房主分离时需经过一次转发。

## 事件处理

事件由图主持有：

```
1. 图主决定事件类型和选项
2. 图主本地执行事件逻辑
3. 图主 → 房主: stage_sync（事件结果）
4. 房主转发 → 全员同步
```

未来可扩展投票模式（兼容塔2），当前版本暂不实现。

## Mod 兼容性设计：内容校验与分层引用

### 内容校验

同名类/方法不等同于相同类/方法。通过内容哈希比对判定两个实体是否真正一致：

```
A 有 Mod v1.0 的 Strike_P
B 有 Mod v1.1 的 Strike_P  (同一 card_id，但效果被改过)
  → 内容校验：resource_hash 不同
  → B 不能建立本地引用（不认为这是"相同卡牌"）
  → B 使用远程引用 → 解引用时经房主路由调用 A 执行
```

```typescript
// 内容校验
function validateResource(resourceId: string, remoteHash: string): boolean {
  const localHash = sha256(loadResourceBytes(resourceId));
  return localHash === remoteHash;
}
```

校验应用于所有可执行对象：卡牌类、遗物类、能力类、怪物类、Map 生成函数、事件类等。

### 分层引用策略

```
收到远程卡牌:

  1. 提取 resource_hash
  2. 本地按 card_id 查找类定义
  3. 内容校验（hash 比对）:
     ├─ 校验通过 → 本地引用（引擎执行，触发本地钩子，引擎自动产 VFX）
     └─ 校验失败/无本地定义 → 远程引用（解引用时经房主路由调用所有者）
         → 结果用 fallback 数值渲染
```

### 交叉引用

引用系统使交叉所有权变得自然：

```
A 打出 B 的远程卡牌 Strike_P:
  A 提交到房主队列 → 房主调度 → 调用 B 执行 Strike_P
  Strike_P 的效果触发"对所有玩家施加能力"
    → B 持有对 A 远程角色的远程引用
    → 解引用：B → 房主 → A → A 执行施加逻辑 → A 触发本地钩子

B 的远程卡牌触发 A 的本地遗物 Vajra:
  B 执行卡牌时发出 BaseMod.publishOnCardUse()
  A 本地遗物 Vajra 订阅了 onCardUse 事件
    → Vajra 是本地引用 → A 直接执行遗物逻辑 → 触发 A 本地钩子
```

远程遗物可以被本地卡牌触发（本地卡牌发出事件 → 远程遗物的引用订阅了该事件 → 经房主路由调用所有者执行）。远程卡牌也可以触发仅在本地存在的遗物（遗物在本地 → 本地引用 → 直接在本地生效）。

### Fallback 效果类型（保持原有）

| kind | 说明 | 关键字段 |
|------|------|----------|
| `damage` | 造成伤害 | `target`, `amount`, `damage_type` |
| `gain_block` | 获得格挡 | `target`, `amount` |
| `apply_power` | 施加能力 | `target`, `power_id`, `amount` |
| `remove_power` | 移除能力 | `target`, `power_id` |
| `heal` | 治疗 | `target`, `amount` |
| `gain_energy` | 获得能量 | `target`, `amount` |
| `draw_card` | 抽牌 | `target`, `amount` |
| `discard_card` | 弃牌 | `target`, `amount` |
| `exhaust_card` | 消耗卡牌 | `target`, `card_id` |
| `gain_gold` | 获得金币 | `target`, `amount` |
| `obtain_relic` | 获得遗物 | `target`, `relic_id` |
| `obtain_potion` | 获得药水 | `target`, `potion_id` |
| `lose_hp` | 失去生命 | `target`, `amount` |

## BaseMod 事件抑制机制

BaseMod 的事件系统基于 `@SpirePatch` 注入游戏方法 → `BaseMod.publishXxx()` → 遍历 `ArrayList<SubscriberType>` 调用回调。BaseMod 自身不提供任何内置的抑制标志。

CrossSpire 通过 **`@SpirePatch` 拦截 `BaseMod.publishXxx()` 方法**来实现开关控制：

```java
// CrossSpireMod.java — 全局开关
public class CrossSpireMod {
    public static final AtomicInteger eventSuppression = new AtomicInteger(0);

    public static void suppressEvents(Runnable fn) {
        eventSuppression.incrementAndGet();
        try { fn.run(); }
        finally { eventSuppression.decrementAndGet(); }
    }
}
```

抑制的触发场景：
- 非所有者收到 `combat_result` → 操作重放时 suppressEvents 包裹（结果已由所有者触发过一次钩子，不能重复触发）
- 在线角色状态写入（HP 变化、遗物获取等）时 suppressEvents 包裹
- 远程引用解引用 → 所有者在远端触发钩子 → 结果经房主路由回来 → 本地 suppressEvents 渲染

### 引用中的钩子触发规则

| 场景 | 是否触发钩子 | 原因 |
|------|-------------|------|
| 所有者解引用自己的对象 | 是 | 计算源点，钩子应触发 |
| 非所有者收到结果同步 | 否（suppressEvents） | 结果已在所有者处触发过一次 |
| 交叉引用中本地遗物被远程卡牌触发 | 是 | 遗物是本地引用，在本地直接执行 |

由于 BaseMod 没有 gold change hook，金币同步需要通过自定义 `@SpirePatch` 直接拦截 `AbstractPlayer.gainGold()` / `loseGold()` 方法。

## 素材传递系统

> 能本地渲染就本地渲染。就地原则，懒加载，按需传输。

### 素材与引用的关系

素材传递与引用系统协同工作：
- 远程引用解引用 → 需要渲染 → 检查素材
- 本地引用 → 素材已在本地，直接使用

### 三层就近查找

```
渲染远端卡 "Strike_P" 的素材:
   │
   ├─ 1. 本地卡池找到该卡牌 + 内容校验通过
   │     → 本地引用 → 零传输 — 远端和本地用同一 Mod 版本
   │
   ├─ 2. 本地磁盘缓存命中
   │     → 零网络 — 之前缓存过，从 {cache_dir}/{source_player}/{card_id}/ 加载
   │
   └─ 3. 本地没有且未缓存
         → 向素材来源方发送 resource_request
         → 显示占位素材 (灰色卡牌 + card_name 文字)
         → 收到 resource_response 后写入磁盘缓存 → 内存加载 → 替换占位
```

### 运行时素材拦截

游戏将 Spine 骨骼数据和纹理存储在 `AbstractCreature` 的实例字段中，已在内存中，无需从磁盘重新读取：

```java
// AbstractCreature.class (desktop-1.0.jar 确认)
protected TextureAtlas atlas;
protected Skeleton skeleton;
public AnimationState state;               // ← 公开字段
protected AnimationStateData stateData;
public static SkeletonMeshRenderer sr;     // ← 静态共享渲染器

// AbstractPlayer — 角色纹理 (公开字段)
public Texture shoulderImg;
public Texture shoulder2Img;
public Texture corpseImg;
public Texture img;                        // 半身像 (原版角色为 null，使用 Spine)
```

通过 `basemod.ReflectionHacks` 即可提取：

```java
TextureAtlas atlas = ReflectionHacks.getPrivate(
    player, AbstractCreature.class, "atlas");
Skeleton skeleton = ReflectionHacks.getPrivate(
    player, AbstractCreature.class, "skeleton");
SkeletonData data = skeleton.getData(); // 含全部骨骼、插槽、动画定义
```

素材发送方**保留原始文件路径引用**（如 `images/characters/ironclad/idle/skeleton.json`），按路径读取原始字节进行传输。纹理数据通过 `Texture.getTextureData()` 提取像素后编码为 PNG。

### 远程素材类层次

所有远程素材类**不继承游戏基类**（避免副作用，不污染卡池/遗物池），自行实现渲染：

```
RemoteCardResource                  ← 卡牌素材投影
  ├── cardId, cardName
  ├── cardType, cardRarity          ← 决定外框样式
  ├── energyCost, description
  ├── largeTexture: Texture         ← 大图 (SingleCardView, 250×190)
  ├── smallTexture: Texture         ← 小图 (手牌, 128×128)
  └── render(sb, x, y, scale)       ← 绘制卡牌外框 + 素材

RemoteRelicResource                 ← 遗物素材投影
  ├── relicId, relicName
  ├── texture: Texture              ← 48×48 图标
  ├── description: String
  └── renderOnPlayerBar(sb, x, y)   ← 在玩家状态栏渲染遗物

RemotePowerResource                 ← 能力素材投影
  ├── powerId, powerName
  ├── icon48: Texture               ← 小图标 (能力槽位, 48×48)
  ├── icon84: Texture               ← 大图标 (悬停提示, 84×84)
  ├── description: String
  └── renderOnCombatHud(sb, x, y)

RemoteCharacterResource             ← 角色骨骼素材投影
  ├── characterId                   ← e.g. "IRONCLAD"
  ├── skeletonData: SkeletonData
  ├── atlas: TextureAtlas
  ├── [动态] currentAnimation / animationTime
  ├── [动态] drawX, drawY, scaleX, scaleY
  └── render(sb)                    ← 委托给 SkeletonRenderer

RemoteMonsterResource               ← 怪物素材投影 (未来)
RemotePotionResource                ← 药水素材投影 (未来)
```

### 缓存系统

```
RemoteAssetCache
  ├── L1 内存缓存
  │   Map<CacheKey, WeakReference<Texture>>
  │   Map<CacheKey, SkeletonData>
  │   上限: 128MB → LRU 淘汰
  │
  ├── L2 磁盘缓存
  │   路径: {game_dir}/crossspire_cache/
  │         ├── {source_player_id}/
  │         │   ├── cards/{card_id}/
  │         │   │   ├── large.png
  │         │   │   ├── small.png
  │         │   │   └── meta.json        ← name, type, rarity, cost, description
  │         │   ├── relics/{relic_id}.png
  │         │   ├── powers/{power_id}/
  │         │   │   ├── icon48.png
  │         │   │   └── icon84.png
  │         │   ├── potions/{potion_id}.png
  │         │   ├── characters/{character_id}/
  │         │   │   ├── skeleton.json
  │         │   │   ├── skeleton.atlas
  │         │   │   └── skeleton.png
  │         │   └── monsters/{monster_id}/...
  │         └── manifest.json            ← 所有缓存素材的 checksum
  │
  ├── CacheKey = sha256(resource_type + resource_id + variant)[0:16]
  ├── 校验: response.checksum vs 磁盘 checksum → 不匹配则重新下载
  └── 过期: 上次访问 > 30 天自动清理
```

### 素材协议消息

素材消息是独立的顶层 `type`，用于在玩家之间传递 Mod 卡牌/遗物/角色等视觉素材。

| 消息类型 | 方向 | 说明 |
|----------|------|------|
| `resource_registry` | C→房主→全员 | 连接时发送本地拥有的素材清单 |
| `resource_request` | C→房主→特定C | 请求特定素材 (由 `target` 字段指定接收方) |
| `resource_response` | 特定C→房主→请求方 | 素材数据响应 |
| `animation_sync` | C→房主→全员 | 角色动画切换通知 |

```typescript
// 素材注册 — 连接时交换一次
interface ResourceRegistry {
  type: "resource_registry";
  source: string;
  cards: string[];        // 本地 CardLibrary 拥有的 card_id
  relics: string[];       // 本地 RelicLibrary 拥有的 relic_id
  powers: string[];       // 本地拥有的 power_id
  potions: string[];      // 本地拥有的 potion_id
  characters: string[];   // 本地拥有的角色骨架
}

// 素材请求 — 懒加载触发，点对点定向
interface ResourceRequest {
  type: "resource_request";
  source: string;
  target: string;         // 素材来源方 player_id
  resource_type: "card_texture" | "card_large" | "card_small"
               | "relic_texture" | "power_icon48" | "power_icon84"
               | "potion_texture" | "character_skeleton"
               | "character_atlas" | "character_png";
  resource_id: string;    // 如 "Strike_R" 或 "IRONCLAD"
}

// 素材响应 — 包含 base64 编码的素材数据
interface ResourceResponse {
  type: "resource_response";
  source: string;
  target: string;
  resource_type: string;
  resource_id: string;
  mime: string;           // "image/png" | "application/json" | "text/plain"
  data_base64: string;
  checksum: string;       // SHA-256，用于缓存校验
}

// 动画同步 — 仅在动画切换时推送
interface AnimationSync {
  type: "animation_sync";
  source: string;
  animation: string;      // "Idle", "Attack", "Hit" 等 Spine 动画名
  track: number;          // track index
  loop: boolean;
  mix_duration: number;   // 过渡时间 (秒)
}
```

## 在线角色：独立 AbstractPlayer 实例与渲染集成

在线角色是纯渲染和状态镜像，状态通过解引用写入：

```
RemotePlayer extends AbstractPlayer:
  • 卡组/遗物/药水/能力/HP/格挡/能量 — 状态镜像
  • 所有状态写入 suppressEvents 包裹
  • 不持有可执行对象的本地引用（除非内容校验通过且角色切换）
  • 渲染集成通过 BaseMod RenderSubscriber（保持原有设计）
```

在线角色持有的引用：
- 对自己远端角色的远程引用（状态写入路径，经房主路由）
- 对图主怪物/事件的远程引用（渲染用）
- 对自身遗物/能力的远程引用（被动效果触发用）

## RNG 同步策略

战斗确定性是同步的前提：相同的种子 + 相同的动作序列 = 相同的战斗结果。

### 策略

```
1. 游戏开始时所有玩家协商共享种子 → 全员使用同一种子
2. 共享 RNG（确定性随机）:
   - 地图生成 → 图主用种子 RNG 计算 → 经房主广播
   - 怪物意图选择 → 图主用种子 RNG 计算 → 经房主广播
   - 怪物攻击目标选择 → 图主用种子 RNG 计算 → 经房主广播
   - 在线角色抽牌 → 图主用种子 RNG + 远端角色卡组状态计算 → 经房主推送
3. 本地 RNG（非共享，玩家决策）:
   - 随机遗物效果 (Dead Branch 等) → 本地 RNG → 结果同步给其他人
```

## 网络拓扑

### 星型拓扑

```
                     ┌──────────────┐
                     │    房主       │
                     │  (网络路由)   │
                     │  O(n) 连接    │
                     └──┬───┬───┬──┘
                        │   │   │
              ┌─────────┘   │   └─────────┐
              ▼             ▼             ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Player A │ │ Player B │ │ Player C │
        └──────────┘ └──────────┘ └──────────┘

        客户端间不直接通信。所有消息经房主转发。
        房主维护 n-1 条连接（n = 总玩家数）。
```

### 加入流程

```
1. B 输入 A 的 IP:port:password → 连接 A
   注：A 可以是任何已加入房间的玩家，不一定是房主
2. B → A: hello(B的ID, IP, port)
3. A → B: room_info {
     name, password_hash,
     host: {id, ip, port},        // 当前房主
     members: [{id, ip, port}],   // A 记录的全体成员
     stage_host: {id}             // 当前图主
   }
4. B 与房主建立连接（如果 A 不是房主）
5. 房主 → 全员: player_joined(B)
6. 房主收集 B 的完整状态快照 → 广播给现有成员
```

### 房主即所有者时的短路

```
A.ownerId == A.hostId 时:
  房主识别 invoke 的 ownerId == 自己 → 本地执行 → 直接回传结果
  跳过一次转发往返
```

### 掉线处理

| 掉线角色 | 处理方式 |
|----------|----------|
| 普通客户端 | 房主检测心跳超时 → 广播 player_left → 移除中央队列中该客户端的待处理项 |
| 图主 | 图主持有对象全部变空引用 → 自动保存进度等待重连 → 可投票新图主（暂不实现） |
| 房主 | 心跳检测全体超时 → 投票选新房主 → 新房主拉取完整状态快照 → 重建中央队列和路由 |

### 中继服务器

在当前阶段使用中继服务器作为房主通道。服务器路由消息到房间内所有人，与星型拓扑逻辑一致。

## 协议消息定义

### 顶层信封

```typescript
interface Message {
  type: string;          // 消息类型
  source: string;        // 发送方 ID
  seq: number;           // 单调序列号
  target?: string;       // 定向目标 ID（仅 invoke/invoke_result/resource 类消息使用）
  timestamp: number;     // 发送时间戳
  // ... type-specific fields
}
```

### 队列消息

| type | 方向 | 说明 |
|------|------|------|
| `queue_submit` | C→房主 | 客户端提交卡牌到中央队列 |
| `queue_update` | 房主→全员 | 房主广播当前队列状态 |
| `queue_empty` | 房主→全员 | 队列清空，可结束回合 |

```typescript
interface QueueSubmitMessage extends Message {
  type: "queue_submit";
  sender_id: string;
  owner_id: string;
  card_id: string;
  resource_hash: string;
  target: string;
}

interface QueueUpdateMessage extends Message {
  type: "queue_update";
  entries: QueueEntry[];
}

interface QueueEntry {
  packet_id: string;       // sender_id + seq
  sender_id: string;
  owner_id: string;
  card_id: string;
  target: string;
  status: "pending" | "executing" | "done";
}
```

### 引用/调用消息

| type | 方向 | 说明 |
|------|------|------|
| `invoke` | C→房主→owner | 远程引用解引用时的网络调用 |
| `invoke_result` | owner→房主→C | 远程执行结果回传 |
| `reference_register` | C→房主→全员 | 宣告自己可提供的引用对象清单 |
| `reference_migrate` | C→房主→newOwner | 引用转移请求 |

```typescript
interface InvokeMessage extends Message {
  type: "invoke";
  target: string;          // 所有者 ID（房主据此路由）
  ref_id: string;          // 引用 ID
  trigger: string;         // 触发接口 (onCardUse / atBattleStart / ...)
  args: any;               // 调用参数
}

interface InvokeResultMessage extends Message {
  type: "invoke_result";
  target: string;          // 原调用方 ID（房主据此路由）
  ref_id: string;
  effects: EffectDescription[];
  operation_sequence: OperationStep[];
}
```

### 战斗/状态消息

| type | 方向 | 说明 |
|------|------|------|
| `combat_result` | 房主→全员 | 卡牌/怪物执行结果广播 |
| `monster_intent` | 图主→房主→全员 | 怪物意图变更 |
| `player_state` | 所有者→房主→全员 | 在线角色属性更新 |
| `stage_sync` | 图主→房主→全员 | 地图/事件/怪物状态推送 |
| `full_snapshot` | 房主→全员 | 完整阶段状态快照（房主启动时） |

### 控制消息

| type | 方向 | 说明 |
|------|------|------|
| `hello` | C→任意成员 | 加入时宣告身份和网络信息 |
| `ping` / `pong` | C↔房主 | 心跳保活 |
| `player_joined` / `player_left` | 房主→全员 | 玩家进出通知 |
| `room_info` | 任意成员→C | 响应 hello，返回房间完整信息 |
| `stage_host_election` | 房主→全员 | 新阶段投票发起 |
| `stage_host_result` | 房主→全员 | 投票结果 |
| `host_election` | — | 房主掉线后投票选新房主 |
| `host_result` | — | 新房主选举结果 |

### 素材消息（保持原有）

| type | 方向 | 说明 |
|------|------|------|
| `resource_registry` | C→房主→全员 | 本地素材清单 |
| `resource_request` | C→房主→特定C | 请求特定素材 |
| `resource_response` | 特定C→房主→请求方 | 素材数据 |
| `animation_sync` | C→房主→全员 | 角色动画切换 |

### 内容校验哈希

```typescript
// 附加在引用注册和标准包中
interface ResourceHash {
  resource_type: "card" | "relic" | "power" | "monster" | "event" | "potion";
  resource_id: string;
  hash: string;           // SHA-256 of resource bytecode
  version?: string;       // Mod 版本号（可选）
}
```

## 项目结构

```
CrossSpire/
├── ARCHITECTURE.md              # 本文档
├── AGENTS.md                    # 仓库规则 + AI Agent 准则
├── .gitignore
├── cross-spire-server/          # 中继服务器 (Node.js/TypeScript)
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│       ├── server.ts            # Express + WebSocket 主入口
│       ├── store.ts             # 房间/票据 内存存储
│       ├── sequence.ts          # 序列号管理
│       └── protocol.ts          # TypeScript 协议类型
├── shared/                       # 跨语言共享定义
│   └── cross-spire-protocol/
│       └── protocol-schema.json # JSON Schema 协议定义
└── mods/                        # STS1 Mod (Java, ModTheSpire + BaseMod)
    └── cross-spire/
        ├── build.gradle.kts
        ├── README.md
        └── src/main/java/crossspire/
            ├── CrossSpireMod.java              # @SpireInitializer 入口
            ├── EventSuppression.java            # 全局抑制开关
            ├── SuppressBaseModPatches.java      # @SpirePatch 拦截 BaseMod.publish*
            ├── network/
            │   ├── RoomHostClient.java          # 房主连接管理（星型拓扑）
            │   ├── HeartbeatManager.java        # 心跳检测 + 掉线处理
            │   ├── RelayClient.java             # 中继服务器回退
            │   └── Protocol.java                # 消息 POJO + 序列化
            ├── reference/
            │   ├── Reference.java               # 引用抽象基类
            │   ├── LocalReference.java          # 本地引用
            │   ├── RemoteReference.java         # 远程引用（经房主路由）
            │   ├── NullReference.java           # 空引用
            │   ├── ReferenceFactory.java        # 引用工厂（决定走哪种引用）
            │   └── ContentValidator.java        # 内容校验（字节码哈希比对）
            ├── resource/
            │   ├── RemoteResourceManager.java   # 素材总控
            │   ├── RemoteAssetCache.java        # L1 + L2 缓存
            │   ├── RemoteAssetServer.java       # 响应素材请求
            │   ├── ResourceRegistryTracker.java # 远端素材清单
            │   ├── RemoteCardResource.java      # 远程卡牌素材投影
            │   ├── RemoteRelicResource.java     # 远程遗物素材投影
            │   ├── RemotePowerResource.java     # 远程能力素材投影
            │   ├── RemotePotionResource.java    # 远程药水素材投影
            │   └── RemoteCharacterResource.java # 远程角色素材投影
            ├── remote/
            │   ├── RemotePlayer.java           # extends AbstractPlayer
            │   ├── RemotePlayerRegistry.java   # 在线角色注册表
            │   ├── RemoteRenderer.java         # 实现 RenderSubscriber
            │   └── StageHost.java              # 图主：地图/事件/怪物权威
            ├── combat/
            │   ├── CentralQueueManager.java    # 房主中央队列调度
            │   ├── LocalCapturePatches.java    # 捕获本地操作 → 提交到房主
            │   └── CombatResultReplayer.java   # 操作重放 + suppressEvents
            └── ui/
                ├── LobbyScreen.java            # 加入房间界面
                ├── ServerPicker.java           # IP:port:password 输入
                ├── RoomPanel.java              # 房间内界面
                ├── RoomChat.java               # 聊天
                ├── QueueDisplay.java           # 中央队列可视化
                └── RemoteStatsOverlay.java     # 在线角色状态覆盖层
```

## 未来塔2兼容性预留

### 跨游戏挑战

| 塔1 | 塔2 | 差异 |
|-----|-----|------|
| Java + LibGDX | C# + Godot | 完全不同的运行时 |
| ModTheSpire (Javassist 字节码注入) | BepInEx (Harmony 方法Patch) | 不同的Mod框架 |
| 本地无多人 | 内置4人合作 | 多人基础不同 |
| STS1 卡池/遗物/角色 | STS2 卡池/遗物/角色 | 部分重叠 |

### 预留设计

1. **引用系统语言无关**：Reference<T> 是协议层抽象，Java 和 C# 均可实现
2. **内容校验跨游戏**：相同 resource_hash 的实体在塔1和塔2应效果一致，可直接本地引用；否则走远程引用+fallback
3. **实体映射表**：`shared/cross-spire-protocol/entity-mappings/` 维护塔1↔塔2的 card_id / relic_id / character_id 映射
4. **中继服务器不关心游戏版本**：协议上层转发，不做游戏逻辑
5. **塔2 Mod 预留相同的模块结构**：C# 实现 `crossspire` 命名空间，复用相同的协议定义

### 塔2 实现要点（未来）

- 使用 Harmony(X）拦截 Godot 中的 `GameManager` / `CombatManager` 等关键方法
- 在线角色复用塔2的 `Player` 类体系
- `EventSuppression` 通过 Harmony patch 拦截塔2的事件系统
- 与塔2内置多人模式共存（CrossSpire 联机和塔2自带联机互不干扰）

### 实体映射示例

```json
{
  "characters": {
    "IRONCLAD": "铁甲战士",
    "THE_SILENT": "静默猎人",
    "DEFECT": "故障机器人"
  },
  "cards": {
    "Strike_R": { "sts2_id": "Strike_R", "mapped": true },
    "Defend_R": { "sts2_id": "Defend_R", "mapped": true },
    "Bash": { "sts2_id": "Bash", "mapped": true }
  },
  "relics": {
    "Burning Blood": { "sts2_id": "BurningBlood", "mapped": true },
    "Vajra": { "sts2_id": "Vajra", "mapped": true }
  }
}
```

未映射的实体永远走远程引用 + fallback 效果通道。
