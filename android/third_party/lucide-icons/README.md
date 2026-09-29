# 선 아이콘 (Lucide)

`app/src/main/res/drawable/` 의 `ic_*.xml` 중 주석에 «Lucide»가 적힌 것은
[Lucide](https://lucide.dev) 1.48.0 의 SVG 를 안드로이드 벡터로 옮긴 것이다(ISC 라이선스, 전문은 `LICENSE`).

- 하단 탭(홈·통계·도감)은 예전 채운 아이콘을 그대로 쓴다 — Lucide 가 아니다.
- 선 굵기 2, 끝·이음 둥글게. 색은 Compose 의 `Icon(tint = …)` 나 레이아웃의 `app:tint` 가 정한다.
- 앱 아이콘(일력 한 장)과 알림 작은 아이콘(`ic_stat_haruchi`)은 직접 그린 것이다 — Lucide 가 아니다.
- 옮길 때 `circle`·`rect` 는 같은 모양의 `path` 로 바꿨다. 모양은 원본 그대로다.
