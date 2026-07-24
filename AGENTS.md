# Repository Rules

## SDD Document Map

| 文件 | SDD 角色 | 内容 |
|------|---------|------|
| [`docs/spec.md`](docs/spec.md) | spec | 用户故事、功能需求、验收标准 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | plan | 架构设计、协议、模块定义、数据模型 |
| `AGENTS.md` | dev-guide | 本文档 — 开发约定与规则 |
| [`docs/development/logic-layer-testing.md`](docs/development/logic-layer-testing.md) | testing-guide | 测试金字塔、逻辑层写法、与 Harness 分工 |
| [`docs/reference/`](docs/reference/) | reference | BaseMod + ModTheSpire API 参考 |
| [`shared/cross-spire-protocol/protocol-schema.json`](shared/cross-spire-protocol/protocol-schema.json) | data-model | 协议消息 JSON Schema |

## Core Philosophy

CrossSpire is an open-source multiplayer mod for Slay the Spire 1, designed with a dual-role projection model and future Slay the Spire 2 cross-game compatibility.

## Code Conventions

### Mod Code (Java, mods/cross-spire/)

- Every gameplay patch must be documented in the mod's `README.md`, stating what the fix does, what symptom it addresses, and which patch class implements it.
- Do not accumulate unrelated Spire patches in one monolithic file. Split by domain so each patch can be reviewed and reverted independently.
- Patch class naming: `XxxPatches.java` for a domain. A single patch file may contain multiple `@SpirePatch` inner classes for the same domain.
- Always use `@SpirePatch(clz = ..., method = ..., paramtypez = ...)` with explicit `paramtypez` to avoid ambiguous method resolution.
- Use `@SpirePrefixPatch` / `@SpirePostfixPatch` annotations on static inner classes.

### Shared Protocol (shared/cross-spire-protocol/)

- `protocol-schema.json` is the source of truth for message formats.
- Java implementation derives types from this schema via `Protocol.java`.
- When adding new message types, update the JSON schema first, then the Java implementation.

## Reference Documentation

The `docs/reference/` directory contains API documentation for the two key modding frameworks used by CrossSpire. Consult these when writing patches or adding custom game content.

| File | Content |
|------|---------|
| `modthespire-overview.md` | ModTheSpire installation, usage, and build instructions |
| `modthespire-spirepatch.md` | `@SpirePatch` API — Prefix, Postfix, Insert, Instrument, Replace, Raw, Locator |
| `basemod-hooks.md` | All BaseMod subscriber hooks — Adder, Before/Pre, After/Post, Render, Update |
| `basemod-custom-cards.md` | `CustomCard` constructor, textures, registration (`EditCardsSubscriber`) |
| `basemod-custom-characters.md` | `CustomPlayer`, enum patching, `EditCharactersSubscriber` |
| `basemod-custom-relics.md` | `CustomRelic`, `addRelic`, `addRelicToCustomPool` |
| `basemod-custom-events.md` | `AbstractEvent`, `PhasedEvent`, `AddEventParams.Builder` |
| `basemod-custom-colors.md` | `CardColor` enum patching, `addColor` API |
| `basemod-custom-keywords.md` | Keyword JSON registration, `addKeyword` |
| `basemod-custom-potions.md` | `addPotion` API |

## Local machine config (not production)

- Maintainer paths, ADB serials, and connector ports live in gitignored `.env.local` (template: `.env.example`).
- Process docs under `docs/development/` use env **names** only (`$CROSSSPIRE_*`, `$STS_*`, `$SLAY_THE_AMETHYST_ROOT`).
- OpenCode plugin `.opencode/plugins/local-env.ts` loads whitelist keys into shell env and test-agent system context. Restart opencode after changing agents/plugins.
- The external `amethyst-tools` OpenCode reference maps to `$CROSSSPIRE_AMETHYST_TOOLS_DIR`; use it for file inspection rather than adding a `scripts/` symlink in this repository. Harness artifacts use `$CROSSSPIRE_HARNESS_OUT_DIR`.

## OpenCode 测试 subagent

只读验证用，定义在 `.opencode/agent/*.md`。主 agent **写代码**；跑测与联机检查用 Task / `@` 委派，避免主会话堆满 gradle/harness 日志。

| Agent | 何时用 | 何时不用 |
|-------|--------|----------|
| `junit-test` | **语义默认门禁**：协议、Gate/Planner/Policy、逻辑 scenario 改完；排查 unit 失败；用户要 JUnit | 纯读代码/设计；代码尚不可编译；只改 docs；需要设备联机（转 harness） |
| `android-deploy-jar` | 改了 mod 源码后要上机验证；设备需刷新 `CrossSpire.jar`；E2E 前构建+推送 | 只跑 unit/逻辑层；jar 未变；无设备；纯 console 且设备已是新 jar；**语义回归** |
| `android-harness` | 联机、host/join/console、真机路径；用户明确设备 E2E / 发布 smoke | **默认每次改动**；phase/ownership/induce 等应用 JUnit 覆盖的语义；无 `.env.local`/设备；还需先推 jar 时先用 deploy |
| `android-arthas` | Android JVM 线程、类加载、方法参数/返回值、调用耗时或 Arthas bridge 诊断 | 默认每次改动；**游戏语义**或联机验收；需要改源码/热替换 |

