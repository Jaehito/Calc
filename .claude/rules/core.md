# 핵심 코딩 규칙 (Aftertime.SOS)

> **이 파일이 SSOT.** Claude Code 전용 — `.cursor/**`·`AGENTS.md`는 읽지도 수정하지도 않는다(동기화 의무 없음).

## 네임스페이스

모든 게임 코드는 `Aftertime.SOS.*` 하위 네임스페이스를 사용한다.
타 프로젝트 반입 소스도 `Aftertime.SOS.*`로 이관했다(2026-08-13). 유일한 잔여는 `ExtendedButton`·`ExtendedButtonType`(`Aftertime.SecretSome.UI`) — `Title.unity`에 네임스페이스 문자열이 직렬화돼 있어 씬 저장이 필요하므로 보류 중이다.

| 폴더 | 네임스페이스 |
|------|-------------|
| `Common/` | `Aftertime.SOS.Common` |
| `FSM/` | `Aftertime.SOS.FSM` |
| `Battle/` | `Aftertime.SOS.Battle.*` |
| `SaveLoad/` | `Aftertime.SOS.SaveLoad` |
| `UI/` | `Aftertime.SOS.UI` |
| `Scene/` | `Aftertime.SOS.Scene` |
| `Steam/` | `Aftertime.SOS.Steam` |
| `TestEditor/` | `Aftertime.SOS.TestEditor` |
| `Contents/` | `Aftertime.SOS.Content` |
| `Tests/` | `Aftertime.SOS.Tests.*` |

## 금지사항

- `sealed` 클래스 선언 금지
- `??` (null 병합 연산자) 사용 금지
- `var` 타입 추론 금지 — 항상 명시적 타입 사용 (예외 맥락은 `STYLE-EXCEPTION` 주석 필수 — `docs/coding-style.md` 조항 26)
- Unity `Coroutine` / `IEnumerator` 금지 — `UniTask` 사용 (예외: `Dev/Assets/Tests/**` 테스트의 `[UnityTest]` 시그니처 `IEnumerator`는 허용)

## 스타일 SSOT

전체 네이밍·Bo/DTO·UI 패턴 + **서식·관례 조항 전문(1~42)·`STYLE-EXCEPTION` 표기법·소급 범위**: [`docs/coding-style.md`](../../docs/coding-style.md) — 필요할 때 읽는다. `.claude/rules/`에 **새 상시 로드 스타일 문서를 만들지 않는다** (이 참조 1줄이 진입점).

## Battle 프로토타입 — 의존성·이벤트 (강제)

- **VContainer, `[Inject]`, LifetimeScope 신규 도입 금지**
- **R3, `CompositeDisposable`, `ReactiveProperty` 신규 도입 금지** (UniTask는 허용)
- **전역 EventBus / R3 Subject 신규 도입 금지** — 소유자 로컬 `event`·`Action`·직접 호출만
- 의존성 DTO: **`BattleRunContext` + `BattleUiCallbacks`** (다른 `*Container`/`*Deps` 신규 금지)
- 조립: **`BattleLayout.Init()`** 한 곳에서 `new *Rule` / FSM / `RunFlow` 명시 조립 (생성자 인자 5개 이하)
- **드릴링 금지** — 중간 클래스는 자기가 쓰지 않는 의존성을 생성자 파라미터로 받아 하위에 넘기지 않는다. 조립 루트(`BattleLayout.Init()`)에서 **완성된 협력자**를 주입한다. 묶음 전달이 필요하면 DTO 1개(`BattleRunContext` / `BattleUiCallbacks`)로.
- 비동기: **UniTask** only

## Battle 레이어 (요약)

상세: `.claude/rules/battle-architecture.md` (Battle 파일 작업 시 자동 로드)

| 레이어 | 폴더 | UI 참조 |
|--------|------|---------|
| Model | `Battle/Bo/` (`*Bo`), `Battle/Model/` (테이블·enum) | 금지 |
| Run | `Battle/Run/` | 금지 |
| FSM / Turn / Rule | `Battle/FSM/`, `Battle/Turn/`, `Battle/Rule/` | 금지 |
| UI | `Battle/UI/` | 허용 |

## 네이밍 규칙

- 클래스: `PascalCase`
- 인터페이스: `IPascalCase`
- private 필드: `_camelCase`
- 상수/static readonly: `PascalCase`
- 메서드: `PascalCase`
- 지역 변수 / 파라미터: `camelCase`

### 메서드 접두사

| 접두사 | 용도 |
|--------|------|
| `Init` | 조립·초기화 (조립 SSOT `BattleLayout.Init()`). 예외: `SingletonMonoBehaviour.Initialize()` 프레임워크 훅 |
| `Update*` | 데이터·상태 변경 + 값 전달 (구 `Apply*` 통합) |
| `Refresh*` | UI 표시 갱신 |
| `Try*` | 가드 붙은 변경, bool 반환 |
| `Enter*` / `Exit*` | Run 페이즈 |
| `Get*` / `Is*` / `Has*` / `Create*` / `Reset` / `Evaluate*` | 조회·생성·초기 복귀·평가 |

