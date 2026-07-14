# CrossSpire (mod)

Slay the Spire 1 多人联机 Mod。依赖 ModTheSpire + BaseMod。Java 8, Gradle, jar ≈ 624KB。

## 构建

```bash
cd mods/cross-spire
./gradlew clean jar test
# 输出: build/libs/CrossSpire.jar
```

## 子包

| 包 | 职责 |
|----|------|
| `crossspire` | 入口 `@SpireInitializer` + `EventSuppression` |
| `combat/` | `CentralQueueManager` 房主中央队列 + `CombatResultReplayer` 诱导重放 + `CardStub`/`PowerStub` |
| `network/` | `RelayClient` 星型拓扑 + `HeartbeatManager` + `Protocol` POJO |
| `reference/` | `Reference` 引用模型 (Local/Remote/Null) + `ContentValidator` + `ReferenceFactory` |
| `remote/` | `RemotePlayer` / `RemotePlayerState` / `RemoteRenderer` / `StageHost` |
| `resource/` | 素材传递：`RemoteAssetCache` + `RemoteAssetServer` + `RemoteCharacterResource` |
| `rng/` | `SyncedRng` + `RngManager` 确定性随机 |
| `sync/` | `MessageRouter` + `SyncExecutor` + 各域 Patches |
| `ui/` | `LobbyScreen` / `QueueDisplay` / `CrossSpireHUD` / `CrossSpireCommand` |

## 文档

- `../../docs/ARCHITECTURE.md` — 架构设计
- `../../docs/spire-patches.md` — 25 个 @SpirePatch 完整清单
- `../../docs/console-commands.md` — 控制台命令 + 测试流程
- `../../docs/ACHIEVEMENTS.md` — 开发里程碑
