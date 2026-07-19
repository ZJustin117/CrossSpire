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

所有游戏和 Mod JAR 路径都必须显式提供。Android 开发者可以将这些参数指向本地 SlayTheAmethyst assets，Desktop 开发者可以指向标准 STS/ModTheSpire 安装；不要把个人机器的绝对路径提交到构建脚本。

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
- `../../docs/console-commands.md` — BaseMod console 命令
- `../../docs/development/` — Android Harness / Arthas 开发手册（维护者参考）
- `../../docs/reference/` — BaseMod + ModTheSpire API 参考
