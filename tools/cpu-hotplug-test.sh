#!/system/bin/sh
# ============================================================
# CPU 热插拔 / 多核状态 复测脚本
# 目标设备：XXB / b906（MTK6582, Android 5.1）
#
# 用法：
#   adb push cpu-hotplug-test.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/cpu-hotplug-test.sh
#
# 判定逻辑：
#   施加负载后 cpu1/2/3 变为 online  -> hotplug 正常工作，空闲关核只是省电策略
#   施加负载后仍只有 cpu0 online     -> 要么内核禁用了热插拔，要么 ODM 把 min_cpus 锁成 1
# ============================================================

CPU=/sys/devices/system/cpu
HP=$CPU/cpufreq/hotplug

dump_state() {
  echo "  online    = $(cat $CPU/online)"
  echo "  cur_freq  = $(cat $CPU/cpu0/cpufreq/scaling_cur_freq) kHz"
  for n in 1 2 3; do
    f=$CPU/cpu$n/online
    if [ -f "$f" ]; then
      echo "  cpu$n      = $(cat $f)"
    else
      echo "  cpu$n      = (无 online 文件)"
    fi
  done
  # /proc/stat 聚合行，用于事后算 CPU 利用率
  echo "  stat      = $(grep '^cpu ' /proc/stat)"
  echo "  loadavg   = $(cat /proc/loadavg)"
}

echo "############ 1. 基线（空闲） ############"
echo "present   = $(cat $CPU/present)"
echo "possible  = $(cat $CPU/possible)"
dump_state
echo "  governor  = $(cat $CPU/cpu0/cpufreq/scaling_governor)"
echo "  min_freq  = $(cat $CPU/cpu0/cpufreq/scaling_min_freq) kHz"
echo "  max_freq  = $(cat $CPU/cpu0/cpufreq/scaling_max_freq) kHz"
echo "  avail     = $(cat $CPU/cpu0/cpufreq/scaling_available_frequencies)"

echo ""
echo "############ 2. hotplug governor 参数 ############"
ls $HP 2>/dev/null || echo "  (无 $HP 目录)"
for p in up_threshold down_threshold sampling_rate min_cpus max_cpus; do
  if [ -f "$HP/$p" ]; then
    echo "  $p = $(cat $HP/$p)"
  fi
done

echo ""
echo "############ 3. 内核命令行 ############"
cat /proc/cmdline

echo ""
echo "############ 4. 施加负载（8 个忙循环） ############"
pids=""
i=0
while [ $i -lt 8 ]; do
  sh -c 'while :; do :; done' &
  pids="$pids $!"
  i=$((i + 1))
done
echo "  已启动 PID:$pids"

sleep 4
echo ""
echo "--- 负载 4 秒后 ---"
dump_state

sleep 6
echo ""
echo "--- 负载 10 秒后 ---"
dump_state

echo ""
echo "############ 5. 释放负载 ############"
kill $pids 2>/dev/null
sleep 4
echo "--- 释放 4 秒后 ---"
dump_state

echo ""
echo "############ 6. 尝试手动拉起 cpu1 ############"
echo "  （预期：shell 用户无权限写入 sysfs）"
if echo 1 > $CPU/cpu1/online 2>/dev/null; then
  echo "  写入成功 -> online = $(cat $CPU/online)"
  echo 0 > $CPU/cpu1/online 2>/dev/null
else
  echo "  写入失败（权限不足），shell 无法手动控制热插拔"
fi

echo ""
echo "DONE"
