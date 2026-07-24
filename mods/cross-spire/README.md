# CrossSpire (mod)

Slay the Spire 1 多人联机 Mod。依赖 ModTheSpire + BaseMod，使用 Java 8 和 Gradle。

## 平台支持

- Android：当前开发和验证平台为 SlayTheAmethyst 的 ModTheSpire + BaseMod 兼容运行时。
- Desktop：保持标准 ModTheSpire + BaseMod API 兼容目标，当前阶段暂未执行端到端验证。
- iOS、主机和 SlayTheAmethyst 之外的 Android STS 运行时：不支持。

Android 调试使用外部 SlayTheAmethyst Harness 调用标准 BaseMod console。CrossSpire 发布 JAR 不依赖 Harness、game-probe、ADB 或 Android SDK。

## 构建

```bash
# 在 CrossSpire 仓库根加载 .env.local（模板 .env.example）
set -a && source .env.local && set +a
cd mods/cross-spire
./gradlew clean jar test \
  -PstsJar="$CROSSSPIRE_STS_JAR" \
  -PbaseModJar="${CROSSSPIRE_BASEMOD_JAR:-$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/BaseMod.jar}" \
  -PmodTheSpireJar="${CROSSSPIRE_MODTHESPIRE_JAR:-$SLAY_THE_AMETHYST_ROOT/app/src/main/assets/components/mods/ModTheSpire.jar}"
# 输出: build/libs/CrossSpire.jar
```

Android 设备推送：OpenCode `@android-deploy-jar`，或见 `docs/development/android-harness.md`（`mods_library/CrossSpire.jar` + `force-stop`）。

所有游戏和 Mod JAR 路径都必须显式提供。Android 开发者可以将这些参数指向本地 SlayTheAmethyst assets，Desktop 开发者可以指向标准 STS/ModTheSpire 安装；不要把个人机器的绝对路径提交到构建脚本。

## 子包

| 包 | 职责 |
|----|------|
| `crossspire` | 入口 `@SpireInitializer` + `EventSuppression` |
| `combat/` | `CentralQueueManager` 小队队长中央队列 + `CombatResultReplayer` 诱导重放 + `InteractionCapture` + Stub 对象 |
| `network/` | `StarConnectionManager` / `RoomHost` 星型拓扑 + `HeartbeatManager` + `Protocol` POJO |
| `party/` | `PartyState` / `PartyManager` 小队目录、确定性队长选举与 `PartyCoordinator` 路由授权 |
| `map/` | 地图目录、登记授权、MapHost/NIH 选举、小队 room pin、NodeEntry 分配与 node open 授权 |
| `event/` | `EventApprovalCoordinator` 与 `EventChoiceSender`：RoomHost individual 事件批准、去重及本队结果 relay |
| `reference/` | `Reference` 引用模型 (Local/Remote/Null) + `ContentValidator` + `ReferenceFactory` |
| `remote/` | `RemotePlayer` / `RemotePlayerRegistry` / `RemoteRenderer` / `StageHost`；渲染投影由 `PartyVisibility` 限制为同队成员 |
| `resource/` | 素材传递：`RemoteAssetCache` + `RemoteAssetServer` + `RemoteCharacterResource` |
| `ui/` | `LobbyScreen` / `QueueDisplay` / 在线角色状态覆盖层 |

## Gameplay Patches

| Patch class | Fix | Symptom addressed |
|-------------|-----|-------------------|
| `MonsterTurnPatches.PreTurnLogic` | Only the stage host may execute `MonsterGroup.applyPreTurnLogic()` while connected; other clients wait for authoritative `combat_result`. | Remote clients previously ran local monster AI as well as applying the stage-host result, allowing double damage and divergent combat state. |
| `CombatSyncPatches.OnMonsterRoomEntry` | Clears in-combat `ComponentAttachment` metadata before a new monster room begins. | Buff ownership metadata from a completed combat could survive into a later room and gate the wrong projected power. |
| `MonsterTurnPatches.PreTurnLogic` | Runs connected monster AI only on the stage host during the active `monster_turn` transaction. | A delayed, duplicated, or non-stage-host monster completion could otherwise replay effects or advance a later turn. |
| `NativeEventApprovalPatches.ButtonEffectDispatch` | Gates a bound, hash-matched native event's shared `AbstractEvent.update()` button dispatch until its `event_choice_request` receives the matching approval; after a permitted execute, diffs personal gold/HP/block/deck and emits `event_player_result`. | A local event option could previously execute its side effects before RoomHost validation, or leave party members without a personal-result delta after native execution. |
| `NativeEventApprovalPatches.GateGridConfirm` | While a native event is bound, blocks `GridCardSelectScreen` confirm clicks until a matching `cardSelect` approval arrives. | Card-select event steps could previously finalize without RoomHost approval. |
| `NativeEventApprovalPatches.GateHandConfirm` | While a native event is bound, blocks `HandCardSelectScreen` confirm clicks until a matching `targetSelect` approval arrives (`selected_targets` = selected card IDs). | Hand-select event steps could previously finalize without RoomHost approval. |
| `EventSyncPatches.OnEnterRoom` | Skips legacy event_interface broadcast while a P7 party event node is opened natively (`suppressBroadcast`). | Opening a party-scoped event would otherwise emit the old JSON interface and bypass StandardPacket approval binding. |
| `MapPatches.RegisterGeneratedMap` | Retries the elected MapHost's immutable map capture from the dungeon update loop once the STS map and entry node exist. | An election completed before STS map generation previously left no later capture opportunity, causing the room flow to fall back to diagnostic topology. |
| `MapPatches.ReapplyAuthoritativeMap` | Re-applies the accepted `MapDefinition` after vanilla `AbstractDungeon.generateMap()`. | Join clients that bootstrap a local run regenerate a private map and would otherwise overwrite the party-authoritative topology before node open. |
| `CombatSyncPatches.OnMonsterRoomEntry` | Skips legacy `room_enter` when party map is bound or `ActiveNodeTracker` has a `node_instance_id`. | Dual clients on the P7 node-open path would otherwise also receive host-spawn `room_enter` and install a second combat shell. |
| `CombatRewardPatches.OnEndBattle` | On connected combat end with an active `node_instance_id`, emits `reward_phase_enter` so the party can gate next `room_pin` until all `reward_done`. | Victory previously left no shared reward phase; members could pin the next room while others still on CombatRewardScreen. |
| `EndTurnSyncPatches.CaptureDisable` | Broadcasts `player_end_turn` only from `EndTurnButton.disable(true)`. | Patching `enable()` fired at every player_turn start and auto-advanced into `monster_turn`, rejecting `play`. |
| `MapUnlockPatches.OnDungeonMapOpen` / `OnEventOpenMap` | RoomInstanceHost opening the dungeon map broadcasts `room_exit_unlocked`. | Party stayed `exit_locked` unless console `mapunlock`; continue arrow did not free peers to pin. |

Navigation (T9): `RoomNavigationGate` locks `room_pin` after each `node_instance_opened`; RoomInstanceHost broadcasts `room_exit_unlocked` (or reward_phase_complete maps to unlock). Shop/rest multiplayer SoT is room type + instance id only—not inventory or campfire options.

## 文档

- `../../docs/ARCHITECTURE.md` — 架构设计
- `../../docs/console-commands.md` — BaseMod console 命令
- `../../docs/development/` — Android Harness / Arthas 开发手册（维护者参考）
- `../../docs/reference/` — BaseMod + ModTheSpire API 参考
