# CrossSpire — 杀戮尖塔联机Mod架构设计

## 目录

1. [设计目标](#设计目标)
2. [关键术语定义](#关键术语定义)
3. [核心思想：双角色投影与所有权模型](#核心思想双角色投影与所有权模型)
4. [就近原则](#就近原则)
5. [图主权威模型](#图主权威模型)
6. [待打出队列：战斗流程](#待打出队列战斗流程)
7. [怪物回合](#怪物回合)
8. [事件处理](#事件处理)
9. [双通道协议：Invoke / Sync](#双通道协议invoke--sync)
10. [BaseMod 事件抑制机制](#basemod-事件抑制机制)
11. [Mod 兼容性设计：Fallback 效果系统与分层同步](#mod-兼容性设计fallback-效果系统与分层同步)
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

在深入架构之前，必须精确定义以下关键操作，所有协议和实现都基于这些术语：

| 术语 | 英文 | 定义 |
|------|------|------|
| **触发** | Trigger | 行为可被钩子(hook)发现并回调。钩子包括本地钩子(BaseMod事件、SpirePatch等)和远程钩子(通过网络订阅的远端事件) |
| **同步** | Sync | 将计算结果和渲染特效从一方传递到另一方，**接收方不触发任何钩子**。用于状态镜像，而非因果传播 |
| **调用** | Invoke | 一方将信息传递给另一方，由被调用方在**自身的Player对象处**执行逻辑、渲染、触发钩子。调用完成后，被调用方将结果**同步**给调用方。调用方和被调用方可以是同一实体（就近原则：图主调用自己退化为纯本地操作） |
| **所有者** | Owner | 某个游戏元素（卡牌、遗物、药水、人物、怪物、地图等）的拥有者，掌握该元素的完整信息和执行逻辑，允许被其他玩家调用 |
| **图主** | Stage Host | 当前阶段（Act + Floor）地图的拥有者，由所有成员投票选定。图主拥有怪物、事件和地图的完整权威，负责编排所有非玩家本地的执行 |
| **待打出队列** | Pending Play Queue | 图主维护的FIFO队列，存放玩家提交的待处理卡牌。队列中的牌按先进先出顺序处理，前一张的效果完全计算并同步完毕后才处理下一张 |

### 操作关系图

```
触发 (Trigger):   Action ──→ Hook (local or remote)
同步 (Sync):      Owner A ──→ Player B   [B does NOT trigger any hooks]
调用 (Invoke):    Caller ──→ Owner ──→ Owner executes locally ──→ Sync result back to Caller
```

触发与同步/调用的核心区别：**触发是一个动作"是否被钩子发现"的开关属性；同步和调用是"执行权和数据流向"的编排模式。**

## 核心思想：双角色投影与所有权模型

每台机器上有**两种角色**：

```
机器A                                    机器B
┌──────────────────────┐                ┌──────────────────────┐
│ 本地角色 (Player A)   │                │ 本地角色 (Player B)   │
│ • 完整本地引擎执行      │                │ • 完整本地引擎执行      │
│ • 玩家实际操作的角色    │                │ • 玩家实际操作的角色    │
│ • 本机Mod生效          │                │ • 本机Mod生效          │
│ • 卡牌/遗物/药水的所有者 │                │ • 卡牌/遗物/药水的所有者 │
├──────────────────────┤                ├──────────────────────┤
│ 在线角色 (Player B投影) │◄──投影───►    │ 在线角色 (Player A投影) │
│ • 独立 AbstractPlayer  │               │ • 独立 AbstractPlayer  │
│ • 接收B的状态同步       │               │ • 接收A的状态同步       │
│ • 纯渲染+状态镜像       │               │ • 纯渲染+状态镜像       │
│ • 不触发本地钩子        │               │ • 不触发本地钩子        │
└──────────────────────┘                └──────────────────────┘
```

**核心原则**：
- 在线角色是**渲染和状态镜像**——它活在本地游戏循环中，拥有手牌、遗物、能力、格挡值的外观副本，能被UI渲染，但其状态由远端"所有者机器"通过同步写入，**不触发任何钩子**。
- 执行权归属**所有者**——每张卡牌、每个遗物的效果计算由拥有该元素的玩家机器执行。图主是调度者，不是执行者（除非图主本身就是该元素的所有者）。

## 就近原则

> 能本地处理就本地处理，不行才由远程所有者处理。

### 计算所有权

| 元素 | 所有者 | 谁执行 |
|------|--------|--------|
| 卡牌效果 | 卡牌所在机器的玩家 | 所有者本地执行，触发本地钩子 |
| 遗物被动效果 | 遗物所在机器的玩家 | 所有者本地执行 |
| 怪物行为（AI、伤害） | 图主 | 图主本地执行（怪物所有者=图主） |
| 地图/事件 | 图主 | 图主本地执行 |
| 药水效果 | 药水所在机器的玩家 | 所有者本地执行 |
| RNG 抽取 | 图主 | 图主用共享种子执行 |

### 调用链优化

当调用方和被调用方是同一实体时（例如图主打出自己拥有的卡牌），"网络调用"退化为纯本地操作，零延迟走最短路径。这是就近原则的自然延伸。

### 分层同步

卡牌所有者执行完毕后，图主将结果同步给其他玩家时采用分层策略：

```
接收方本地有该卡牌定义？
  ├─ 是 → 操作重放（接收方用本地引擎重放卡牌，引擎自动产生 VFX、动画等）
  │       suppressEvents 包裹，不触发钩子
  └─ 否 → 纯数值同步（应用 fallback 效果数值 + 手动渲染通用 VFX）
          接收方只需要理解效果类型（damage/block/power 等），不需要理解原卡牌
```

这与现有 fallback 系统一致，只在同步时机上做了分层决策。

## 图主权威模型

图主不是固定的"房主"，而是按**阶段(Stage)** 选定的逻辑角色。每进入一个新阶段时，所有成员投票选定图主。被选中的玩家成为当前阶段的**地图、怪物和事件的所有者**。

### 图主的三重职责

```
1. 地图权威
   • 生成当前阶段地图（用共享种子）→ 同步给所有人覆盖其原有地图
   • 进入房间时，由图主的决定房间函数确定房间类型及内容：
     - 战斗 → 图主统一怪物配置
     - 事件 → 图主决定具体事件

2. 战斗编排
   • 维护待打出队列 (FIFO)
   • 逐个调度队列中的牌：调用卡牌所有者 → 接收结果 → 同步全员
   • 怪物回合：遍历怪物 → 执行 AI → 同步伤害/意图结果

3. RNG 管理
   • 用共享种子统一生成所有随机抽取
   • 怪物意图/目标、在线角色抽牌结果均由 RNG 确定后推送
```

### 图主调用协议

```
┌──────────────────────────────────────────────────────────┐
│                    图主 (调度者)                           │
│                                                          │
│  待打出队列: [Strike_R(A), Defend_G(B), ...]              │
│                                                          │
│  取出 Strike_R(A) → invoke_card → Player A (所有者)       │
│    │                                                     │
│    │  Player A 本地执行 Strike_R，触发钩子，计算效果       │
│    │  Player A 回传 invoke_result 给图主                  │
│    │                                                     │
│  ← 图主接收结果，更新怪物状态                             │
│  ← 图主广播 state_sync 给其他玩家：                       │
│       • 有该卡牌者：操作重放 + suppressEvents             │
│       • 无该卡牌者：fallback 数值 + 通用渲染              │
│                                                          │
│  重复处理下一张牌...                                       │
└──────────────────────────────────────────────────────────┘
```

### 图主掉线

图主掉线时：
- 自动保存多人进度，等待图主重新上线
- 若等待超时放弃该局——不实现在线迁移
- 在线角色保持最后一刻状态，界面显示"等待图主..."

## 待打出队列：战斗流程

### 玩家视角

1. **提交卡牌**：玩家打出一张牌 → 卡牌从手牌悬浮至队列区域 → 本地卡牌阻塞，不触发本地钩子
2. **继续操作**：卡牌已从手牌移除，玩家可以继续打牌，新牌也进入队列
3. **等待处理**：队列中的牌由远端处理，处理到自己的牌时播放动画/效果
4. **回合结束**：待打出队列清空后，玩家才可以结束回合

### 图主视角

```
待打出队列 (FIFO)

  ┌───────────────────────────────────────────────────────┐
  │  queue_entry 1: { card: Strike_R, source: A, target: Monster_0 }  │
  │  queue_entry 2: { card: Defend_G, source: B, target: self }       │
  └───────────────────────────────────────────────────────┘

  图主处理流程：

  1. 从队列头部取出一项
  2. invoke_card → 卡牌所有者 (可能是图主自己)
     如果所有者=图主 → 直接本地执行，跳过网络
     如果所有者≠图主 → 网络调用，等待回复
  3. 所有者执行卡牌，计算完整效果，触发本地钩子
  4. 所有者回传 invoke_result（效果数值 + 操作描述）
  5. 图主应用结果到共享状态（怪物HP等）
  6. 图主广播 state_sync 给全员：
     - 分层同步（有卡牌→操作重放，无卡牌→fallback数值）
  7. 重复直到队列为空
```

### 队列协议消息

```typescript
// 玩家提交卡牌到队列 → 图主
interface QueueSubmit {
  type: "queue_submit";
  source: string;
  seq: number;
  card_id: string;
  upgraded: boolean;
  target: string;         // "monster_0" | "player_b" | "self"
  energy_cost: number;
}

// 图主广播队列当前状态 → 全员
interface QueueUpdate {
  type: "queue_update";
  source: string;         // stage_host id
  seq: number;
  entries: QueueEntry[];  // 完整队列快照
}

// 图主调用卡牌所有者
interface InvokeCard {
  type: "invoke_card";
  source: string;         // stage_host id
  target: string;         // card owner id
  seq: number;
  request_id: string;     // 唯一请求ID，用于关联 invoke_result
  card_id: string;
  upgraded: boolean;
  game_target: string;    // 游戏内目标：monster_0, player_b, self
}

// 卡牌所有者回传结果
interface InvokeResult {
  type: "invoke_result";
  source: string;         // card owner id
  target: string;         // stage_host id
  seq: number;
  request_id: string;     // 对应 invoke_card.request_id
  effects: EffectDescription[];   // 纯数值效果
  operation_sequence: OperationStep[];  // 用于操作重放
}
```

## 怪物回合

怪物由图主拥有。怪物回合流程如下：

```
图主遍历所有怪物：

1. 对每个未死亡的怪物：
   a. 图主用共享种子RNG确定怪物意图 (intent)
   b. 广播 monster_intent → 全员（显示意图图标）
   c. 图主在本地执行怪物动作（AI 逻辑，触发必要钩子）
   d. 收集效果结果（对谁造成多少伤害，施加什么能力等）
   e. 广播 state_sync → 全员
      - 有该怪物定义者：操作重放 + suppressEvents
      - 无该怪物定义者：fallback 数值

2. 所有怪物动作完成后，回合结束
```

怪物操作由于所有者 = 图主，此时"调用"退化为图主本地执行，无需网络往返。只有最终结果需要同步。

## 事件处理

事件由图主拥有和主持：

- **投票模式**（兼容塔2）：图主展示事件选项 → 所有玩家投票 → 图主裁定结果 → 同步全员
- **不投票模式**：图主直接选择选项（当前设计暂不实现投票，留作将来扩展）

```
图主进事件 → 图主决定事件类型 + 选项 → 图主本地执行 → 广播 state_sync → 全员同步事件结果
```

## 双通道协议：Invoke / Sync

协议消息分为两种通道，差异在于**接收方是否触发钩子**：

### Invoke（调用消息）— 触发钩子

图主调用卡牌所有者执行卡牌。被调用方在自身上执行，正常触发本地钩子：

```json
{
  "type": "invoke_card",
  "source": "stage_host",
  "target": "player_a",
  "seq": 42,
  "request_id": "req_abc123",
  "card_id": "Strike_R",
  "upgraded": false,
  "game_target": "monster_0"
}
```

Invoke 是**明确的目标指令**："请你执行这张卡牌"。只有被调用方触发钩子。调用方和第三方只接收 sync 结果。

### Sync（同步消息）— 不触发钩子

表示状态数据的被动镜像。接收方通过 `@SpirePatch` 抑制事件发布后直接写入或重放：

```json
{
  "type": "state_sync",
  "subtype": "combat_result",
  "source": "stage_host",
  "seq": 48,
  "request_id": "req_abc123",
  "card_id": "Strike_R",
  "effects": [
    { "kind": "damage", "target": "monster_0", "amount": 15 }
  ],
  "operation_sequence": [
    { "step": "play_card", "card_id": "Strike_R", "source": "player_a", "target": "monster_0" },
    { "step": "damage", "target": "monster_0", "amount": 15 }
  ]
}
```

同步采用分层策略：
- **接收方有该卡牌** → 使用 `operation_sequence` 进行操作重放，suppressEvents 包裹，引擎自动产生 VFX
- **接收方无该卡牌** → 使用 `effects` 纯数值同步，手动渲染通用效果

| 消息类型 | 语义 | 触发钩子 | 举例 |
|----------|------|----------|------|
| **Invoke** | 远程调用，交付执行权 | **是（仅被调用方）** | 图主调卡牌所有者执行卡牌 |
| **Sync** | 被动状态镜像/结果广播 | **否** | 战斗结果同步、怪物HP变更、在线角色属性更新 |

### 状态同步子类型

| subtype | 说明 | 关键字段 |
|---------|------|----------|
| `remote_player` | 在线角色属性更新 | `player: RemotePlayerState` |
| `combat_result` | 卡牌/怪物动作执行结果 | `effects`, `operation_sequence`, `card_id?` |
| `monster_intent` | 怪物意图变更 | `monster_id`, `intent` |
| `monster_state` | 怪物状态变更 | `monsters: Record<monster_id, MonsterState>` |
| `full_snapshot` | 完整状态快照 | `players`, `monsters`, `floor`, `act`, `seed` |

## BaseMod 事件抑制机制

[保持原有内容，本章节不变]

## Mod 兼容性设计：Fallback 效果系统与分层同步

这是实现跨Mod、跨游戏兼容性的核心机制。

### 分层同步

```
图主广播战斗结果:

  接收方收到 state_sync(combat_result):
    │
    ├─ 1. 本地卡池有该卡牌定义
    │     → 用 operation_sequence 操作重放
    │     → 本地引擎执行，引擎自动产 VFX/动画
    │     → suppressEvents 包裹，不触发钩子
    │
    ├─ 2. 本地卡池无该卡牌定义
    │     → 用 effects 纯数值同步
    │     → 手动渲染通用效果（伤害数字、格挡条等）
    │     → suppressEvents 包裹
    │
    └─ 3. 跨游戏（塔1→塔2或反向）
          → 永远走 effects 数值通道
          → 实体映射表翻译 card_id / relic_id
```

### Fallback 效果类型

同原有设计，效果类型保持不变：damage / gain_block / apply_power / remove_power / heal / gain_energy / draw_card / discard_card / exhaust_card / gain_gold / obtain_relic / obtain_potion / lose_hp。

### OperationSequence 结构

```typescript
interface OperationStep {
  step: "play_card" | "damage" | "gain_block" | "apply_power" | "remove_power"
      | "heal" | "gain_energy" | "draw_card" | "discard_card" | "exhaust_card"
      | "gain_gold" | "obtain_relic" | "obtain_potion" | "lose_hp"
      | "monster_move";     // 怪物动作
  // step-specific fields
  card_id?: string;        // for play_card
  source: string;          // 操作发起方 player_id
  target: string;          // 操作目标
  amount?: number;
  // ...
}
```

## 素材传递系统

[保持原有内容，本章节不变]

## 在线角色：独立 AbstractPlayer 实例与渲染集成

在线角色继承 `AbstractPlayer`，但角色定位已从"有执行权的本地角色投影"变为"纯渲染+状态镜像"：

```
RemotePlayer extends AbstractPlayer:
  • drawPile / hand / discardPile / exhaustPile — 卡组镜像
  • relics — 遗物列表镜像（含计数器）
  • potions — 药水槽镜像
  • powers — 能力/Buff/Debuff镜像
  • currentHealth / maxHealth / currentBlock — 战斗数值镜像
  • energy / energyPerTurn — 能量系统镜像

所有状态通过 StateSync 写入，写入时 suppressEvents 包裹。
在线角色不执行卡牌逻辑、不触发钩子。
执行权属于所有权机器，在线角色只负责渲染。
```

渲染集成保持原有设计（BaseMod RenderSubscriber）。

## RNG 同步策略

战斗确定性是同步的前提：相同的种子 + 相同的动作序列 = 相同的战斗结果。

### 策略

```
1. 游戏开始时生成共享种子 → 所有玩家使用同一种子
2. 图主管理所有关键随机抽取：
   - 地图生成 → 图主用种子RNG计算 → 同步给所有人
   - 怪物意图选择 → 图主用种子RNG计算 → 广播 intent
   - 怪物攻击目标选择 → 图主用种子RNG计算
   - 卡牌奖励生成 → 图主用种子RNG计算 → 广播
3. 在线角色的**抽牌由图主推送**：
   - 在线角色不自己做RNG抽取
   - 图主依照远端角色所有者机器的卡组状态
   - 图主计算抽牌结果 → 推送 card_id 列表
4. 本地角色的**玩家决策RNG**由本地处理：
   - 随机遗物效果（如 Dead Branch）→ 本地RNG → 结果同步给其他人
```

### 为什么不在线角色自己做RNG

若在线角色自己用共享种子做RNG，需保证两端卡组状态**完全相同**——若某玩家使用Mod导致卡组差异，RNG立即分叉。由所有者机器执行，避免了此问题。

## 网络拓扑

[保持原有内容，本章节不变]

## 协议消息定义

### 顶层信封

```typescript
interface GameMessage {
  type: "invoke_card" | "invoke_result" | "state_sync"
     | "queue_submit" | "queue_update"
     | "stage_host_election" | "stage_host_result"
     | "resource_registry" | "resource_request" | "resource_response"
     | "animation_sync"
     | "control";
  subtype?: string;
  source: string;      // 发送方 player_id
  seq: number;         // 单调递增序列号，用于有序投递和去重
  target?: string;     // 定向消息目标
  // ... type-specific fields
}
```

### Invoke 消息

| subtype | 说明 | 方向 | 关键字段 |
|---------|------|------|----------|
| `invoke_card` | 图主调卡牌所有者执行 | Host→Owner | `card_id`, `upgraded`, `game_target`, `request_id` |
| `invoke_result` | 所有者回传执行结果 | Owner→Host | `request_id`, `effects`, `operation_sequence` |

### StateSync 消息

| subtype | 说明 | 方向 | 关键字段 |
|---------|------|------|----------|
| `combat_result` | 卡牌/怪物执行结果广播 | Host→All | `request_id`, `effects`, `operation_sequence` |
| `remote_player` | 在线角色属性 | Owner→All | `player: RemotePlayerState` |
| `monster_intent` | 怪物意图 | Host→All | `monster_id`, `intent` |
| `monster_state` | 怪物状态变更 | Host→All | `monsters: Record<monster_id, MonsterState>` |
| `full_snapshot` | 完整状态快照 | Host→All | `players`, `monsters`, `floor`, `act`, `seed` |

### 队列消息

| subtype | 说明 | 方向 | 关键字段 |
|---------|------|------|----------|
| `queue_submit` | 玩家提交卡牌到队列 | Player→Host | `card_id`, `upgraded`, `target`, `energy_cost` |
| `queue_update` | 图主广播队列状态 | Host→All | `entries: QueueEntry[]` |

### 阶段控制消息

| subtype | 说明 | 方向 | 关键字段 |
|---------|------|------|----------|
| `stage_host_election` | 新阶段开始时发起图主投票 | Server→All | `candidates`, `stage_info` |
| `stage_host_result` | 投票结果 | Server→All | `host_id`, `stage_info` |

### QueueEntry 结构

```typescript
interface QueueEntry {
  entry_id: string;      // 队列项唯一ID
  source: string;        // 提交者 player_id
  card_id: string;
  upgraded: boolean;
  target: string;        // 游戏内目标
  status: "pending" | "executing" | "done";
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
│       ├── sequence.ts          # 序列号管理 + 有序保证
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
            │   ├── RelayClient.java             # WebSocket 客户端
            │   ├── LobbyApiClient.java          # HTTP REST 客户端
            │   ├── MessageRouter.java           # 消息分发
            │   └── Protocol.java                # 消息 POJO + JSON 序列化
            ├── resource/
            │   ├── RemoteResourceManager.java   # 素材总控 (本地→缓存→请求)
            │   ├── RemoteAssetCache.java        # L1 内存 + L2 磁盘缓存
            │   ├── RemoteAssetServer.java       # 响应素材请求
            │   ├── ResourceRegistryTracker.java # 远端素材清单管理
            │   ├── RemoteCardResource.java      # 远程卡牌素材投影
            │   ├── RemoteRelicResource.java     # 远程遗物素材投影
            │   ├── RemotePowerResource.java     # 远程能力素材投影
            │   ├── RemotePotionResource.java    # 远程药水素材投影
            │   └── RemoteCharacterResource.java # 远程角色骨骼素材投影
            ├── remote/
            │   ├── RemotePlayer.java           # extends AbstractPlayer
            │   ├── RemotePlayerRegistry.java   # 在线角色注册表
            │   ├── RemoteRenderer.java         # 实现 RenderSubscriber, 战斗渲染
            │   └── StageHost.java              # 图主：阶段权威、队列编排、战斗主持
            ├── sync/
            │   ├── LocalCapturePatches.java    # 捕获本地操作 → 生成 queue_submit
            │   ├── InvokeExecutor.java         # 处理 Invoke 消息
            │   ├── SyncExecutor.java           # 处理 StateSync 消息 (分层同步)
            │   ├── SequenceTracker.java        # seq 序列号追踪
            │   └── RngSync.java               # RNG 种子同步 + 共享抽取
            └── ui/
                ├── LobbyScreen.java            # 创建/加入房间界面
                ├── ServerPicker.java           # 服务器地址输入
                ├── RoomPanel.java              # 房间内界面
                ├── RoomChat.java               # 聊天
                ├── QueueDisplay.java           # 待打出队列可视化
                └── RemoteStatsOverlay.java     # 在线角色状态覆盖层
```

## 未来塔2兼容性预留

[保持原有内容，本章节不变]
