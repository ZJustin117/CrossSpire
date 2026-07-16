# CrossSpire — SDD 迁移任务清单 (Task)

> 27/29 完成, 2 推迟 | 77 tests | JAR 467KB | 12 commits

## 进度

| Phase | Done | Deferred |
|-------|------|----------|
| P1 架构修复 | 5/5 | |
| P2 功能补全 | 9/10 | T2.4#5-6 host migration |
| P3 清理稳定 | 7/8 | T3.8 回归(evergreen) |
| 归档 | 6/6 | |

## P1 架构修复 ✅

- T1.1 删除共享 RNG → StageHost.stageRng
- T1.2 StandardPacket + PacketOperation
- T1.3 P2PManager → StarConnectionManager
- T1.4 删除 BroadcastManager

## P2 功能补全 (9/10)

- T2.1 修复诱导重放管道 (CardStub/PowerStub/EffectCapture回收集)
- T2.2 EndTurnButton gate (Prefix+Postfix)
- T2.3 怪物回合 HP 增量法
- T2.4 房主选举 (4/6: pong/timeout/detection/disconnect — host migration deferred)
- T2.5 事件系统 (deferred)
- T2.6 交互选择 (deferred)
- T2.7a/b/c 房间标注+共识+图主导航

## P3 清理稳定 (7/8)

- T3.1 Cache LRU + SHA-256 verify
- T3.2 CentralQueueManager 竞态修复
- T3.3 seq 全域统一
- T3.4 Bug 修复 (7项, 1项deferred)
- T3.5 ResourceRegistry 查询 API
- T3.6 Heartbeat seq
- T3.7 spire-patches.md 更新

## 归档 ✅

- A1 EffectCapture API
- A2 ReferenceFactory
- A3 TriggerRegistry fire
- A4 RemoteResource JavaDoc
- A5 RemoteAssetServer 线程安全
- A6 StageHost.electHost
