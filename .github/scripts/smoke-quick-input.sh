#!/bin/bash
# 에뮬레이터에 디버그 APK 를 깔고 기록 창을 바로 띄운다. 로그인 없이 뜨는 상태를 본다.
# 기록 창은 exported=false 라 셸 권한으로는 못 연다 — adb root 로 연다.
set -x
mkdir -p smoke
APK=$(ls android/app/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$APK"
adb root
sleep 3
adb wait-for-device
adb logcat -c

adb shell am start -W -n com.calc.expense/.QuickInputActivity
sleep 6
adb exec-out screencap -p > smoke/1-open.png

# 이름 + 금액을 치면 금액이 알약으로 바뀌어야 한다. (%s 는 공백)
adb shell input text "coffee%s4500"
sleep 3
adb exec-out screencap -p > smoke/2-typed.png

# 엔터로 기록 → 결과 문구가 입력 칸 아래 한 줄 자리에 떠야 하고, 숫자·입력 칸은 그대로여야 한다.
# (로그인 없는 에뮬레이터라 기록 자체는 실패 문구가 뜬다 — 자리만 본다.)
adb shell input keyevent 66
sleep 3
adb exec-out screencap -p > smoke/3-submitted.png

# 뒤로 가기 한 번 = 키보드만 내림. 숫자·입력 칸이 키보드 있을 때와 같은 자리여야 한다.
adb shell input keyevent 4
sleep 2
adb exec-out screencap -p > smoke/4-keyboard-hidden.png

adb logcat -d -b crash > smoke/crash.txt
adb logcat -d > smoke/logcat.txt
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > smoke/top.txt || true
cat smoke/top.txt

if [ -s smoke/crash.txt ]; then
  echo "::error::기록 창이 죽었다"
  cat smoke/crash.txt
  exit 1
fi
exit 0
