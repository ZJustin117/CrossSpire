# CrossSpire 控制台命令

CrossSpire 通过 BaseMod 的 `ConsoleCommand` API 注册了 `crossspire` 命名空间，共 21 个主子命令，另有 `connect` 作为 `join` 的别名。命令可以直接在 BaseMod console 输入，也可以由 SlayTheAmethyst Harness 调用 BaseMod console。

实现类：`crossspire.ui.CrossSpireCommand`

---

## 命令列表

### 网络连接

| 命令 | 说明 |
|------|------|
| `crossspire host <advertised-ip> <port>` | 以房主身份启动 P2P 监听并公布显式地址（星型拓扑） |
| `crossspire join <ip> <port>` | 作为客户端连接到指定房主 |
| `crossspire disconnect` | 断开连接并清空远程玩家注册表 |

### 状态查看

| 命令 | 说明 |
|------|------|
| `crossspire status` | 显示连接状态（PlayerId/Peers/Queue size） |
| `crossspire info` | 显示全部信息（status + lobby + combat + queue） |
| `crossspire lobby` | 显示大厅信息（远程玩家列表/HP/Block/Powers/StageHost） |
| `crossspire combat` | 显示当前战斗信息（HP/Block/Energy/手牌/怪物） |
| `crossspire gamestate` | 显示完整游戏状态快照（HP/Gold/Relics/Potions/MasterDeck/Powers） |

### 大厅流程

| 命令 | 说明 |
|------|------|
| `crossspire ready [char]` | 标记本地玩家就绪，可选角色名 |
| `crossspire start [char] [seed]` | 开始游戏，可选角色名和种子 |
| `crossspire vote <player_id>` | 选举图主（stage host），全员一致即确认 |
| `crossspire room <index>` | 标注下一个要进入的房间（用于地图导航共识） |
| `crossspire snapshot` | 发送当前游戏状态快照 (`full_snapshot`) |

### 战斗

| 命令 | 说明 |
|------|------|
| `crossspire play <card_id> [target]` | 打出一张卡牌，通过 CentralQueueManager 调度执行 |
| `crossspire queue` | 显示当前待打出队列 |

### 事件系统（就近原则 + 沙盒转录）

| 命令 | 说明 |
|------|------|
| `crossspire cevent <event_name...>` | 通过 EventHelper.getEvent 获取事件实例，进入事件房间，广播 event_interface |
| `crossspire eventsel <option_index>` | 沙盒模式：生成 buttonEffect transcript 发往 stage host 重播 |
| `crossspire eselect <option_index> <card_id> [card_id2...]` | 完整 transcript：buttonEffect + cardSelect + confirm 一次性发送 |
| `crossspire evote <option_index>` | 事件投票模式：标注投票选项，房主聚合后共识执行 |
| `crossspire select <card_id>` | 从 GridCardSelectScreen 的 targetGroup 中选一张卡插入 selectedCards |
| `crossspire confirm` | 模拟点击 GridCardSelectScreen 的确认按钮（+ isScreenUp=false + event.update） |

---

## 详细说明

### `crossspire host <advertised-ip> <port>`

```
crossspire host 127.0.0.1 54321
```

房主创建 P2P 监听端口，并在 `hello` 中公布显式传入的地址。所有客户端连接到房主后形成**星型拓扑**（O(n) 连接，客户端间不直连）。CrossSpire 不提供隐式 IP 或端口默认值。

### `crossspire join <ip> <port>`

```
crossspire join 127.0.0.1 54321
```

客户端连接到指定房主 IP:port。连接成功后自动进行 `resource_registry` 素材清单交换。

### `crossspire disconnect`

```
crossspire disconnect
```

停止 HeartbeatManager 和 StarConnectionManager，清空 RemotePlayerRegistry。

### `crossspire status`

```
crossspire status
```

输出示例：
```
=== CrossSpire Status ===
Connected: YES
PlayerId: b9c0db98
Room: CROSS
Peers: 1
Queue size: 0
```

### `crossspire gamestate`

```
crossspire gamestate
```

输出示例：
```
=== Game State ===
HP: 80/80  Block: 0
Gold: 99  Energy: 0
Room: EventRoom  Floor: 0  Act: 1
Event: LivingWall
Relics (1): Burning Blood
Potions (3): Potion Slot, Potion Slot, Potion Slot
MasterDeck (9):
Strike_Rx4, Defend_Rx4, Bash
Draw: 0  Discard: 0  Exhaust: 0
```

### `crossspire vote <player_id>`

```
crossspire vote b9c0db98
```

发送 `stage_vote` 消息到房主。房主聚合后：全员一致 → 广播 `stage_host_result` → 全员 `setStageHost`；未一致 → 广播 `stage_votes` 快照。

### `crossspire room <index>`

```
crossspire room 0
```

标注下一个地图房间 index。房主聚合所有玩家标注后：

- 全员一致 → 构造 `room_consensus` → stage host 导航到该房间
- 未一致 → 广播 `room_pins` 快照

### `crossspire play <card_id> [target]`

