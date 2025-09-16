package io.izzel.arclight.common.mixin.core.server;

import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 主线程监控器 - 检测并报告主线程卡死原因
 */
@Mixin(MinecraftServer.class)
public class MainThreadMonitorMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Arclight-Monitor");
    private static final long FREEZE_THRESHOLD_MS = 10000; // 10秒认为是卡死
    private static final long CHECK_INTERVAL_MS = 1000; // 每秒检查一次

    @Shadow @Final private Thread serverThread;

    private ScheduledExecutorService monitorService;
    private AtomicLong lastTickTime = new AtomicLong();
    private volatile boolean isMonitoring = false;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void arclight$initMonitor(CallbackInfo ci) {
        // 创建监控线程池
        monitorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Arclight-MainThread-Monitor");
            t.setDaemon(true);
            return t;
        });
    }

    @Inject(method = "runServer", at = @At("HEAD"))
    private void arclight$startMonitoring(CallbackInfo ci) {
        isMonitoring = true;
        lastTickTime.set(System.currentTimeMillis());

        // 启动监控任务
        monitorService.scheduleWithFixedDelay(() -> {
            if (!isMonitoring) return;

            long now = System.currentTimeMillis();
            long lastTick = lastTickTime.get();
            long elapsed = now - lastTick;

            if (elapsed > FREEZE_THRESHOLD_MS) {
                LOGGER.error("========================================");
                LOGGER.error("主线程已卡死 {} 秒！", elapsed / 1000);
                LOGGER.error("========================================");

                // 收集主线程堆栈
                dumpMainThreadStack();

                // 收集所有线程信息
                dumpAllThreads();

                // 收集系统状态
                dumpSystemInfo();

                LOGGER.error("========================================");
            }
        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void arclight$updateTickTime(CallbackInfo ci) {
        // 每次 tick 更新时间戳
        lastTickTime.set(System.currentTimeMillis());
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void arclight$stopMonitoring(CallbackInfo ci) {
        isMonitoring = false;
        if (monitorService != null) {
            monitorService.shutdownNow();
        }
    }

    private void dumpMainThreadStack() {
        LOGGER.error("=== 主线程堆栈追踪 ===");

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo threadInfo = threadMXBean.getThreadInfo(serverThread.getId(), Integer.MAX_VALUE);

        if (threadInfo != null) {
            LOGGER.error("线程: {} (ID: {})", threadInfo.getThreadName(), threadInfo.getThreadId());
            LOGGER.error("状态: {}", threadInfo.getThreadState());

            // 如果线程在等待锁
            if (threadInfo.getLockInfo() != null) {
                LOGGER.error("等待锁: {} (拥有者: {})",
                    threadInfo.getLockInfo(),
                    threadInfo.getLockOwnerName());
            }

            // 打印堆栈
            StackTraceElement[] stackTrace = threadInfo.getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 50); i++) { // 限制50行
                StackTraceElement element = stackTrace[i];
                LOGGER.error("  at {}.{}({}:{})",
                    element.getClassName(),
                    element.getMethodName(),
                    element.getFileName(),
                    element.getLineNumber());

                // 高亮关键位置
                String className = element.getClassName();
                if (className.contains("arclight") ||
                    className.contains("bukkit") ||
                    className.contains("spigot") ||
                    className.contains("Connection") ||
                    className.contains("Netty") ||
                    className.contains("Channel")) {
                    LOGGER.error("  ^^^ 关键位置！");
                }
            }
        }
    }

    private void dumpAllThreads() {
        LOGGER.error("=== 其他可疑线程 ===");

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();

        // 检查死锁
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            LOGGER.error("!!! 检测到死锁 !!!");
            ThreadInfo[] deadlockInfos = threadMXBean.getThreadInfo(deadlockedThreads);
            for (ThreadInfo info : deadlockInfos) {
                if (info != null) {
                    LOGGER.error("死锁线程: {} - 等待: {}",
                        info.getThreadName(),
                        info.getLockInfo());
                }
            }
        }

        // 找出 CPU 占用高的线程
        long[] allThreadIds = threadMXBean.getAllThreadIds();
        for (long id : allThreadIds) {
            ThreadInfo info = threadMXBean.getThreadInfo(id);
            if (info != null && info.getThreadState() == Thread.State.RUNNABLE) {
                String name = info.getThreadName();
                // 只显示相关线程
                if (name.contains("Netty") || name.contains("Worker") ||
                    name.contains("Pool") || name.contains("Async")) {
                    LOGGER.error("活跃线程: {} - 状态: {}", name, info.getThreadState());
                }
            }
        }
    }

    private void dumpSystemInfo() {
        LOGGER.error("=== 系统信息 ===");

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        LOGGER.error("内存: 已用 {} MB / 总计 {} MB / 最大 {} MB",
            usedMemory, totalMemory, maxMemory);

        // 如果内存使用率超过 90%
        if (usedMemory > maxMemory * 0.9) {
            LOGGER.error("!!! 内存使用率过高，可能导致 GC 压力 !!!");
        }

        LOGGER.error("处理器核心: {}", runtime.availableProcessors());
        LOGGER.error("当前时间: {}", new java.util.Date());
    }
}