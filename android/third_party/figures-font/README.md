# 금액 숫자 글꼴 (Haruchi Figures)

`app/src/main/res/font/figures_*.ttf` 는 [Pretendard](https://github.com/orioncactus/pretendard) 1.3.9 에서
숫자와 금액 기호(`0-9 , . + - − % ₩ : / ~ ( ) × #` 와 공백)만 잘라 낸 것이다. 한 파일에 6KB 남짓이다.

- 한글은 이 글꼴에 없어서 시스템 글꼴로 넘어간다 — 금액 숫자만 이 글꼴로 보인다.
- Pretendard 는 SIL OFL 1.1 이고 «Pretendard» 가 예약 글꼴 이름이다. 잘라 낸 것은 변형판이므로
  이름을 `Haruchi Figures` 로 바꿨다. 저작권·라이선스 표기(name 테이블)는 그대로 두었고,
  라이선스 전문은 같은 폴더의 `LICENSE.txt` 에 있다.
- 다시 만들 때: `pyftsubset` 으로 위 글자만 남기고 `tnum`·`kern` 기능을 유지한 뒤 name 테이블의
  글꼴 이름(ID 1·3·4·6·16·17)에서 Pretendard 를 바꾼다.