```
crossspire play Strike_R
crossspire play Bash
crossspire play Strike_R JawWorm
```

房主模式下：构造 `QueueSubmitMessage` → `CentralQueueManager.onQueueSubmit` → 队列调度执行。客户端模式下：发送 `queue_submit` 到房主。

目标：攻击卡默认第一个存活的怪物；其他卡默认 self。

### `crossspire cevent <event_name...>`

```
crossspire cevent Big Fish
crossspire cevent Living Wall
```

内部调用 `EventHelper.getEvent(key)` 查找事件实例。支持含空格的事件名称：

- `crossspire cevent Big Fish` — 进入 BigFish 事件
- `crossspire cevent Living Wall` — 进入 LivingWall 事件

事件进入后自动广播 `event_interface`（含 `event_class` 全限定名和 `OPTIONS`）。

### `crossspire eventsel <option_index>`

```
crossspire eventsel 0
```

生成 `event_transcript {buttonEffect: 0}` 发往 stage host。stage host 收到后反射调用 `event.buttonEffect(0)` 重播。

### `crossspire eselect <option_index> <card_id> [card_id2...]`

```
crossspire eselect 0 Strike_R
crossspire eselect 1 Defend_R Bash
```

生成完整 transcript：
```json
{
  "type": "event_transcript",
  "actions": [
    {"type": "buttonEffect", "index": 0},
    {"type": "cardSelect", "cards": ["Strike_R"]},
    {"type": "confirm"}
  ]
}
```

stage host 收到后依次执行：buttonEffect → cardSelect 注入 → confirm 确认。LivingWall "Forget" 选项测例：

```
crossspire start IRONCLAD
crossspire cevent Living Wall
crossspire eventsel 0          # 点击 "Forget"
crossspire select Strike_R     # 选中 Strike_R
crossspire confirm             # 确认 → deck 10→9 ✅
crossspire gamestate           # 验证
```

### `crossspire evote <option_index>`

```
crossspire evote 0
```

发送 `event_vote {option_index: 0}` 到房主。房主使用 `RoomHost.castEventVote` + `checkEventVoteConsensus` 聚合：

- 全员一致 → 自动构造 event_transcript buttonEffect → 重播
- 未一致 → 广播 `event_votes` 快照

### `crossspire select <card_id>`

```
crossspire select Strike_R
```

在已打开的 `GridCardSelectScreen` 的 `targetGroup` 中查找 cardID 对应的真牌，注入 `selectedCards`，并点击 `confirmButton`。

### `crossspire confirm`

```
crossspire confirm
```

模拟点击 GridCardSelectScreen 确认按钮。额外执行：
- `gridSelectScreen.update()` — 处理 confirm 回调
- `AbstractDungeon.isScreenUp = false` — 解除 LivingWall 的阻塞检查
- `event.update()` — 强制事件状态机立即处理

---

## Android 双设备联机测试

当前维护者测试台使用 D1 `localhost:15555` 和 D2 `localhost:25555`。命令由 SlayTheAmethyst Harness 调用标准 BaseMod console，不再通过 ADB 写入 batch 文件。

D2 的 `127.0.0.1:54321` 由外部测试基础设施自动转发到 D1 的 `127.0.0.1:54321`；这不是普通双设备网络的默认行为，也不由 CrossSpire 维护。完整环境、前置条件和故障定位见 [`development/android-harness.md`](./development/android-harness.md)。

```bash
export SLAY_THE_AMETHYST_ROOT=/path/to/SlayTheAmethystModded

# D1 (host)
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:15555 \
  -ConsoleCommand "crossspire host 127.0.0.1 54321"

# D2 (join)
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:25555 \
  -ConsoleCommand "crossspire join 127.0.0.1 54321"

# 战斗
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:15555 \
  -ConsoleCommand "crossspire start IRONCLAD"
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:15555 \
  -ConsoleCommand "fight Cultist"
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:15555 \
  -ConsoleCommand "crossspire play Strike_R"

# 事件
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:15555 \
  -ConsoleCommand "crossspire cevent Living Wall"
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:25555 \
  -ConsoleCommand "crossspire eselect 0 Strike_R"

# 诊断
python3 "$SLAY_THE_AMETHYST_ROOT/scripts/tools/main.py" sts-harness \
  -Command console -DeviceSerial localhost:15555 \
  -ConsoleCommand "crossspire gamestate"
```

---

## 命令速查表（21 个主子命令）

```
crossspire host <advertised-ip> <port>
crossspire join <ip> <port>
crossspire disconnect
crossspire status
crossspire info
crossspire lobby
crossspire combat
crossspire gamestate
crossspire ready [char]
crossspire start [char] [seed]
crossspire vote <player_id>
crossspire room <index>
crossspire snapshot
crossspire play <card_id> [target]
crossspire queue
crossspire cevent <event_name...>
crossspire eventsel <option_index>
crossspire eselect <option_index> <card_id> [card...]
crossspire evote <option_index>
crossspire select <card_id>
crossspire confirm
```
