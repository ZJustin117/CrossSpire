# TODO — CrossSpire

## 未完成

- [ ] **3.4 图主选举协议** — `stage_host_election` / `stage_host_result` 从 reserved 变为实际实现。
- [ ] **E2E P2P 验证** — 设备端日志系统刷新后完整验证 host→join→combat 流程。

## 已完成

21/22 项生产改造任务已完成（2026-07-14 ~ 2026-07-16），详见 `docs/ACHIEVEMENTS.md`。

| 类别 | 完成 | 内容 |
|------|------|------|
| 一、架构完整性 | 5/5 | 统一队列 + 深层诱导重放 + target 校验 + 消除重复结果 |
| 二、生产质量 | 7/7 | EventSuppression 封装 + HeartbeatManager + hostId 路由 + 掉线 + 骨架修复 + takeTurn |
| 三、功能对齐 | 3/4 | 怪物意图快照 + 三阶段分离 + RemotePlayer 实例化 + 渲染定位 |
| 四、代码债务 | 5/5 | 死代码清理 + 缓存校验/过期 + initialize 精简 + README |
| 五、P2P 迁移 | 1/1 | 删除 relay 服务器, TCP P2P 直连, 51 测试通过, JAR 450KB |
