---
name: card-verify
description: 카드 능력을 Play 모드에서 사람처럼(실제 마우스 드래그·클릭) 자동 발동시켜 검증하고, 스크린샷과 판정 CSV를 남긴다. "카드 검증", "카드 효과 테스트", "카드 능력 확인" 요청 시 사용. 특정 카드만 또는 전량(133장 = 플레이어 109 + 적 24) 검증 가능.
---

# 카드 능력 자동 검증

플레이어 카드(1xxx·5xxx)와 적 카드(2xxx)의 능력이 **실제로 발동하고 값이 맞는지** 인게임에서 검증한다. EditMode 테스트로는 "발화 지점 미배선"을 못 잡으므로 이 하네스가 유일한 신뢰 경로다.

**로스터 133장 = 플레이어 109 + 적 24** (라이브 카탈로그에서 매번 다시 센다 — 하드코딩 목록 없음. 이 수치는 2026-08-07 실측).

## 실행 — 웹 패널이 기본 경로

검증은 **agent 인스턴스 패널(`http://localhost:17800`)** 「실행」 탭의 **「카드 검증」 그룹**에서 돌린다 (원본 패널에서 실행하지 않는다 — `.claude/rules/playtest.md`).

- `GET /api/card-verify` — 슬라이스 목록 + 로스터 장수. `available:false` 면 그 사유가 그대로 온다(디파인 꺼짐·컴파일 실패 등).
- `POST /api/run-card-verify` — `{ sliceId, cardIds[], goldenUpdate, autoBoot }`. 이미 실행 중이면 409.
- `GET /api/card-verify/result` — 마지막 `result.csv` 를 등급별 집계 + 카드별 행으로.

슬라이스는 **전체 / 플레이어 / 적 / 30장 분할**이며 서로 겹치므로 **한 번에 하나만** 고른다. `cardIds` 를 주면 그 카드만 돈다(슬라이스 무시).

내부 흐름: 패널이 핸드오프 `Library/PlaytestPendingCardVerify.json` 을 쓰고 → PlayMode 러너 `Aftertime.SOS.Tests.PlayMode.CardVerifyPanelTest.RunPendingCardVerify` 1개를 태운다 → 러너가 리플렉션으로 하네스를 돌린다. 핸드오프가 없으면 러너는 `Assert.Ignore` 라서 「전체선택 실행」에 섞여도 회귀를 만들지 않는다(노코드 시나리오 러너와 같은 계약).

`goldenUpdate: true` 는 회귀 기준을 이번 실행 값으로 덮어쓴다 — 그 실행의 회귀 축은 아무것도 잡지 못하고 등급이 전부 `PASS(회귀-신규)` 가 된다. 사양을 의도적으로 바꾼 뒤 1회만.

### 하네스 직접 호출 (패널을 못 쓸 때만)

`Aftertime.SOS.Common.Debug.CardVerifyHarness` (Assembly-CSharp, `#if SOS_DEBUG_TOOLS`)

```csharp
CardVerifyHarness.StartVerificationAll(startIndex, count, autoBoot);  // 로스터 슬라이스
CardVerifyHarness.StartVerification(int[] cardIds, autoBoot);         // 특정 카드만
```

`autoBoot: true` → 타이틀에서 `새로하기`·경로 선택까지 합성 클릭으로 자동 부팅한다. **현재 이 경로는 고장나 있다 — 아래 「알려진 이슈」.**

`execute_code` 호출 예 (compiler=`codedom`, `using` 금지 — 정규화된 타입명 사용):

```csharp
var t=System.Type.GetType("Aftertime.SOS.Common.Debug.CardVerifyHarness, Assembly-CSharp");
var m=t.GetMethod("StartVerificationAll", System.Reflection.BindingFlags.Public|System.Reflection.BindingFlags.Static);
return m.Invoke(null, new object[]{0, 40, true});
```

**전량(133장)은 슬라이스로 나눠라** — 패널의 30장 분할 슬라이스가 그 용도다. 40장 ≈ 9분, 전량 ≈ 30~40분. 슬라이스마다 CSV를 `result_sliceN.csv`로 보존한 뒤 합본을 만든다(`result.csv` 는 실행마다 덮어쓴다).

### 진척 추적 (Unity를 건드리지 말 것)

```bash
ls -lt --time-style=+%H:%M:%S CardVerifyShots/ | head -5
```

