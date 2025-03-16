package com.testkit.trace;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TraceInfo {

    private static final ThreadLocal<TraceInfo> traceThreadLocal = new ThreadLocal<>();

    private final TraceInfo parent;
    private TraceInfo root;
    private AtomicInteger sequence;
    private String reqid;
    private String linkid;
    private final long beginTime;
    private final String group;
    private final String biz;
    private final String action;
    private int begin;
    private int end = -1;
    private int cost;
    private List<Digest> digests;
    private Map<String, TraceInfo> segments = new ConcurrentHashMap<>();
    private String status;
    private Throwable error;
    private String performance;

    public static TraceInfo getCurrent() {
        try {
            return traceThreadLocal.get();
        } catch (Throwable e) {
            return null;
        }
    }

    public static void set(TraceInfo traceInfo) {
        try {
            if (traceInfo == null) {
                traceThreadLocal.remove();
            } else {
                traceThreadLocal.set(traceInfo);
            }
        } catch (Throwable e) {
        }
    }

    public static TraceInfo buildRoot(String reqid, String group, String biz, String action) {
        TraceInfo traceInfo = new TraceInfo(null, group, biz, action);
        traceInfo.reqid = reqid + "@0";
        return traceInfo;
    }

    public TraceInfo(TraceInfo parent, String group, String biz, String action) {
        this.beginTime = System.currentTimeMillis();
        this.parent = parent;
        this.group = group == null ? "" : group;
        this.biz = biz != null ? biz : "";
        this.action = action != null ? action : "";
        this.end = -1;
        this.performance = "TIMEOUT";

        try {
            if (this.parent != null) {
                this.root = this.parent.root;
                this.sequence = this.parent.sequence;
                this.reqid = this.parent.reqid;
                this.linkid = this.parent.linkid + "." + this.sequence.incrementAndGet();
                this.begin = (int) (beginTime - this.root.beginTime);
                this.parent.segments.put(this.linkid, this);
            } else {
                this.root = this;
                this.linkid = "0";
                this.sequence = new AtomicInteger(0);
                this.begin = 0;
                this.reqid = generateRandomString(16) + "@0";
            }
        } catch (Throwable e) {
            System.err.println("Testkit trace instance error");
            e.printStackTrace();
        }
    }

    public int fetchCurrentDepth() {
        int depth = 1;
        TraceInfo current = this;
        //死循环判断
        while (current.parent != null && depth < 100) {
            // 如果父节点的 biz 与当前节点的 biz 相同，继续向上查找
            if (Objects.equals(current.parent.biz, current.biz)) {
                current = current.parent;
                depth++;
            } else {
                break; // 终止条件
            }
        }
        return depth;
    }

    public TraceInfo stepIn() {
        try {
            traceThreadLocal.set(this);
        } catch (Throwable e) {
            System.err.println("Testkit trace stepIn error");
            e.printStackTrace();
        }
        return this;
    }

    public TraceInfo stepOut(Object ret, Throwable error, String status, List<Digest> digests) {
        if (this.end != -1) {
            return this;
        }

        try {
            this.cost = (int) (System.currentTimeMillis() - this.beginTime);
            this.end = this.begin + this.cost;
            this.performance = "NORMAL";

            if (error != null) {
                this.status = status != null ? status : "error_" + error.getClass().getSimpleName();
                this.error = error;
            } else if (status == null) {
                if (ret == null) {
                    this.status = "NULL";
                } else if (ret instanceof Collection) {
                    int size = ((Collection<?>) ret).size();
                    if (size == 0) {
                        this.status = "collect_empty";
                    } else {
                        this.status = "collect_" + size;
                    }
                } else if (ret instanceof Map) {
                    int size = ((Map<?, ?>) ret).size();
                    if (size == 0) {
                        this.status = "map_empty";
                    } else {
                        this.status = "map_" + size;
                    }
                } else if (ret.getClass().isEnum()) {
                    this.status = String.valueOf(ret);
                } else {
                    this.status = "OK";
                }
            } else {
                this.status = status;
            }
            this.digests = digests;
            set(this.parent);
        } catch (Throwable e) {
            System.err.println("Testkit trace stepOut error");
            e.printStackTrace();
        }
        return this;
    }

    public TraceInfo stepOut(Object ret, Throwable error) {
        return stepOut(ret, error, null, null);
    }

    /**
     * 性能表字符串
     * 可以理解为简化版链路信息
     * 输出的信息被使用图化html图化展示
     *
     * @return
     * @see horse_up_down_class.html
     */
    public String toProfilerString() {
        try {
            if (digests == null) {
                digests = new ArrayList<>();
            }
            digests.add(new Digest("_req_id", reqid));
            digests.add(new Digest("_time", formatTimestampManual(beginTime)));
            return String.join("$M$", profiler());
        } catch (Throwable e) {
            System.err.println("Testkit trace toProfilerString error");
            e.printStackTrace();
            return "";
        }
    }


    /**
     * 性能表
     *
     * @return
     */
    private List<String> profiler() {
        int i = 0;
        do {
            try {
                //在异步场景,有节点又加入了linkInfo时可能会报错
                //所以这里最多重试3次
                List<String> links = new ArrayList<>();
                appendProfiler(links);
                return links;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        } while (++i <= 3);
        List<String> links = new ArrayList<>();
        links.add("serializeProfilerError");
        return links;
    }

    private void appendProfiler(List<String> links) {
        //id group#biz#action(Y,status,performance,begin,cost)da=v1|db=v2
        //未结束
        String profiler;
        if (cost < 0) {
            profiler = linkid + " " + group + "#" + biz + "#" + action + "(-,-," + performance + "," + begin + ",-)";
        } else {
            profiler = linkid + " " + group + "#" + biz + "#" + action + "(" + (error == null ? "Y" : "N") + "," + (status == null ? "-" : status) + "," + performance + "," + begin + "," + cost + ")";
        }
        if (digests != null) {
            profiler += digests.stream().map(new Function<Digest, String>() {
                @Override
                public String apply(Digest digest) {
                    return digest.toString();
                }
            }).collect(Collectors.joining(";"));
        }
        links.add(profiler);
        if (segments.isEmpty()) {
            return;
        }
        for (TraceInfo value : this.segments.values()) {
//            if (value.fetchCurrentDepth() > value.getSingleClsDepth()) {
//                continue;
//            }
            value.appendProfiler(links);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TRACE_MONITOR - ")
                .append(linkid).append(" ")
                .append("[").append(reqid).append(",0,")
                .append(linkid).append("]").append(group).append(";").append(biz).append(";").append(action).append(",")
                .append((error == null ? "Y" : "N")).append(",")
                .append((status == null ? "-" : status)).append(",")
                .append(cost).append(",")
                .append(performance).append(";")
        ;

        return sb.toString();
    }

    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true)
            .optionalEnd()
            .toFormatter()
            .withZone(ZoneId.systemDefault());

    private static String formatTimestampManual(long timestamp) {
        // Constant values
        Instant instant = Instant.ofEpochMilli(timestamp);
        return FORMATTER.format(instant);
    }


    public String getReqid() {
        return reqid;
    }


    private static final char[] CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int POOL_SIZE = CHAR_POOL.length;


    private static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHAR_POOL[random.nextInt(POOL_SIZE)]);
        }
        return sb.toString();
    }

    public static class Digest implements Serializable {

        private static final long serialVersionUID = -4221416475600320143L;

        /**
         * key
         */
        private String k;

        /**
         * 值
         */
        private String v;

        public Digest() {
        }

        public Digest(String k, String v) {
            this.k = k;
            this.v = v;
        }

        public String getK() {
            return k;
        }

        public void setK(String k) {
            this.k = k;
        }

        public String getV() {
            return v;
        }

        public void setV(String v) {
            this.v = v;
        }

        @Override
        public String toString() {
            return k + "|" + (v == null ? "-" : v);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Digest digest = (Digest) o;
            return Objects.equals(k, digest.k) &&
                    Objects.equals(v, digest.v);
        }

        @Override
        public int hashCode() {
            return Objects.hash(k, v);
        }
    }
}