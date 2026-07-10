# CrossSpire — 杀戮尖塔联机Mod架构设计

## 目录

1. [设计目标](#设计目标)
2. [核心思想：双角色投影模型](#核心思想双角色投影模型)
3. [就近原则](#就近原则)
4. [双通道协议：Request vs StateSync](#双通道协议request-vs-statesync)
5. [BaseMod 事件抑制机制](#basemod-事件抑制机制)
6. [房主权威模型](#房主权威模型)
7. [Mod 兼容性设计：Fallback 效果系统](#mod-兼容性设计fallback-效果系统)
8. [在线角色：独立 AbstractPlayer 实例](#在线角色独立-abstractplayer-实例)
9. [RNG 同步策略](#rng-同步策略)
10. [网络拓扑](#网络拓扑)
11. [协议消息定义](#协议消息定义)
12. [项目结构](#项目结构)
13. [未来塔2兼容性预留](#未来塔2兼容性预留)

---

## 设计目标

CrossSpire 是一个开源的杀戮尖塔1联机Mod，目标：

1. **塔1多人同步战斗** — 多个玩家在同一场战斗中操作各自的角色
2. **Mod 兼容性** — 每个玩家可以使用自己的Mod，不要求所有人安装相同Mod
3. **跨游戏连接预留** — 架构预留与塔2互联的能力（塔1 Java/LibGDX ↔ 塔2 C#/Godot）
4. **开源** — 全栈开源的替代方案，对标闭源的 Together in Spire

## 核心思想：双角色投影模型

每台机器上有**两种角色**：

```
机器A                                机器B
┌──────────────────────┐            ┌──────────────────────┐
│ 本地角色 (Player A)   │            │ 本地角色 (Player B)   │
│ • 完整本地引擎执行      │            │ • 完整本地引擎执行      │
│ • 玩家实际操作的角色    │            │ • 玩家实际操作的角色    │
│ • 本机Mod生效          │            │ • 本机Mod生效          │
│ • 状态是本机权威源      │            │ • 状态是本机权威源      │
├──────────────────────┤            ├──────────────────────┤
│ 在线角色 (Player B投影) │◄──投影───►│ 在线角色 (Player A投影) │
│ • 独立 AbstractPlayer  │            │ • 独立 AbstractPlayer  │
│ • 接收B的动作/效果      │            │ • 接收A的动作/效果      │
│ • 本地渲染、本地存在    │            │ • 本地渲染、本地存在    │
└──────────────────────┘            └──────────────────────┘
```

**核心原则**：在线角色不是静态数据、不是简单的HP条——它是一个**活在本地游戏循环中的真实 Player 实例**。它拥有自己的手牌、遗物、能力、格挡值。它能被UI渲染、能被战斗逻辑查询。但它的状态由远端机器上的"本地角色"决定。

## 就近原则

> 能本地处理就本地处理，不行再 fallback 远程。

### 动作包 vs 效果包

| 场景 | 发送什么 | 接收方怎么做 |
|------|----------|-------------|
| 卡牌在**接收方**本地存在 | 动作包（只有 cardId + target） | 用本地引擎执行，触发本地 BaseMod |
| 卡牌在**接收方**本地不存在（Mod卡牌） | 动作包 + fallback 效果包 | 直接应用效果数值，不尝试查找卡牌 |
| 卡牌在**接收方**本地不存在，但效果涉及发送方的Mod | fallback 效果包 | 发送方本地计算完整效果，接收方只应用数值 |

这样，每台机器**用自己最熟悉的引擎**处理自己能处理的部分，只有无法理解的部分才降级为数值传递。

## 双通道协议：Request vs StateSync

两种消息类型，触发不同的本地行为：

### Request（请求包）— 触发 BaseMod

表示一个主动游戏行为。接收方将其视为"真实发生在共享世界中的动作"：

```json
{
  "type": "request",
  "subtype": "play_card",
  "source": "player_a",
  "seq": 42,
  "card_id": "Strike_R",
  "target": "monster_0",
  "cost_paid": 1,
  "fallback": { "effects": [...] }
}
```

- **本地卡牌存在** → 用本地引擎执行，BaseMod.onCardUse 正常触发
- **本地卡牌不存在** → 应用 fallback 效果，**仍然触发 BaseMod** 因为这是一个真实动作
- 远端角色打出卡牌这个事实本身就是一个值得让本地Mod感知的事件

### StateSync（状态包）— 不触发 BaseMod

表示状态数据的被动镜像。接收方通过 `@SpirePatch` 抑制 BaseMod 事件发布后直接写入：

```json
{
  "type": "state_sync",
  "subtype": "remote_player",
  "source": "player_a",
  "seq": 48,
  "player": {
    "hp": 65, "max_hp": 80,
    "relics": ["Burning Blood"],
    "potions": ["Fire Potion", null]
  }
}
```

- 直接写入在线角色的字段（HP、遗物列表、药水槽等）
- **不触发 BaseMod**：因为状态变化是"结果"不是"原因"
- 如果远端HP下降是因为中了怪物的攻击——怪物攻击已经作为 Request 处理过了，重复处理HP下降会导致双倍效果

| 消息类型 | 语义 | 触发 BaseMod | 举例 |
|----------|------|-------------|------|
| **Request** | 主动游戏行为 | **是** | 打出卡牌、使用药水、结束回合 |
| **StateSync** | 被动数据镜像 | **否** | 远程角色HP变化、怪物状态变更、遗物获取通知 |

## BaseMod 事件抑制机制

BaseMod 的事件系统基于 `@SpirePatch` 注入游戏方法 → `BaseMod.publishXxx()` → 遍历 `ArrayList<SubscriberType>` 调用回调。BaseMod 自身不提供任何内置的抑制标志。

CrossSpire 通过 **`@SpirePatch` 拦截 `BaseMod.publishXxx()` 方法**来实现开关控制：

```java
// OpenTogetherMod.java — 全局开关
public class CrossSpireMod {
    public static final AtomicInteger eventSuppression = new AtomicInteger(0);

    public static void suppressEvents(Runnable fn) {
        eventSuppression.incrementAndGet();
        try { fn.run(); }
        finally { eventSuppression.decrementAndGet(); }
    }
}

// SuppressBaseModPatches.java — 拦截 publish 方法
@SpirePatch(clz = BaseMod.class, method = "publishPostBattle")
public static class SuppressPostBattle {
    @SpirePrefixPatch
    public static SpireReturn<Void> Prefix(AbstractRoom battleRoom) {
        if (CrossSpireMod.eventSuppression.get() > 0) {
            return SpireReturn.Return(null);
        }
        return SpireReturn.Continue();
    }
}
```

需要拦截的 BaseMod.publish* 方法列表：
- `publishPostBattle` — 战斗结束
- `publishOnPlayerDamaged` — 玩家受伤
- `publishOnPlayerLoseBlock` — 玩家失去格挡
- `publishRelicGet` — 获得遗物
- `publishPotionGet` — 获得药水
- `publishPostPotionUse` — 使用药水
- `publishOnCardUse` — 卡牌使用
- `publishPostPowerApply` — 能力应用
- `publishPostDraw` — 抽牌
- `publishPostExhaust` — 消耗
- `publishOnPlayerTurnStart` — 回合开始
- `publishPostEnergyRecharge` — 能量恢复

由于 BaseMod 没有 gold change hook，金币同步需要通过自定义 `@SpirePatch` 直接拦截 `AbstractPlayer.gainGold()` / `loseGold()` 方法。

## 房主权威模型

房主不是按"谁创建房间"决定的网络角色，而是按"谁负责管理共享状态"决定的逻辑角色：

```
房主 (Host) 的三重角色:

1. 普通本地角色 + 在线角色管理
   和 Client 一样，有自己的本地角色和在线角色

2. 共享状态权威 (Monster / Event / Map / RNG)
   • 怪物 AI 计算 → 广播结果
   • 事件选项 → 收集投票后裁定
   • 地图生成 → 用共享种子生成 → 广播
   • RNG 种子 → 管理并推送

3. Mod 交互代理 (Mod Proxy) —— 不需要！
   房主不运行 Client 的 Mod。
   Client 的 Mod 效果由 Client 本地计算。
   效果数值发送给房主，房主只做轻量校验后应用到共享状态。
```

### 消息流

```
Client A 打出 Mod 卡牌:
  1. Client A 本地计算效果 → 打包 Request（含 fallback）→ 发到中继
  2. 中继转发到房主
  3. 房主:
     a. 轻量校验效果合理性（伤害上限、能量消耗等）
     b. 在线角色(Player A).executeAction(...) — 触发 BaseMod，正常执行
     c. 如果效果影响共享怪物 → 更新怪物状态
     d. 广播 monster_state StateSync 给所有客户端
  4. 所有客户端收到 StateSync → suppressEvents 更新怪物状态

同步战斗/基础卡牌:
  1. Client A 打出基础卡牌 → 打包 Request（无 fallback）→ 中继 → 房主
  2. 房主用本地引擎执行 → 结果（怪物HP变化等）→ 广播 StateSync
  3. 所有客户端同步更新

本地状态（仅影响自己）:
  1. Client A 消耗能量、手牌变动 → 打包 StateSync(remote_player) → 中继
  2. 中继广播给所有其他客户端
  3. 其他客户端 suppressEvents 更新 Player A 的在线角色
```

### 房主迁移

如果房主断线，下一个玩家接管房主角色。切换逻辑：
1. 检测房主心跳超时
2. 选择下一个客户端作为新房主
3. 新房主拉取所有在线角色的完整状态快照
4. 重建共享状态（怪物、事件、地图）
5. 继续游戏

## Mod 兼容性设计：Fallback 效果系统

这是实现跨Mod、跨游戏兼容性的核心机制。

### 四级处理策略

```
收到 remote play_card:
  │
  ├─ 1. 本地卡池找到该卡牌
  │     → 本地引擎执行，触发本地 BaseMod
  │     → 本地Mod被动效果正确触发
  │
  ├─ 2. 本地卡池找不到，但有 fallback
  │     → 逐个应用 fallback.effects
  │     → 仍触发 BaseMod（这是一个真实动作）
  │     → 本地Mod被动效果仍能响应
  │
  ├─ 3. 本地卡池找不到，且无 fallback
  │     → 记录 warning，跳过
  │     → 请求发送方重新发送含 fallback 的包
  │
  └─ 4. 跨游戏（塔1→塔2或反向）
        → 永远走 fallback（引擎不同，不可能本地执行）
        → 需要实体映射表翻译 card_id / relic_id
```

### Fallback 效果类型

```json
{
  "effects": [
    { "kind": "damage",          "target": "monster_0", "amount": 15, "damage_type": "FIRE" },
    { "kind": "gain_block",      "target": "self",      "amount": 8 },
    { "kind": "apply_power",     "target": "monster_0", "power_id": "Vulnerable", "amount": 2 },
    { "kind": "remove_power",    "target": "self",      "power_id": "Weak" },
    { "kind": "heal",            "target": "self",      "amount": 5 },
    { "kind": "gain_energy",     "target": "self",      "amount": 1 },
    { "kind": "draw_card",       "target": "self",      "amount": 2 },
    { "kind": "discard_card",    "target": "self",      "amount": 1 },
    { "kind": "exhaust_card",    "target": "self",      "card_id": "Strike_R" },
    { "kind": "gain_gold",       "target": "self",      "amount": 50 },
    { "kind": "obtain_relic",    "target": "self",      "relic_id": "Vajra" },
    { "kind": "obtain_potion",   "target": "self",      "potion_id": "Fire Potion" },
    { "kind": "lose_hp",         "target": "self",      "amount": 3 }
  ]
}
```

效果类型设计原则：
- **只传递数值，不传递逻辑** — 接收方不需要理解原卡牌的运作方式
- **覆盖所有常见游戏操作** — damage / block / power / heal / draw 等
- **可扩展** — 新增效果类型只需两端同步更新协议版本

## 在线角色：独立 AbstractPlayer 实例

在线角色继承 `AbstractPlayer`，拥有完整角色能力：

```
RemotePlayer extends AbstractPlayer:
  • drawPile / hand / discardPile / exhaustPile — 卡组管理
  • relics — 遗物列表（含计数器）
  • potions — 药水槽
  • powers — 能力/Buff/Debuff
  • currentHealth / maxHealth / currentBlock — 战斗数值
  • energy / energyPerTurn — 能量系统

方法分为两类：
  sync*()  — StateSync 调用，suppressEvents 包裹
  execute*() — Request 调用，正常触发 BaseMod
```

### 在线角色的关键特性

1. **能被本地 UI 自然渲染** — 血条、格挡、能力图标由本地战斗渲染器处理
2. **能被本地战斗逻辑查询** — `getAlivePlayers()` / `getAllPowers()` 等
3. **能触发本地 Mod 的被动效果** — 当在线角色获得格挡时，如果本地有Mod监听"任意角色获得格挡"，则正常触发
4. **遗物能正常运作** — 遗物的 `onEquip()` / `atBattleStart()` 等方法在实例化时被调用，后续触发由 StateSync 管理

## RNG 同步策略

战斗确定性是同步战斗的前提：相同的种子 + 相同的动作序列 = 相同的战斗结果。

### 策略

```
1. 房主在游戏开始时生成并广播共享种子
2. 房主管理所有随机事件的抽取：
   - 怪物意图选择 → 房主用种子RNG计算 → 广播
   - 怪物攻击目标选择 → 房主用种子RNG计算 → 广播
   - 卡牌奖励生成 → 房主用种子RNG计算 → 广播（每层生成一次）
3. 在线角色的抽牌由房主推送：
   - 在线角色不需要自己做RNG抽取
   - 房主用远端角色所在机器的本地角色状态确定卡组
   - 房主计算抽牌结果 → 推送 card_id 列表
4. 客户端本地角色使用独立RNG（玩家决策RNG）：
   - 随机遗物效果（如 Dead Branch）由本地RNG处理
   - 结果通过 StateSync 同步给其他玩家
```

### 为什么不在线角色自己做RNG

如果在线角色自己用共享种子做RNG抽取，需要保证两端的卡组状态**完全相同**。如果其中一个玩家打了Mod，手牌可能略有不同，RNG结果就会分歧。由房主统一推送避免了这个问题。

## 网络拓扑

```
┌─────────────────────────────────────────────────────┐
│              Cross Spire Relay Server               │
│              (Node.js / TypeScript)                  │
│                                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │  REST API    │  │  WebSocket  │  │  UDP Relay  │ │
│  │  • 房间 CRUD  │  │  • 消息路由  │  │  (未来)      │ │
│  │  • 心跳      │  │  • 控制通道  │  │  NAT穿透     │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
│                                                     │
│  消息 = 透传。服务器不解析游戏逻辑。                    │
│  只保证有序投递。                                     │
└──────────┬───────────────────────────────┬──────────┘
           │  WebSocket + JSON             │  WebSocket + JSON
    ┌──────┴──────┐                 ┌──────┴──────┐
    │  塔1 Client  │                 │  塔2 Client  │
    │  (Java)      │                 │  (C#/Godot)  │
    │              │                 │  [未来实现]   │
    │ 本地角色+在线 │                 │ 本地角色+在线  │
    └─────────────┘                 └─────────────┘
```

### 连接拓扑选择

- **当前阶段**：中继全透传。服务器路由消息到房间内所有人。简单、可靠。
- **未来可选**：客户端间直连（WebSocket P2P 或 WebRTC），中继仅做信令服务器。

### 协议传输

- 传输层：WebSocket（全双工、低延迟、防火墙友好）
- 编码：JSON（语言无关、可调试）
- 消息格式：见下文[协议消息定义](#协议消息定义)
- 控制通道和游戏数据通道复用同一个 WebSocket 连接

## 协议消息定义

### 顶层信封

```typescript
interface GameMessage {
  type: "request" | "state_sync";
  subtype: string;
  source: string;     // 发送方 player_id
  seq: number;        // 单调递增序列号，用于有序投递和去重
  // ... subtype 特定字段
}
```

### Request 子类型

| subtype | 说明 | 关键字段 |
|---------|------|----------|
| `play_card` | 打出卡牌 | `card_id`, `upgraded`, `target`, `cost_paid`, `fallback?` |
| `end_turn` | 结束回合 | `source` |
| `use_potion` | 使用药水 | `potion_id`, `target`, `fallback?` |
| `select_reward` | 选牌/奖励 | `reward_index` |
| `select_event` | 事件选项 | `option_index` |

### StateSync 子类型

| subtype | 说明 | 关键字段 |
|---------|------|----------|
| `remote_player` | 在线角色属性 | `player: RemotePlayerState` |
| `monster_state` | 怪物状态变更 | `monsters: Record<monster_id, MonsterState>` |
| `full_snapshot` | 完整状态快照 | `players`, `monsters`, `floor`, `act`, `seed` |

### 控制通道消息

控制通道消息与游戏数据消息复用一个 WebSocket 连接，通过顶层字段区分：

| 控制消息类型 | 说明 |
|-------------|------|
| `connected` | 连接确认握手 |
| `ping` / `pong` | 心跳保活 |
| `host_hello` / `client_hello` | 角色注册 |
| `room_chat` | 房间聊天 |
| `player_joined` / `player_left` | 玩家进出 |
| `room_state` | 房间状态变更 |

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
│       ├── router.ts            # WebSocket 消息路由
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
            ├── CrossSpireMod.java          # @SpireInitializer 入口
            ├── EventSuppression.java        # BaseMod 事件抑制全局开关
            ├── SuppressBaseModPatches.java  # @SpirePatch 拦截 BaseMod.publish*
            ├── network/
            │   ├── RelayClient.java         # WebSocket 客户端
            │   ├── LobbyApiClient.java      # HTTP REST 客户端
            │   ├── MessageRouter.java       # 消息分发 (request vs state_sync)
            │   └── Protocol.java            # 消息 POJO + JSON 序列化
            ├── sync/
            │   ├── LocalCapturePatches.java # 捕获本地操作 → 生成消息包
            │   ├── RequestExecutor.java     # 处理 Request 消息 (触发BaseMod)
            │   ├── StateSyncExecutor.java   # 处理 StateSync 消息 (抑制BaseMod)
            │   ├── SequenceTracker.java     # seq 序列号追踪
            │   └── RngSync.java            # RNG 种子同步 + 共享抽取
            ├── remote/
            │   ├── RemotePlayer.java        # extends AbstractPlayer
            │   ├── RemotePlayerRegistry.java # 在线角色注册表
            │   ├── HostAuthority.java       # 房主：怪物/事件/地图权威
            │   └── RemoteRenderer.java      # 战斗中渲染在线角色
            └── ui/
                ├── LobbyScreen.java         # 创建/加入房间界面
                ├── ServerPicker.java        # 服务器地址输入
                ├── RoomPanel.java           # 房间内界面
                ├── RoomChat.java            # 聊天
                └── RemoteStatsOverlay.java  # 在线角色状态覆盖层
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

1. **协议语言无关**：所有消息使用 JSON + WebSocket，Java 和 C# 均可处理
2. **Fallback 系统即跨游戏桥梁**：塔1 Mod卡牌的效果通过 fallback 传到塔2，无需塔2安装对应Mod
3. **实体映射表**：`shared/cross-spire-protocol/entity-mappings/` 维护塔1→塔2的 card_id / relic_id / character_id 映射
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

未映射的实体永远走 fallback 效果通道。