카드당 20~30초. CSV 행이 늘지 않고 3분 이상 정지면 막힌 것 — 아래 함정 확인.

## 산출물

레포 루트 `CardVerifyShots/` (gitignore됨). **agent 에서 돌리면 agent 루트 아래에 생긴다** (`C:\UnityProjects\SOS-agent\CardVerifyShots\`).
- `{cardId}_{순번}_{before|targeting|success}.png` — 대상 선택형은 `targeting`(화살표가 대상을 가리키는 상태) + `success` 2장. 조건 음성 케이스는 `negative_armed_{레시피}`(사보타주 직후)·`negative_before_{레시피}`(조작 직전)·`negative_after_{레시피}`(조작 직후) 3장 — 파일명의 레시피로 어느 사보타주인지 바로 읽힌다
- `baseline/{cardId}.json` — 회귀 골든(카드별 상태 스냅샷)
- `result.csv` (UTF-8 BOM), 헤더 26칸:
  `cardId,이름,트리거,시나리오,조작,판정,발화,값대조,값상세,회귀,회귀상세,조건,조건상세,조건값,사보타주_무장직후,사보타주_조작직전,사보타주_조작직후,분류,실패지점,차단원인,시도횟수,능력로그,상태변화,플래그,스크린샷,메모`
  - `조건값` = SO 의 `condition`·`condition_value` 원본. `사보타주_*` = 음성 케이스가 겨냥한 상태의 시점별 실측(`기대<N 실제M 유지=O/X`).
  - **`사보타주_조작직전`의 `유지=X` 면 그 행의 조건 판정은 `판정불가`다** — 하네스가 조건을 못 깬 것이라 게임 버그로 읽으면 안 된다.

**슬라이스는 파일명이 겹쳐 덮어써진다**(순번이 1부터 재시작) — PNG 개수로 진척을 판단하지 말고 CSV 행 수를 봐라.

## 판정 — 3축과 등급

축 3개를 따로 평가하고 **가장 강한 증거**로 등급을 매긴다. 등급 문자열이 하네스↔패널 계약이다.

| 등급 | 뜻 | 신뢰도 |
|---|---|---|
| `PASS(값대조)` | SO의 `_executeOps/_executeValues` 로 독립 계산한 기대 delta와 실제 delta가 일치 | 강 |
| `PASS(조건)` | 조건을 깬 음성 케이스에서 발동하지 않았고 `condition unmet` 로그가 있음 | 강 |
| `PASS(회귀)` | 저장된 골든과 상태 스냅샷이 일치 | 중 |
| `PASS(회귀-신규)` | **이번에 기준을 처음 기록** — 대조한 게 아니다. 다음 실행부터 회귀를 잡는다 | 없음 |
| `PASS(발화만)` | 능력이 발동했다는 사실만 확인(값 대조 없음) | 약 |
| `FAIL` / `SKIP` | 실패 / 검증 못 함 — SKIP 을 통과로 세지 말 것 | — |

**집계는 반드시 등급별로 보고한다.** "133장 PASS" 로 뭉뚱그리면 회귀-신규·발화만이 검증으로 오해된다. 패널 결과 탭도 같은 이유로 등급 칩을 항상 펼쳐 보여준다.

발화 축 자체의 판정은 `[Ability] execute|applied` 로그 **또는** 카드의 `*AbilityApplied` 플래그. 상태 diff는 기록 전용(HIT 드로우 등 무관 변화가 섞임).

FAIL은 하네스가 3분류한다:
- **하네스결함** — 좌표·타이밍·사전상태 문제 → 하네스를 고친다
- **조건미충족** — 게임 규칙상 정상(무덤 0장인 GraveRequired 등) → 사전 상태를 갖춰 재시도. 확률형(weight/ChanceCheck)은 최대 5회 재시도
- **게임버그의심** — 조작·조건이 맞는데 발동 안 됨 → **게임 코드를 고치지 말고** 재현 절차·근거를 기록해 보고

## 트리거별 사람 조작 (시나리오 SSOT)

| 트리거 | 조작 | 필요 사전 상태 |
|---|---|---|
| Rune | 필드 카드를 **필드·홀드 밖으로 드래그해 놓기** → 화살표 타겟팅 → **대상 셀 클릭** | 소울칩, (타겟형) 대상 카드 |
| Use(수동) | 위와 동일 | — |
| Use(자동)·Hit | **HIT 버튼 클릭** (GUARD도 Hit 발화) | 덱 top 주입 |
| Guard | GUARD 버튼 클릭 | 소울칩·빈 셀 |
| Play | 필드에 둔 뒤 **STAND** → 라운드 정산 시 발화 | — |
| Hold | 필드 → **홀드 슬롯 드래그** | 홀드 비어있음, 소울칩 |
| Release | 홀드 → 필드 드래그, **홀드한 다음 라운드**에만 가능(`!HoldMadeThisRound`) | 동반 필드 카드 필요 |
| Discard | STAND 후 정산 무덤행 (또는 GUARD 카드 1초 후 정산) | — |
| Exile | 소실 유발 — 소실 키워드 정산, 또는 발화광(1111)으로 소실 | — |
| Blackjack | 점수 **정확히 21** | 점수 강제 주입 |
| Burst | 21 초과 (false→true 전이 1회만) | — |
| RoundWin/Loss | STAND 후 승/패 | 점수 우위/열위 |
| Passive | 조작 없음, 정산 시 평가 | — |
| **TurnStart/TurnEnd** | ❌ **런타임 발화 지점 없음 → 영구 SKIP** | — |

## 함정 (전부 실제로 겪은 것)

- **입력 주입은 `InputSystem.QueueStateEvent(Mouse.current, MouseState)` + `InputSystem.Update()`만 동작.** `InputState.Change`는 값이 유지되지 않는다.
- **드롭 좌표는 격자 스캔으로 구한다** — `IsPointerOutsideFieldAndHold` && `ResolveFieldDragDropIntent().IsValid == false` 둘 다 만족해야 함. 아니면 드롭이 "필드 재배치"로 해석돼 발동하지 않는다.
- **드래그 게이트**: `RunPhase.Battle` · `!_isProcessingAction` · `PlayerTurn` · `!PlayerBurst` · 셀 채워짐 · 카드뷰 부모가 `CardStack`. 라운드 시작 직후 딜러 선행 히트 0.35초 동안 드래그가 전면 차단된다.
- **전투 보상 패널이 화면 전체를 덮어 모든 클릭을 삼킨다** — 배치 중 전투가 끝나면 반드시 모달을 먼저 해소(`_completeButton` 넘기기)해야 한다. 증상이 45초 타임아웃으로만 나타나 진단이 어렵다.
- **F1 디버그 패널이 열려 있으면 스크린샷에 찍힌다** — 캡처 전 Hide.
- 씬 전환 페이드(0.3+0.3초) 중 캡처는 검은 화면. 완료 신호가 없어 고정 대기가 필요하다.
- `condition=CardRequired` 카드는 **동반 필드 카드**가 있어야 발동한다(Hit/Guard 플랜은 덱top 주입만 하므로 취약).

## 라이브 DB 커버리지 한계

`Burst`·`Guard`·`RoundLoss`·`ManualUse`·`AutoUse` 트리거 카드가 **0장**이라 해당 분기는 구현됐지만 실행되지 않는다. 이런 카드가 테이블에 생기면 자동으로 커버된다.

## 알려진 이슈 (2026-08-07)

- **타이틀 부팅 실패** — 하네스 booter 의 타이틀 「새로하기」 합성 클릭이 먹지 않아 `autoBoot: true` 경로가 60초 타임아웃으로 실패한다(스크린샷 확인). 별도 작업으로 분리됨. 패널 경로는 기본값 `autoBoot: false` 로 러너가 `BattleSceneTestDriver.BootBattlePrototype()` 으로 전투 씬을 먼저 띄우므로 타이틀을 거치지 않는다 — **다만 이 우회가 실제로 통과하는지는 아직 미실행 확인이다.**
- 위 버그가 풀리기 전까지 패널 카드 검증의 end-to-end 결과는 없다. 배선·파싱만 구현된 상태다.

## 최근 결과 (2026-07-31 — 낡음)

**플레이어 109장 PASS / 0 FAIL.** 단, 그때의 PASS 는 **발화 축만** 본 것이다(3축 판정 도입 전). 지금 기준으로는 대부분 `PASS(발화만)` 에 해당하며, 값·조건·회귀는 확인되지 않았다. 시나리오 분포: Rune 56 · Play 17 · Hit 15 · Passive 5 · Exile 4 · RoundWin 4 · Blackjack 2 · Discard 2 · Hold 1 · Release 1 (+ 복합 3). 적 24장은 이 결과에 포함되지 않는다.
