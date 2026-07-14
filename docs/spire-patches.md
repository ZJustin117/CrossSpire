# @SpirePatch 清单

CrossSpire 全部 25 个 MTS 注入点，按功能域分组。

## BaseMod 事件抑制 (12 Prefix)

| 内部类 | 目标方法 | 参数 |
|--------|----------|------|
| `SuppressPostBattle` | `BaseMod.publishPostBattle` | `AbstractRoom` |
| `SuppressOnPlayerDamaged` | `BaseMod.publishOnPlayerDamaged` | `int, DamageInfo` |
| `SuppressOnPlayerLoseBlock` | `BaseMod.publishOnPlayerLoseBlock` | `int` |
| `SuppressRelicGet` | `BaseMod.publishRelicGet` | `AbstractRelic` |
| `SuppressPotionGet` | `BaseMod.publishPotionGet` | `AbstractPotion` |
| `SuppressPostPotionUse` | `BaseMod.publishPostPotionUse` | `AbstractPotion` |
| `SuppressOnCardUse` | `BaseMod.publishOnCardUse` | `AbstractCard` |
| `SuppressPostPowerApply` | `BaseMod.publishPostPowerApply` | `AbstractPower, AbstractCreature, AbstractCreature` |
| `SuppressPostDraw` | `BaseMod.publishPostDraw` | `AbstractCard` |
| `SuppressPostExhaust` | `BaseMod.publishPostExhaust` | `AbstractCard` |
| `SuppressOnPlayerTurnStart` | `BaseMod.publishOnPlayerTurnStart` | — |
| `SuppressPostEnergyRecharge` | `BaseMod.publishPostEnergyRecharge` | — |

全部位于 `crossspire.SuppressBaseModPatches`，实现 `@SpirePrefixPatch` + `EventSuppression.isSuppressed()` 开关。

## 卡牌/战斗捕获 (3 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnUseCard` | `AbstractPlayer.useCard` | `AbstractCard, AbstractMonster, int` | 捕获打牌 → `queue_submit` |
| `OnMonsterRoomEntry` | `MonsterRoom.onPlayerEntry` | — | 广播 `room_enter` |
| `CaptureEndTurn` | `EndTurnButton.enable` | — | 广播 `player_end_turn` |

## 怪物/意图 (2 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnCreateIntent` | `AbstractMonster.createIntent` | — | 广播 `monster_intent` |
| `PreBattle` | `AbstractMonster.usePreBattleAction` | — | 战前效果广播 |

## 状态同步 (3 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `GainGold` | `AbstractPlayer.gainGold` | `int` | 金币变更 → `player_state` |
| `LoseGold` | `AbstractPlayer.loseGold` | `int` | 同上 |
| — | `AbstractDungeon.generateSeeds` | — | RNG 种子同步 |

## 事件 (2 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnEnterRoom` | `AbstractEvent.onEnterRoom` | — | 事件进入广播 |
| `EnterCombat` | `AbstractEvent.enterCombat` | — | 事件→战斗过渡广播 |

## 动画 (1 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnSetAnimation` | `AnimationState.setAnimation` | `int, String, boolean` | 角色动画变更 → `animation_sync` |

## 累计

| 类型 | 数量 | 文件 |
|------|------|------|
| `@SpirePrefixPatch` | 12 | `SuppressBaseModPatches.java` |
| `@SpirePostfixPatch` | 13 | `LocalCapturePatches`, `CombatSyncPatches`, `EndTurnSyncPatches`, `MonsterIntentBroadcastPatches`, `MonsterTurnPatches`, `GoldSyncPatches`, `RngSyncPatches`, `EventSyncPatches`, `AnimationSyncPatches` |
| **总计** | **25** | 9 文件 |
