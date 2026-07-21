# CrossSpire — 杀戮尖塔联机Mod架构设计 (Plan)

> 本文档是 SDD 架构中的 `plan.md`。用户故事、功能需求与验收标准见 [`spec.md`](./spec.md)。

## 目录

1. [设计目标](#设计目标)
2. [关键术语定义](#关键术语定义)
3. [核心思想：引用模型与双角色投影](#核心思想引用模型与双角色投影)
4. [引用系统](#引用系统)
5. [就近原则](#就近原则)
6. [地图实例、节点实例与小队](#地图实例节点实例与小队)
    - [小队地图标注与共识](#小队地图标注与共识)
7. [小队中央队列：战斗流程](#小队中央队列战斗流程)
8. [Buff/Power 所有权与自发触发](#buffpower-所有权与自发触发)
9. [小队战斗阶段同步](#小队战斗阶段同步)
10. [怪物回合](#怪物回合)
11. [事件处理](#事件处理)
12. [所有者交互选择](#所有者交互选择)
13. [Mod 兼容性设计：内容校验与分层引用](#mod-兼容性设计内容校验与分层引用)
14. [BaseMod 事件抑制机制](#basemod-事件抑制机制)
15. [素材传递系统](#素材传递系统)
16. [在线角色：独立 AbstractPlayer 实例与渲染集成](#在线角色独立-abstractplayer-实例与渲染集成)
17. [RNG 同步策略](#rng-同步策略)
18. [网络拓扑](#网络拓扑)
19. [协议消息定义](#协议消息定义)
20. [项目结构](#项目结构)
21. [未来塔2兼容性预留](#未来塔2兼容性预留)

---

## 设计目标

CrossSpire 是一个开源的杀戮尖塔1联机Mod，目标：

1. **塔1多人同步战斗** — 多个玩家在同一场战斗中操作各自的角色
2. **Mod 兼容性** — 每个玩家可以使用自己的Mod，不要求所有人安装相同Mod
3. **跨游戏连接预留** — 架构预留与塔2互联的能力（塔1 Java/LibGDX ↔ 塔2 C#/Godot）
4. **开源** — 全栈开源的替代方案，对标闭源的 Together in Spire

### 平台与调试边界

当前实现和端到端验证以 SlayTheAmethyst 提供的 Android ModTheSpire + BaseMod 兼容运行时为基准。CrossSpire 本身只依赖 STS1、ModTheSpire、BaseMod 和 Java 8 API，不调用 Android SDK，也不依赖 SlayTheAmethyst 的 Java 类。

```
Android 开发/自动化:
  SlayTheAmethyst Harness
    → game-probe CONSOLE 协议
    → BaseMod DevConsole
    → CrossSpireCommand

CrossSpire 运行时:
  ConsoleCommand.addCommand("crossspire", CrossSpireCommand.class)
    → 显式 host/join/status 等命令
```

Harness、game-probe 和 ADB 属于外部开发基础设施，不进入 CrossSpire 的依赖图、协议 Schema 或发布 JAR。CrossSpire 不负责设备选择、ADB 连接、调试端口转发或测试台网络代理。

Desktop 继续以标准 ModTheSpire + BaseMod API 为兼容目标，可以使用相同的 BaseMod console 命令；本阶段不执行 Desktop 端到端验证，因此不将其列为已验证平台。

维护者测试台的 D1/D2 serial、loopback 转发和复现步骤见 [`development/`](./development/)（入口 [`development/README.md`](./development/README.md)）。这些值不得成为生产代码默认值。

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
| 房主 | Room Host | **网络路由和目录角色**。维护到所有客户端的星型连接（O(n)）；记录成员存活、每个 `party_id` 的队长、MapRegistry 和 NodeInstanceRegistry；路由所有跨客户端消息。房主不生成地图或节点内容，也不执行游戏逻辑（除非本人恰好是对象所有者） |
| 图主 | Map Host | **一次性地图生成者**。仅在小队的阶段过渡中生成并注册一张不可变 `MapDefinition`；注册成功后不管理该地图的节点、怪物、事件或战斗。每张 `MapInstance` 恰有一个 MapHost |
| 节点实例主 | Node Instance Host | **小队节点内容权威**。每个小队在活动阶段恰有一个，负责该小队所有 `NodeInstance` 的房间解析、怪物、事件、商店、宝箱、篝火及节点内核心状态；可与队长或 MapHost 是同一玩家，但身份独立 |
| 小队 | Party | 同一地图阶段中共享可见性、战斗队列、战斗阶段和地图标注的玩法域。每队独立处于阶段过渡、地图选择或活动节点；成员可显式离队或申请加入其他小队。跨小队相遇本期不实现 |
| 队长 | Party Leader | 小队玩法协调角色，固定为当前成员 ID 字典序最小者。管理本队中央队列、end-turn 聚合、combat phase 与地图标注共识；所有消息仍经房主路由。队长不因身份本身取得地图或节点内容权威 |
| 诱导重放 | Induced Replay | 非发送者收到 `combat_result` 时的本地处理：**(1) AUTHORITATIVE_APPLY** — `suppressEvents` 写入 effects 数值/VFX；**(2) LOCAL_OWNER_ONLY** — 仅触发 `logic_owner_id == self` 的 buff/遗物/组件。禁止无门控全量 `useCard`/BaseMod hook 重算。非所有者投影无逻辑效果 |
| 逻辑所有者 | Logic Owner | Buff/Power 的执行权归属。**施加者优先**；施加者掉线后回退到 content-hash 可执行者，再回退宿主权威（怪物→NodeInstanceHost，玩家自身→该玩家） |
| 组件附着 | ComponentAttachment | 挂在 `player:<id>` 或 `monster:<instance_id>` 上的 buff/power 实例元数据：`instance_id`、`resource_id/hash`、`logic_owner_id`、`amount` |
| 怪物 mutation | Monster Mutation | 非节点实例主对怪物核心状态的修改只能发 `monster_mutation_proposal`；当前 `node_instance_host_id` 校验 revision 后 `monster_mutation_commit` 广播。节点实例主是本队活动节点怪物 HP/死亡/生成的唯一写入者 |
| 战斗阶段 | Combat Phase | 由**队长**按 `party_id` 广播的对齐信号（`queue_empty` / 聚合 `player_end_turn` / `combat_phase`）。本队客户端跟本地引擎进入对应阶段；**不**由队长/图主远程点名触发他人 buff |
| 标准包 | Standard Packet | 本 Mod 中所有引用（卡牌/遗物/怪物/事件/玩家/素材）的网络传输统一封装。固定头部 `{packet_id, source, seq, timestamp, ref_id, owner_id, resource_hash?, operation}` + `operation` 特定的 `payload`。玩法范围包必须带 `party_id`；标准包经房主路由，引用系统据此完成解引用、诱导重放、状态同步等所有操作。控制消息（心跳、加入/离开、投票等）不属于标准包，保持独立格式 |

### 操作关系

```
远程引用解引用（全经房主路由，所有消息为标准包）:
  A 持有指向 C 对象的远程引用
    → A 调用 ref.dereference(args)
    → 引用系统识别为 REMOTE
    → A → 房主: StandardPacket { operation: invoke, owner_id: C, payload: { trigger, args } }
    → 房主转发 → C
    → C 本地执行，触发钩子
    → C → 房主: StandardPacket { operation: invoke_result, owner_id: A, payload: { effects, operation_sequence } }
    → 房主转发 → A
    → A 渲染结果（不触发钩子）

本地引用解引用:
  A 持有指向自己对象的本地引用
    → A 调用 ref.dereference(args)
    → 引用系统识别为 LOCAL
    → 直接调用，零网络往返

房主就是所有者时（最短路径）:
  A 持有指向房主/B 对象的远程引用
    → A → 房主: StandardPacket { operation: invoke, owner_id: B }
    → 房主识别 owner_id == 自己 → 本地执行，触发钩子
    → 房主 → A: StandardPacket { operation: invoke_result }
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
  │     → owner 本地执行（REAL 模式）→ 触发钩子 → 回传 invoke_result 给房主
  │     → 房主转发 invoke_result 给调用方
  │     → 调用方渲染结果，不触发钩子
  │     → 返回 Result
  │
  └─ type == NULL
        → tryDegrade()     // 本地是否已有完整定义？
        → tryMigrate()     // 是否有其他节点持有副本？
        → 都失败 → 抛出空引用异常，界面显示不可达
```

### 解引用的两种执行模式

解引用根据**执行位置**切换模式，模式选择不由上层指定，由引用系统自动判定：

```
┌─ REAL 模式（发送者/所有者机器上）
│   真实调用 use() / AI() → 修改游戏状态 → 触发 BaseMod hooks + @SpirePatch 拦截器
│   产出 combat_result（含 operation_sequence + effects）
│   其中 apply_power 必须记录 logic_owner_id = 施加者
│
└─ INDUCED 模式（非发送者收到 combat_result 后）— 两阶段，禁止全量 hook 重算
    1) AUTHORITATIVE_APPLY（suppressEvents）
       ├─ damage / gain_block / heal / ... → 仅写数值 + VFX
       └─ apply_power → 写入层数/图标投影 + ComponentAttachment 元数据
          （非 logic_owner 节点上 Power 回调 no-op）
    2) LOCAL_OWNER_ONLY
       ├─ 仅 fire TriggerRegistry / 本地组件中 logic_owner_id == self 的项
       ├─ 禁止无门控 publishOnCardUse / 全量 AbstractPlayer.useCard 深层链
       └─ 本地被动若产生新 effects → 提交房主（带 origin_owner_id + hop_count）
```

**核心逻辑**：
- **效果步骤**用 suppressEvents 包裹，仅写入数值和 VFX——数值已由 REAL 执行者计算
- **被动步骤**不是“人人重放即触发”，而是 **local-owner-only**：只有逻辑所有者在本机自发执行自己的 buff/遗物
- 非所有者节点上的同名 buff 投影：**可显示、不可被触发、无逻辑效果**
- `suppressEvents` 使用 `AtomicInteger` 计数器，支持嵌套
- induced 副作用必须带 `origin_owner_id` 与 `hop_count`；超限丢弃，防止反馈环

### 诱导重放中的 Stub / 投影对象

INDUCED 的 AUTHORITATIVE_APPLY 阶段可使用轻量 stub 仅用于**状态投影**，不用于触发全量事件链：

```java
// CardStub — metadata only；不得作为“全量 hook 触发器”
class CardStub extends AbstractCard {
    String cardID;
    CardType type;
    CardRarity rarity;
    int cost;
    @Override
    void use(AbstractPlayer p, AbstractMonster m) { /* no-op */ }
}

// PowerStub — 非 logic_owner 时回调必须 no-op
class PowerStub extends AbstractPower {
    String powerID;
    String logicOwnerId;
    int amount;
    // atStartOfTurn / onAttacked / ... → 若 localPlayerId != logicOwnerId 则 return
}
```

Stub/投影是瞬态或附着镜像：收到 `combat_result` 时更新状态 → **仅 local owner 组件**可产生新 proposal/effects → 销毁或保留为显示层。

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

## 地图实例、节点实例与小队

阶段不再有一个承担全部内容的 `StageHost`。每个小队在进入下一 Act 前先进入不属于任何地图或节点的 `STAGE_TRANSITION`；该小队完成组队、选图和角色选举后才进入地图。MapHost、NodeInstanceHost、队长和房主是独立角色。

### 角色分离

```
┌─────────────────────────────────────────┐
│           房主 (网络路由/目录)            │
│  • 维护到所有客户端的星型连接              │
│  • 转发所有 invoke / state_sync 消息      │
│  • 保存地图和节点实例目录                    │
│  • 心跳检测、掉线处理                      │
│  • 不执行游戏逻辑（除非自己是所有者）        │
├─────────────────────────────────────────┤
│          MapHost (一次性地图生成)          │
│  • 生成并登记不可变 MapDefinition          │
│  • 登记完成后不管理任何节点内容              │
│  • 可就是房主，但不是必须                   │
├─────────────────────────────────────────┤
│     NodeInstanceHost (本队节点内容权威)     │
│  • 生成本队 NodeInstance 与节点内一切内容    │
│  • 怪物/事件/商店/宝箱/篝火/核心状态          │
│  • 每个活动小队恰有一个                      │
├─────────────────────────────────────────┤
│            队长 (小队玩法协调)             │
│  • 本队中央队列、end-turn 聚合、战斗阶段      │
│  • 本队地图标注与共识                        │
│  • 固定为成员 ID 字典序最小者                │
│  • 不因队长身份决定地图/房间内容              │
└─────────────────────────────────────────┘
```

### 小队生命周期与隔离

```
Room 创建/成员加入
  → 默认小队 P0 包含所有在线成员
  → leader(P0) = sort(member_ids)[0]

crossspire party leave
  → 当前成员离开 P0，创建仅含自己的新小队 Px
  → 两队均重新计算 leader；成员保留当前地图节点
  → 房主广播 party_snapshot

crossspire party join <party_id>
  → 房主将 party_join_request 路由给目标队长
  → 队长 approve/reject
  → approve 后房主广播 party_snapshot，并重算相关队长
```

`party_id` 是玩法隔离键，不是新的网络拓扑：所有包仍经房主的星型连接。以下状态必须按 `party_id` 独立存储和路由：

- 中央队列、queue update/empty、end-turn 聚合和 combat phase
- 地图标注与 room consensus
- 事件 interface、choice request/approval、投票和玩家结果
- 同队在线角色可见性、战斗渲染和状态投影

图主只生成和登记地图，不维护房间内容。房主保存 MapRegistry 与 NodeInstanceRegistry；小队可绑定同一张地图，但本期不实现不同小队在同一节点相遇、合并或共享战斗。

### 阶段过渡与地图目录


`STAGE_TRANSITION` 按小队独立存在，不属于任何 `MapInstance` 或 `NodeInstance`。它允许本队组队、创建或加入地图、选举主机和确认地图快照；不得执行房间生成、事件、战斗或遗物的入房逻辑。其他小队可以继续其活动地图或节点，不被阻塞。

```
MAP_COMPLETED
  → STAGE_TRANSITION
  → 创建新图: 本队选 MapHost → map_register → MapRegistry
  │  或加入既有图: map_join_request → MapRegistry
  → 本队选 NodeInstanceHost
  → 全员确认地图快照
  → MAP_ACTIVE
  → 地图标注 / NodeInstance
```

每张 `MapInstance` 恰有一个 `map_host_id`，但 MapHost 只负责生成和登记；房主的 MapRegistry 保存地图定义，使 MapHost 在登记后掉线也不影响其他小队加入。加入既有地图的小队不重新选 MapHost，而是为本队选出自己的唯一 NodeInstanceHost。

`MapDefinition` 是不可变的，至少包含稳定 `node_id`、坐标、边、基础节点类别和图标、燃烧精英标记、`boss_descriptor`、地图规则版本和生成摘要。Boss descriptor 的 ID/hash 决定 Boss 生成；图标资源只用于显示。定义不含怪物实例、问号最终结果、事件、商店库存、宝箱奖励或篝火选项。

MapHost 生成的只是地图骨架：普通/精英/商店/篝火/宝箱/Boss 的基础节点类别、路径和图标在此固定；`?` 保持问号节点，直到首次进入时解析。佛珠手链、小宝箱等影响问号结果的遗物只在节点实例生成时生效，绝不在地图或过渡阶段提前消耗。

### 小队范围的主机选举

MapHost 投票只发生在创建新地图的小队，NodeInstanceHost 投票发生在每个完成选图的小队。房主按 `party_id` 聚合全员一致投票；无超时和默认选择。两种角色可以同人，但选票、结果和目录字段必须独立。

#### 选举消息

```
map_host_vote                  C→房主→本队  {"type":"map_host_vote","party_id":"<id>","candidate_id":"<id>"}
map_host_result                房主→本队    {"type":"map_host_result","party_id":"<id>","map_host_id":"<id>"}
node_instance_host_vote        C→房主→本队  {"type":"node_instance_host_vote","party_id":"<id>","candidate_id":"<id>"}
node_instance_host_result      房主→本队    {"type":"node_instance_host_result","party_id":"<id>","node_instance_host_id":"<id>"}
```

#### 选举流程

```
1. 创建新地图的小队在 STAGE_TRANSITION 内投 MapHost
2. 房主按 party_id 聚合并广播 map_host_votes
3. 本队全员投同一 candidate → 房主广播 map_host_result
4. MapHost 生成并登记地图；加入既有地图的小队跳过此步
5. 本队投 NodeInstanceHost，房主聚合后广播 node_instance_host_result
6. 全员确认地图快照 → 房主广播 stage_transition_complete
```

#### 共识条件

与房间标注相同，但投票范围是目标小队。房主维护按角色和 `party_id` 分组的投票表，每次 vote 后检测该小队所有在线成员是否投同一候选。无超时、不走默认选择。

#### 掉线与恢复

- MapHost 在 `map_register` 成功后没有运行时接管职责；其掉线不影响已登记地图。
- NodeInstanceHost 在活动节点掉线时，房主暂停该小队节点流程，进行本队重选和快照恢复；MapHost 不成为后备节点主。
- 队长掉线只按成员 ID 重选队长；不得隐式改变 `node_instance_host_id`。
- 房主掉线时，新房主从成员、小队、MapRegistry 和活动节点快照重建目录和路由。

#### 与房主角色的关系

房主仅聚合本队投票、维护目录并路由消息；MapHost 或 NodeInstanceHost 与房主同体时可以在本地完成相应步骤，但房主不因此取得内容执行权。

### 节点实例与强所有权

房主为经过路径校验的进入请求分配 `NodeInstance`。同一张地图的不同小队进入同一 `node_id` 时，也必须使用不同实例：

```
NodeInstanceKey = (map_instance_id, party_id, node_id, visit_id)
```

NodeInstanceHost 是本队节点内怪物、事件、商店、宝箱、篝火、问号解析和核心状态的强所有者。地图图形可以共享，但位置、已访问节点、生成遗物状态和战斗状态按小队隔离；本期不实现相遇、合并或共享战斗。

首个非战斗节点切片只处理 `monster` 与 `event` 两种不可变 `MapNode.room_type`。NodeInstanceHost 为已分配实例生成类型化 `NodeGenerationResult`：`monster` 必须带 `encounter`，`event` 必须带完整 `event_interface`。RoomHost 仅校验该类型化提交并向本队打开实例；打开 event 实例后仅 NodeInstanceHost 经房主发布该已提交的 interface。此切片不解析 `?`、不生成商店/篝火/宝箱，也不提前消耗生成修正器。

影响房间生成的遗物只在节点实例生成时生效。NodeInstanceHost 使用只读、版本化的生成上下文和声明式修正器，不能临时迁移或挂载远端 `AbstractRelic`。未知 Mod 遗物由真实所有者受控解析；Tiny Chest 等修正器计数变更必须与 `node_generation_commit` 原子提交。

### 小队地图标注与共识

小队进入可选择的地图节点时，仅该小队成员通过标注机制协调"进入哪个房间"。这是一个非战斗的协调流程；控制消息经房主路由，聚合和共识由队长执行。

#### 协议消息

```
room_pin       C→房主→队长       {"type":"room_pin","source":"<playerId>","party_id":"<id>","room":<index>}
room_pins      队长→房主→本队    {"type":"room_pins","source":"<leaderId>","party_id":"<id>","pins":{"alice":1,"bob":1}}
room_consensus 队长→房主          {"type":"room_consensus","source":"<leaderId>","party_id":"<id>","map_instance_id":"<id>","node_id":"<id>"}
```

**字段说明**:
- `room`: 现有命令使用的整数 index（0-based）；发送前必须解析为稳定 `node_id`
- `party_id`: 标注所属小队；非成员和过期队长的标注必须拒绝
- `pins`: `Map<playerId, roomIndex>`，队长维护的本队标注快照

#### 流程

```
1. 玩家标注
   玩家A → 房主 → 队长: room_pin {party_id: P, room: 1}
   队长记录 {A: 1} → 房主 → P: room_pins

   玩家B → 房主 → 队长: room_pin {party_id: P, room: 1}
   队长记录 {A: 1, B: 1} → 房主 → P: room_pins

2. 共识检测
   队长每次收 pin 后检查: 是否所有在线小队成员标记了同一个 room?
    若全队一致 → 队长 → 房主: room_consensus {party_id: P, map_instance_id: M, node_id: N}

3. 房主分配，节点实例主执行
   房主校验 P 的地图绑定、当前位置和相邻节点 → 创建或返回幂等 NodeInstance
   房主 → P 的 NodeInstanceHost: node_instance_allocate
   节点实例主生成内容并仅向 P 同步 node_instance 和内容
```

#### 再标注

同一玩家重复发送 `room_pin` 即覆盖旧标注。队长经房主广播的 `room_pins` 中包含本队最新标注。若小队成员多次标注仍未达共识，无超时——不走自动选择，等待本队一致。

#### 与 MapHost/房主/队长角色的关系

- **队长**：聚合本队标注与检测共识，不执行节点内容逻辑
- **房主**：校验路径、分配/恢复 NodeInstance，并路由到本队节点实例主
- **节点实例主**：收到 `node_instance_allocate` 后在本地执行节点生成，并向该小队同步内容
- **MapHost**：不接收 `room_consensus`，也不参与节点进入

#### 命令入口

`crossspire room <index>` — 标注下一个房间，重复标注即覆盖。该命令构造 `room_pin` 发往房主。

## 小队中央队列：战斗流程

### 核心设计

每个小队队长维护该小队唯一中央队列；房主只在成员、队长和图主之间路由：

```
1. 小队成员提交卡牌 → 发 queue_submit 到房主 → 本队队长
2. 队长收到 → 根据 timestamp + sender_id 排序插入本队中央队列
3. 队长经房主向本队广播 queue_update（当前队列快照）
4. 队长从队列头部取出一项：
   a. 经房主发 invoke 到卡牌所有者（如果所有者=队长自己，直接本地执行）
   b. 等待所有者回传 invoke_result
   c. 经房主将执行结果广播给本队（combat_result）
   d. 重复直到队列为空
5. 队列清空后才允许结束回合
```

### 协议消息（使用标准包）

队列相关消息是标准包（参见 §17 协议消息定义）中 `operation` 为 `queue_submit` / `queue_update` 的实例。在此仅列出 payload 字段：

```typescript
// operation = "queue_submit" 的 payload
interface QueueSubmitPayload {
  party_id: string;
  card_id: string;
  target: string;          // 游戏内目标 "monster_0" | "player_b" | "self"
}

// operation = "queue_update" 的 payload
interface QueueUpdatePayload {
  party_id: string;
  entries: QueueEntry[];   // 完整队列快照
}

interface QueueEntry {
  packet_id: string;       // sender_id + seq (从标准包头部提取)
  source: string;          // 提交者 (从标准包头部提取)
  owner_id: string;        // 卡牌所有者 (从标准包头部提取)
  card_id: string;
  target: string;
  status: "pending" | "executing" | "done";
}
```

标准包头部提供的字段（`packet_id`, `source`, `owner_id`, `resource_hash`, `timestamp`）与 payload 组合成完整的信息，不再在 payload 中重复。

### 调度流程

```
小队中央队列调度:

   P 队列: [Strike_R(A), Defend_G(B), Bash_R(leader)]

  1. 取出 Strike_R(A):
      owner_id = A，不是队长
      → 队长 → 房主 → A: invoke(Strike_R)
     → A 本地执行，触发钩子
      → A → 房主 → 队长: invoke_result(effects)
      → 队长 → 房主 → P: combat_result(effects)

  2. 取出 Defend_G(B):
      owner_id = B，不是队长
      → 队长 → 房主 → B: invoke(Defend_G)
     → B 本地执行，触发钩子
      → B → 房主 → 队长: invoke_result(effects)
      → 队长 → 房主 → P: combat_result(effects)

   3. 取出 Bash_R(leader):
      owner_id = leader == 队长自己
      → 队长本地执行，触发钩子
      → 队长 → 房主 → P: combat_result(effects)
```

### 诱导重放（local-owner-only）

队长经房主向本队广播 `combat_result` 后，接收方按 `executor_id`（原始 REAL 执行者，**不得**被改写为队长或房主 ID）分流：

```
队长 → 房主 → P: combat_result {
  executor_id: A,
  operation_sequence: [...],
  effects: [...]   // apply_power 含 logic_owner_id
}

接收方:

  executor_id == 自己:
    → REAL 已执行 → 跳过 apply，仅 UI/队列状态

  executor_id != 自己:
    1) AUTHORITATIVE_APPLY (suppressEvents)
       → 写 damage/block/heal/… 与 power 层数投影
       → 登记 ComponentAttachment（含 logic_owner_id）
       → 不触发非本地所有权 buff 回调

    2) LOCAL_OWNER_ONLY
       → 仅执行 logic_owner_id == self 的遗物/buff/注册组件
       → 禁止无门控全量 useCard / publishOnCardUse 让所有本地 patch 再算一遍
       → 若本地被动改自己的玩家状态 → 本地权威写 + 广播
        → 若本地被动改怪物 → monster_mutation_proposal → NodeInstanceHost（经房主）
        → 新 effects 带 origin_owner_id + hop_count，提交本队队长队列尾部
```

**关键禁令**：
- 不存在“NodeInstanceHost/房主扫描 attachment 再远程 invoke 灾厄所有者”
- 不存在“有兼容定义的节点都可跑该 buff”
- 非 `logic_owner_id` 节点上同名 power：**无效果、不可被触发**
- commit/权威 apply 的写入不得再次被捕获为 proposal

诱导重放产出的**本地所有者**新效果进入本队队长中央队列尾部，保证顺序；怪物核心变更必须等 NodeInstanceHost commit。

### 玩家视角

1. **提交卡牌**：卡牌从手牌悬浮至队列区域 → 经房主发 `queue_submit` 到本队队长 → 等待
2. **继续操作**：卡牌已离手，可继续打牌加入队列
3. **等待执行**：队长逐个调度队列中的项，处理到自己的项时播放动画
4. **回合结束**：队列全部清空后队长经房主向本队广播 `queue_empty`，本队成员可结束回合

### 队列可视化

队长经房主向本队广播 `queue_update`，本队成员显示相同队列 UI：
- 队列项按顺序排列，显示提交者、卡牌名、状态（等待中/执行中/已完成）
- 执行中的项高亮
- 自己的项排队时可以看自己在第几个

## Buff/Power 所有权与自发触发

### 所有权规则

| 规则 | 定义 |
|------|------|
| 默认 | `logic_owner_id = 施加者`（`apply_power` 时写入） |
| 掉线回退 | content-hash 可本地执行者 → 宿主权威（怪物 host→NodeInstanceHost；玩家 host→该玩家） |
| 投影 | 非 logic_owner 节点可渲染层数/图标；**回调 no-op，不可被触发** |
| 执行 | 房主阶段/combat 事实对齐后，**仅 logic_owner 在本地阶段自发执行** |

不存在图主扫描 attachment 再 `invoke` 所有者的调度路径。

### 数据模型

```
ComponentAttachment {
  instance_id
  resource_id / resource_hash
  logic_owner_id          // 施加者优先
  host_entity_id          // player:<id> | monster:<instance_id>
  amount / power_id
}
```

### 触发路径

```
房主广播阶段或 combat_result 事实
        ↓
各客户端本地进入对应 STS 阶段 / 收到事实
        ↓
仅 logic_owner_id == self 的 attachment 在本地 hook 中自发执行
        ↓
  改自己 → 玩家权威写入 + player_state / effects
   改怪物 → monster_mutation_proposal → NodeInstanceHost commit → 全员 AUTHORITATIVE_APPLY
```

### 灾厄示例

```
1. A 对怪物 M 施加灾厄 → effects.apply_power { logic_owner_id: A, host: monster:M }
2. 房主同步阶段（如 queue_empty + 全员 end_turn 对齐 → 本地进入 pre_monster_turn）
3. 仅 A 机器上灾厄逻辑自发执行；B/C/图主上同名投影无效果
4. A 判定层数 >= HP → proposal { kill / set_monster_hp }
5. 图主 apply 死亡生命周期 → commit { dead: true, revision++ }
6. 全员只写 commit，不再跑灾厄逻辑
```

### 与诱导重放的关系

- `combat_result` 提供**事实**与权威数值，不是“在每个客户端重算所有被动”的许可
- 本地 owner-only 被动是跨 Mod 兼容的正确路径：只要求施加者安装定义
- hop_count / origin_owner_id 防止 induced 副作用环

## 小队战斗阶段同步

**队长**是其小队战斗阶段对齐的唯一协调者（图主不负责点名 buff，房主只路由）。

| 信号 | 谁发 | 本地后果 |
|------|------|----------|
| `combat_result` | 队长→房主→本队 | AUTHORITATIVE_APPLY + local-owner-only 被动 |
| `queue_empty` | 队长→房主→本队 | 允许结束回合；阶段门控 |
| `player_end_turn` 聚合 | 玩家→房主→队长，队长协调 | 对齐进入怪物回合侧本地阶段 |
| `combat_phase` | 队长→房主→本队 | 显式枚举：`player_turn` / `resolving_queue` / `queue_empty` / `pre_monster_turn` / `monster_turn` / `post_monster_turn` |

客户端在收到本队队长阶段信号后**跟本地引擎**推进；buff 在本地时机自发，不由队长或图主远程 invoke。所有这类包必须带 `party_id`。

## 怪物回合

怪物**核心状态**（HP/格挡/死亡/生成/AI 意图与 `takeTurn`）由当前 NodeInstanceHost 持有。附着在怪物上的 **buff 逻辑**仍归施加者（见上一节）。

回合分三个阶段：

### 阶段一：意图确定（回合开始时，玩家回合之前）

```
1. NodeInstanceHost 用本地 RNG 确定所有未死亡怪物的意图
2. NodeInstanceHost → 房主: monster_intent（全部怪物意图快照）
3. 房主仅向该 party_id 广播 → 渲染意图图标（禁止客户端 createIntent 重算 AI）
4. 玩家据此规划出牌、评估威胁
```

### 阶段二：玩家回合

玩家提交卡牌到本队队长中央队列，队长调度执行（参见"小队中央队列"章节）。期间 local-owner buff 可响应本队 `combat_result` 事实。

### 阶段三：怪物动作执行（玩家回合结束后）

```
1. 房主阶段对齐后，NodeInstanceHost 遍历未死亡怪物，本地执行 takeTurn / AI
2. 收集节点实例主侧权威效果 → NodeInstanceHost → 房主: combat_result
3. 房主仅向该 party_id 转发 → AUTHORITATIVE_APPLY（suppressEvents 写数值）
4. 若意图变更 → NodeInstanceHost 经房主广播 monster_intent
5. 各端 local-owner buff 若在该阶段有自发逻辑 → 各自执行；改怪物走 mutation
```

怪物 **AI 动作**由 NodeInstanceHost 本地执行。节点实例主和房主分离时消息经房主转发一次。

### 实现：HP 增量法（绕过 takeTurn 抽象方法限制）

`AbstractMonster.takeTurn()` 为抽象方法，MTS 无法注入。采用 `@SpirePatch` 钩子链捕获图主侧净效果（实现细节可演进为完整 operation_sequence）：

```
BeforeTurn: 采样玩家 HP/Block
AfterMonsterTurns: 差值 → combat_result(damage/gain_block 等)
```

### 怪物状态 mutation（提案/提交）

非 NodeInstanceHost 不得直接把本地怪物投影上的修改当作最终状态广播。

```
logic_owner 本地执行 buff/卡牌副作用
  → 捕获对 monster_instance_id 的语义修改 + 可选 before/after
  → monster_mutation_proposal { base_revision, effects, transaction_id }
   → 房主路由 → NodeInstanceHost
   → NodeInstanceHost CAS revision → apply → 死亡/分裂等生命周期
  → monster_mutation_commit { commit_revision, state }
  → 房主广播 → 全员投影覆盖
```

过期 `base_revision` → reject + 最新快照，不得在旧状态上继续算。

**后续（不阻塞本契约）**：策反等需 `turn_directive`（改 takeTurn 目标），不单靠状态 mutation。

## 事件处理

事件遵循**批准后的就近执行**。本地存在同一事件类且 `resource_hash` 匹配的玩家直接运行原生事件 UI；选择在 `buttonEffect` 产生副作用前被拦截，只有房主批准后，选择者才继续本地执行。此路径不使用 sandbox 或 transcript。

### 事件职责和执行路径

```
NodeInstanceHost: 生成 event_instance_id、事件内容和 resource_hash；决定本队节点内的共享后果
房主: 路由并校验/批准选择；按 party_id 聚合 voting 选择；记录每位玩家结果
本地匹配选择者: 原生渲染 UI → 请求批准 → 获批后本地 buttonEffect → 上报个人结果
本地不匹配选择者: RemoteEventDisplay → 请求批准 → 图主执行并定向应用个人结果
队长: 只管理本队投票、队列和阶段，不重写节点实例主的事件内容权威
```

### event_interface 与内容校验

NodeInstanceHost 进入事件节点时向所在小队广播：

```
event_interface {
  event_instance_id, party_id,
  event_class, event_id, resource_hash,
  name, description, options[], mode,
  image_ref?, phase_key?
}
```

接收端用 `Class.forName(event_class)` 定位本地定义，并比较 `resource_hash`。两者都匹配才调用 `onEnterRoom()` 并原生渲染事件。否则显示 `RemoteEventDisplay`；fallback 仍参与相同的批准协议，不尝试在不匹配内容上 sandbox 试跑。

### 原生选择请求与批准

`AbstractEvent.buttonEffect(int)` 与后续原生选牌/确认 UI 的 patch 在副作用前拦截操作。每一步都有稳定的 `request_id` 和 `ui_step`，使重复包、过期批准和并发选择可被拒绝或幂等处理。

原版事件的首个 gate 放在 `AbstractEvent.update()` 内对 `buttonEffect(int)` 的共享调用点：只有已绑定到已验证 `event_interface` 的本地事件实例会被拦截。首次调用冻结该 dialog 并发送 request；同一 `event_instance_id`、`request_id`、`ui_step` 和选项的批准只能放行一次；拒绝或不匹配批准不能执行副作用，拒绝后恢复输入。未绑定事件、单人和旧协议路径保持原生行为。

event 节点打开时，匹配客户端用 `Class.forName(event_class)` 构造事件并进入 `EventRoom`，在 `onEnterRoom` 后绑定 approval gate；hash 或类不匹配则显示 `RemoteEventDisplay`，不尝试 sandbox。打开原生事件期间抑制旧 legacy `event_interface` 广播。图像事件、选牌/目标后的额外 UI step 与 fallback NIH 执行不属于该首个 gate。

```
1. 匹配客户端选择按钮、卡牌或目标
2. patch 暂停本地事件流程，发送 event_choice_request 给房主
3. 房主校验 event_instance_id、party_id、成员资格、事件 hash、选项、UI step 和 request_id
4. 非 voting：每位玩家的有效选择独立批准
   voting：队长/房主按 party_id 聚合；小队全员一致才批准
5. 收到 event_choice_approved 的匹配客户端恢复原生流程并执行 buttonEffect
6. 客户端发送 event_player_result（个人状态差量、完成状态和可选共享后果）
7. 房主记录结果，并向本小队广播该玩家 player_state/差量投影
```

普通个人收益（HP、金币、牌、遗物、药水）由获批选择者本地执行。个人结果不要求同小队玩家做出同样选择；其他成员只收到该玩家的状态投影。

### 共享世界后果与事件内房间

MapHost 只拥有不可变地图定义；NodeInstanceHost 是本队房间内容和怪物核心状态的唯一权威。`event_player_result` 不能直接改变这些共享对象：

- 事件内战斗或特殊房间由 NodeInstanceHost 创建稳定 `instance_id`。选择相同产生房间选项的成员进入同一小队路径；队长管理该路径的队列和阶段，NodeInstanceHost 生成/确认内容。
- 直接离开事件去地图上其他节点不是个人事件结果。成员必须先 `party leave`，保留当前节点；新单人小队或已加入的小队再做本队地图共识。
- 跨小队相遇、合并和共享战斗是显式未来工作，不由 event approval 隐式触发。

### Fallback 执行

无本地匹配定义的成员在 `RemoteEventDisplay` 选择后仍发送 `event_choice_request`。获批后，NodeInstanceHost 代表该玩家执行/解析该选项，产生的个人结果定向应用到该玩家，并经房主同步其状态差量。这样 fallback 不要求选择者拥有 Mod 事件实现。

### 事件消息

```
event_interface        NodeInstanceHost→房主→本队
event_choice_request   选择者→房主
event_choice_approved  房主→选择者（voting 批准可广播本队）
event_choice_rejected  房主→选择者
event_votes             队长→房主→本队（仅 voting）
event_player_result     选择者/图主→房主→本队
```

## 所有者交互选择

当图主或卡牌所有者在执行过程中需要玩家进行交互选择（如选牌、选择目标等），通过捕获 BaseMod 界面并回传给发起方实现。

### 适用场景

| 场景 | 举例 | 所有者 |
|------|------|--------|
| 卡牌效果要求选牌 | "选择手牌中一张牌丢弃"、"选择牌堆中一张牌加入手牌" | 卡牌所有者 |
| 遗物效果要求选择 | "选择一张攻击牌升级"、"选择一种药水丢弃" | 遗物所有者（通常也是卡牌所有者所在机器） |
| 药水效果要求选择 | "选择一张卡牌消耗" | 药水所有者 |
| 事件要求选牌 | "选择一张卡牌移除" | NodeInstanceHost |

### 交互选择流程

```
1. 所有者在执行 invoke 过程中，触发了需要交互的逻辑
   → 通过 @SpirePatch 拦截 GridCardSelectScreen / HandCardSelectScreen 等选择界面
   → 捕获选择参数: { select_type, valid_cards[], prompt_text, min_select, max_select }

2. 所有者 → 房主: interact_request {
     invoke_id,                    // 关联的 invoke 请求
     select_type,                  // "card_select" | "hand_select" | "grid_select" 等
     options: [...],               // 可选对象列表（card_id, relic_id, potion_id 等）
     prompt_text,
     min_select,
     max_select
   }

3. 房主转发 → 原始调用方（发起这个 invoke 的玩家）

4. 调用方显示选择 UI（本地渲染选择界面，复用游戏原生的选择控件）

5. 调用方选择完毕 → 房主: interact_response {
     invoke_id,
     selected: [...]               // 略过选中的对象 ID 列表
   }

6. 房主转发 → 所有者

7. 所有者收到选择结果 → 继续执行 invoke 的剩余逻辑 → 产出 invoke_result
```

交互选择标准包的 payload 结构见 §17 协议消息定义。

### 超时处理

若调用方在超时时间内未响应用交互请求，所有者按默认策略处理（取第一个选项 / 随机选择 / 取消交互），并通过房主通知调用方交互已超时。

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

引用系统使交叉所有权变得自然，但**被动逻辑只在所有者节点执行**：

```
A 打出 B 的远程卡牌 Strike_P:
  A 提交到房主队列 → 房主调度 → 调用 B 执行 Strike_P（REAL @ B）
  若 Strike_P 对玩家施加能力 → apply_power.logic_owner_id = B（施加者）
  房主广播 combat_result → 全员 AUTHORITATIVE_APPLY 写层数投影

B 的卡牌事实触发 A 的本地遗物（logic_owner = A）:
  B REAL 执行 → combat_result
  A：AUTHORITATIVE_APPLY 写卡牌效果数值
  A：LOCAL_OWNER_ONLY 仅执行 A 自己的遗物/buff
  B/其他人：不执行 A 的遗物逻辑
```

远程对象的**逻辑**仍经引用对所有者 REAL 解引用；本地仅 owner 的被动可在收到事实后自发执行。禁止“全员诱导 = 全员跑被动”。

### 自定义遗物 / 怪物 buff（local-owner-only）

```
场景: A 有自定义遗物（打出攻击牌时生效），B 没有该 mod

1. B 打出攻击牌 → REAL @ B
2. 房主广播 combat_result（executor_id = B）
3. A：AUTHORITATIVE_APPLY 写 B 的伤害等数值
4. A：LOCAL_OWNER_ONLY → 仅 A 的遗物执行 → 新 effects → 房主
5. 全员对 A 的新 effects 做 AUTHORITATIVE_APPLY
   B 不执行 A 的遗物代码 ✓
```

灾厄类怪物 buff：

```
1. A 施加灾厄 → logic_owner_id = A
2. 房主阶段对齐 → 本地进入触发阶段
3. 仅 A 自发执行灾厄 → proposal → 图主 commit
4. 非 A 节点同名投影无效果、不可触发
```

**已否决**：NodeInstanceHost INDUCED 全量 useCard 以“自动”跑怪物 power；非所有者有定义即本地执行。

### Fallback 效果类型

| kind | 说明 | 关键字段 |
|------|------|----------|
| `damage` | 造成伤害 | `target`, `amount`, `damage_type` |
| `gain_block` | 获得格挡 | `target`, `amount` |
| `apply_power` | 施加能力 | `target`, `power_id`, `amount`, **`logic_owner_id`** |
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
| `set_monster_hp` | （计划）权威设怪物 HP | `target`, `amount` |
| `monster_death` | （计划）权威怪物死亡 | `target` |

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
- 非发送者 `combat_result` → **AUTHORITATIVE_APPLY** 全程 suppressEvents 写数值/VFX/层数投影
- **LOCAL_OWNER_ONLY** 执行本地所有者被动时**不**开全局 suppress（否则 owner 被动无法改状态）；但其对怪物的修改走 proposal，对权威 apply 的二次捕获必须门控
- 在线角色/怪物投影状态写入（HP、commit 覆盖）时 suppressEvents 包裹
- REAL 在所有者远端触发钩子 → 结果经房主回来 → 他人只做权威 apply + 自己的 local-owner 被动

### 引用中的钩子触发规则

| 场景 | 是否触发钩子 | 原因 |
|------|-------------|------|
| 所有者解引用自己的对象（REAL 模式） | 是 | 计算源点 |
| 非发送者 AUTHORITATIVE_APPLY | 否（suppressEvents） | 数值已由 executor 计算 |
| 非发送者 LOCAL_OWNER_ONLY（logic_owner==self） | 是（仅这些组件） | 施加者/所有者自发执行 |
| 非发送者上他人 buff 投影 | 否 | 无效果、不可触发 |
| NodeInstanceHost 收到他人 combat_result | 否全量 hook；节点实例主自有 logic_owner 组件可跑 | 禁止 INDUCED 自动跑全部怪物 power |
| commit 覆盖怪物投影 | 否（suppressEvents） | 不得再捕获为 proposal |

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

素材发送方**不直接从内存提取** Spine 数据（序列化复杂、版本依赖）。改为**按字符→路径映射表**从原始文件读取并传输：

```java
// RemoteAssetServer.java — 字符→路径映射
CHAR_SKELETON_PATHS.put("IRONCLAD", "images/characters/ironclad/idle/skeleton.json");
CHAR_ATLAS_PATHS.put("IRONCLAD",   "images/characters/ironclad/idle/skeleton.atlas");
CHAR_PNG_PATHS.put("IRONCLAD",     "images/characters/ironclad/idle/skeleton.png");

// 读取原始文件并通过 Gdx.files.internal() 获取字节
FileHandle fh = Gdx.files.internal(path);
String skeletonJson = fh.readString("UTF-8");
// ... Base64 编码后传输
```

素材接收方从磁盘缓存重建：`SkeletonJson.readSkeletonData(jsonFile)` + `TextureAtlas(FileHandle)` → Skeleton。

纹理数据通过 `Pixmap` 从 `Gdx.files.internal().png` 读取后编码为 PNG。

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

素材消息使用标准包格式（§17），用于在玩家之间传递 Mod 卡牌/遗物/角色等视觉素材。素材通过 `owner_id` 路由到来源方或请求方。

| operation | 方向 | 说明 |
|-----------|------|------|
| `resource_registry` | C→房主→全员 | 连接时发送本地拥有的素材清单 |
| `resource_request` | C→房主→特定C | 请求特定素材 (由 `owner_id` 路由) |
| `resource_response` | 特定C→房主→请求方 | 素材数据响应 |
| `animation_sync` | C→房主→全员 | 角色动画切换通知 |

以下为各 operation 的 payload 结构（标准包头部字段 `source`、`owner_id`、`ref_id` 不重复列出）：

```typescript
// resource_registry payload
interface ResourceRegistryPayload {
  cards: string[];        // 本地 CardLibrary 拥有的 card_id
  relics: string[];       // 本地 RelicLibrary 拥有的 relic_id
  powers: string[];       // 本地拥有的 power_id
  potions: string[];      // 本地拥有的 potion_id
  characters: string[];   // 本地拥有的角色骨架
}

// resource_request payload
interface ResourceRequestPayload {
  resource_type: "card_texture" | "card_large" | "card_small"
               | "relic_texture" | "power_icon48" | "power_icon84"
               | "potion_texture" | "character_skeleton"
               | "character_atlas" | "character_png";
  resource_id: string;    // 如 "Strike_R" 或 "IRONCLAD"
}

// resource_response payload
interface ResourceResponsePayload {
  resource_type: string;
  resource_id: string;
  mime: string;           // "image/png" | "application/json" | "text/plain"
  data_base64: string;
  checksum: string;       // SHA-256，用于缓存校验
}

// animation_sync payload
interface AnimationSyncPayload {
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
- 对 NodeInstanceHost 怪物/事件的远程引用（渲染用）
- 对自身遗物/能力的远程引用（被动效果触发用）

## RNG 同步策略

在引用模型中，执行权归属所有者——谁执行，就用谁的 RNG。结果通过广播传递，其他玩家不需要复现随机过程。

### 原则

```
每玩家维护独立本地种子。

RNG 归属 = 执行者:

   地图生成   → MapHost 本地 RNG → 登记不可变 MapDefinition
   问号/房间生成 → NodeInstanceHost 本地 RNG + 生成修正器 → node_generation_commit
   怪物意图   → NodeInstanceHost 本地 RNG → 本队广播
   怪物 AI/伤害 → NodeInstanceHost 本地 RNG → 本队广播 combat_result
  在线角色抽牌 → 远端所有者本地 RNG → 推送
  卡牌效果   → 卡牌所有者本地 RNG → 广播
  随机遗物 (Dead Branch) → 本地 RNG → 结果同步

MapHost 的地图结果经 MapRegistry 提供；NodeInstanceHost 的 RNG 结果只向本队通过 node-instance 消息 / combat_result 广播。
本地决策 RNG 结果通过 player_state / combat_result 同步给全员。
不同玩家的 RNG 互不干扰。
```

### 与传统"共享种子"方案的对比

| | 共享种子 | 独立本地 RNG |
|--|---------|------------|
| 核心假设 | 所有人本地复现相同随机序列 | 谁执行谁 RNG，结果广播 |
| 对 Mod 差异的容忍 | 极低—卡组差异导致卡牌奖励 RNG 分叉 | 完全容忍—不在他人机器上复现 |
| 网络依赖 | 种子同步一次 | 每次随机的广播 |
| 确定性 | 要求全局确定 | 不要求—结果就是广播数据 |
| 实现复杂度 | 需保证所有端点卡组/状态全同 | 简单——各有各的 RNG |

独立本地 RNG 天然与所有权模型一致，且对 Mod 差异极度鲁棒。MapHost 的地图登记和 NodeInstanceHost 的本队同步覆盖了所有非本地决策。

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

## 协议消息定义

### 标准包（Standard Packet）

本 Mod 中所有引用对象（卡牌/遗物/怪物/事件/玩家/素材）的网络传输统一封装为标准包。控制消息（心跳、加入/离开、投票等）不属于标准包，保持独立格式。

```typescript
interface StandardPacket {
  // ===== 固定头部（所有标准包共有）=====
  packet_id: string;       // source + seq，唯一标识
  source: string;          // 发送方 ID
  seq: number;             // 单调序列号
  timestamp: number;       // 发送时间戳

  // ===== 引用标识（所有引用传输共有）=====
  ref_id: string;          // 引用唯一标识 "card:Strike_R@playerA"
                           //   "monster:Cultist@stageHost"、"event:BigFish@stageHost"
                           //   "player:playerB@playerB"、"relic:Vajra@playerA"
  owner_id: string;        // 引用所有者 ID（房主据此路由）
  resource_hash?: string;  // 内容校验哈希（SHA-256 of resource bytecode）

  // ===== 操作 =====
  operation: PacketOperation;

  // ===== 操作载荷 =====
  payload: any;
}

// operation 枚举
type PacketOperation =
  // 阶段过渡 / 地图与节点目录
  | "stage_transition_open" | "map_host_vote" | "map_host_result"
  | "map_register" | "map_registered" | "map_join_request" | "map_joined"
  | "node_instance_host_vote" | "node_instance_host_result" | "stage_transition_complete"
  | "node_instance_allocate" | "node_generation_commit" | "node_instance_opened"
  // 队列
  | "queue_submit" | "queue_update" | "queue_empty"
  // 引用/调用
  | "invoke" | "invoke_result" | "reference_register" | "reference_migrate"
  // 战斗/状态
  | "combat_result" | "monster_intent" | "player_state" | "full_snapshot"
  // 事件
   | "event_interface" | "event_choice_request" | "event_choice_approved"
   | "event_choice_rejected" | "event_player_result" | "event_result" | "event_votes"
   // 小队
   | "party_snapshot" | "party_leave_request" | "party_join_request"
   | "party_join_approved" | "party_join_rejected" | "party_leader_changed"
  // 素材
  | "resource_registry" | "resource_request" | "resource_response" | "animation_sync";
```

### 操作载荷定义

各 `operation` 的 `payload` 仅包含该操作独有的字段。引用信息（`ref_id`, `owner_id`, `resource_hash`）、路由信息（`packet_id`, `source`, `seq`, `timestamp`）均从标准包头部提取，不重复。

#### 队列操作

```
operation: "queue_submit"
  payload: { party_id: string, card_id: string, target: string }
  说明: 小队成员经房主提交卡牌到本队队长中央队列

operation: "queue_update"
  payload: { party_id: string, entries: QueueEntry[] }
  说明: 队长经房主向本队广播当前队列快照

interface QueueEntry {
  packet_id: string;      // 从源头标准包提取
  source: string;         // 提交者
  owner_id: string;       // 卡牌所有者
  card_id: string;
  target: string;
  status: "pending" | "executing" | "done";
}

operation: "queue_empty"
  payload: { party_id: string }
  说明: 本队队列清空，本队成员可结束回合
```

#### 阶段过渡、地图与节点实例操作

```
stage_transition_open
  payload: { party_id, act_id, party_revision }
  说明: 房主为完成当前地图的小队打开独立过渡；过渡不属于地图或节点实例。

map_host_vote | node_instance_host_vote
  payload: { party_id, candidate_id, party_revision }
  说明: 房主只在目标小队内聚合一致投票。MapHost 仅在创建新地图时选；每个绑定地图的小队都选唯一 NodeInstanceHost。

map_register
  payload: { party_id, map_host_id, request_id, map: MapDefinition }
  说明: MapHost 向房主登记一次不可变地图。登记成功后 MapHost 不承担节点内容职责。

map_join_request
  payload: { party_id, map_instance_id, request_id, party_revision }
  说明: 小队绑定同阶段已登记地图；本队随后仍必须选自己的 NodeInstanceHost。

node_instance_allocate
  payload: { node_instance: NodeInstanceInfo, request_id }
  说明: 房主校验路径后路由给本队节点实例主。键为 (map_instance_id, party_id, node_id, visit_id)，不同小队不得共用。

node_generation_commit
  payload: { node_instance_id, generation_revision, generation_result, modifier_state_delta }
  generation_result: { room_type: "monster", encounter, node_id }
                   | { room_type: "event", event_interface, node_id }
  说明: 节点实例主原子提交问号解析/房间内容与生成遗物状态变化；重试不能再次消耗 RNG 或修正器计数。`event_interface` 只在 RoomHost 打开 event 实例后由 NodeInstanceHost 发布。
```

#### 引用/调用操作

```
operation: "invoke"
  payload: {
    trigger: string;      // 触发接口 onCardUse / atBattleStart / onPlayerDamaged / ...
    args: any;            // 调用参数
  }
  说明: 远程引用解引用时的网络调用（C→房主→owner）

operation: "invoke_result"
  payload: {
    effects: EffectDescription[];          // 纯数值效果
    operation_sequence: OperationStep[];   // 操作序列（用于 INDUCED 重放）
  }
  说明: 远程执行结果回传（owner→房主→C）

operation: "reference_register"
  payload: {
    available: { ref_id: string, resource_hash: string }[];
  }
  说明: 宣告自己可提供的引用对象清单（连接时发送）

operation: "reference_migrate"
  payload: {
    new_owner_id: string;  // 迁移目标
  }
  说明: 引用转移请求
```

#### 战斗/状态操作

```
operation: "combat_result"
  payload: {
    effects: EffectDescription[];  // apply_power 含 logic_owner_id
    operation_sequence: OperationStep[];
    card_id?: string;
    monster_id?: string;
    turn_transaction_id?: string; // monster_turn 完成时必须匹配当前 monster_turn phase
    executor_id: string;           // 原始 REAL 执行者，队长/房主不得改写
  }
  说明: 队长→房主→本队。`monster_id=monster_turn` 仅接受当前 NodeInstanceHost、当前 phase transaction 且未处理过的完成结果；接收方 AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY，禁止全量 hook 重算

operation: "combat_phase"
  payload: {
    party_id: string;
    phase: "player_turn" | "resolving_queue" | "queue_empty"
         | "pre_monster_turn" | "monster_turn" | "post_monster_turn";
    transaction_id?: string;
  }
  说明: 队长→房主→本队阶段对齐；客户端跟本地引擎；不远程点名 buff

operation: "monster_mutation_proposal"   // 计划
  payload: MonsterMutationProposal
  说明: Peer→当前 NodeInstanceHost（经房主）：非节点实例主对怪物核心状态的提案

operation: "monster_mutation_commit"   // 计划
  payload: MonsterMutationCommit
  说明: NodeInstanceHost→本队（经房主）：权威怪物状态

operation: "monster_mutation_reject"   // 计划
  payload: { transaction_id, monster_instance_id, reason, commit_revision? }
  说明: 图主→提案方：过期/非法

operation: "monster_intent"
  payload: {
    monsters: { monster_id: string, intent: string }[];
  }
  说明: NodeInstanceHost 意图快照经房主仅向本队广播；客户端禁止 createIntent 重算

operation: "player_state"
  payload: {
    hp: number; max_hp: number; current_block: number;
    energy: number; energy_per_turn: number;
    hand_size: number; draw_pile_size: number; discard_pile_size: number;
    powers: { power_id: string, amount: number, logic_owner_id?: string }[];
    relics: string[];
    potions: (string | null)[];
  }
  说明: 在线角色属性更新

operation: "full_snapshot"
  payload: {
    players: Record<string, PlayerState>;
    maps: MapDefinition[];
    parties: {
      party_id: string; leader_id: string; member_ids: string[];
      phase_status: "stage_transition" | "map_active" | "active_node" | "map_completed";
      act_id: string; map_instance_id?: string; map_position?: string;
      node_instance_host_id?: string; active_node_instance_id?: string; party_revision: number;
    }[];
    active_node_instances: NodeInstanceSnapshot[];
  }
  说明: 房主目录快照（房主启动/迁移时）；MapDefinition 来自 MapRegistry，活动节点快照来自各自 NodeInstanceHost；应含 attachment 列表（计划）
```

#### 小队操作

```
operation: "party_snapshot"
  payload: {
    parties: PartyInfo[];
  }
  说明: 房主的小队玩法目录快照；包含小队过渡、地图绑定和节点实例主，不含跨小队可见性或队列内容。

operation: "party_leave_request"
  payload: { party_id: string; }
  说明: 成员请求离开当前小队；成功后创建单人小队并保留当前地图节点。

operation: "party_join_request"
  payload: { party_id: string; request_id: string; }
  说明: 房主路由给目标队长，队长用 party_join_approved/rejected 回应。

operation: "party_join_approved" | "party_join_rejected"
  payload: { party_id: string; request_id: string; player_id: string; reason?: string; }

operation: "party_leader_changed"
  payload: { party_id: string; leader_id: string; }
  说明: 成员变化或队长掉线后，按成员 ID 字典序确定新队长。
```

所有玩法范围的标准包 (`queue_*`, `invoke*`, `combat_result`, `combat_phase`, `player_end_turn`, `player_state`, `room_*`, `event_*`) 在 payload 或固定扩展字段中携带 `party_id`。资源、心跳和纯房间控制消息不需要 `party_id`。

#### 事件操作

```
operation: "event_interface"
  payload: {
    event_instance_id: string;
    party_id: string;
    event_class: string;        // 全限定类名 "com.megacrit.cardcrawl.events.exordium.BigFish"
    event_id: string;           // 简短 ID "BigFish"
    resource_hash: string;
    name: string;
    description: string;
    options: { index: number, text: string, enabled: boolean }[];
    mode: "individual" | "voting";
    image_ref?: string;         // 素材引用，走 resource_request 获取
    phase_key?: string;         // PhasedEvent 当前阶段
  }
  说明: NodeInstanceHost 向所在小队广播事件接口。D2 仅在类和 hash 匹配时本地实例化。

operation: "event_choice_request"
  payload: {
    event_instance_id: string;
    party_id: string;
    request_id: string;
    ui_step: string;
    option_index: number;
    selected_cards?: string[];
    selected_targets?: string[];
    resource_hash: string;
  }
  说明: 原生 buttonEffect/选牌 UI 在副作用前请求房主批准。

operation: "event_choice_approved" | "event_choice_rejected"
  payload: {
    event_instance_id: string;
    party_id: string;
    request_id: string;
    ui_step: string;
    reason?: string;
  }
  说明: individual 事件独立批准每个有效请求；voting 仅在本队达成一致后批准。

operation: "event_player_result"
  payload: {
    event_instance_id: string;
    party_id: string;
    request_id: string;
    player_id: string;
    player_state?: PlayerState;
    effects?: EffectDescription[];
    shared_outcome?: { type: "event_room" | "leave_party"; instance_id?: string; option_index: number; };
  }
  说明: 匹配选择者本地执行后的个人结果，或 fallback 时图主定向执行的结果。

operation: "event_result"
  payload: {
    result_type: "done" | "combat" | "next_phase";
    effects?: EffectDescription[];
    next_phase?: string;
  }
  说明: NodeInstanceHost 确定的本队节点共享事件结果；个人结果使用 event_player_result。
```

#### 事件投票操作 (仅 voting 模式)

```
operation: "event_votes"
  payload: {
    event_instance_id: string;
    party_id: string;
    votes: { [playerId]: { option_index: number, ui_step: string } };
  }
  说明: 队长按 party_id 广播投票状态。全队对同一合法请求一致后，房主批准该请求。
```

#### 素材操作

```
operation: "resource_registry"
  payload: {
    cards: string[]; relics: string[]; powers: string[];
    potions: string[]; characters: string[];
  }
  说明: 本地素材清单（连接时交换一次）

operation: "resource_request"
  payload: {
    resource_type: "card_texture" | "card_large" | "card_small"
                 | "relic_texture" | "power_icon48" | "power_icon84"
                 | "potion_texture" | "character_skeleton" | "character_atlas" | "character_png";
    resource_id: string;
  }
  说明: 请求特定素材（C→房主→特定C，由 owner_id 路由）

operation: "resource_response"
  payload: {
    resource_type: string;
    resource_id: string;
    mime: string;
    data_base64: string;
    checksum: string;       // SHA-256，用于缓存校验
  }
  说明: 素材数据响应

operation: "animation_sync"
  payload: {
    animation: string;
    track: number;
    loop: boolean;
    mix_duration: number;
  }
  说明: 角色动画切换
```

### 控制消息（非标准包）

控制消息不包含引用信息，不封装为标准包，保持独立格式。

```typescript
interface ControlMessage {
  type: string;            // 消息类型
  source: string;          // 发送方 ID
  seq: number;             // 单调序列号
  timestamp: number;       // 发送时间戳
  // ... type-specific fields
}
```

| type | 方向 | 说明 | 额外字段 |
|------|------|------|----------|
| `hello` | C→任意成员 | 加入时宣告身份和网络信息 | `ip, port, player_name` |
| `ping` / `pong` | C↔房主 | 心跳保活 | — |
| `player_joined` / `player_left` | 房主→全员 | 玩家进出通知 | `player_id, player_name` |
| `room_info` | 任意成员→C | 响应 hello，返回房间完整信息 | `name, host, members[], stage_host` |
| `stage_host_election` | 房主→全员 | 新阶段投票发起 | `candidates[]` |
| `stage_host_result` | 房主→全员 | 投票结果 | `host_id` |
| `host_election` | 房主→全员 | 房主掉线后投票选新房主 | `candidates[]` |
| `host_result` | 房主→全员 | 新房主选举结果 | `host_id` |

## 项目结构

```
CrossSpire/
├── ARCHITECTURE.md              # 本文档
├── AGENTS.md                    # 仓库规则 + AI Agent 准则
├── .gitignore
├── shared/                       # 跨语言共享定义
│   └── cross-spire-protocol/
│       ├── protocol-schema.json # JSON Schema 协议定义
│       └── entity-mappings/     # 塔1↔塔2 实体映射表
└── mods/                        # STS1 Mod (Java, ModTheSpire + BaseMod)
    └── cross-spire/
        ├── build.gradle.kts
        ├── README.md
        └── src/main/java/crossspire/
            ├── CrossSpireMod.java              # @SpireInitializer 入口
            ├── EventSuppression.java            # 全局抑制开关
            ├── SuppressBaseModPatches.java      # @SpirePatch 拦截 BaseMod.publish*
            ├── network/
            │   ├── StarConnectionManager.java   # 星型连接管理
            │   ├── RoomHost.java                # 房主目录与路由职责
            │   ├── HeartbeatManager.java        # 心跳检测 + 掉线处理
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
            │   └── StageHost.java              # legacy 单一阶段权威；迁移为 MapHost 身份与按小队 NodeInstanceHost 授权
            ├── combat/
            │   ├── CentralQueueManager.java    # 小队中央队列调度（P7 目标）
            │   ├── LocalCapturePatches.java    # 捕获本地操作 → 提交到房主
            │   ├── CombatResultReplayer.java   # AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY
            │   ├── InteractionCapture.java     # 捕获 BaseMod 选择面板 → interact_request
            │   ├── LocalOwnerGate.java         # 已实现: induced 仅 logic_owner==self
            │   ├── ComponentAttachment.java    # 已实现 (T5.2)
            │   ├── ComponentAttachmentRegistry.java  # 已实现 (T5.2)
            │   ├── CombatTurnOrchestrator.java # 已实现 (P6): 回合阶段状态机
            │   ├── MonsterTurnCapture.java     # 已实现 (P6): HP 增量纯逻辑
            │   ├── MonsterMutationCapture.java # planned (T5.3): 事务化怪物修改捕获
            │   └── MonsterAuthorityCoordinator.java  # planned (T5.3): NodeInstanceHost proposal→commit
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
4. **协议语言无关**：所有消息使用 JSON，Java 和 C# 均可处理。房主路由模式不关心游戏版本
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
