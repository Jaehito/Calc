# 테이블 SSOT / 구글드라이브 접근 (강제)

> Claude Code 전용. `.cursor/**`·`AGENTS.md`와 무관 — 동기화하지 않는다.

## 원칙

- **구글드라이브 시트가 모든 테이블의 SSOT.** 로컬 xlsx·코드 하드코딩·목업 이미지와 값이 다르면 **항상 드라이브 시트 값을 따른다.**
- 사용자가 드라이브/시트 링크를 주거나 "테이블 확인 / SSOT"를 요청하면 **무조건 `.secrets` OAuth 토큰으로 Drive API를 직접 fetch**한다.
- **`WebFetch` 사용 금지** — 비공개 시트라 항상 401이며, WebFetch는 이 토큰으로 인증하지 못한다(익명 HTTP). 시도해서 시간 낭비하지 않는다.

## 절차 (검증됨)

1. `.secrets/google_token.json` → `refresh_token`
2. `.secrets/google_client_secret.json` → `installed.{client_id, client_secret, token_uri}`
3. refresh_token으로 access token 갱신: `POST {token_uri}`, form `grant_type=refresh_token`
4. `https://docs.google.com/spreadsheets/d/{SHEET_ID}/export?format=csv&gid={GID}` 를 `Authorization: Bearer {access}` 헤더로 GET
5. 응답을 **UTF-8로 파일 저장 후 `Read` 도구로 읽는다** (콘솔 직접 출력은 mojibake).

## 관련 테이블 함께 읽기

테이블 작업 시 대상 파일 1개만 보지 말고 **같은 도메인 폴더의 형제 시트 + `enum` 하위폴더를 함께 읽는다** (모든 테이블을 읽으라는 뜻 아님 — 그 폴더 범위만).

- 루트 폴더 ID `1CwxoyMfjBk6Ot6G_eGdHJaYCFeDNCW9W` → 도메인 폴더: 저장 데이터 · 적 테이블 · 맵/보상 · 버프 · 엠블렘 · 이벤트 · 카드 · 해금.
- 대상이 어느 폴더인지 모르면 **해당 폴더만 나열**해 형제 파일을 확인한다 (`files.list`, `q='{folderId}' in parents and trashed=false`). 폴더 = 도메인이므로 이 구조가 SSOT — 시트 목록을 이 룰에 하드코딩하지 않는다(드리프트 방지).

## 주의

- **토큰·시크릿 값을 화면에 출력하지 않는다** — 구조·키 이름만 노출.
- 스크립트는 스크래치패드에 두고, **실행 cwd = 레포 루트**(상대경로 `.secrets/`가 보이도록).
- `urllib`만으로 충분 (추가 의존성 없음). 호출 형태: `python fetch_sheet.py {SHEET_ID} {GID} {OUT_CSV}`.
- 인증 경로 구분: 이 토큰은 Unity 에디터 싱크(`GoogleOAuthClient`, scope `drive.readonly`)와 동일 자격이다. Unity 싱크 메뉴는 여전히 유효하지만, 링크로 준 임의 시트는 위 직접 fetch가 빠르다.
