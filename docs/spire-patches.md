# @SpirePatch 清单

CrossSpire 全部 MTS 注入点，按功能域分组。最后更新: 2026-07-19

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

## 卡牌/战斗捕获 (5 Postfix, 1 Prefix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnUseCard` | `AbstractPlayer.useCard` | `AbstractCard, AbstractMonster, int` | 捕获打牌 → `queue_submit` / `suppressDepth` 防护 |
| `OnMonsterRoomEntry` | `MonsterRoom.onPlayerEntry` | — | 广播 `room_enter` |
| `CaptureEndTurn` (Prefix) | `EndTurnButton.enable` | — | `queue_empty` gate 阻止启用 |
| `CaptureEndTurn` (Postfix) | `EndTurnButton.enable` | — | 广播 `player_end_turn` |

## 状态同步 (4 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `GainGold` | `AbstractPlayer.gainGold` | `int` | 金币变更 → `player_state` |
| `LoseGold` | `AbstractPlayer.loseGold` | `int` | 同上 |
| `OnDamage` | `AbstractPlayer.damage` | `DamageInfo` | 玩家受伤 → `player_state` 广播 (新增) |

## 怪物/意图 (2 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnCreateIntent` | `AbstractMonster.createIntent` | — | 广播 `monster_intent` (使用 host 本地 RNG) |
| `PreBattle` | `AbstractMonster.usePreBattleAction` | — | 战前效果广播 |

## 事件 (3 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnEnterRoom` | `AbstractEvent.onEnterRoom` | — | 事件进入广播 |
| `EnterCombat` | `AbstractEvent.enterCombat` | — | 事件→战斗过渡广播 |
| `OnOpenMap` | `AbstractEvent.openMap` | — | 事件结束 → `event_result` 广播 (新增) |

## 动画 (1 Postfix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnSetAnimation` | `AnimationState.setAnimation` | `int, String, boolean` | 角色动画变更 → `animation_sync` |

## 渲染安全 (2 Prefix)

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `BattleStartSafety` | `BattleStartEffect.update` | — | 远程战斗中 suppress BattleStartEffect |
| `TransitionSafety` | `LevelTransitionTextOverlayEffect.render` | `SpriteBatch` | 远程战斗中 suppress 过场效果 |

## 累计

| 类型 | 数量 | 文件 |
|------|------|------|
| `@SpirePrefixPatch` | 16 | `SuppressBaseModPatches`(12), `RenderSafetyPatches`(2), `EndTurnSyncPatches`(1), `MonsterTurnPatches`(1) |
| `@SpirePostfixPatch` | 18 | `LocalCapturePatches`, `CombatSyncPatches`, `EndTurnSyncPatches`, `MonsterIntentBroadcastPatches`, `MonsterTurnPatches`, `GoldSyncPatches`, `EventSyncPatches`, `AnimationSyncPatches`, `PlayerStatePatches` |
| **总计** | **34** | 11 文件 |

## 变更记录

- 删除: `RngSyncPatches` — 共享 RNG 策略已由独立本地 RNG 替代 (FR-6.1)
- 新增: `PlayerStatePatches.OnDamage` — 玩家受伤实时播报 `player_state` (T3.4b)
- 新增: `RenderSafetyPatches` — 远程战斗渲染兼容 (T3.4b)
- 修改: `EndTurnSyncPatches` — 增加 Prefix gate (T2.2)
- 修改: `LocalCapturePatches` — 增加 `suppressDepth` 计数防护 (T2.7e)
- 修改: `MonsterTurnPatches` — 回退为 `usePreBattleAction` (T2.3, HP 增量法暂不实现)
- T5.2（无新 @SpirePatch）：`ComponentAttachmentRegistry` + `ApplyPowerEffects` + `PowerStub`/`PowerLogicGate` 回调 no-op；Replayer AUTHORITATIVE_APPLY 登记 attachment；Bash 启发式 `magic_number`→`apply_power`+`logic_owner_id`
- T5.4（无新 @SpirePatch）：`CombatPhaseCoordinator` + 房主 `combat_phase` 广播；`RoomHost` end-turn 共识 → `pre_monster_turn`
- P6/T6.2：`MonsterTurnPatches` — `MonsterGroup.applyPreTurnLogic` Prefix 采样玩家 HP/Block + Postfix 差值 `combat_result`；仅 stage host 且 `combat_phase=monster_turn`
- P7/T7.0a：`MonsterTurnPatches.PreTurnLogic` Prefix 在联机非图主节点返回 `SpireReturn.Return(null)`，阻止本地怪物 AI 与图主权威结果叠加；`CombatPhaseCoordinator` 拒绝非房主、重复或非法的远端阶段 transaction
- P7/T7.0b：`CombatSyncPatches.OnMonsterRoomEntry` 在进入新战斗前清空 `ComponentAttachmentRegistry`；`CombatResultReplayer.remove_power` 同步移除同目标实体的 attachment metadata
- P7/T7.0c：`MonsterTurnPatches.PreTurnLogic` 仅在联机图主收到当前 `monster_turn` phase 时运行；结果携带 `turn_transaction_id`，接收端在重放前拒绝错误图主、重复或旧回合的 monster-turn result
- P6/T6.1：无新 patch — `CombatTurnOrchestrator` + end_turn 后广播 `pre_monster_turn`→`monster_turn`
