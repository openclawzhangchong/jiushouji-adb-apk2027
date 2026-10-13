#!/system/bin/sh
# 在设备本地连续滑动，避免 adb 往返开销
i=0
while [ $i -lt 200 ]; do
  input swipe 600 640 120 640 40
  i=$((i+1))
done
echo "SWIPE_DONE"
