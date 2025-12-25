#!/bin/bash

# 设置线程限制
ulimit -n 999999
ulimit -u 999999

# Velocity 优化启动脚本 - 支持 140+ 玩家
java -Xms8G -Xmx8G \
  -XX:+UseG1GC \
  -XX:G1HeapRegionSize=32M \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -XX:+UseStringDeduplication \
  -XX:+UseFastAccessorMethods \
  -XX:+AggressiveOpts \
  -XX:+UseCompressedOops \
  -XX:+OptimizeStringConcat \
  -XX:+UseNUMA \
  -XX:ThreadStackSize=256 \
  -XX:MaxDirectMemorySize=2G \
  -Dio.netty.allocator.type=pooled \
  -Dio.netty.leakDetection.level=disabled \
  -Dio.netty.recycler.maxCapacityPerThread=4096 \
  -Dio.netty.recycler.maxSharedCapacityFactor=2 \
  -jar velocity.jar