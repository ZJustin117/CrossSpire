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
| `combat/` | `CentralQueueManager` 房主中央队列 + `CombatResultReplayer` 诱导重放 + `InteractionCapture` + Stub 对象 |
| `network/` | `RoomHostClient` 星型拓扑 + `HeartbeatManager` + `Protocol` POJO |
| `reference/` | `Reference` 引用模型 (Local/Remote/Null) + `ContentValidator` + `ReferenceFactory` |
| `remote/` | `RemotePlayer` / `RemotePlayerRegistry` / `RemoteRenderer` / `StageHost` |
| `resource/` | 素材传递：`RemoteAssetCache` + `RemoteAssetServer` + `RemoteCharacterResource` |
| `ui/` | `LobbyScreen` / `QueueDisplay` / 在线角色状态覆盖层 |

## 文档

- `../../docs/ARCHITECTURE.md` — 架构设计
- `../../docs/reference/` — BaseMod + ModTheSpire API 参考
