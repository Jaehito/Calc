# 지출 기록 — 잠금화면에서 Notion으로

안드로이드 잠금화면 알림에 `커피 4500` 을 입력하면 Notion 지출 DB에 한 줄이 추가됩니다.
잠금 해제가 필요 없습니다.

```
잠금화면 알림 ──[기록]──▶ 인라인 답장 입력 ──▶ 파싱 ──▶ Notion API ──▶ DB 한 줄
```

## 구성

| 경로 | 역할 |
|---|---|
| `android/` | 안드로이드 앱 (Kotlin) |
| `notion_expense.py` | PC에서 연동을 확인·기록하는 CLI (설치 불필요) |

앱 소스 (`android/app/src/main/java/com/calc/expense/`):

| 파일 | 역할 |
|---|---|
| `ExpenseParser.kt` | `커피 4500` → `Expense("커피", 4500)` |
| `NotionIds.kt` | 붙여넣은 URL에서 DB ID 추출 |
| `NotionClient.kt` | Notion REST 호출 (외부 의존성 없음) |
| `NotificationHelper.kt` | 잠금화면 상시 알림 + 인라인 답장 |
| `ReplyReceiver.kt` | 입력 수신 → 파싱 → 기록 |
| `BootReceiver.kt` | 재부팅 후 알림 복구 |
| `SettingsStore.kt` | 토큰 암호화 저장 |
| `MainActivity.kt` | 설정 화면 |

---

## 1. Notion 준비

**토큰 발급**
1. https://www.notion.so/my-integrations → **New integration** → Internal
2. **Internal Integration Secret** 복사

**DB에 권한 주기** — 이걸 빼먹으면 404가 납니다
- 지출 DB 페이지 → 우측 상단 `•••` → **연결(Connections)** → 만든 인테그레이션 추가

**DB 속성** — 아래 세 가지가 있어야 합니다. 이름은 자유롭게 바꿔도 되고, 앱 설정에 그 이름을 적으면 됩니다.

| 기본 이름 | 타입 |
|---|---|
| 이름 | `title` |
| 금액 | `number` |
| 날짜 | `date` |

"이번 달 지출"은 DB 뷰에서 날짜 필터를 `이번 달` 로 걸어두면 자동으로 채워집니다.

---

## 2. 앱 설치

빌드된 APK:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

**USB 연결이 되는 경우** — 개발자 옵션에서 USB 디버깅을 켜고:

```bash
C:/Users/cjh07/AppData/Local/Android/Sdk/platform-tools/adb.exe install -r C:/Calc/android/app/build/outputs/apk/debug/app-debug.apk
```

**USB 없이** — APK 파일을 폰으로 옮긴 뒤(카카오톡 나에게 보내기, 드라이브 등) 파일 관리자에서 탭하세요.
디버그 서명이라 **출처를 알 수 없는 앱 설치**를 허용해야 합니다.

---

## 3. 앱 설정

1. 앱 실행 → 토큰과 DB ID 입력 (DB URL을 통째로 붙여넣어도 ID만 뽑아냅니다)
2. 속성 이름 3개 확인
3. **저장하고 연결 확인** → DB 제목이 뜨면 성공
4. **알림 켜기** → 알림 권한 허용

---

## 4. 사용

잠금화면 알림의 **기록** 을 누르고 입력:

| 입력 | 결과 |
|---|---|
| `커피 4500` | 커피 / 4,500원 |
| `점심 김밥 6000` | 점심 김밥 / 6,000원 |
| `4500 커피` | 커피 / 4,500원 |
| `택시 12,000원` | 택시 / 12,000원 |
| `커피 4천` | 커피 / 4,000원 |
| `장보기 1.5만` | 장보기 / 15,000원 |

규칙은 **마지막 숫자 토큰이 금액, 나머지가 이름**입니다.
결과는 같은 알림에 `✓ 커피 4,500원 기록됨 · 오후 3:21` 처럼 표시됩니다.

---

## 5. 삼성(One UI) 확인 사항

인라인 답장 자체는 표준 API지만, One UI는 기본 설정이 잠금화면 내용을 가립니다.

- **설정 → 알림 → 잠금화면 알림** → *내용 표시* 켜기
- **설정 → 알림 → 고급 설정 → 알림 아이콘만 표시** 끄기
- **설정 → 배터리 → 백그라운드 사용 제한** 에서 이 앱을 **제한 안 함**으로 (절전 모드가 알림을 죽입니다)

그래도 답장 칸을 눌렀을 때 잠금 해제를 요구한다면, 그건 기기 정책이라 앱에서 우회할 수 없습니다.
지문 한 번 찍고 입력하는 형태가 됩니다.

---

## 6. 문제 해결

| 증상 | 원인 |
|---|---|
| `401` | 토큰이 틀렸습니다 |
| `403` / `404` | DB의 `•••` → 연결 에 인테그레이션을 추가하지 않았습니다 |
| `속성이 DB에 없습니다` | 앱에 적은 속성 이름과 실제 DB 이름이 다릅니다 |
| `... 은 number 이어야 하는데 rich_text 입니다` | 금액 속성 타입을 숫자로 바꾸세요 |
| `시간 초과` | 요청이 Notion에 닿았는지 알 수 없습니다. DB를 확인하고 없으면 다시 입력하세요 |
| 재부팅 후 알림 사라짐 | 배터리 최적화에서 앱을 제외하세요 |
| 평문 저장 경고가 뜸 | 기기 키스토어 문제. 앱 전용 저장소라 다른 앱은 못 읽지만, 루팅 기기라면 주의하세요 |

---

## 개발

```bash
cd C:/Calc/android && ./gradlew.bat testDebugUnitTest
```

```bash
cd C:/Calc/android && ./gradlew.bat assembleDebug
```

툴체인은 `C:/Users/cjh07/AppData/Local/Android/` 아래에 있습니다 (JDK 17, SDK 35, Gradle 8.11.1).
`JAVA_HOME` 을 `.../Android/jdk-17` 로 지정해야 합니다.

## 보안

- 토큰은 `EncryptedSharedPreferences`(AES-256)로 저장하며, 기기 키스토어 초기화에 실패하면 앱 전용 평문 저장으로 내려앉고 설정 화면에 경고를 띄웁니다.
- `ReplyReceiver` 는 `exported=false` 라 다른 앱이 호출할 수 없습니다.
- 디버그 서명 APK입니다. 배포용이 아니라 개인 사용 목적입니다.
