# 선 아이콘 (Lucide)

`app/src/main/res/drawable/` 의 `ic_*.xml` 중 주석에 «Lucide»가 적힌 것은
[Lucide](https://lucide.dev) 1.48.0 의 SVG 를 안드로이드 벡터로 옮긴 것이다(ISC 라이선스, 전문은 `LICENSE`).

- 하단 탭(홈·통계·도감)은 예전 채운 아이콘을 그대로 쓴다 — Lucide 가 아니다.
- 선 굵기 2, 끝·이음 둥글게. 색은 Compose 의 `Icon(tint = …)` 나 레이아웃의 `app:tint` 가 정한다.
- 앱 아이콘(웃는 지갑)과 알림 작은 아이콘(`ic_stat_haruchi`)은 직접 그린 것이다 — Lucide 가 아니다.
- 카테고리 아이콘(`ic_cat_*`), 홈 세 줄(`ic_calendar`·`ic_piggy`·`ic_receipt`), 기록 버튼(`ic_pencil`),
  스티커(`ic_sticker_*`)도 직접 그린 색 있는 그림이다 — Lucide 가 아니고 tint 하지 않는다.
  지금 Lucide 로 남은 것은 설정·뒤로·꺾쇠·체크·추세·반복·기록(↑)·지우기(✕) 같은 기능 아이콘이다.
- 옮길 때 `circle`·`rect` 는 같은 모양의 `path` 로 바꿨다. 모양은 원본 그대로다.
