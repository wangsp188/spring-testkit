package com.testkit.side_server;

import java.util.Map;
import java.util.concurrent.*;

class TaskManager {
    // 存储任务的结果  
    private static final Map<String, Future<Ret>> taskMap = new ConcurrentHashMap<>();
    // 线程池用于执行任务  
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    // 启动任务并返回请求ID  
    public static String startTask(String reqId,Callable<Ret> task) {
        // 生成唯一的请求ID  
        // 将任务包装成FutureTask并提交到 executor
        Future<Ret> future = executor.submit(task);
        // 存储请求ID和任务映射  
        taskMap.put(reqId, future);
        return reqId;  // 返回生成的请求ID
    }

    // 根据请求ID获取任务结果  
    public static Ret getResult(String reqId, int timeout) {
        // 获取对应的任务  
        Future<Ret> future = taskMap.get(reqId);
        if (future == null) {
            return Ret.fail("not found task");
        }

        try {
            // 等待任务完成并获取结果  
            return future.get(timeout, TimeUnit.SECONDS);  // 任务还没有完成会阻塞直到任务完成
        } catch (InterruptedException e) {
            e.printStackTrace();
            return Ret.fail("thread interrupted");
        }catch (TimeoutException e){
            e.printStackTrace();
            return Ret.fail("time out");
        }catch (ExecutionException e){
            return Ret.fail(TestkitSideServer.getNoneTestkitStackTrace(e.getCause()));
        }
    }
//    / 停止一个任务
    public static boolean stopTask(String reqId) {
        // 获取对应的任务
        Future<Ret> future = taskMap.get(reqId);
        if (future == null) {
            return false;  // 没有找到这个任务
        }

        // 尝试取消任务
        boolean cancelled = future.cancel(true);
        if (cancelled) {
            taskMap.remove(reqId);  // 如果成功取消，则移除任务
        } else if (future.isDone() || future.isCancelled()) {
            taskMap.remove(reqId);  // 确保任务结束或已取消后移除
        }
        return cancelled;
    }


    // 关闭executor  
    public static void shutdown() {
        executor.shutdown();
    }


    private static final char[] CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int POOL_SIZE = CHAR_POOL.length;


    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHAR_POOL[random.nextInt(POOL_SIZE)]);
        }
        return sb.toString();
    }
}