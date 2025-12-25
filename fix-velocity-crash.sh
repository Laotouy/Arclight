#!/bin/bash

echo "=== Velocity 崩溃修复方案 ==="
echo ""
echo "1. 立即停止服务器并删除问题插件："
echo "   rm -f plugins/AntiWorldDownloader*.jar"
echo ""
echo "2. 修改系统限制 (/etc/security/limits.conf)："
echo "   * soft nproc 999999"
echo "   * hard nproc 999999"
echo "   * soft nofile 999999"
echo "   * hard nofile 999999"
echo ""
echo "3. 修改系统参数 (/etc/sysctl.conf)："
echo "   kernel.threads-max = 999999"
echo "   kernel.pid_max = 999999"
echo "   vm.max_map_count = 655360"
echo ""
echo "4. 应用系统配置："
echo "   sudo sysctl -p"
echo ""
echo "5. 使用优化的启动参数："
cat << 'EOF'
/opt/java/jdk-21.0.8/bin/java -Xms8G -Xmx8G \
  -XX:+UseG1GC \
  -XX:G1HeapRegionSize=32M \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1ReservePercent=10 \
  -XX:MaxTenuringThreshold=1 \
  -XX:ThreadStackSize=256 \
  -XX:MaxDirectMemorySize=2G \
  -Djava.util.concurrent.ForkJoinPool.common.parallelism=8 \
  -XX:ParallelGCThreads=8 \
  -XX:ConcGCThreads=2 \
  -Dio.netty.allocator.type=pooled \
  -Dio.netty.leakDetection.level=disabled \
  -Dfile.encoding=UTF-8 \
  -Dvelocity.packet-decode-logging=false \
  -jar velocity-3.4.0-SNAPSHOT-528.jar
EOF
echo ""
echo "6. 监控线程数量："
echo "   watch -n 1 'ps -eLf | grep java | wc -l'"
echo ""
echo "警告：AntiWorldDownloader 插件存在严重问题，创建了 13万+ 线程！"
echo "建议：寻找替代插件或联系插件作者修复此问题"