### 委派规则（省 token / 防涣散）

1. **一次委派 = 一个窄目标**（如「全量 `./gradlew test`」、`android-deploy-jar` 推双机，或「D1 host + D2 join + `crossspire status`」）。禁止「重构 + 跑测 + 翻日志 + 修代码」塞进同一子任务。
2. **顺序**：改代码 → **语义/规则变更必须** `junit-test` → **仅当**联机契约或 Android 路径受影响：`android-deploy-jar`（需新 jar）→ `android-harness`。`android-arthas` 仅 JVM 诊断；不要默认双开 E2E，**不要用 harness 代替逻辑层 JUnit**。
3. **失败**：子 agent **只回摘要**（pass/fail、失败类/方法、关键输出摘录）。**主 agent 修源码**后再委派复测；子 agent 不 edit。
4. **不要**在主会话里自己跑完整 suite/长 harness，除非 subagent 不可用；也不要把整份 test 日志贴回主对话。
5. JUnit、deploy-jar 与 harness **一般串行**（先 unit，再 jar 推送，再 E2E）；无依赖的并行 harness 不要开。
6. Task 恢复会话时 `task_id` 仅用系统返回的 `ses…`；**新任务省略 `task_id`**（勿传随机 UUID）。插件 `local-env` 会在执行前**剥离**非 `ses` 前缀的 `task_id`（不当作 resume）。
7. 依赖 `.env.local`；缺变量时子 agent 应阻塞并列出键名，主 agent 勿发明绝对路径。
8. 写测/抽 Policy 时对照 [`docs/development/logic-layer-testing.md`](docs/development/logic-layer-testing.md)；**禁止**在测试类内镜像生产 `if` 决策。

## Storage


- Temporary files, decompiled sources, and scratch files go into `agent-tmp/` directory. Do not commit them.
- Debug artifacts go into `debug-artifacts/` (gitignored).


## Git

- Commit message format:
  - `feat:` — new feature
  - `fix:` — bug fix
  - `perf:` — performance improvement
  - `chores:` — maintenance work
- Do not commit secrets, tokens, or signing keys.
- Do not commit build outputs (`dist/`, `build/`, `bin/`).
- Do not commit `node_modules/`.

## Mod Compatibility Rules

- The mod must work alongside other popular STS1 mods (BaseMod, StSLib, content mods).
- Use `@SpirePatch` with `optional = true` when patching mod classes that may not be present.
- Event suppression must be granular — suppress only the events that need suppression, not a global "disable all BaseMod" flag.
- The `SuppressBaseModPatches.java` must intercept each `BaseMod.publishXxx()` method individually, allowing independent control.

## Protocol Design Rules

- All reference transport uses the `StandardPacket` envelope (see `docs/ARCHITECTURE.md` §19) with fixed header + `operation` + `payload`.
- Control messages (heartbeat, join/leave, elections) are not encapsulated as standard packets.
- Every message has a `seq` field (monotonic integer, per-source) for ordering and deduplication.
- `fallback.effects` is always an array — even for single-effect cards — for consistency.
- Protocol changes should be backward-compatible within a major version.
- Buff/power **logic owner = applier-first** (`logic_owner_id` on `apply_power`). Non-owners hold display-only projections (callbacks no-op).
- Induced replay is **AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY** — never ungated full `useCard`/BaseMod hook replay on every client.
- During the P6 room-wide baseline, combat **phase alignment is room-host responsibility**. P7 migrates this coordination to the party leader: all gameplay-scoped phase, queue, map-pin, event, and player-visibility messages carry `party_id`; RoomHost only routes and maintains the room directory. Buffs fire spontaneously on the logic owner’s machine. Monster core state mutations from non-stage-hosts go through proposal → stage-host commit.

## TDD / SDD Workflow

1. Before implementation, update the applicable SDD source of truth: `spec.md` for acceptance behavior, `ARCHITECTURE.md` for ownership/routing/protocol design, and `protocol-schema.json` before Java when message formats change. Testing rules: [`docs/development/logic-layer-testing.md`](docs/development/logic-layer-testing.md) and `ARCHITECTURE.md` §22.
2. Start each behavior change with a focused failing JUnit test. Prefer pure Gate/Planner/Policy and multi-step logic scenarios for multiplayer rules (phase, ownership, induce, queue admit). Add patch/engine tests only where engine behavior is the contract.
3. Implement the smallest change that makes the new test pass. New branching in `MessageRouter` / `CombatResultReplayer` / patches should call pure helpers in `main` (do not leave decisions only in engine-coupled methods). Keep SDD ownership boundaries and major-version protocol compatibility.
4. Update patch documentation in `mods/cross-spire/README.md` whenever adding or materially changing a gameplay patch.
5. Delegate **semantic regression** to `junit-test` after each coherent slice. Only after JUnit is green: `android-deploy-jar` if devices need a new JAR, then `android-harness` **only** for device/network/console contracts or release co-op smoke — never as the sole check for phase/ownership/induce.
