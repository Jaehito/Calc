# Unity UI — UGUI 전용

> **이 파일이 SSOT.** Claude Code 전용 — `.cursor/**`·`AGENTS.md`는 읽지도 수정하지도 않는다(동기화 의무 없음).

## 원칙

- **모든 게임 UI는 Unity UGUI** (`Canvas`, `RectTransform`, `Image`, `Button`, `TextMeshProUGUI` 등)
- **UI Toolkit 사용 금지** — `UIDocument`, UXML, USS, `VisualElement` 기반 UI 금지
- **런타임에 정적 레이아웃 전체를 코드로 조립하지 않는다**

## 정적 UI → 씬(또는 프리팹)에 UGUI 배치

다음은 **씬·프리팹에 미리 배치**하고, 스크립트는 `[SerializeField]`로 참조만 연결한다.

- 화면 레이아웃(패널, 바, 고정 버튼, 라벨, 게이지 틀)
- 항상 존재하는 고정 개수 UI(예: 필드 5칸, 유물 8칸 슬롯 틀)
- Canvas / EventSystem

**Canvas 표준**

- `CanvasScaler`: `Scale With Screen Size`
- `referenceResolution`: **3840 × 2160** (`BattlePrototype`, `Title`). `Global` 등 기타 씬은 별도(예: Global 1920×1080).
- 한글 TMP: `Resources/UI/BattleUIFontSettings` 또는 `Assets/Resource/Fonts/` SDF 폰트

**바인딩**

```csharp
// ✅ 씬에 배치된 참조
[SerializeField] private TextMeshProUGUI _hpText;
[SerializeField] private FieldCellView[] _playerFieldCells;

public void Refresh()
{
    _hpText.text = "HP " + _sessionModel.PlayerHp;
}
```

## 동적 UI → 프리팹 Instantiate

개수·내용이 **런타임에 변하는** 요소만 코드에서 생성한다.

- 상점 카드 목록, 인벤토리 슬롯, 데미지 플로팅 텍스트, 동적 리스트 행

**방법**

1. `Resources/` 또는 Addressables에 **UGUI 프리팹** 준비
2. `Instantiate(prefab, parent)` 후 `SetData` / `Refresh`로 값만 갱신

```csharp
// ✅ 동적 항목 — 프리팹 기반
ShopItemView itemView = Instantiate(_shopItemPrefab, _content);
itemView.SetData(itemData);

// ❌ 런타임 UGUI 조립
GameObject row = new GameObject("Row");
row.AddComponent<RectTransform>();
row.AddComponent<Image>();
row.AddComponent<TextMeshProUGUI>();
```

## 금지

| 금지 | 대안 |
|------|------|
| `Awake`/`Start`에서 Canvas·패널·버튼 전체 생성 | 씬 Setup Editor 또는 Unity 에디터에서 배치 |
| UI Toolkit (`manage_ui` MCP) | UGUI + `manage_scene` / `manage_gameobject` |
| `WireframePanelBuilder`식 런타임 UI 빌더 | 씬 `[SerializeField]` 바인딩 |
| 동적 목록을 매 프레임 `new GameObject` | 풀링 또는 프리팹 Instantiate |

## Unity MCP 작업 시

작업 전 **`Dev/Assets/MCPForUnity/ProjectInstructions.md`** 를 확인한다.

- **`manage_ui` 사용 금지** (UI Toolkit 전용)
- 정적 UGUI: `manage_scene`, `manage_gameobject`, `manage_prefabs`, `execute_menu_item`
- 대량 초기 배치: Editor 메뉴 Setup 스크립트(예: `Aftertime/SOS/Setup BattlePrototype Scene`) 우선 검토
- 작업 후: 씬 저장 + `read_console` Error 0 확인

## 에디터 Setup 패턴

씬 UI를 코드로 한 번 구성해야 할 때는 **Editor 전용 Setup 스크립트**(`[MenuItem]`)로 씬에 UGUI를 생성·저장한다. 플레이 모드 런타임 생성과 구분한다.

- 예: `Dev/Assets/Scripts/Battle/Editor/BattlePrototypeSceneSetupEditor.cs`
- 예: `Dev/Assets/Scripts/UI/Editor/TitleSceneSetupEditor.cs`

## 레이아웃 겹침 방지

- TopBar·한 줄 라벨: **고정 X 3개 이상 나열 금지** — `HorizontalLayoutGroup` 또는 X 간격 ≥ (width + 16px)
- 동적 목록: 고정 높이 3분할 패널 금지 → `ScrollRect` + `VerticalLayoutGroup` + `ContentSizeFitter`
- TMP 긴 텍스트: `enableWordWrapping = true`, 버튼 옆 label은 `LayoutElement.flexibleWidth = 1`
- spawn/Refresh 후: `LayoutRebuilder.ForceRebuildLayoutImmediate` (shop content·route strip 등)

## 중첩 오버레이 (뒤로가기)

- deck inspect 등 **자식 팝업** 닫기(← / ESC / `Hide`)는 **단일 `OnHidden` → 부모 복귀** 경로로 처리
- `Update` ESC와 back 버튼이 **서로 다른 복귀 로직**을 갖지 않도록 한다 (한쪽만 복구하면 소프트락)
- Hide 부모 전 **return target**(Shop / CardEnhance 등)을 기록하고, 닫을 때 직전 부모 UI를 다시 `Show`
- 비전투 서비스(카드 제거·인챈트): **확정 pick 전** 골드 차감·Sold 플래그 금지

## 3840 타이포그래피·가독성

**폰트·카드 텍스트 크기는 Layout 상수만 사용한다.** magic number `fontSize` 1920 잔재 금지.

| 씬/영역 | 상수 클래스 |
|---------|------------|
| 전투 HUD·카드·보상 | `Dev/Assets/Scripts/Battle/UI/BattleHudLayout.cs` |
| 맵·상점·이벤트·휴식 | `Dev/Assets/Scripts/Battle/UI/BattleMapUiLayout.cs` |
| 타이틀 메뉴 | `Dev/Assets/Scripts/UI/TitleUiLayout.cs` |

**카드 텍스트**

- `PlayingCardView.Create(..., cardSize)` + `ConfigureTypography` 사용
- `ConfigureTypography(cardSize)`에서 rank font **하드코드 금지** — Layout 상수 경유만 (`ApplyRankTextLayout`·`_rankFontSize`는 존재하지 않는 옛 표기였음)

**TMP overflow**

- 모든 `TextMeshProUGUI`: `overflowMode = TextOverflowModes.Overflow` **필수**
- `Ellipsis`, `Truncate`, TMP `Masking` overflow **금지**
- Setup Editor·런타임 TMP 생성·`Refresh`/`ConfigureTypography` 시 적용
- SSOT: `BattleUiFontUtility.UpdateOverflowMode` (`Dev/Assets/Scripts/Battle/UI/BattleUiFontUtility.cs`) — View 쪽 `UpdateTextOverflowToPanel` 등은 위임만

**Setup Editor**

- `BattlePrototypeSceneSetupEditor`, `TitleSceneSetupEditor`는 위 Layout 상수만 사용

## UI Verify (선택)

시각 대조가 필요하면 [`docs/wireframe/README.md`](../../docs/wireframe/README.md) 절차를 따른다.

**사용자가 비교 기준 문서(PNG/PDF)를 지정한 경우에만** 수행. 미지정 시 Ask-first (`.claude/rules/design-gap-ask-first.md`).
