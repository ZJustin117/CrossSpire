# Repository Rules

## SDD Document Map

| 文件 | SDD 角色 | 内容 |
|------|---------|------|
| [`docs/spec.md`](docs/spec.md) | spec | 用户故事、功能需求、验收标准 |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | plan | 架构设计、协议、模块定义、数据模型 |
| `AGENTS.md` | dev-guide | 本文档 — 开发约定与规则 |
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
- Combat **phase alignment is room-host responsibility**; buffs fire spontaneously on the logic owner’s machine. Monster core state mutations from non-stage-hosts go through proposal → stage-host commit.
