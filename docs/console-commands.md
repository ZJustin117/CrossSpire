# CrossSpire 控制台命令

CrossSpire 通过 BaseMod 的 `ConsoleCommand` API 注册了 `crossspire` 命名空间，共 10 个子命令。在游戏内按 **\`** (反引号) 打开控制台后输入。

实现类：`crossspire.ui.CrossSpireCommand`

---

## 命令列表

| 命令 | 说明 |
|------|------|
| `crossspire connect <url> <room>` | 连接到中继服务器并加入指定房间 |
| `crossspire disconnect` | 断开连接并清空远程玩家注册表 |
| `crossspire status` | 显示连接状态概览 |
| `crossspire info` | 显示全部信息（status + lobby + combat + queue） |
| `crossspire lobby` | 显示大厅信息（远程玩家列表、种子、图主状态） |
| `crossspire combat` | 显示当前战斗信息（必须在战斗中） |
| `crossspire ready [char]` | 标记本地玩家就绪，可选角色名 |
| `crossspire start [char] [seed]` | 开始游戏，可选角色名和种子 |
| `crossspire play <card_id> [target]` | 打出一张卡牌，可选目标 monster id |
| `crossspire queue` | 显示待打出队列 |

---

## 详细说明

### `crossspire connect <url> <room_code>`

连接到指定的 WebSocket 中继服务器并加入房间。

```
crossspire connect ws://10.0.2.2:9876 CROSS
```

- `<url>`: WebSocket 服务器地址（如 `ws://host:port`）
- `<room_code>`: 房间码，双方需使用相同房间码
- 连接成功后会自动完成握手和 `room_join`

### `crossspire disconnect`

断开与中继服务器的连接，清空 `RemotePlayerRegistry`。

```
crossspire disconnect
```

### `crossspire status`

显示当前连接状态的摘要信息：

- 是否已连接
- 本地 PlayerId
- 当前房间码
- 同步种子
- P2P 直连数量
- 队列大小

```
crossspire status
```

### `crossspire info`

依次显示 `status`、`lobby`、`combat`、`queue` 四部分信息。

```
crossspire info
```

### `crossspire lobby`

显示大厅中的远程玩家信息：

- 远程玩家数量
- 每位玩家的 PlayerId（截断为前 8 位）、角色职业、HP/Block
- 玩家身上的能力（Power）列表
- 当前是否为本地图主机（StageHost）
- 同步种子

```
crossspire lobby
```

### `crossspire combat`

显示当前战斗状态（需已进入战斗）：

- 游戏模式
- 当前楼层
- 玩家 HP/Block/Energy
- 手牌/抽牌堆/弃牌堆数量
- 怪物列表（HP/意图/能力数量）

```
crossspire combat
```

### `crossspire ready [char]`

标记本地玩家已就绪，通知房间内其他玩家。

```
crossspire ready IRONCLAD
crossspire ready DEFECT
```

- `[char]`: 角色职业名（`IRONCLAD`、`DEFECT`、`SILENT`、`WATCHER`），默认 `IRONCLAD`

### `crossspire start [char] [seed]`

开始新游戏。双方分别执行，种子由 Lobby 流程同步。

```
crossspire start IRONCLAD 220644
crossspire start DEFECT
```

- `[char]`: 角色职业名，默认 `IRONCLAD`
- `[seed]`: 固定种子，默认自动生成

### `crossspire play <card_id> [target]`

在战斗中打出一张卡牌。`LocalCapturePatches` 自动捕获 `useCard` 事件，生成 `queue_submit` 发往房主的 `CentralQueueManager` 调度执行。

```
crossspire play Strike_R
crossspire play Bash                # target 默认为 self
crossspire play Strike_R JawWorm    # 指定目标怪物
```

- `<card_id>`: 卡牌内部 ID（如 `Strike_R`、`Defend_R`、`Bash`）
- `[target]`: 目标怪物 ID（如 `JawWorm`）或 `self`（默认）

### `crossspire queue`

显示当前分布式打出队列内容，用于调试队列状态。

```
crossspire queue
```

---

## 双设备联机测试流程

### 模拟器/Android 环境

```
crossspire connect ws://10.0.2.2:9876 CROSS
crossspire start IRONCLAD 220644   # 设备 1
crossspire start DEFECT 220644     # 设备 2
crossspire play Strike_R
crossspire info                    # 查看完整状态
```

### 桌面环境

```
crossspire connect ws://localhost:9876 TEST
crossspire ready IRONCLAD
crossspire start
crossspire play Strike_R
crossspire queue
```

---

## 启动脚本

CrossSpire 支持从文件自动加载命令序列，用于自动化测试和双设备联机：

- **`crossspire_startup.txt`**: 启动时自动执行一次（位于 Android `files/sts/` 目录），内容示例：
  ```
  crossspire connect ws://127.0.0.1:9876 CROSS
  ```
- **`crossspire_batch.txt`**: 运行时每 5 秒检测一次，存在时逐行执行后删除。适合推送即时命令：
  ```bash
  printf "crossspire start IRONCLAD 220644\n" > /tmp/cs_batch.txt
  adb -s localhost:15555 push /tmp/cs_batch.txt \
    /storage/emulated/0/Android/data/io.stamethyst/files/sts/crossspire_batch.txt
  sleep 15
  printf "fight Cultist\n" > /tmp/cs_batch.txt    # BaseMod 原生命令，触发 MonsterRoom.onPlayerEntry
  adb -s localhost:15555 push /tmp/cs_batch.txt \
    /storage/emulated/0/Android/data/io.stamethyst/files/sts/crossspire_batch.txt
  sleep 10
  printf "crossspire play Strike_R\n" > /tmp/cs_batch.txt
  adb -s localhost:15555 push /tmp/cs_batch.txt \
    /storage/emulated/0/Android/data/io.stamethyst/files/sts/crossspire_batch.txt
  ```