- UniTask 반환 **public** 메서드는 `~Async` 접미사.

### 이벤트

- 이벤트 필드: `on~` (소문자 시작) — `public event Action onShow;`
- 핸들러: `On~` (PascalCase) — `private void OnShowClicked()`
- 구독 등록 메서드: **`SubscribeEvents()`** 로 통일 (`RegisterEvents`·`Wire`·`Setup*` 금지)

### 클래스 어휘

| 접미사 | 역할 |
|--------|------|
| `*Rule` / `*SubRule` | 게임 로직 판정 |
| `*Presenter` | UI 상호작용·표시 소유 |
| `*Bridge` | 자기 화면 없이 두 조각 연결 |
| `*Calculator` | 수치 산출 (확률·배율·데미지) |
| `*Resolver` | 대상 선정·조건 판정·표시 결정 |
| `*Bo` | 런타임 영역 상태 SSOT |
| `*Panel` / `*View` / `*Layout` / `*SO` | 기존 어휘 유지 |

## 파일·단일 책임

- **1파일 1주요 타입** — 파일명 = public 클래스명.
- **soft 상한 (신규·수정 시):** Model/Service/Resolver/FSM ~400줄, MonoBehaviour View ~600줄. 초과 시 **추출** (기능과 무관한 대규모 리팩터 금지).
  - 기존 초과 파일의 분리는 **후속 페이즈 예정** (파일별 분리안 승인 후 하나씩) — 지금 손대는 파일에서만 추출한다.
- **Layout 허브** (`*Layout`, `*Overlay`) — 줄 수 **늘리지 않음**. 로직은 plain C#로 분리. Battle: `.claude/rules/battle-architecture.md`
- **예외:** Input System 생성 코드, Editor Setup 스크립트 (가능하면 구역별 분리).

## 참조 추적

- **폴더 = 도메인** — 상태 `Model/`, 흐름 `Run/`·`FSM/`, 규칙 `Battle/Rule/`·`Turn/`, 표시 `UI/`.
- **접두사:** `Try*`/`Is*` 변경·가드, `Refresh*` UI 표시, `Update*` 데이터·값 전달, `Enter*`/`Exit*` Run 페이즈 (전체 표는 §메서드 접두사).
- **판정 SSOT** — 승패·버스트·드롭 유효 등은 **한 클래스**; Layout·FSM 중복 금지.
- **Sync SSOT** — Battle Sync 행렬은 `battle-architecture.md`; Layout·FSM에 **새 `Sync*` 헬퍼 추가 금지** (기존 SSOT 호출만).
- 신규 기능 전 **기존 Rule/Resolver/Calculator/Presenter/Model.Try* grep** → 없을 때만 새 plain C# 클래스.

## 에이전트 작업 순서

1. 증상 UI 말고 **상태·규칙 owner** 파일부터 Read (가장 작은 파일 우선).
2. 변경 전 한 줄: `입력 → Model → FSM/Coordinator → Sync → Refresh`.
3. **최소 diff** — 요청 범위 밖 리팩터·포맷 금지.
4. **이중 경로 금지** — 같은 규칙을 Layout과 FSM 양쪽에 넣지 않음; 한쪽 SSOT + 다른 쪽 호출.
5. Model/Bridge/Coordinator/Resolver 변경 시 그 기능을 덮는 **EditMode 테스트를 추가**한다 (`ArchitectureTests` 포함). **실행은 오케스트레이터가 턴 끝에 1회** — 서브에이전트는 `run_tests`·패널 API·`manage_editor`로 테스트를 돌리지 않는다 (`.claude/rules/playtest.md`). 컴파일 확인(`refresh_unity` + `read_console`)은 예외로 허용.
6. 플레이어 규칙·fallback: `.claude/rules/design-gap-ask-first.md`.

## Unity MCP 씬·UI 작업

UI 정책 전체: **`.claude/rules/unity-ugui.md`** (UGUI 전용, 정적=씬 / 동적=프리팹).

MCP UI·씬 작업 전 **`Dev/Assets/MCPForUnity/ProjectInstructions.md`** 를 읽는다.

MCP 도구로 씬 오브젝트를 추가·수정·삭제할 때:

1. `manage_scene` / `manage_gameobject` 등으로 현재 씬 계층을 확인한다
2. 동일 이름·경로 오브젝트 중복 추가를 피한다
3. **정적 UGUI는 씬에 배치** — `manage_ui`(UI Toolkit) 사용 금지
4. 작업 후: 씬 저장 + `read_console` Error 0

**금지:** `*_snapshot.json` 파일을 읽거나 참조하지 않는다. 씬 상태는 MCP/에디터 도구로만 확인한다.
