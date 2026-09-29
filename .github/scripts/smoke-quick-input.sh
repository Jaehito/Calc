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
