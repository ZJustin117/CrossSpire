# CrossSpire — 需求规格说明书 (Spec)

## 目录

1. [概述](#概述)
2. [用户故事](#用户故事)
3. [功能需求](#功能需求)
4. [非功能需求](#非功能需求)
5. [约束与边界](#约束与边界)
6. [术语引用](#术语引用)

（非功能需求含「平台与调试验收」「验证与测试验收」。）

---

## 概述

CrossSpire 是一个开源的杀戮尖塔1（Slay the Spire 1）多人联机 Mod。核心价值主张：

- **多人同步战斗** — 多个玩家在同一场战斗中操作各自的角色，共享怪物和地图
- **Mod 兼容性** — 每个玩家可以使用自己的 Mod，不要求所有人安装相同 Mod。跨 Mod 交互通过引用系统 + fallback 效果自然运作
- **跨游戏预留** — 架构从协议层为塔2（C#/Godot）→塔1（Java/LibGDX）互联预留空间
- **开源** — 对标闭源的 Together in Spire 的全栈开源替代

核心架构决策（详见 `ARCHITECTURE.md`）：引用模型（Reference<T>）封装执行权和网络路由；星型拓扑由房主路由 O(n) 连接；小队队长按 `party_id` 协调本队中央队列与战斗阶段；诱导重放 = 权威状态写入 + **仅逻辑所有者（施加者优先）** 的被动自发执行，禁止全员全量 hook 重算。

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
THEN  房主检测掉线 → 广播 player_left → 移除 B 所在小队队列的待处理项并重选该队队长
       若掉线者是房主 → 全员投票选新房主 → 新房主重建路由和小队目录
      若掉线者是图主 → 图主持有对象全部变空引用 → 自动保存进度等待重连
```

### US-1a Ready / Play 双机开局

**作为** 已入房的联机玩家
**我想要** 双方 ready 后通过 play 共同开局
**以便** 每人在本地真正进入 `GAMEPLAY`，而不是仅房间连接或被动旁路进战

**验收标准：**

```
GIVEN D1 host、D2 join，room size=2，双方在默认小队 P0
WHEN  D1 与 D2 分别执行 ready [char]
THEN  双方交换 player_ready；目录记录各成员角色；未全员 ready 前不得 party_run_start

GIVEN 全员 ready
WHEN  任一方执行 play/start（控制台 crossspire start 或大厅 Play）
THEN  本队队长（或房主代发）广播 party_run_start {party_id, seed, act, members[{player_id, character}], leader_id}
      各端用本机 ready 角色 + 共享 seed 调用 GameStarter.start
      双端均进入 CardCrawlGame.mode=GAMEPLAY 且 AbstractDungeon.player 非空
      禁止将 createGameIfNeeded(IRONCLAD) 旁路作为共进主路径的 D2 开局手段

GIVEN 未全员 ready
WHEN  某方执行 start/play
THEN  拒绝并记录日志；不广播 party_run_start

说明：历史 E2E 中「仅 D1 start + BaseMod fight + D2 room_enter 诱导」仅为 host-spawn combat projection 回归，
不得当作双人同场共进验收。
```

### US-2 多人同步战斗

**作为** 多名联机玩家
**我想要** 在同一场战斗中各自操作角色出牌
**以便** 像本地单人游戏一样体验战斗，同时看到其他玩家的行动

**验收标准：**

```
GIVEN 本队 NodeInstanceHost 和小队队长已确定，双方经 room_pin 共识进入同一 node_instance
WHEN  战斗开始
THEN  双端本地 MonsterRoom 绑定同一 node_instance_id 与 encounter
      NodeInstanceHost 统一怪物配置 → 经房主向本队广播 → 全队显示相同怪物
      每个玩家看到自己的手牌、能量、遗物、药水（金币与 HP 一样为个人经济，不共享余额）
       同小队在线角色以缩小姿态显示在战斗场景中；combat_phase 由队长按 party_id 统一

GIVEN 玩家 A 打出 Strike_R，目标是 monster_0
WHEN  A 将卡牌拖拽到目标
THEN  卡牌从 A 手牌悬浮至队列区域（本地移除但不立即执行）
       A → 房主 → 本队队长: queue_submit（party_id）标准包
       队长插入本队中央队列 → 排序 → 经房主广播 queue_update → 本队队列 UI 更新

GIVEN 队列头部是 A 的 Strike_R（owner_id = A）
WHEN  本队队长调度该项
THEN  队长 → 房主 → A: invoke 标准包
      A 本地 REAL 模式执行 Strike_R：触发钩子、计算伤害、渲染动画
       A → 房主 → 队长: invoke_result（含 effects + operation_sequence）
       队长 → 房主 → 本队: combat_result（executor_id = A，不得改写为队长或房主）
      非执行者：AUTHORITATIVE_APPLY（suppressEvents 写数值/VFX）
                + LOCAL_OWNER_ONLY（仅 TriggerRegistry 中 ownerId==self 的被动）
      禁止无门控 BaseMod.publishOnCardUse / 全量 useCard hook 重算

GIVEN 本队队列全部完成
WHEN  队长经房主广播 queue_empty
THEN  本队玩家的"结束回合"按钮变为可用
       该信号同时作为本队战斗阶段对齐的一部分

GIVEN A 对怪物施加原版 Vulnerable（logic_owner_id = A）  [P5/T5.2 验收基准]
WHEN  队长经房主向本队广播 combat_result 且非执行者 AUTHORITATIVE_APPLY
THEN  本队怪物上有 Vulnerable 层数投影；登记 ComponentAttachment(logic_owner=A)
      非 A 节点不因该 power 产生无门控二次 combat_result 环

GIVEN A 对怪物施加灾厄（logic_owner_id = A）  [P5/T5.3+ 目标；当前无灾厄内容，mutation 延后]
WHEN  队长同步本队阶段后本地进入对应触发阶段
THEN  仅 A 的灾厄逻辑应自发执行；其他节点同名投影无效果
       A 若判定击杀 → monster_mutation_proposal → NodeInstanceHost commit → 全队只写权威状态
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

GIVEN B 收到 combat_result（executor_id = A，B 非执行者）
WHEN  B 处理该消息
THEN  B 做 AUTHORITATIVE_APPLY：suppressEvents 写 effects 数值/VFX
      B 不调用 BaseMod.publishOnCardUse（禁止无门控全量 hook）
      B 仅 fire TriggerRegistry 中 ownerId==B 的 onCardUse 条目（LOCAL_OWNER_ONLY）

GIVEN A 通过 TriggerRegistry 注册了 owner=A 的 onCardUse 被动
WHEN  B 打出自己的攻击牌（B REAL 执行）且房主广播 combat_result
THEN  A：AUTHORITATIVE_APPLY 写入 B 的权威效果
      A：LOCAL_OWNER_ONLY 仅 dereference A 的 TriggerRegistry 条目 → 新 effects 可提交（origin_owner_id + hop_count）
      B 及其他节点不执行 A 的被动逻辑
      注：未注册到 TriggerRegistry 的裸 @SpirePatch 不会被 induced 自动触发（契约：须经所有权门控）

GIVEN 怪物上有 A 施加的原版 Vulnerable（logic_owner_id = A）  [P5/T5.2]
WHEN  非 A 节点收到 combat_result
THEN  AUTHORITATIVE_APPLY 写层数投影；LOCAL_OWNER_ONLY 不 fire A 的 power 逻辑
      PowerStub/投影回调在非 owner 上 no-op

GIVEN 怪物上有 A 施加的灾厄（logic_owner_id = A），NodeInstanceHost 可以是 B  [P5/T5.3+；当前无灾厄内容]
WHEN  房主同步阶段后本地进入触发时机
THEN  仅 A 应自发执行灾厄；B/其他人同名投影无效果、不可触发
       A → NodeInstanceHost proposal → NodeInstanceHost commit 死亡/HP → 全队状态一致
       不存在 NodeInstanceHost 扫描 attachment 再 invoke A

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
GIVEN 小队完成当前阶段
WHEN  房主为该小队打开不属于任何地图或节点的 STAGE_TRANSITION
THEN  只有该小队成员参与组队、选图和主机选择
       其他仍在活动地图或节点中的小队不被阻塞
       过渡期间不得创建怪物、事件、商店、宝箱、篝火或消耗房间生成遗物状态

GIVEN 小队在 STAGE_TRANSITION 中选择创建新地图
WHEN  本队全体在线成员对 MapHost 候选人达成一致
THEN  房主广播 map_host_result
       MapHost 用本地 RNG 生成不可变 MapDefinition 并以 map_register 登记到房主 MapRegistry
       MapDefinition 包含节点 ID、坐标、路径、基础房间类别/图标、燃烧精英和 Boss descriptor
       不包含怪物实例、问号最终结果、事件选项、商店库存、宝箱奖励或篝火选项
       map_register 成功后 MapHost 不再参与该地图的任何节点、战斗、事件或内容执行

GIVEN 小队在 STAGE_TRANSITION 中选择加入同阶段的已登记地图
WHEN  房主验证该 MapInstance 可加入
THEN  房主将该小队绑定到既有 map_instance_id，并只向本队发送不可变 MapDefinition
       本队不重新选举该地图的 MapHost，也不得修改地图定义

GIVEN 小队已经创建或加入 MapInstance
WHEN  本队全体在线成员对 NodeInstanceHost 候选人达成一致并确认地图快照
THEN  房主记录该小队唯一的 node_instance_host_id，广播 stage_transition_complete
       小队进入 MAP_ACTIVE，从起点开始地图标注
       NodeInstanceHost 可以等于队长或 MapHost，但两个身份在协议和目录中独立

GIVEN 小队进入事件节点
WHEN  本队 NodeInstanceHost 实例化事件
THEN  NodeInstanceHost 广播 event_interface（event_instance_id、party_id、node_instance_id、名称、描述、选项、event_class 全限定名、resource_hash、mode）
       内容校验通过的本小队成员在本地通过 Class.forName 构建同名事件实例并原生渲染 UI
       无事件类或 resource_hash 不匹配的成员显示 RemoteEventDisplay fallback UI

GIVEN 事件有 3 个选项，选项 2 需要 50 金币
WHEN  某玩家的金币不足 50
THEN  该玩家界面上选项 2 灰显（disabled = true），无法选择

GIVEN D2 玩家通过原生 UI 选择了事件选项 1
WHEN  原生选择 patch 在 buttonEffect(1) 造成副作用前拦截选择
THEN  D2 发送 event_choice_request（event_instance_id、party_id、request_id、UI step、选项和选卡/目标）给房主
       非 voting 事件中，房主校验事件、小队成员资格、选项与 UI step 后独立批准该玩家的有效请求
       D2 仅在收到 event_choice_approved 后继续本地 buttonEffect，产出自己的 HP、金币、牌、遗物和药水变化
       D2 上报 event_player_result；房主记录并向本小队广播该玩家的状态差量/快照
       同一 request_id 的重复包、过期批准和不匹配 UI step 不得重复执行

GIVEN 事件选项为"选择一张牌移除"（需要交互选择）
WHEN  D2 的本地匹配事件打开 GridCardSelectScreen
THEN  D2 使用原生选牌 UI；确认的卡片 ID 连同 UI step 进入 event_choice_request
       房主批准后 D2 恢复本地事件流程并执行该选择

GIVEN 事件需要集体决策（如全队决定 fight/leave）
WHEN  event_interface.mode = "voting"
THEN  房主按 party_id 聚合 event_choice_request
       全队成员对相同合法选项达成一致后才广播 event_choice_approved
       未一致 → 广播 event_votes 快照，等待改票

GIVEN D2 未安装 NodeInstanceHost 的 Mod 事件类
WHEN  Class.forName 失败或 resource_hash 不匹配
THEN  D2 显示 RemoteEventDisplay 并仍发送 event_choice_request
       批准后由 NodeInstanceHost 执行该玩家的选择，定向应用其个人结果，并经房主同步其状态差量

GIVEN 非 voting 事件的个人选项产生事件内战斗/特殊房间
WHEN  多名成员选择同一房间产生选项
THEN  NodeInstanceHost 生成一个带稳定 instance_id 的事件内房间
       选择该选项的成员进入同一小队路径；该小队的队长管理队列与阶段，NodeInstanceHost 确定房间内容
       event_player_result.shared_outcome type=event_room 携带 encounter 与 member_ids
       成员本地安装共享 STS 战斗壳（MonsterRoom）并锁定导航直至 RIH unlock

GIVEN 成员要离开事件并前往地图上的其他节点
WHEN  该结果不能与当前小队共享
THEN  成员必须先离开当前小队，保留当前地图节点
       新单人小队或已加入的小队再通过本队地图共识选择相邻节点
```

### US-4a 房间标注（进入下一个房间的共识投票）

**作为** 小队成员
**我想要** 在地图上选择下一个要进入的房间，并与同小队成员达成一致
**以便** 队伍协调推进，而不是各自走到不同房间

**验收标准：**

```
GIVEN 队伍已清空当前房间（战斗结束/事件完成），站在地图节点上
AND   本队 map 已绑定、成员均已 GAMEPLAY、NodeInstanceHost 已确定
WHEN  玩家 A 在控制台输入 crossspire room 1
THEN  A 发送 room_pin {party_id, room: 1} 到房主，再由房主路由给本队队长
       队长记录 A 的标注 → 经房主向本小队广播 room_pins
       本小队客户端可看到当前的标注分布
       未 GAMEPLAY / 未 map 绑定 / exit_unlocked=false 时 pin 被拒绝

GIVEN 队长记录 {A: 1, B: 1} — 所有在线小队成员标注一致
WHEN  队长检测到本小队共识
THEN  队长经房主发送 room_consensus {party_id, map_instance_id, node_id} 给房主
       房主校验小队地图绑定、当前位置和边可达性，为本队分配或返回幂等的 NodeInstance
       房主将 node_instance_allocate 路由给本队 NodeInstanceHost
       NodeInstanceHost 在本地生成节点内容，并按 party_id 同步该小队
       本队全员自动 open 同一 node_instance_id（双端本地进房，投影模型）

GIVEN 本队处于任一活动 RoomInstance 且 exit_unlocked=false
WHEN  任一方 room_pin
THEN  pin 被拒绝（统一导航门控；含战斗奖励未结束、商店/篝火未由实例主解锁）

GIVEN 玩家 A 已标注 room 2，后改为 room 1
WHEN  A 再次输入 crossspire room 1
THEN  队长更新 A 的标注为 room 1 → 经房主广播 room_pins
       重新检测共识

GIVEN 不同玩家标注了不同房间
WHEN  尚无全员一致
THEN  队长继续等待，不做任何房间切换
       无自动超时或默认选择——等待全员达成一致
```

### US-4d 战斗胜利与奖励屏

**作为** 刚打完同一 node_instance 战斗的小队
**我想要** 各自完成完整 STS 奖励屏，再一起投票下一房间
**以便** 个人构筑推进且队伍仍协调进图

**验收标准：**

```
GIVEN 本队在同一 node_instance 战斗中全部怪物死亡
WHEN  NodeInstanceHost / 队长广播 reward_phase_enter（party_id, node_instance_id）
THEN  本队各端打开完整 CombatRewardScreen（卡/药水/遗物等按 STS 流程）
      NIH 可下发 reward_offer（候选池由 node_instance_id 派生，可复现）
      每人独立选卡/跳过；reward_pick / reward_player_result 只影响该玩家

GIVEN 玩家获得金币或选卡
WHEN  本地 apply
THEN  金币与 HP 一样为个人经济：A 的 gainGold 不得写入 B 的余额
      远端仅可选展示投影（player_state），不做共享金库

GIVEN 成员关闭奖励屏 / Proceed
WHEN  发送 reward_done
THEN  队长聚合；全员 done 后广播 reward_phase_complete
      小队 map_position 保持刚结束的战斗节点；允许再次 room_pin 投票下一房
```

### US-4e 统一房间实例与地图解锁

**作为** 小队
**我想要** 所有房间类型共用同一实例管线，并由房间实例主决定何时解锁地图选点
**以便** 商人/篝火/战斗/事件一致 follow，且不同步商店货架或篝火选项

**验收标准：**

```
GIVEN 本队 pin 共识进入任意 room_type（monster/event/shop/rest/treasure/elite/boss）
WHEN  房间实例主 node_generation_commit + node_instance_opened
THEN  全队同一 room_instance_id、同一 room_type 枚举
      shop/rest 仅同步「这是商人/篝火」身份，不同步 inventory 或 rest 选项
      商品、篝火选项与结果均本地 STS/Mod 逻辑

GIVEN 房间实例主在本地解锁地图（继续箭头 / 离开房间 / 战斗奖励完成等）
WHEN  广播 room_exit_unlocked {party_id, room_instance_id}
THEN  本队各端 exit_unlocked=true，可显示继续箭头并接受 room_pin
      非房间实例主不得擅自将全队标为 unlocked

GIVEN 新的 node_instance_opened 到达本队
WHEN  成员仍在旧 RoomInstance
THEN  强制离开旧实例并进入新实例（force-follow）
```

### US-4b 过渡阶段与主机选举

**作为** 联机玩家
**我想要** 在每阶段开始前与小队完成组队、选图和主机选举
**以便** 明确地图生成与节点内容的责任人，且不阻塞其他小队

**验收标准：**

```
GIVEN 小队处于 STAGE_TRANSITION 且选择创建新地图
WHEN  每位在线成员发送 map_host_vote {party_id, candidate_id}
THEN  房主只在该小队内聚合选票并广播 map_host_votes

GIVEN 本队所有在线成员投票给同一 player_id
WHEN  房主检测到本队一致
THEN  房主广播 map_host_result {party_id, map_host_id}
       该 MapHost 只生成并登记 MapDefinition

GIVEN 小队已创建或加入地图
WHEN  每位在线成员发送 node_instance_host_vote {party_id, candidate_id}
THEN  房主只在该小队内聚合选票；全员一致后广播 node_instance_host_result
       获选者成为本队唯一 NodeInstanceHost，负责本队每个节点实例的内容权威

GIVEN 玩家 A 已投票 bob，后改为 alice
WHEN  A 重复发送同一主机角色的 vote
THEN  房主更新 A 的选票 → 广播该角色的本队 votes
      重新检测共识

GIVEN 本队成员投票给不同候选者
WHEN  尚无全员一致
THEN  房主继续等待，不做任何切换
        无自动超时或默认选择——等待全员达成一致
```

### US-4c 小队管理

**作为** 房间中的玩家
**我想要** 显式离开或加入小队
**以便** 与同队成员共享战斗、地图选择和可见性，同时允许不同小队在同一阶段分开推进

**验收标准：**

```
GIVEN 房间刚建立
WHEN  房间成员完成加入
THEN  全部成员属于同一个默认小队
       小队队长为成员 ID 字典序最小者
        房主拥有网络连接、小队目录、MapRegistry 和 NodeInstanceRegistry
        已激活地图的小队各自拥有唯一 NodeInstanceHost；MapHost 不拥有房间内容

GIVEN A 在小队 P 中
WHEN  A 执行 crossspire party leave
THEN  A 离开 P 并创建仅含 A 的新小队，保留当前地图节点
       房主广播 party_snapshot；P 与新小队按成员 ID 分别确定队长
       A 不再看见 P 的角色、队列、战斗阶段或地图标注

GIVEN A 想加入小队 Q
WHEN  A 执行 crossspire party join <party_id>
THEN  房主将 party_join_request 路由给 Q 的队长
       队长批准后房主广播 party_snapshot，A 成为 Q 成员并按规则重新确定队长

GIVEN 小队队长掉线
WHEN  房主移除该成员
THEN  剩余成员 ID 字典序最小者成为新的队长
        房主广播 party_leader_changed 与 party_snapshot

GIVEN 小队的 NodeInstanceHost 掉线
WHEN  该小队仍有活动 NodeInstance
THEN  房主暂停该小队节点流程并打开本队 NodeInstanceHost 重选/恢复流程
       新主机只能从已验证的节点快照恢复后继续，MapHost 不接管该节点

GIVEN 两个不同小队绑定同一个 MapInstance
WHEN  两队进入相同 node_id
THEN  房主创建不同 node_instance_id，键为 (map_instance_id, party_id, node_id, visit_id)
       怪物、事件、商店、宝箱、生成遗物状态和战斗状态完全隔离
       本期不得相遇、合并或共享战斗
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
THEN  B 的 AUTHORITATIVE_APPLY 写入数值并播放 VFX（不执行 A 的 buff 逻辑）
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
       移除 C 所在小队中央队列中所有 owner_id = C 的待处理项
       按需要重新选举该小队队长
      C 的角色在 UI 上标记为"离线"
      C 持有的远程引用全部变空引用

GIVEN 已完成 map_register 的 MapHost 掉线
WHEN  房主检测心跳超时
THEN  MapRegistry 继续提供已登记 MapDefinition；MapHost 无需重选

GIVEN NodeInstanceHost 掉线
WHEN  房主检测该小队仍有活动节点实例
THEN  房主暂停该小队节点流程并广播等待节点实例主恢复/重选状态
       本队重新选出 NodeInstanceHost，并以节点快照恢复后才继续
       MapHost 不接管怪物、事件或节点状态

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

### US-9 游戏内联机操作（P8）

**作为** 联机玩家
**我想要** 在游戏 UI 中完成建房、准备、投票与选房
**以便** 无需打开 BaseMod console 即可双机共进体验

**验收标准：**

```
GIVEN 双机加载同一 CrossSpire JAR
WHEN  D1 在 Lobby 点 Host，D2 点 Join（或等价 UI 端口）
THEN  建立星型房间，双方 ID 与连接状态可见

GIVEN 房间已连接
WHEN  双方点击角色选择屏按钮图标并 Ready，再 Play
THEN  全员 ready 后 party_run_start，双端进入 GAMEPLAY

GIVEN 本队需选举 MapHost / NodeInstanceHost
WHEN  玩家点击 Vote 面板中成员旁的角色图标
THEN  发送与 console maphost/nodehost 相同路径的选票；图标为 charSelect/*Button.png

GIVEN map 已绑定且 exit_unlocked
WHEN  玩家在 Pin 面板选择出边
THEN  发送 room_pin；本队 pins 快照以成员角色图标展示；共识后同 node_instance 进房

说明：Harness 自动化可继续用 console；UI 与 console 必须走同一 actions 层。
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

| ID | 需求 | 关联故事 | 实现状态 |
|----|------|---------|---------|
| FR-2.1 | 小队中央队列：队长调度本队的 queue_submit；房主只路由 | US-2, US-4c | 已实现（P7 party_id 隔离） |
| FR-2.2 | 引用解引用：远程 invoke → owner 本地 REAL 执行 → invoke_result 回传 | US-2 | 已实现 |
| FR-2.3 | combat_result 广播：房主转发全员；保留 `executor_id`（不得改写为房主） | US-2 | 已实现（MessageRouter/QueueComplete） |
| FR-2.4 | 诱导重放：AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY（TriggerRegistry owner==self）；禁止 publishOnCardUse 全量 hook | US-2, US-3, US-7 | 已实现（CombatResultReplayer + LocalOwnerGate） |
| FR-2.5 | 回合结束：本队队列清空后队长广播 queue_empty，本队成员可结束回合 | US-2, US-4c | 已实现（P7 队长聚合） |
| FR-2.6 | 怪物核心状态：NodeInstanceHost 意图/AI；附着 buff 归施加者自发 | US-2, US-4 | 部分：意图广播；P6 HP 增量 capture；mutation 延后 T5.3 |
| FR-2.7 | 在线角色渲染：RemotePlayer + RenderSubscriber | US-2 | 已实现 |
| FR-2.8 | 战斗阶段：队长按 party_id 同步（queue_empty / end_turn 聚合 + 显式 combat_phase） | US-2, US-4c | 已实现（P6 + P7 party 隔离） |
| FR-2.9 | Buff 逻辑所有权：施加者优先；协议字段 `logic_owner_id`；非 owner 投影 no-op | US-2, US-3 | 已实现（T5.2 原版 Vulnerable）；灾厄/mutation 仍延后 |
| FR-2.10 | 怪物 mutation：proposal → NodeInstanceHost commit | US-2, US-3 | 未实现（schema 草案 only）；**延后**至有灾厄/改怪物核心状态内容后 |

### FR-3 Mod 兼容性

| ID | 需求 | 关联故事 | 实现状态 |
|----|------|---------|---------|
| FR-3.1 | 内容校验：resource_hash (SHA-256) 比对，决定本地引用 vs 远程引用 | US-3 | 已实现 |
| FR-3.2 | 分层引用：hash 一致 → 本地引用；不一致/无定义 → 远程引用 + fallback | US-3 | 已实现 |
| FR-3.3 | 交叉触发：LOCAL_OWNER_ONLY 经 TriggerRegistry；远程逻辑经引用 REAL 解引用 | US-3 | 部分：门控+ComponentAttachment（原版）；Mod 交叉/灾厄后续 |
| FR-3.4 | 诱导重放门控：非 local owner 不 fire；禁止 publishOnCardUse 全量链 | US-3, US-7 | 已实现 |
| FR-3.5 | Fallback 效果 + `logic_owner_id` 字段；计划 `set_monster_hp`/`monster_death` | US-3 | 部分：字段已有；`set_monster_hp`/`monster_death` 随 T5.3 接线 |

### FR-4 地图与事件

| ID | 需求 | 关联故事 |
|----|------|---------|
| FR-4.1 | 小队过渡：阶段开始前本队进入 STAGE_TRANSITION，完成组队、选图、MapHost/NodeInstanceHost 选择和地图确认 | US-4, US-4b |
| FR-4.2 | 地图生成：MapHost 用本地 RNG 生成不可变 MapDefinition 并登记到房主 MapRegistry | US-4 |
| FR-4.3 | 节点决定：NodeInstanceHost 按不可变 `MapNode.room_type` 生成节点内容，并在 `node_generation_commit` 原子提交类型化结果；首个 event 切片支持 `monster`/`event`，MapHost 登记后不处理节点 | US-4 |
| FR-4.4 | 事件接口：已打开的 event 节点由 NodeInstanceHost 从已提交的 `generation_result.event_interface` 按 party_id 广播带 event class/hash 的 event_interface；匹配端原生渲染，否则 fallback | US-4 |
| FR-4.5 | 事件选择：原生选择先 event_choice_request；获批后本地执行个人结果，fallback 由 NodeInstanceHost 定向执行 | US-4 |
| FR-4.6 | 所有者交互选择：invoke 中触发的选择面板（选牌/选目标等）回传给调用方 UI | US-4, US-7 |
| FR-4.7 | 命令标注：`crossspire room <index>` 为本队标注下一个房间，重复标注即覆盖 | US-4a |
| FR-4.8 | 共识检测：队长维护本队标注表，全队选择同一 node_id 时经房主分配节点实例并路由给 NodeInstanceHost | US-4a |
| FR-4.9 | 小队主机选举：创建地图时选 MapHost；每个绑定地图的小队选 NodeInstanceHost；均为本队全员一致 | US-4b |
| FR-4.10 | 主机恢复：MapHost 登记后掉线不影响地图；NodeInstanceHost 掉线暂停本队节点并重选/恢复 | US-4b, US-6 |
| FR-4.11 | 小队管理：默认小队、显式 leave/join、成员最小 ID 队长、按小队隔离可见性/队列/阶段/标注 | US-4c | 已实现（P7 PartyManager + visibility） |
| FR-4.12 | 事件批准：event 节点打开后匹配端实例化原生事件并绑定；`buttonEffect` 前请求批准，匹配批准只恢复一次，拒绝恢复输入且不产生副作用；不匹配端 fallback UI；个人结果本地执行并按玩家同步；voting 按小队共识 | US-4, US-7 | 已实现（含 HandCard/targetSelect + personal relic/potion/card 差量） |

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
| FR-6.2 | 执行者 RNG 归属：谁执行就用谁的 RNG（MapHost/NodeInstanceHost/卡牌所有者/本地） | US-4, US-2 |
| FR-6.3 | 结果广播：RNG 结果通过标准包广播，不需要复现 | US-4, US-2 |

## 非功能需求

| ID | 类别 | 需求 |
|----|------|------|
| NFR-1 | 性能 | 4 人房间，卡牌提交到结果渲染总延迟 < 500ms（局域网） |
| NFR-2 | 性能 | 浏览器/UI 帧率不低于 30fps（与单人模式一致） |
| NFR-3 | 兼容性 | 兼容 ModTheSpire + BaseMod + StSLib 及其他主流内容 Mod |
| NFR-4 | 兼容性 | `@SpirePatch` 使用 `optional = true` 标记可选 Mod 的 patch |
| NFR-5 | 可靠性 | 房间掉线后 30s 内自动检测并进入恢复流程 |
| NFR-6 | 可靠性 | NodeInstanceHost 活动节点快照不低于 5 分钟一次；MapDefinition 在 map_register 后由房主目录保存 |
| NFR-7 | 协议 | 所有引用传输使用 StandardPacket 信封 + JSON 编码 |
| NFR-8 | 协议 | 标准包内 `resource_hash` 必须以 SHA-256 计算 |
| NFR-9 | 协议 | 协议版本向后兼容（主版本内不破坏现有消息格式） |
| NFR-10 | 安全 | 不传输/存储任何用户个人身份信息（PII） |
| NFR-11 | 安全 | 不在日志或网络传输中泄露本地文件系统路径 |
| NFR-12 | 平台 | 同一 CrossSpire JAR 在 SlayTheAmethyst 的 Android MTS/BaseMod 兼容运行时中加载，不引用 Android SDK API |
| NFR-13 | 调试 | CrossSpire 只注册标准 BaseMod console 命令；Harness/game-probe 是外部开发工具，不是运行时依赖 |
| NFR-14 | 可移植性 | 生产源码和资源不得包含 Android 应用存储绝对路径、ADB serial 或维护者测试台端口转发配置 |
| NFR-15 | 可靠性 | CrossSpire 不通过启动文件或后台轮询文件执行控制台命令 |
| NFR-16 | 可测试性 | 联机规则（战斗 phase、ownership/`logic_owner_id`、combat_result admit 与 induce 门控、queue 策略等）须可在无设备、无 STS 运行时主循环下用 JUnit 回归 |
| NFR-17 | 可测试性 | 默认语义回归不依赖 Android Harness；Harness 仅用于设备/联机契约与发布级共进 smoke |
| NFR-18 | 可测试性 | 测试不得复制生产决策逻辑；Gate/Planner/Policy 以 `src/main` 源码为唯一实现 |

### 平台与调试验收

```gherkin
GIVEN SlayTheAmethyst 以 ModTheSpire + BaseMod 加载 CrossSpire
WHEN Harness 通过 BaseMod console 执行 "crossspire status"
THEN 命令由 CrossSpireCommand 处理
AND CrossSpire 不直接依赖 Harness、game-probe 或 ADB

GIVEN CrossSpire 已完成初始化
WHEN 旧的 crossspire_startup.txt 或 crossspire_batch.txt 存在
THEN CrossSpire 不读取、不执行也不删除这些文件
AND 不创建文件轮询线程

GIVEN D1 和 D2 使用维护者 Android 测试台
WHEN D1 执行 "crossspire host 127.0.0.1 54321"
AND D2 执行 "crossspire join 127.0.0.1 54321"
THEN D2 通过测试台预置的 D2 localhost:54321 到 D1 localhost:54321 转发连接 D1
AND 该转发由 CrossSpire 外部基础设施负责
```

### 验证与测试验收

细则见 [`development/logic-layer-testing.md`](./development/logic-layer-testing.md) 与 `ARCHITECTURE.md` §22。

```gherkin
GIVEN 战斗 ownership、induce 门控或 phase/queue 规则发生行为变更
WHEN  贡献者在 mods/cross-spire 运行 ./gradlew test
THEN  相关 pure Gate/Planner/Policy 或逻辑 scenario 测试覆盖该规则
AND   不要求双设备 Android Harness 作为该规则的唯一验收手段

GIVEN 仅需验证 host/join、console 可达性或设备路径
WHEN  使用 Android Harness 与 BaseMod console
THEN  允许 E2E smoke
AND   该 smoke 不替代逻辑层 JUnit 对 induce/phase/ownership 的断言

GIVEN 某联机决策在 MessageRouter 或 CombatResultReplayer 中分支
WHEN  为其编写单元测试
THEN  决策实现位于 main 中的 pure 辅助类型并由生产代码调用
AND   测试类不镜像另一套 if 逻辑
```

## 约束与边界

### 当前版本做

- 1-4 人联机（集中在 4 人场景）
- 塔1 基础角色 + Mod 角色同房
- 房主路由星型拓扑
- 图主掉线 → 等待重连，可重新投票选新图主
- 房间可分为多个显式小队；跨小队相遇暂不实现
- 事件选择：individual 一人选择即生效；小队 voting 按 party 全员同选项共识（P7.5 已交付）
- MapHost 注册后，本队所有客户端以权威 `MapDefinition` 重建本地地图拓扑；节点打开只允许进入已重建且路径、类别均匹配的节点
- SlayTheAmethyst 提供的 Android ModTheSpire + BaseMod 兼容运行时
- 联机语义默认以逻辑层 JUnit 为回归门禁（NFR-16–18）

### 当前版本不做

- P2P 全互联（O(n²) 连接已淘汰）
- 图主在线迁移
- 跨回合存档分享（仅本地保存）
- 塔2 实际联机实现（仅预留架构）
- 超过 4 人的房间
- iOS 与主机端支持
- 独立 Android APK 或 SlayTheAmethyst 之外的 Android STS 运行时适配
- Desktop 端到端验证（保留标准 ModTheSpire/BaseMod 兼容目标，本阶段暂缓验证）
- 以 Android Harness 作为 phase/ownership/induce 等语义的全量日常回归库
- 以 `desktop-1.0.jar` 战斗仿真作为默认单元测试策略
- 图主扫描 attachment 远程 invoke buff 所有者
- 非 logic_owner 节点有定义即执行被动（全量诱导重放）
- 策反等 turn_directive（协议预留后续）
- **本阶段（T5.2）**：灾厄等自定义 buff 内容；怪物 mutation proposal/commit 真机验收（schema 草案保留，实现延后 T5.3）

### 技术约束

- 依赖：ModTheSpire 3.30+, BaseMod 5.54+, Java 8, Gradle
- 当前验证平台为 SlayTheAmethyst Android；CrossSpire 不调用 Android SDK API
- CrossSpire 面向标准 BaseMod API，Desktop ModTheSpire/BaseMod 为兼容目标但当前未验证
- Android 自动化通过外部 SlayTheAmethyst Harness 调用 BaseMod console；最终用户运行 CrossSpire 不需要 Harness 或 game-probe
- 所有网络 IO 在独立线程，不阻塞游戏主循环
- 禁止将 `AbstractCreature.sr` 或 `TextureAtlas` 序列化传输（素材通过文件路径 + 原始字节传输）

## 术语引用

以下术语的定义和维护位置为 `ARCHITECTURE.md`。spec 中使用这些术语时，含义以 ARCHITECTURE.md 为准。

| 术语 | ARCHITECTURE.md 章节 |
|------|---------------------|
| 引用 / 本地引用 / 远程引用 / 空引用 | §2, §4 |
| 解引用 / 引用退化 / 引用转移 | §2, §4 |
| 小队 / 队长 (Party / Party Leader) | §2, §6, §7 |
| 标准包 (StandardPacket) | §2, §19 |
| 房主 / 图主 | §2, §6 |
| 诱导重放 / REAL / INDUCED（AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY） | §2, §4, §7 |
| 逻辑所有者 / ComponentAttachment | §2, §8 |
| 战斗阶段（房主同步） | §2, §9 |
| 怪物 mutation proposal/commit | §2, §10 |
| 内容校验 / resource_hash | §2, §13 |
| 分层引用 / 交叉引用 | §13 |
| Fallback 效果类型 | §13 |
