package com.testkit.trace;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 线程切换工具
 *
 * @Author shaopeng
 * @Date 2021/11/13
 */
public class TraceThreadContextTransferTool {


    /**
     *
     * 全局切换器包装Runnable
     *
     * @param runnable 非空
     * @return
     */
    public static TransitiveRunnableWrapper wrap(Runnable runnable) {
        if (runnable == null) {
            return null;
        }
        if (runnable instanceof TransitiveRunnableWrapper) {
            return (TransitiveRunnableWrapper) runnable;
        }
        return new TransitiveRunnableWrapper(runnable);
    }

    /**
     * 全局切换器包装Callable
     *
     * @param callable 非空
     * @param <R>
     * @return
     */
    public static <R> TransitiveCallableWrapper<R> wrap(Callable<R> callable) {
        if (callable == null) {
            return null;
        }
        if (callable instanceof TransitiveCallableWrapper) {
            return (TransitiveCallableWrapper<R>) callable;
        }
        return new TransitiveCallableWrapper<>(callable);
    }

    /**
     * 全局切换器包装Supplier
     *
     * @param supplier 非空
     * @param <R>
     * @return
     */
    public static <R> TransitiveSupplierWrapper<R> wrap(Supplier<R> supplier) {
        if (supplier == null) {
            return null;
        }
        if (supplier instanceof TransitiveSupplierWrapper) {
            return (TransitiveSupplierWrapper<R>) supplier;
        }
        return new TransitiveSupplierWrapper<>(supplier);
    }


    /**
     * 批量包装Runnable
     *
     * @param runnables
     * @return
     */
    public static List<? extends TransitiveRunnableWrapper> wrapRuns(Collection<? extends Runnable> runnables) {
        if (runnables == null) {
            return null;
        }
        return runnables.stream().map(TraceThreadContextTransferTool::wrap).collect(Collectors.toList());
    }

    /**
     * 批量包装Callable
     *
     * @param callables
     * @param <R>
     * @return
     */
    public static <R> List<? extends TransitiveCallableWrapper<R>> wrapCalls(Collection<? extends Callable<R>> callables) {
        if (callables == null) {
            return null;
        }
        return callables.stream().map(TraceThreadContextTransferTool::wrap).collect(Collectors.toList());
    }

    /**
     * 批量包装Supplier
     *
     * @param suppliers
     * @param <R>
     * @return
     */
    public static <R> List<? extends TransitiveSupplierWrapper<R>> wrapSuppliers(Collection<? extends Supplier<R>> suppliers) {
        if (suppliers == null) {
            return null;
        }
        return suppliers.stream().map(TraceThreadContextTransferTool::wrap).collect(Collectors.toList());
    }


    /**
     * 包装超类
     */
    private static abstract class AbsTransitiveWrapper<T> {
        /**
         * 被代理
         */
        private final T delegate;
        /**
         * 主线程
         * 当线程池的拒绝策略是CallerRunsPolicy时
         * 线程池会用主线程直接运行任务,不存在线程切换
         */
        private final Thread mainThread = Thread.currentThread();
        /**
         * 当前代理需要操作的数据
         */
        private final TraceInfo traceInfo;

        public AbsTransitiveWrapper(T delegate) {
            if (delegate == null) {
                throw new IllegalArgumentException("delegate can not be null");
            }
            this.delegate = delegate;
            this.traceInfo = TraceInfo.getCurrent();
        }

        /**
         * 将存储的对象放到当前线程中(在目标线程中执行)
         */
        protected void write() {
            TraceInfo.set(traceInfo);
        }

        /**
         * 清理被污染的数据
         */
        protected void clean() {
            TraceInfo.set(null);
        }

        public TraceInfo getTraceInfo() {
            return traceInfo;
        }

        public Thread getMainThread() {
            return mainThread;
        }

        public T getDelegate() {
            return delegate;
        }
    }

    /**
     * runnable包装
     *
     * @Author shaopeng
     * @Date 2021/11/14
     */
    private static class TransitiveRunnableWrapper extends AbsTransitiveWrapper<Runnable> implements Runnable {

        private TransitiveRunnableWrapper(Runnable runnable) {
            super(runnable);
        }

        @Override
        public void run() {
            Runnable delegate = getDelegate();
            if (Thread.currentThread() == getMainThread() || getTraceInfo() == null) {
                delegate.run();
            } else {
                try {
                    write();
                    delegate.run();
                } finally {
                    clean();
                }
            }
        }

    }


    /**
     * Caller包装
     *
     * @Author shaopeng
     * @Date 2021/11/14
     */
    private static class TransitiveCallableWrapper<R> extends AbsTransitiveWrapper<Callable<R>> implements Callable<R> {

        private TransitiveCallableWrapper(Callable<R> callable) {
            super(callable);
        }

        @Override
        public R call() throws Exception {
            Callable<R> delegate = getDelegate();
            if (Thread.currentThread() == getMainThread() || getTraceInfo() == null) {
                return delegate.call();
            } else {
                try {
                    write();
                    return delegate.call();
                } finally {
                    clean();
                }
            }
        }

    }


    /**
     * Supplier包装
     *
     * @Author shaopeng
     * @Date 2021/11/14
     */
    private static class TransitiveSupplierWrapper<R> extends AbsTransitiveWrapper<Supplier<R>> implements Supplier<R> {

        private TransitiveSupplierWrapper(Supplier<R> supplier) {
            super(supplier);
        }

        @Override
        public R get() {
            Supplier<R> delegate = getDelegate();
            if (Thread.currentThread() == getMainThread() || getTraceInfo() == null) {
                return delegate.get();
            } else {
                try {
                    write();
                    return delegate.get();
                } finally {
                    clean();
                }
            }
        }
    }
}
