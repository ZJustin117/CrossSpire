# CrossSpire — 需求规格说明书 (Spec)

## 目录

1. [概述](#概述)
2. [用户故事](#用户故事)
3. [功能需求](#功能需求)
4. [非功能需求](#非功能需求)
5. [约束与边界](#约束与边界)
6. [术语引用](#术语引用)

---

## 概述

CrossSpire 是一个开源的杀戮尖塔1（Slay the Spire 1）多人联机 Mod。核心价值主张：

- **多人同步战斗** — 多个玩家在同一场战斗中操作各自的角色，共享怪物和地图
- **Mod 兼容性** — 每个玩家可以使用自己的 Mod，不要求所有人安装相同 Mod。跨 Mod 交互通过引用系统 + fallback 效果自然运作
- **跨游戏预留** — 架构从协议层为塔2（C#/Godot）→塔1（Java/LibGDX）互联预留空间
- **开源** — 对标闭源的 Together in Spire 的全栈开源替代

核心架构决策（详见 `ARCHITECTURE.md`）：引用模型（Reference<T>）封装执行权和网络路由；星型拓扑（房主路由 O(n) 连接）；房主中央队列编排；诱导重放实现跨 Mod 被动效果触发。

## 用户故事

### US-1 创建与加入房间

**作为** 一名玩家
**我想要** 创建或加入一个多人游戏房间
**以便** 与其他玩家一起进行杀戮尖塔联机冒险

**验收标准：**

```
GIVEN 玩家 A 启动了游戏并进入联机大厅
WHEN  A 创建房间（设置名称、密码、IP:port）
THEN  创建成功，A 成为房主（网络路由角色），等待其他玩家加入

GIVEN 玩家 B 获得了房间中任一成员（如 A）的 IP:port:password
WHEN  B 输入信息并请求加入
THEN  B 与 A 建立连接，A 返回房间完整信息（房主 ID、成员列表、图主 ID）
      B 与房主建立星型连接
      房主广播 player_joined 通知全员
      B 收到完整阶段状态快照并同步显示

GIVEN B 在房间中
WHEN  B 断开连接或心跳超时
THEN  房主检测掉线 → 广播 player_left → 移除中央队列中 B 的待处理项
      若掉线者是房主 → 全员投票选新房主 → 新房主重建路由和队列
      若掉线者是图主 → 图主持有对象全部变空引用 → 自动保存进度等待重连
```

### US-2 多人同步战斗

**作为** 多名联机玩家
**我想要** 在同一场战斗中各自操作角色出牌
**以便** 像本地单人游戏一样体验战斗，同时看到其他玩家的行动

**验收标准：**

```
GIVEN 阶段图主已确定，战斗房间开始
WHEN  战斗开始
THEN  图主统一怪物配置 → 经房主广播 → 全员显示相同怪物
      每个玩家看到自己的手牌、能量、遗物、药水
      在线角色（其他玩家）以缩小姿态显示在战斗场景中

GIVEN 玩家 A 打出 Strike_R，目标是 monster_0
WHEN  A 将卡牌拖拽到目标
THEN  卡牌从 A 手牌悬浮至队列区域（本地移除但不立即执行）
      A → 房主: queue_submit 标准包
      房主插入中央队列 → 排序 → 广播 queue_update → 全员队列 UI 更新

GIVEN 队列头部是 A 的 Strike_R（owner_id = A）
WHEN  房主调度该项
THEN  房主 → A: invoke 标准包
      A 本地 REAL 模式执行 Strike_R：触发钩子、计算伤害、渲染动画
      A → 房主: invoke_result（含 effects + operation_sequence）
      房主 → 全员: combat_result 标准包
      非发送者诱导重放：事件步骤触发本地钩子，效果步骤 suppressEvents 写数值 + VFX

GIVEN 队列全部完成
WHEN  房主广播 queue_empty
THEN  所有玩家的"结束回合"按钮变为可用
```

### US-3 Mod 共存 — 不同 Mod 的玩家在同一个房间

**作为** 安装了自定义 Mod（新卡牌/遗物/角色）的玩家
**我想要** 与其他未安装相同 Mod 的玩家一起游玩
**以便** 我的 Mod 内容能正常运作，不影响他人，也不被他人的 Mod 打断

**验收标准：**

```
GIVEN A 有 Mod 卡牌 Strike_P（resource_hash = H1），B 没有此 Mod
WHEN  A 打出 Strike_P
THEN  queue_submit 标准包携带 resource_hash = H1
      房主调度 → 调用 A 执行（A 是所有者）
      A 本地 REAL 模式执行，触发 A 的 BaseMod 钩子
      房主广播 combat_result：effects + operation_sequence 携带 card_id = "Strike_P"

GIVEN B 收到 combat_result（非发送者）
WHEN  B 进入 INDUCED 模式
THEN  B 按 card_id 查找本地定义
      本地无定义 → 不建立本地引用
      使用 operation_sequence 走事件链（publishOnCardUse 等）
      Stub 对象 use() 为 no-op，不重复产生效果
      效果步骤 suppressEvents 写数值 + VFX

GIVEN A 的 Mod 遗物使用自定义 @SpirePatch 监听"打出攻击牌"
WHEN  B 打出自己的攻击牌（B 是所有者，B 本地 REAL 执行）
THEN  combat_result 广播到 A
      A INDUCED 重放 play_card 步骤 → CardStub → useCard(stubCard)
      → useCard() 上的 @SpirePatch 全量触发 → A 的自定义遗物被触发 ✓
      → useCard() 内部 publishOnCardUse() 自然触发 → BaseMod 订阅者 ✓
      遗物产生新效果 → 提交房主队列 → 广播全员

GIVEN A 的 Mod v1.0 和 B 的 Mod v1.1 拥有同名卡牌 Strike_P，但 resource_hash 不同
WHEN  B 尝试使用 A 的 Strike_P
THEN  内容校验失败 → B 不建立本地引用 → 始终走远程引用 → 调用 A 执行
```

### US-4 地图、事件与阶段推进

**作为** 联机玩家
**我想要** 按阶段（Act）推进，经历战斗、事件、商店
**以便** 完整的尖塔冒险得以在多人模式下进行

**验收标准：**

```
GIVEN 当前阶段将要开始或结束
WHEN  需要选定下一个阶段的图主
THEN  房主发起 stage_host_election 投票 → 全员投票 → 图主确定

GIVEN 图主已确定
WHEN  进入新阶段
THEN  图主用本地 RNG 生成地图 → 经房主广播 → 全员覆盖本地地图
      图主决定房间类型和内容
      战斗 → 图主配置怪物 →（走 US-2 战斗流程）
      事件 → 图主实例化 AbstractEvent →（走事件流程）
      商店 → 图主确定商品

GIVEN 队伍进入事件房间
WHEN  图主实例化事件
THEN  图主捕获 event_interface（名称、描述、选项、图片引用）→ 经房主广播
      全员渲染相同事件 UI

GIVEN 事件有 3 个选项，选项 2 需要 50 金币
WHEN  某玩家的金币不足 50
THEN  该玩家界面上选项 2 灰显（disabled = true），无法选择

GIVEN 某玩家选择了事件选项 1
WHEN  房主转发 event_select 到图主
THEN  图主调用 event.buttonEffect(1) → 事件逻辑在图主本地执行（REAL 模式）
      若 transitionKey → 图主重新捕获新阶段界面 → 广播 event_interface
      若 openMap → 图主广播 event_result（含所有产出）
      若进入战斗 → 图主走战斗流程

GIVEN 事件选项为"选择一张牌移除"（需要交互选择）
WHEN  图主执行中触发 GridCardSelectScreen
THEN  图主捕获选择参数 → interact_request 标准包 → 房主转发给做出选择的玩家
      该玩家本地渲染选择 UI → 选择完成 → interact_response 回传
      图主继续执行事件逻辑
```

### US-5 Mod 素材互通

**作为** 使用自定义 Mod 角色/卡牌/遗物的玩家
**我想要** 其他玩家能看到我的 Mod 素材（卡牌图、遗物图标、角色骨骼）
**以便** 即使他们没有安装我的 Mod，也能看到完整的视觉体验

**验收标准：**

```
GIVEN A 使用自定义角色骨架，B 没有该素材
WHEN  A 加入房间
THEN  A 发送 resource_registry 标准包（含所有本地素材清单）
      房主转发 → 全员记录 A 的素材清单

GIVEN B 需要渲染 A 的角色骨骼动画
WHEN  B 本地未缓存 A 的角色素材
THEN  B 三层就近查找：
      1. 本地卡池（失败—B 没有该 Mod）
      2. 磁盘缓存（失败—首次见到）
      3. resource_request → 房主路由 → A
      A 从本地文件按路径读取 skeleton.json/atlas/png → resource_response
      B 写入磁盘缓存 → 内存加载 → 渲染

GIVEN B 已缓存过 A 的某卡牌素材
WHEN  该卡牌再次出现
THEN  B 从 L2 磁盘缓存直接加载，零网络传输

GIVEN A 打出卡牌，卡牌上有特效动画
WHEN  A 的 invoke_result 广播后
THEN  B 的 INDUCED 重放自然产生 VFX（由本地引擎渲染）
      B 使用素材传递系统获取该卡牌的图素材用于 UI 显示
      骨骼动画同步通过 animation_sync 标准包传递（动画名、track、loop）
```

### US-6 掉线与恢复

**作为** 联机玩家
**我想要** 在队友掉线时游戏不崩溃，有明确的等待和恢复机制
**以便** 偶然的网络波动不会中断游戏体验

**验收标准：**

```
GIVEN 普通客户端 C 掉线
WHEN  房主检测 C 心跳超时
THEN  房主广播 player_left(C)
      移除中央队列中所有 owner_id = C 的待处理项
      C 的角色在 UI 上标记为"离线"
      C 持有的远程引用全部变空引用

GIVEN 图主掉线
WHEN  全员检测图主心跳超时
THEN  图主持有的地图/怪物/事件引用全部变空引用
      自动保存当前进度到本地磁盘
      全员界面显示"等待图主..."
      不尝试自动迁移（当前版本）

GIVEN 房主掉线
WHEN  全员检测房主心跳超时
THEN  发起 host_election 投票 → 选出新房主
      新房主拉取所有在线角色完整状态快照
      重建中央队列（清空旧房主的待处理项）
      重建星型路由连接
      游戏继续

GIVEN 掉线的玩家 C 重新连入
WHEN  C 向当前房主发送 hello
THEN  房主返回 room_info + 当前阶段 full_snapshot
      C 同步状态 → 恢复 UI → 重新加入战斗
```

### US-7 所有者交互选择

**作为** 联机玩家
**我想要** 在执行需要选择的卡牌/遗物效果时能从调用方收到交互提示
**以便** 像单人游戏一样自然地做出选择（选牌、选目标等）

**验收标准：**

```
GIVEN A 作为卡牌所有者正在执行 invoke，效果要求"选择手牌中一张丢弃"
WHEN  触发 HandCardSelectScreen
THEN  A 捕获选择参数 → interact_request 标准包 → 房主转发给调用方
      调用方本地渲染手牌选择 UI → 选择一张牌
      → interact_response 回传 → A 继续执行 → 产出 invoke_result

GIVEN 调用方在超时时间内未做出选择
WHEN  超时触发
THEN  所有者按默认策略（取第一个选项 / 随机 / 取消）继续执行
      房主通知调用方交互已超时
```

### US-8 塔2跨游戏连接（未来）

**作为** 塔2玩家
**我想要** 与塔1玩家联机
**以便** 两代游戏的玩家可以一起冒险

**验收标准（未来实现）：**

```
GIVEN 塔2 玩家启动 CrossSpire for STS2
WHEN  通过相同协议加入塔1玩家的房间
THEN  塔2 客户端实现相同的 StandardPacket 协议 + Reference<T> 抽象
      塔2 本地存在的实体（如 Strike_R 两代共有）→ 内容校验通过 → 本地引用
      塔1 Mod 实体 → 永远走远程引用 + fallback 效果通道
      实体映射表翻译 card_id / relic_id / character_id
```

## 功能需求

### FR-1 房间系统

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-1.1 | 创建房间：定名称、密码、监听 IP:port | US-1 |
| FR-1.2 | 加入房间：输入任一成员 IP:port:password，获取房间信息并建立连接 | US-1 |
| FR-1.3 | 星型拓扑：房主维护 O(n) 连接，所有消息经房主路由 | US-1 |
| FR-1.4 | 心跳保活：房主与所有客户端间定期 ping/pong | US-1, US-6 |
| FR-1.5 | 房主迁移：房主掉线 → 投票选新房主 → 状态重建 | US-6 |

### FR-2 战斗系统

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-2.1 | 中央队列：房主接收 queue_submit → 排序 → 逐个调度 | US-2 |
| FR-2.2 | 引用解引用：远程 invoke → owner 本地 REAL 执行 → invoke_result 回传 | US-2 |
| FR-2.3 | combat_result 广播：房主将执行结果转发全员 | US-2 |
| FR-2.4 | 诱导重放（INDUCED 模式）：非发送者走操作序列，事件步骤触发钩子，效果步骤 suppressEvents | US-2, US-3, US-7 |
| FR-2.5 | 回合结束：队列清空后房主广播 queue_empty，全员可结束回合 | US-2 |
| FR-2.6 | 怪物系统：图主统一管理 → 意图广播 → 动作执行 → 结果同步 | US-2, US-4 |
| FR-2.7 | 在线角色渲染：RemotePlayer overrides AbstractPlayer，BaseMod RenderSubscriber 渲染 | US-2 |

### FR-3 Mod 兼容性

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-3.1 | 内容校验：resource_hash (SHA-256) 比对，决定本地引用 vs 远程引用 | US-3 |
| FR-3.2 | 分层引用：hash 一致 → 本地引用；不一致/无定义 → 远程引用 + fallback | US-3 |
| FR-3.3 | 交叉引用：远程遗物被本地事件触发 / 本地遗物被远程事件触发 | US-3 |
| FR-3.4 | 诱导重放深层触发：no-op Stub → 调用真实游戏方法 → @SpirePatch + BaseMod 全量执行 | US-3, US-7 |
| FR-3.5 | Fallback 效果类型：13 种（damage/block/power/heal/energy/draw/discard/exhaust/gold/relic/potion/lose_hp） | US-3 |

### FR-4 地图与事件

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-4.1 | 图主投票：每阶段开始前房主发起投票，决定图主 | US-4 |
| FR-4.2 | 地图生成：图主用本地 RNG 生成 → 广播覆盖全员 | US-4 |
| FR-4.3 | 房间决定：图主确定战斗/事件/商店类型和内容 | US-4 |
| FR-4.4 | 事件接口捕获：图主捕获 event_interface → 广播全员渲染 | US-4 |
| FR-4.5 | 事件选择：任意玩家可选 → event_select → 图主执行 buttonEffect → 广播结果 | US-4 |
| FR-4.6 | 所有者交互选择：invoke 中触发的选择面板（选牌/选目标等）回传给调用方 UI | US-4, US-7 |

### FR-5 素材系统

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-5.1 | 素材注册：连接时交换 resource_registry（卡牌/遗物/能力/角色/药水清单） | US-5 |
| FR-5.2 | 三层就近查找：本地卡池 → 磁盘缓存 → resource_request | US-5 |
| FR-5.3 | 素材传输：resource_request/response 经房主路由，含 checksum 校验 | US-5 |
| FR-5.4 | 缓存系统：L1 内存 (128MB LRU) + L2 磁盘 ({game_dir}/crossspire_cache/) | US-5 |
| FR-5.5 | 骨骼动画同步：animation_sync 标准包传递动画状态 | US-5 |

### FR-6 RNG 策略

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-6.1 | 独立本地种子：每玩家维护自己的 RNG，不统筹全局种子 | US-4, US-2 |
| FR-6.2 | 执行者 RNG 归属：谁执行就用谁的 RNG（图主/卡牌所有者/本地） | US-4, US-2 |
| FR-6.3 | 结果广播：RNG 结果通过标准包广播，不需要复现 | US-4, US-2 |

## 非功能需求

| ID | 类别 | 需求 |
|----|------|------|
| NFR-1 | 性能 | 4 人房间，卡牌提交到结果渲染总延迟 < 500ms（局域网） |
| NFR-2 | 性能 | 浏览器/UI 帧率不低于 30fps（与单人模式一致） |
| NFR-3 | 兼容性 | 兼容 ModTheSpire + BaseMod + StSLib 及其他主流内容 Mod |
| NFR-4 | 兼容性 | `@SpirePatch` 使用 `optional = true` 标记可选 Mod 的 patch |
| NFR-5 | 可靠性 | 房间掉线后 30s 内自动检测并进入恢复流程 |
| NFR-6 | 可靠性 | 图主掉线 → 自动保存进度（本地磁盘），不低于 5 分钟一次 |
| NFR-7 | 协议 | 所有引用传输使用 StandardPacket 信封 + JSON 编码 |
| NFR-8 | 协议 | 标准包内 `resource_hash` 必须以 SHA-256 计算 |
| NFR-9 | 协议 | 协议版本向后兼容（主版本内不破坏现有消息格式） |
| NFR-10 | 安全 | 不传输/存储任何用户个人身份信息（PII） |
| NFR-11 | 安全 | 不在日志或网络传输中泄露本地文件系统路径 |

## 约束与边界

### 当前版本做

- 1-4 人联机（集中在 4 人场景）
- 塔1 基础角色 + Mod 角色同房
- 房主路由星型拓扑
- 图主掉线 → 暂停等待，不迁移
- 事件不投票（一人选择即生效）

### 当前版本不做

- P2P 全互联（O(n²) 连接已淘汰）
- 图主在线迁移
- 跨回合存档分享（仅本地保存）
- 塔2 实际联机实现（仅预留架构）
- 超过 4 人的房间
- 移动端/主机端支持

### 技术约束

- 依赖：ModTheSpire 3.30+, BaseMod 5.54+, Java 8, Gradle
- 运行时仅支持 Desktop (LibGDX)，不涉及 Android/iOS
- 所有网络 IO 在独立线程，不阻塞游戏主循环
- 禁止将 `AbstractCreature.sr` 或 `TextureAtlas` 序列化传输（素材通过文件路径 + 原始字节传输）

## 术语引用

以下术语的定义和维护位置为 `ARCHITECTURE.md`。spec 中使用这些术语时，含义以 ARCHITECTURE.md 为准。

| 术语 | ARCHITECTURE.md 章节 |
|------|---------------------|
| 引用 / 本地引用 / 远程引用 / 空引用 | §2, §4 |
| 解引用 / 引用退化 / 引用转移 | §2, §4 |
| 标准包 (StandardPacket) | §2, §17 |
| 房主 / 图主 | §2, §6 |
| 诱导重放 / REAL 模式 / INDUCED 模式 | §2, §4 |
| 内容校验 / resource_hash | §2, §11 |
| 分层引用 / 交叉引用 | §11 |
| Fallback 效果类型 | §11 |
