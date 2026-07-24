# 游戏逻辑层测试规范

本文是 CrossSpire **语义回归**的权威开发手册。设备联机与 Harness 操作见 [`android-harness.md`](./android-harness.md)。Agent 委派顺序见仓库根 [`AGENTS.md`](../../AGENTS.md)。

## 1. 测试金字塔

```text
        ╱ Android dual-device harness ╲     稀有、慢；仅联机/设备契约 smoke
       ╱   (console + logs + devices)  ╲
      ╱─────────────────────────────────╲
     ╱  （一般不做）@SpirePatch / STS 引擎集成 ╲
    ╱─────────────────────────────────────────╲
   ╱  ★ 逻辑层 JUnit：Policy / Gate / scenario ★  默认语义门禁
  ╱   pure 协议、phase、ownership、queue、admit…  ╲
 ╱─────────────────────────────────────────────────╲
```

| 层 | 工具 | 默认何时用 |
|----|------|------------|
| 纯 JUnit + 逻辑 scenario | `mods/cross-spire` `./gradlew test`（`@junit-test`） | **每次**协议/规则/Gate/Planner/Policy 变更 |
| Patch / 引擎集成 | 一般不建 | 仅当引擎行为本身是合同且无法抽 pure |
| Android Harness | `@android-harness`（先 `@android-deploy-jar` 如需新 jar） | host/join/console、设备路径、发布级共进 smoke |
| Arthas | `@android-arthas` | JVM 诊断，不替代语义断言 |

**原则：** 测 CrossSpire 联机规则，不测 STS 卡牌数值；不把双设备 E2E 当作 phase/ownership/induce 的日常回归库。

## 2. 测什么 / 不测什么

### 测（逻辑层）

- 战斗 phase 合法转移与 gate（queue_submit、end_turn、monster_turn）
- ownership：`logic_owner_id`、applier-first、non-owner 不跑 passive
- induce：AUTHORITATIVE_APPLY + LOCAL_OWNER_ONLY 决策、own-result skip、host 对 peer 本地 induce
- personal target：`self` / 个人效果仅 executor 本机
- induced hop 上限与丢弃
- queue：source+seq 去重、顺序、markDone
- party / nav / pin 等 pure manager 与 gate
- 协议 DTO / Gson 往返、`StandardPacket` 字段

### 不测（默认）

- STS 引擎伤害公式、卡牌 `use` 完整副作用树
- `@SpirePatch` 字节码是否挂上（用 README 文档 + 必要时真机）
- ADB 转发、connector、jar 热更类加载（Harness / 运维文档）
- 从 `desktop-1.0.jar` 反编译或仿真整局战斗作为默认策略

## 3. 写法约定

### 3.1 生产代码即契约

- 决策逻辑必须在 `src/main/java` 的 pure 类型中：`*Gate`、`*Planner`、`*Policy`、coordinator 静态策略。
- **禁止**在测试类内复制一套 `if` 镜像生产实现（反模式：历史上 `CombatResultHostApplyPolicyTest` 内手写 `shouldLocalInduce` 未绑定 main）。
- 测试只调用 main 中的 API 并断言结果。

### 3.2 提取优先于 mock

- 当前栈：JUnit 4、无 Mockito；优先抽 pure 函数，少用协作 mock。
- 需要 stub 时用手写最小类型（如 `StubRef extends Reference`），或注入 playerId 等静态测试钩子后 `@After` 复位。
- `CombatResultReplayer` / `MessageRouter` 中的 **if 决策**应下沉到 pure；类本身保留 STS 写数与网络 I/O。

### 3.3 表驱动与 scenario

- 单策略：参数表 / 多 `@Test` 覆盖边界（空 id、own vs peer、非法 phase）。
- 多步语义：用 DTO / `JsonObject` + 内存状态（phase、queue、attachments、local playerId）驱动 pure API，**不**加载 `AbstractDungeon`。
- 包约定：
  - 单策略：与 main 镜像，如 `crossspire.combat.LocalOwnerGateTest`
  - 多步场景（实现任务落地后）：`crossspire.combat.scenario`（或 `crossspire.logic`）

### 3.4 TDD

