# @SpirePatch 清单

CrossSpire 全部 MTS 注入点，按功能域分组。最后更新: 2026-07-22

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
| `OnMonsterRoomEntry` | `MonsterRoom.onPlayerEntry` | — | 广播 `room_enter`（map-bound / active node_instance 时跳过） |
| `CombatRewardPatches.OnEndBattle` | `AbstractRoom.endBattle` | — | 活跃 node_instance 时 `reward_phase_enter` |
| `EndTurnSyncPatches.GateEnable` (Prefix) | `EndTurnButton.enable` | — | `queue_empty` gate 阻止启用（不广播 end-turn） |
| `EndTurnSyncPatches.CaptureDisable` (Postfix) | `EndTurnButton.disable` | `boolean` | 仅 `disable(true)` 广播 `player_end_turn`（T8.0；避免 enable 误触发） |
| `MapUnlockPatches.OnDungeonMapOpen` | `DungeonMapScreen.open` | `boolean` | RIH 打开地图 → `room_exit_unlocked`（T9.3） |
| `MapUnlockPatches.OnEventOpenMap` | `AbstractEvent.openMap` | — | 事件离开打开地图 → unlock |

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

## 事件（3 Postfix，1 Prefix）

| 内部类 | 目标方法 | 参数 | 功能 |
|--------|----------|------|------|
| `OnEnterRoom` | `AbstractEvent.onEnterRoom` | — | 事件进入广播 |
| `EnterCombat` | `AbstractEvent.enterCombat` | — | 事件→战斗过渡广播 |
| `OnOpenMap` | `AbstractEvent.openMap` | — | 事件结束 → `event_result` 广播 (新增) |
| `GateButtonEffect` (Prefix) | `AbstractEvent.update` 中 `buttonEffect(int)` 调用点 | `int` | 已绑定原生事件的选择先请求批准；仅匹配批准放行一次 |
| `RegisterGeneratedMap` (Postfix) | `AbstractDungeon.update` | — | MapHost 在原版地图与入口节点确实可用后重试捕获并登记权威地图 |
| `ReapplyAuthoritativeMap` (Postfix) | `AbstractDungeon.generateMap` | — | 客户端本地 bootstrap 生成地图后立刻重放权威 `MapDefinition` |

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
| `@SpirePrefixPatch` | 17 | `SuppressBaseModPatches`(12), `RenderSafetyPatches`(2), `EndTurnSyncPatches`(1), `MonsterTurnPatches`(1), `NativeEventApprovalPatches`(1) |
| `@SpirePostfixPatch` | 19 | `LocalCapturePatches`, `CombatSyncPatches`, `EndTurnSyncPatches`, `MonsterIntentBroadcastPatches`, `MonsterTurnPatches`, `GoldSyncPatches`, `EventSyncPatches`, `AnimationSyncPatches`, `PlayerStatePatches`, `MapPatches` |
| **总计** | **36** | 13 文件 |

## 变更记录

- 删除: `RngSyncPatches` — 共享 RNG 策略已由独立本地 RNG 替代 (FR-6.1)
- 新增: `PlayerStatePatches.OnDamage` — 玩家受伤实时播报 `player_state` (T3.4b)
- 新增: `RenderSafetyPatches` — 远程战斗渲染兼容 (T3.4b)
- 修改: `EndTurnSyncPatches` — 增加 Prefix gate (T2.2)
- 修改: `LocalCapturePatches` — 增加 `suppressDepth` 计数防护 (T2.7e)
- 历史（已替代）: `MonsterTurnPatches` 曾回退为 `usePreBattleAction`；现行实现见 P6/T6.2 的 `MonsterGroup.applyPreTurnLogic` HP 增量捕获。
- T5.2（无新 @SpirePatch）：`ComponentAttachmentRegistry` + `ApplyPowerEffects` + `PowerStub`/`PowerLogicGate` 回调 no-op；Replayer AUTHORITATIVE_APPLY 登记 attachment；Bash 启发式 `magic_number`→`apply_power`+`logic_owner_id`
- T5.4（无新 @SpirePatch）：`CombatPhaseCoordinator` + 房主 `combat_phase` 广播；`RoomHost` end-turn 共识 → `pre_monster_turn`
- P6/T6.2：`MonsterTurnPatches` — `MonsterGroup.applyPreTurnLogic` Prefix 采样玩家 HP/Block + Postfix 差值 `combat_result`；仅 stage host 且 `combat_phase=monster_turn`
- P7/T7.0a：`MonsterTurnPatches.PreTurnLogic` Prefix 在联机非图主节点返回 `SpireReturn.Return(null)`，阻止本地怪物 AI 与图主权威结果叠加；`CombatPhaseCoordinator` 拒绝非房主、重复或非法的远端阶段 transaction
- P7/T7.0b：`CombatSyncPatches.OnMonsterRoomEntry` 在进入新战斗前清空 `ComponentAttachmentRegistry`；`CombatResultReplayer.remove_power` 同步移除同目标实体的 attachment metadata
- P7/T7.0c：`MonsterTurnPatches.PreTurnLogic` 仅在联机图主收到当前 `monster_turn` phase 时运行；结果携带 `turn_transaction_id`，接收端在重放前拒绝错误图主、重复或旧回合的 monster-turn result
- P6/T6.1：无新 patch — `CombatTurnOrchestrator` + end_turn 后广播 `pre_monster_turn`→`monster_turn`
- P7/T7.4：`NativeEventApprovalPatches.ButtonEffectDispatch` — 在 `AbstractEvent.update()` 的共享调用点拦截已绑定、class/hash 匹配事件的 `buttonEffect(int)`；请求批准期间冻结输入，拒绝后恢复输入，批准仅放行一次。
- P7/T7.4c：`NativeEventApprovalPatches` 在获批后的 `buttonEffect` 前后捕获个人状态并上报 `event_player_result`（gold/HP/block/deck 差量）。
- P7/T7.4d：`NativeEventApprovalPatches.GateGridConfirm` — 原生事件绑定时拦截 `GridCardSelectScreen.update` 确认点击，要求 `cardSelect` 批准后放行。
- P7/T7.4e：`NativeEventApprovalPatches.GateHandConfirm` — 原生事件绑定时拦截 `HandCardSelectScreen.update` 确认点击，要求 `targetSelect`（`selected_targets`）批准后放行。
- P7/T7.3：`MapPatches.RegisterGeneratedMap` — MapHost 选举可能早于原版地图生成；在 `AbstractDungeon.update()` 检查地图与入口节点就绪后重试捕获并登记权威 `MapDefinition`，避免退回诊断地图。
- P7/T7.3：`MapPatches.ReapplyAuthoritativeMap` — 成员本地 `GameStarter`/`generateMap` 会覆盖拓扑；Postfix 立即重放已接受的权威地图，保证 `node_instance_opened` 找到坐标一致的 MonsterRoom。
