# TODO — CrossSpire

## 未完成

- [ ] **3.4 图主选举协议** — `stage_host_election` / `stage_host_result` 从 reserved 变为实际实现（房主广播候选者→成员回复选票→宣布结果）。ARCHITECTURE.md 标注"暂不实现"。

## 已完成

20/21 项生产改造任务已完成（2026-07-14 ~ 2026-07-16），详见 `docs/ACHIEVEMENTS.md`。

| 类别 | 完成 | 内容 |
|------|------|------|
| 一、架构完整性 | 5/5 | 统一队列 + 深层诱导重放 + target 校验 + 消除重复结果 |
| 二、生产质量 | 7/7 | EventSuppression 封装 + HeartbeatManager + hostId 路由 + 掉线 + 骨架修复 + takeTurn |
| 三、功能对齐 | 3/4 | 怪物意图快照 + 三阶段分离 + RemotePlayer 实例化 + 渲染定位 |
| 四、代码债务 | 5/5 | 死代码清理 + 缓存校验/过期 + initialize 精简 + README |
