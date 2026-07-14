# CrossSpire — Slay the Spire Multiplayer Mod

**版本**: 0.2.0 | **Java 8 + ModTheSpire + BaseMod**

开发用连接 + 指令注入方式在双设备模拟器上调试：
```
crossspire connect ws://127.0.0.1:9876 CROSS
crossspire start IRONCLAD 220644
fight Cultist
crossspire play Strike_R
```

## @SpirePatch 清单

### BaseMod 事件抑制 (12)

| 文件 | 类 | 目标方法 | 参数 |
|------|-----|----------|------|
| `SuppressBaseModPatches` | `SuppressPostBattle` | `BaseMod.publishPostBattle` | `AbstractRoom` |
| | `SuppressOnPlayerDamaged` | `BaseMod.publishOnPlayerDamaged` | `int, DamageInfo` |
| | `SuppressOnPlayerLoseBlock` | `BaseMod.publishOnPlayerLoseBlock` | `int` |
| | `SuppressRelicGet` | `BaseMod.publishRelicGet` | `AbstractRelic` |
| | `SuppressPotionGet` | `BaseMod.publishPotionGet` | `AbstractPotion` |
| | `SuppressPostPotionUse` | `BaseMod.publishPostPotionUse` | `AbstractPotion` |
| | `SuppressOnCardUse` | `BaseMod.publishOnCardUse` | `AbstractCard` |
| | `SuppressPostPowerApply` | `BaseMod.publishPostPowerApply` | `AbstractPower, AbstractCreature, AbstractCreature` |
| | `SuppressPostDraw` | `BaseMod.publishPostDraw` | `AbstractCard` |
| | `SuppressPostExhaust` | `BaseMod.publishPostExhaust` | `AbstractCard` |
| | `SuppressOnPlayerTurnStart` | `BaseMod.publishOnPlayerTurnStart` | — |
| | `SuppressPostEnergyRecharge` | `BaseMod.publishPostEnergyRecharge` | — |

### 卡牌/战斗捕获 (3)

| 文件 | 类 | 目标方法 | 参数 | 说明 |
|------|-----|----------|------|------|
| `LocalCapturePatches` | `OnUseCard` | `AbstractPlayer.useCard` | `AbstractCard, AbstractMonster, int` | 捕获打牌 → `queue_submit` 到房主 |
| `CombatSyncPatches` | `OnMonsterRoomEntry` | `MonsterRoom.onPlayerEntry` | — | 广播 `room_enter` 通知客端同步进入战斗 |
| `EndTurnSyncPatches` | `CaptureEndTurn` | `EndTurnButton.enable` | — | 广播 `player_end_turn` |

### 怪物 (3)

| 文件 | 类 | 目标方法 | 参数 | 说明 |
|------|-----|----------|------|------|
| `MonsterIntentBroadcastPatches` | `OnCreateIntent` | `AbstractMonster.createIntent` | — | 缓冲单怪意图 |
| | `FlushSnapshot` | `AbstractPlayer.applyStartOfTurnPowers` | — | 回合始全量广播 `monster_intent.intents[]` |
| `MonsterTurnPatches` | `PreBattle` | `AbstractMonster.usePreBattleAction` | — | 战前效果广播 |

### 状态同步 (3)

| 文件 | 类 | 目标方法 | 参数 | 说明 |
|------|-----|----------|------|------|
| `GoldSyncPatches` | `GainGold` | `AbstractPlayer.gainGold` | `int` | 金币变更 → `player_state` |
| | `LoseGold` | `AbstractPlayer.loseGold` | `int` | 同上 |
| `RngSyncPatches` | — | `AbstractDungeon.generateSeeds` | — | 共享 RNG 种子 |

### 事件 (2)

| 文件 | 类 | 目标方法 | 参数 | 说明 |
|------|-----|----------|------|------|
| `EventSyncPatches` | `OnEnterRoom` | `AbstractEvent.onEnterRoom` | — | 事件进入广播 |
| | `EnterCombat` | `AbstractEvent.enterCombat` | — | 事件→战斗过渡广播 |

### 动画同步 (1)

| 文件 | 类 | 目标方法 | 参数 | 说明 |
|------|-----|----------|------|------|
| `AnimationSyncPatches` | `OnSetAnimation` | `AnimationState.setAnimation` | `int, String, boolean` | 本地角色动画变更 → 广播 `animation_sync` |

## 累计统计

| 类别 | 数量 |
|------|------|
| `@SpirePrefixPatch` | 12 (全部 BaseMod 抑制) |
| `@SpirePostfixPatch` | 13 |
| `@SpirePatch` 总计 | 25 |

## 子包结构

| 包 | 职责 |
|----|------|
| `crossspire` | 入口 `@SpireInitializer` + 全局状态 + EventSuppression |
| `combat/` | 中央队列、诱导重放、怪物/事件 Patches、Stub 对象 |
| `network/` | 房主客户端、心跳、协议 POJO |
| `reference/` | 引用模型：Local/Remote/Null + ContentValidator + ReferenceFactory |
| `remote/` | 在线角色状态、渲染、图主 |
| `resource/` | 素材传递：缓存、服务端、角色骨骼投影 |
| `rng/` | 确定性 RNG：SyncedRng + RngManager |
| `sync/` | 消息路由、状态同步执行器、Patches |
| `ui/` | 大厅、队列显示、HUD、控制台命令 |
