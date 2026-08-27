---
paths:
  - "Dev/Assets/Scripts/Battle/**/*.cs"
---

# Battle 아키텍처 invariant

> **이 파일이 SSOT.** Claude Code 전용 — `.cursor/**`·`AGENTS.md`는 읽지도 수정하지도 않는다(동기화 의무 없음).
> 공통 코딩: `.claude/rules/core.md` · 스타일 SSOT: `docs/coding-style.md` · Ask-first: `.claude/rules/design-gap-ask-first.md`

## 금지 / 대신

| 금지 | 대신 |
|------|------|
| `BattleLayout`에 `Enter*` / `Complete*` **로직** 추가 | `RunFlow` |
| layout·FSM에서 `SyncToSession` / `SyncFromSession` 직접 | `RunSessionBridge` only |
| `BattleFieldModel` 슬롯 배열 직접 수정 | `TrySwap*` / `TryMove*` / `TryRelocate*` |
| `new GameObject` + UGUI로 **화면 틀** 런타임 조립 | 씬 Setup Editor 또는 **프리팹** Instantiate |
| `EnsureBuilt` / `EnsureRuntimeOverlays` 패턴 **추가** | SerializeField + Setup |
| Battle 코드에 **EventBus / VContainer / R3** `using`·신규 호출 | plain C# · 직접 호출 · `BattleUiCallbacks` |
| `BattleHudLayout` 외 magic number `fontSize` | Layout 상수 클래스 |
| layout에 필드 드롭 **판정** 로직 추가 | `Battle/Rule/*Rule` (`FieldDropRule`, `CardFieldActivationRule` 등) |
| `BattleLayout` **줄 수 증가** | 로직을 `Battle/Rule/`로 추출; Layout은 `Refresh*`·와이어링만 |
| `Battle/UI` 다른 파일에서 `new *Rule` / `new BattleLoopStateMachine` | `BattleLayout.Init()` 조립 구역만 |
| Service ctor에 `Action` 개별 나열 | `BattleUiCallbacks` DTO 한 개 |

## Apply 경계 (SSOT)

| 구간 | SSOT | 금지 |
|------|------|------|
| Field → Session (점수·데미지점수·버스트) | `FieldDropRule.UpdateFieldScoresFromField` | `session.PlayerScore =` 직접 대입 후 burst 생략 |
| Run ↔ Session (HP·골드·럭) | `RunSessionBridge` Push/Pull | Layout·FSM에서 run/session 필드 직접 복사 |
| Run 페이즈 Enter/Exit | `RunFlow` | Layout에 페이즈 전환 로직 |

FSM·Layout은 위 SSOT **호출만** — 동일 Apply를 위한 **새 `Sync*` 메서드 추가 금지**.

## 진입점 (검색 앵커)

| 역할 | 클래스 |
|------|--------|
| RunPhase Enter/Exit | `RunFlow` |
| Run ↔ Session HP/골드 Apply | `RunSessionBridge` |
| 전투 턴 루프 | `BattleLoopStateMachine` → `BattleTurnRule` *(배치 1에서 리네임)* |
| 필드 카드 배치 | `BattleFieldModel` |
| Field → Session 점수·버스트 | `FieldDropRule.UpdateFieldScoresFromField` |
| 필드 드롭·홀드 판정/적용 | `FieldDropRule` / `PlayerFieldPackedDropResolver` |
| 카드 활성화 타겟 판정 | `CardFieldActivationRule` |
| 포인터→필드 셀 | `BattleFieldPointerResolver` |
| Battle Rule 조립 (`new *Rule`) | `BattleLayout.Init()` only |
| HUD·오버레이 표시 | `BattleLayout` + `BattleLayoutView` (`Refresh*` only) |
| FSM→UI 갱신 | `IBattleHudHost` (`Refresh*` only) |
| Service→Layout UI 갱신 | `BattleUiCallbacks` |

## 레이어

| 폴더 | UI 참조 |
|------|---------|
| `Battle/Model/`, `Battle/Run/`, `Battle/FSM/`, `Battle/Turn/`, `Battle/Rule/` | 금지 |
| `Battle/UI/` | 허용 |

- `Battle/UI/` 어휘: `*Layout`(MonoBehaviour 허브) · `*LayoutView`(SerializeField 참조만) · `*Panel`(모달) · `*Presenter`(UI 상호작용·표시 소유 plain C#) · `*Bridge`(자기 화면 없이 두 조각 연결) · `*Helper`(입력·표시 보조 MB). 전체 표는 `docs/coding-style.md`.
- **위 경로 표기가 확정본이다** — 폴더 재편(System / Implements 분리) 계획은 취소됐다. `ArchitectureTests`의 경로도 이 표기를 기준으로 유지한다.

## 검증

- Model / Bridge / RunFlow / Rule SSOT 변경 시: 그 기능을 덮는 EditMode 테스트 **추가** (실행은 오케스트레이터 — `.claude/rules/playtest.md`)
- UI·씬 변경 시: `read_console` Error 0
- `ArchitectureTests`: Layout baseline·`BattleLoopStateMachine`/`FieldDropRule` 조립 위치