1. 更新适用 SDD（`spec.md` / `ARCHITECTURE.md` / `protocol-schema.json`）。
2. 先写失败的 pure 或 scenario JUnit。
3. 最小实现使测试通过；接线 Replayer/Router/Patches 只调用 pure。
4. 有 gameplay patch 时更新 `mods/cross-spire/README.md`。
5. 委派 `@junit-test`；仅设备契约变更时再 deploy + harness。

## 4. 与 Harness 分工

| 验证目标 | 默认路径 |
|----------|----------|
| induce / ownership / phase / queue admit / hop | **JUnit** |
| 协议字段、`logic_owner_id` 序列化 | **JUnit** |
| host/join、peer 数、console 命令可达 | Harness |
| 共进 smoke：ready→start→room→play 日志路径 | Harness（发布/里程碑，非每次语义改动） |
| 线程/类加载/热更后是否旧类 | Arthas 或 harness + force-stop |

共进 smoke 步骤仍以 [`android-harness.md`](./android-harness.md) 为准；**通过条件中的语义断言应逐步有对应 JUnit**，Harness 只证明设备路径仍通。

## 5. 如何运行

```bash
cd mods/cross-spire
./gradlew test \
  -PstsJar="$CROSSSPIRE_STS_JAR" \
  -PbaseModJar="$CROSSSPIRE_BASEMOD_JAR" \
  -PmodTheSpireJar="$CROSSSPIRE_MODTHESPIRE_JAR"
```

单类 / 过滤（实现 scenario 包后）：

```bash
./gradlew test --tests 'crossspire.combat.*' \
  -PstsJar="$CROSSSPIRE_STS_JAR" \
  -PbaseModJar="$CROSSSPIRE_BASEMOD_JAR" \
  -PmodTheSpireJar="$CROSSSPIRE_MODTHESPIRE_JAR"
```

OpenCode：主 agent 写代码；跑测用 `@junit-test`（只读）。不要在主会话堆完整 gradle 日志。

## 6. 首批逻辑场景清单（规划）

实现见 `docs/task.md` P-Testing（T-Test.1+）。场景意图：

| ID | 场景 | 期望（逻辑层） |
|----|------|----------------|
| S1 | own combat_result | 本机 skip induce（REAL 已执行） |
| S2 | peer combat_result | 非 executor 走 induce；host 对 peer 本地 apply |
| S3 | personal self block/heal | 仅 executor 本机 authoritative 写入个人目标 |
| S4 | apply_power non-owner | LOCAL_OWNER_ONLY 不 fire；投影/权威写入按策略 |
| S5 | induced hop ≥ max | 丢弃，不二次广播 |
| S6 | 非法 phase queue_submit / end_turn | gate 拒绝 |
| S7 | monster_turn_result admit | stage-host + phase + transaction |
| S8 | queue source+seq 重复 | 去重 / 不双计 |

## 7. 目标提取面（架构意图）

从引擎耦合面下沉到 pure（任务驱动，非一次做完）：

| 决策面 | 现状锚点 | 目标 |
|--------|----------|------|
| Admit / local induce | `MessageRouter.broadcastCombatResult`、测试内镜像 | `*Policy` in main |
| Personal target | `ApplyPowerEffects.isLocalPersonalTarget` | 保持 pure + 表测 |
| Hop limit | `CombatResultReplayer` MAX_INDUCED_HOP | pure hop policy |
| Owner-fire vs skip | Replayer `localOwnerReplay` / power projection | pure plan + 表测 |
| Phase / queue / monster | 已有 Coordinator / Gate / Manager | 补边界表测 |

## 8. 相关文档

| 文档 | 角色 |
|------|------|
| [`../../AGENTS.md`](../../AGENTS.md) | 委派、TDD 操作规则 |
| [`../spec.md`](../spec.md) | NFR-16+、验证验收 |
| [`../ARCHITECTURE.md`](../ARCHITECTURE.md) | §22 测试与可验证性 |
| [`../plan.md`](../plan.md) / [`../task.md`](../task.md) | P-Testing 任务 |
| [`android-harness.md`](./android-harness.md) | 设备台 |
| [`../console-commands.md`](../console-commands.md) | console 语义（≠ 单元测） |
