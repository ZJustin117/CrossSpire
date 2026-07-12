# CrossSpire — 杀戮尖塔联机Mod架构设计

## 目录

1. [设计目标](#设计目标)
2. [关键术语定义](#关键术语定义)
3. [核心思想：引用模型与双角色投影](#核心思想引用模型与双角色投影)
4. [引用系统](#引用系统)
5. [就近原则](#就近原则)
6. [图主权威模型](#图主权威模型)
7. [分布式广播队列：战斗流程](#分布式广播队列战斗流程)
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
| 引用 | Reference | 对任意可执行对象（卡牌/遗物/怪物/玩家/地图等）的统一抽象，封装了【所有者是谁】和【如何到达它】。`本地引用` = 对象在本地且内容校验通过，可直接执行；`远程引用` = 对象在远端机器上，解引用时通过网络调用。上层 API 对本地/远程引用透明 |
| 解引用 | Dereference | 调用引用指向的对象，执行其逻辑，并同步结果。本地解引用退化为直接方法调用；远程解引用走网络调用 → 等待结果 → 同步 |
| 本地引用 | Local Reference | 对象在本地存在且内容校验通过。解引用 = 直接调用，无网络开销 |
| 远程引用 | Remote Reference | 对象在远端机器上。解引用 = 网络调用所有者 → 执行 → 回传结果 |
| 空引用 | Null Reference | 远程引用不再可达（所有者掉线或断开）。解引用失败，需引用退化或引用转移 |
| 引用退化 | Reference Degradation | 远程引用 → 本地引用。当本地获得对象完整定义（下载完成、内容校验通过）后降级为本地执行 |
| 引用转移 | Reference Migration | 改变远程引用的指向源（如原所有者掉线后切换到备份源），用于处理空引用 |
| 广播 | Broadcast | 向房间内所有成员发送消息。用于同步分布式队列、宣告新玩家、交换IP、心跳保活等 |
| 内容校验 | Content Validation | 同名类/方法不等同于相同类/方法。通过内容哈希比对判定两个实体是否真正一致。对同 Mod 不同版本隔离有关键意义 |
| 图主 | Stage Host | 当前阶段（Act + Floor）地图/事件/怪物的**强所有者**。其他人只能对此建立远程引用（不允许本地引用）。由所有成员投票选定 |
| 标准包 | Standard Packet | 待打出卡牌队列中的一项，包含 `{packet_id, sender_id, owner_id, timestamp, card_id, resource_hash, target}`。所有人收到后插入本地排序队列 |

### 操作关系

```
远程引用解引用:
  A 持有指向 B 对象的远程引用
    → A 调用 ref.dereference(args)
    → 引用系统识别为 REMOTE
    → 网络调用 B → B 本地执行 → B 回传结果
    → A 收到结果 → 渲染

本地引用解引用:
  A 持有指向自己对象的本地引用
    → A 调用 ref.dereference(args)
    → 引用系统识别为 LOCAL
    → 直接调用，零网络往返

交叉引用示例:
  A 打出 B 的远程卡牌 → 解引用 → 网络调用 B → B 执行
  B 的卡牌效果触发 A 的本地遗物 → A 的遗物是本地引用 → A 本地直接生效
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
│ • 解引用耗时 = 网络RTT    │            │ • 解引用耗时 = 网络RTT    │
└──────────────────────────┘            └──────────────────────────┘
```

**核心原则**：
- 在线角色是渲染和状态镜像——状态由远端所有者通过解引用写入，不自行触发钩子
- 执行权归属所有者——解引用永远在所有者机器上执行逻辑
- 引用系统封装网络复杂性——上层代码不区分本地/远程，只调用 `ref.dereference()`

## 引用系统

### 引用数据结构

```typescript
interface Reference<T> {
  refId: string;             // 对象唯一标识 (如 "card:Strike_R@playerA")
  ownerId: string;           // 所有者机器 ID
  type: "LOCAL" | "REMOTE" | "NULL";
  remoteAddr?: { ip: string; port: number };  // 远程引用目标地址
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
  │     → 通过网络向 ownerId 发送 invoke 请求
  │     → owner 本地执行 → 回传结果
  │     → 本地渲染结果，不触发钩子
  │     → 返回 Result
  │
  └─ type == NULL
        → tryDegrade()     // 本地是否已有完整定义？
        → tryMigrate()     // 是否有其他节点持有副本？
        → 都失败 → 抛出空引用异常，界面显示不可达
```

### 引用生命周期

```
创建 → 远程引用 (网络 invoke)
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

基于 BaseMod 事件体系注册引用触发器。远程遗物可被本地卡牌触发（本地卡牌打出发出事件 → 远程遗物引用收到事件 → 解引用 → 网络调用所有者），远程卡牌也可触发仅在本地的遗物（本地遗物是本地引用，直接在本地生效）。

## 就近原则

引用系统自动保证就近执行：

```
解引用路径选择:

  reference.dereference()
    │
    ├─ 对象在本地且内容校验通过 → 本地引用 → 直接执行
    │   例: A 打出自己拥有的 Strike_R
    │
    ├─ 对象在远端 → 远程引用 → 网络调用所有者
    │   例: A 打出 B 的 Mod 卡牌 → 调用 B 执行
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

图主按阶段投票选定，仅掌管三类对象：**地图、事件、怪物**。

### 图主职责

```
1. 地图权威
   • 用共享种子生成当前阶段地图 → 广播给所有人
   • 进入房间时决定房间类型和内容
     - 战斗 → 图主统一怪物配置
     - 事件 → 图主决定具体事件

2. 怪物权威
   • 怪物初始状态由图主提供
   • 怪物 AI/意图/行为由图主解引用（图主=怪物所有者，始终本地执行）
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

图主作为怪物所有者，解引用怪物时始终走本地路径（图主机器上有怪物完整定义）。

### 图主掉线

图主掉线 → 图主持有的所有引用变为空引用：
- 自动保存进度，等待图主重连
- 可尝试引用转移到下一轮投票次高票者（暂不实现）
- 界面显示"等待图主..."

## 分布式广播队列：战斗流程

### 核心设计

无中心化调度者。每个人本地维护相同的有序队列，只执行自己是所有者的项。

```
1. 玩家提交卡牌 → 封装为标准包 → 广播 (broadcast)
2. 所有人收到标准包 → 插入本地队列 → 排序 (timestamp, sender_id 打破平局)
3. 每个人检查队列头部：
   - 头部包.owner_id == 自己 → 解引用（本地执行）→ 广播 "完成" (broadcast)
   - 头部包.owner_id != 自己 → 等待 "完成" 广播
4. 收到 "完成" 广播 → 移除头部 → 检查下一个
5. 队列清空后才可结束回合
```

### 标准包结构

```typescript
interface QueuePacket {
  type: "queue_packet";
  packet_id: string;       // 唯一ID (sender_id + seq)
  sender_id: string;       // 提交者（打出卡牌的玩家）
  owner_id: string;        // 卡牌所有者（谁负责解引用）
  timestamp: number;       // 出牌时间戳
  card_id: string;         // 卡牌 ID
  resource_hash: string;   // 内容校验用哈希
  target: string;          // 游戏内目标 "monster_0" | "player_b" | "self"
}
```

### 排序规则

按 `timestamp` 升序；同 `timestamp` 按 `sender_id` 字典序升序。由于 sender_id 唯一且各节点按相同算法排序，结果一致。

### 玩家视角

1. **提交卡牌**：卡牌从手牌悬浮至队列区域 → 本地卡牌阻塞 → 广播标准包
2. **继续操作**：卡牌已离手，可继续打牌加入队列
3. **等待执行**：队列中的项由所有者执行，处理到自己的项时播放动画
4. **回合结束**：队列全部完成（所有项 broadcast 过 "完成"）方可结束回合

### 队列可视化

每个玩家处显示相同队列 UI：
- 队列项按顺序排列，显示提交者、卡牌名、状态（等待中/执行中/已完成）
- 执行中的项高亮
- 自己的项排队时可以看自己在第几个

## 怪物回合

怪物由图主持有（所有者 = 图主），处理流程：

```
1. 图主本地遍历所有未死亡怪物
2. 对每个怪物:
   a. 图主用共享种子 RNG 确定怪物意图
   b. 图主对自己的怪物引用解引用（始终本地，图主有怪物完整定义）
   c. 广播 monster_intent（意图图标）→ 全员渲染
   d. 怪物执行动作（AI 逻辑，触发图主本地钩子）
   e. 收集效果结果 → 广播 combat_result → 全员同步:
      - 有该怪物定义者 → 操作重放 + suppressEvents
      - 无该怪物定义者 → fallback 数值渲染
3. 所有怪物动作完成后，回合结束
```

怪物动作由图主解引用 → 始终本地执行，无网络往返。只有最终结果需要广播。

## 事件处理

事件由图主持有：

```
1. 图主决定事件类型和选项
2. 图主本地执行事件逻辑
3. 广播事件结果 → 全员同步
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
  → B 使用远程引用 → 解引用时调用 A 执行
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
收到远程卡牌标准包:

  1. 提取 resource_hash
  2. 本地按 card_id 查找类定义
  3. 内容校验（hash 比对）:
     ├─ 校验通过 → 本地引用（引擎执行，触发本地钩子，引擎自动产 VFX）
     └─ 校验失败/无本地定义 → 远程引用（解引用时网络调用所有者）
         → 结果用 fallback 数值渲染
```

### 交叉引用

引用系统使交叉所有权变得自然：

```
A 打出 B 的远程卡牌 Strike_P:
  A 的队列中 head.owner_id = B
  B 对其解引用（B 本地执行 Strike_P）
  Strike_P 的效果触发"对所有玩家施加能力"
    → B 持有对 A 远程角色的引用
    → 解引用：调用 A 的远程角色 → A 执行施加逻辑 → A 触发本地钩子

B 的远程卡牌触发 A 的本地遗物 Vajra:
  B 执行卡牌时发出 BaseMod.publishOnCardUse()
  A 本地遗物 Vajra 订阅了 onCardUse 事件
    → Vajra 是本地引用 → A 直接执行遗物逻辑 → 触发 A 本地钩子
```

远程遗物可以被本地卡牌触发（本地卡牌发出事件 → 远程遗物的引用订阅了该事件 → 网络调用所有者执行）。远程卡牌也可以触发仅在本地存在的遗物（遗物在本地 → 本地引用 → 直接在本地生效）。

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

引用的 `dereference()` 内部已经处理了 suppressEvents：远程引用解引用 → 所有者在远端触发钩子 → 结果同步回来 → 本地 suppressEvents 渲染。

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

### 运行时素材拦截与远程素材类层次（保持原有）

详见 §12 原素材传递系统内容。

### 缓存系统（保持原有）

```
RemoteAssetCache
  ├── L1 内存缓存: Map<CacheKey, WeakReference<Texture>>, Map<CacheKey, SkeletonData>
  ├── L2 磁盘缓存: {game_dir}/crossspire_cache/{source_player_id}/...
  ├── 校验: response.checksum vs 磁盘 checksum
  └── 过期: 上次访问 > 30 天自动清理
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

在线角色的引用持有：
- 对自己远端角色的远程引用（状态写入的路径）
- 对图主怪物/事件的远程引用（渲染用）
- 对自身遗物/能力的远程引用（被动效果触发用）

## RNG 同步策略

战斗确定性是同步的前提：相同的种子 + 相同的动作序列 = 相同的战斗结果。

### 策略

```
1. 游戏开始时所有玩家协商共享种子 → 全员使用同一种子
2. 共享 RNG（确定性随机）:
   - 地图生成 → 图主用种子 RNG 计算 → 广播
   - 怪物意图选择 → 图主用种子 RNG 计算 → 广播
   - 怪物攻击目标选择 → 图主用种子 RNG 计算 → 广播
   - 在线角色抽牌 → 图主用种子 RNG + 远端角色卡组状态计算 → 推送
3. 本地 RNG（非共享，玩家决策）:
   - 随机遗物效果 (Dead Branch 等) → 本地 RNG → 结果同步给其他人
4. 不要求所有节点 RNG 完全同步 — 只要求共享部分的 RNG 一致
```

## 网络拓扑

### 去中心化 P2P 虚拟网络

```
┌─────────────────────────────────────────────────┐
│             去中心化 P2P 虚拟网络                  │
│                                                 │
│    ┌──────────┐        ┌──────────┐            │
│    │ Player A │◄──P2P──►│ Player B │            │
│    └────┬─────┘        └────┬─────┘            │
│         │   ┌──────────┐    │                  │
│         └───│ Player C │────┘                  │
│             └──────────┘                       │
│  • 每个节点有持久唯一 ID                         │
│  • 全互联 P2P，无中心节点                        │
│  • 中继服务器仅作为备用信令/转发                  │
└─────────────────────────────────────────────────┘
```

### 加入流程

```
1. B 输入 A 的 IP:port:password → 连接 A
2. B → A: hello (B 的 ID, IP, port)
3. A → B: room_info {
     name, password_hash,
     members: [{id, ip, port}],  // A 记录的全体成员
     stage_host: {id, ip, port}
   }
4. B 遍历 members，尝试与每个其他成员建立直连 (P2P)
   ├─ 直连成功 → 建立本地引用（P2P 通道）
   └─ 直连失败 → 回退为通过 A 转发（远程引用指向 A → A 代为转发）
5. A 广播 "player_joined" → 所有人知晓 B → 其他人也尝试与 B 直连
```

### 转发回退

```
B 与 C 无法直连:
  B → A (转发) → C

B 对 C 的引用:
  type: REMOTE
  remoteTarget: A.ip:port + forwardTarget: C.id

B.dereference(cardFromC):
  → 发往 A，A 转发给 C
  → C 执行
  → C 回复 A，A 转发给 B
  → B 渲染结果
```

### 掉线与引用恢复

```
A 掉线:
  1. 心跳超时 → 所有节点检测到
  2. A 相关的所有远程引用 → 空引用
  3. 以 A 为转发者的引用 → 需要引用转移:
     B.forwardTarget 可选列表: [C, D, E...]
     尝试连接 → 找到可直连的 → reference.tryMigrate()
  4. 若 A = 图主 → 图主对象全部空引用 → 等待 A 重连 / 投票新图主 (暂不实现)
```

### 中继服务器回退

如果 NAT 环境导致完全无法 P2P，全部走中继服务器转发。中继服务器此时充当作广播通道和消息路由，不解释游戏逻辑。

## 协议消息定义

### 顶层信封

```typescript
interface Message {
  type: string;          // 消息类型
  source: string;        // 发送方 ID
  seq: number;           // 单调序列号
  target?: string;       // 定向目标 ID（为空则广播）
  timestamp: number;     // 发送时间戳
  // ... type-specific fields
}
```

### 队列消息

| type | 方向 | 说明 |
|------|------|------|
| `queue_packet` | 广播 | 提交卡牌到分布式队列 |
| `queue_complete` | 广播 | 卡牌执行完毕，所有人移除该项 |

```typescript
// 提交卡牌
interface QueuePacketMessage extends Message {
  type: "queue_packet";
  packet_id: string;
  sender_id: string;
  owner_id: string;
  card_id: string;
  resource_hash: string;
  target: string;
}

// 执行完成
interface QueueCompleteMessage extends Message {
  type: "queue_complete";
  packet_id: string;       // 对应的 queue_packet.packet_id
  effects: EffectDescription[];        // 纯数值效果
  operation_sequence: OperationStep[];  // 操作序列（用于操作重放）
}
```

### 引用相关消息

| type | 方向 | 说明 |
|------|------|------|
| `invoke` | 定向 | 远程引用解引用时的网络调用 |
| `invoke_result` | 定向 | 远程执行结果回传 |
| `reference_register` | 广播 | 宣告自己可提供的引用对象清单 |
| `reference_migrate` | 定向 | 引用转移请求（寻备份源） |

```typescript
interface InvokeMessage extends Message {
  type: "invoke";
  target: string;          // 所有者 ID
  ref_id: string;          // 引用 ID
  trigger: string;         // 触发接口 (onCardUse / atBattleStart / ...)
  args: any;               // 调用参数
}

interface InvokeResultMessage extends Message {
  type: "invoke_result";
  target: string;          // 原调用方 ID
  ref_id: string;
  effects: EffectDescription[];
  operation_sequence: OperationStep[];
}
```

### 战斗/状态消息

| type | 方向 | 说明 |
|------|------|------|
| `combat_result` | 图主→广播 | 怪物动作/卡牌执行结果 |
| `monster_intent` | 图主→广播 | 怪物意图变更 |
| `player_state` | 所有者→广播 | 在线角色属性更新 |
| `stage_sync` | 图主→广播 | 地图/事件/怪物状态推送 |
| `full_snapshot` | 图主→广播 | 完整阶段状态快照 |

### 控制消息

| type | 方向 | 说明 |
|------|------|------|
| `hello` | 单向 | 加入时宣告身份和网络信息 |
| `ping` / `pong` | 广播 | 心跳保活 |
| `player_joined` / `player_left` | 广播 | 玩家进出通知 |
| `room_info` | 定向 | 响应 hello，返回房间完整信息 |
| `stage_host_election` | 广播 | 新阶段投票发起 |
| `stage_host_result` | 广播 | 投票结果 |

### 素材消息（保持原有）

| type | 方向 | 说明 |
|------|------|------|
| `resource_registry` | 广播 | 本地素材清单 |
| `resource_request` | 定向 | 请求特定素材 |
| `resource_response` | 定向 | 素材数据 |
| `animation_sync` | 广播 | 角色动画切换 |

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
├── cross-spire-server/          # 备选中继服务器 (Node.js/TypeScript)
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
            │   ├── P2PNetwork.java              # P2P 连接管理
            │   ├── BroadcastManager.java        # 广播消息收发
            │   ├── HeartbeatManager.java        # 心跳检测
            │   ├── RelayClient.java             # 中继服务器回退
            │   └── Protocol.java                # 消息 POJO + 序列化
            ├── reference/
            │   ├── Reference.java               # 引用抽象基类
            │   ├── LocalReference.java          # 本地引用
            │   ├── RemoteReference.java         # 远程引用（网络调用）
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
            │   ├── QueueManager.java           # 分布式广播队列
            │   ├── LocalCapturePatches.java    # 捕获本地操作 → 广播标准包
            │   └── CombatResultReplayer.java   # 操作重放 + suppressEvents
            └── ui/
                ├── LobbyScreen.java            # 加入房间界面
                ├── ServerPicker.java           # IP:port:password 输入
                ├── RoomPanel.java              # 房间内界面
                ├── RoomChat.java               # 聊天
                ├── QueueDisplay.java           # 分布式队列可视化
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
