package com.testkit.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TraceParser {


    private static Pattern timestampPattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}");


    public static List<Map<String, String>> parseTestkitLogs(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
//FLING_LINK - 0 fling#ToolApplication#call-method(N,error_InvocationTargetException,NORMAL,0,3)_req_id|Hc2HI8L4x7lR9Vpb@0;_time|2024-12-01 19:40:44.637$M$0.1 #SpringCacheService#hello(N,error_IllegalArgumentException,NORMAL,3,0)

        if (!content.contains("TRACE_PROFILER -")) {
            return new ArrayList<>();
        }
        String profiler = content.substring(content.indexOf("TRACE_PROFILER -") + "TRACE_PROFILER -".length()).trim();
        String reqid = null;
        String cost = null;
        int i = profiler.indexOf("_req_id|");
        if (i > 0) {
            int y = profiler.indexOf(";", i);
            if (y > 0) {
                reqid = profiler.substring(i + "_req_id|".length(), y);
            }
            String coststr = profiler.substring(0, i - 1);
            cost = coststr.substring(coststr.lastIndexOf(",") + 1);
        }
        ArrayList<Map<String, String>> objects = new ArrayList<>();
        HashMap<String, String> e = new HashMap<>();
        e.put("link", profiler);
        e.put("req_id", reqid);
        e.put("cost", cost);
        objects.add(e);
        return objects;
    }


    public static List<Map<String, String>> parseLinkLos(String content) {
        List<LogEntry> logEntries = parseLogs(content);
        List<Map<String, String>> listLogs = logEntries.stream()
                .filter(logEntry -> logEntry.getTraceid() != null && logEntry.getRpcid() != null && "S_LINK".equals(logEntry.getLogger()))
                .map(new Function<LogEntry, Map<String, String>>() {
                    @Override
                    public Map<String, String> apply(LogEntry logEntry) {
                        String message = logEntry.getMessage();
                        String cost = null;
                        String reqId = null;
                        String link = null;

                        if (message.contains("[P]")) {
                            String profilerStr = message.split("\\[P\\]")[1].trim();
                            link = profilerStr;
                            String[] segments = profilerStr.split(",");
                            if (segments.length > 5) {
                                cost = segments[4].split("\\)")[0].trim();
                            }
                            reqId = profilerStr.split("_req_id\\|")[1].split(";")[0].trim();
                        } else if (message.contains("[L]")) {
                            try {
                                JSONObject jsonObject = JSON.parseObject(message.split("\\[L\\]")[1].trim());
                                cost = jsonObject.getString("cost");
                                if (jsonObject.get("digests") != null) {
                                    JSONArray digests = jsonObject.getJSONArray("digests");
                                    for (Object digest : digests) {
                                        JSONObject digestObj = JSON.parseObject(JSON.toJSONString(digest));
                                        if (digestObj == null) {
                                            continue;
                                        }
                                        if ("_req_id".equals(digestObj.getString("k"))) {
                                            reqId = digestObj.get("v") == null ? null : digestObj.getString("v");
                                            break;
                                        }
                                    }
                                }
                                link = jsonObject.toJSONString();
                            } catch (Throwable e) {
                                // Handle JSON parsing exception
                            }
                        }

                        if (link != null) {
                            Map<String, String> logMap = new HashMap<>();
                            logMap.put("message", message);
                            logMap.put("cost", cost);
                            logMap.put("req_id", reqId);
                            logMap.put("link", link);
                            return logMap;
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> alreadyReqIds = logEntries.stream()
                .filter(logEntry -> logEntry.getTraceid() != null && logEntry.getRpcid() != null && "S_LINK".equals(logEntry.getLogger())).map(new Function<LogEntry, String>() {
                    @Override
                    public String apply(LogEntry logEntry) {
                        return logEntry.getTraceid() + "@" + logEntry.getRpcid();
                    }
                }).collect(Collectors.toSet());

        HashMap<String, List<String>> objectObjectHashMap = new HashMap<>();
        Set<String> filterReqIds = new HashSet<>();
        for (LogEntry logEntry : logEntries) {
            if (!"S_DIGEST".equals(logEntry.getLogger()) || logEntry.getTraceid() == null || logEntry.getRpcid() == null) {
                continue;
            }
            if (alreadyReqIds.contains(logEntry.getTraceid() + "@" + logEntry.getRpcid())) {
                continue;
            }
            int index = logEntry.getMessage().indexOf("[D]");

            if (index < 0) {
                continue;
            }

            String info = logEntry.getMessage().substring(index + 1);
            index = info.indexOf(",");
            if (index < 0) {
                continue;
            }
            info = info.substring(index + 1);

            String linkId = info.substring(0, info.indexOf("]"));

            String group = info.substring(info.indexOf("]") + 1, info.indexOf(";"));

            if (group.startsWith("callback_") && linkId.equals("0")) {
                filterReqIds.add(logEntry.getTraceid() + "@" + logEntry.getRpcid());
                continue;
            }


            info = info.substring(info.indexOf(";") + 1);
            String biz = info.substring(0, info.indexOf(";"));
            String action = info.substring(info.indexOf(";") + 1, info.indexOf(","));
            info = info.substring(info.indexOf(",") + 1);
            String hasError = info.substring(0, info.indexOf(","));
            info = info.substring(info.indexOf(",") + 1);
            String status = info.substring(0, info.indexOf(","));
            info = info.substring(info.indexOf(",") + 1);
            String cost = info.substring(0, info.indexOf(","));
            String performance = info.substring(info.indexOf(",") + 1, info.indexOf(";"));

            index = info.indexOf("[attachment]:");
            String disgests = "";
            if (index > -1) {
                disgests = info.substring(info.indexOf(";") + 1, info.indexOf("[attachment]:")).trim();
                if (disgests.endsWith(";")) {
                    disgests = disgests.substring(0, disgests.length() - 1);
                }
            }

            String proStr = linkId + " " + group + "#" + biz + "#" + action + "(" + hasError + "," + status + "," + performance + ",," + cost + ")";

            objectObjectHashMap.computeIfAbsent(logEntry.getTraceid() + "@" + logEntry.getRpcid(), new Function<String, List<String>>() {
                @Override
                public List<String> apply(String s) {
                    return new ArrayList<>();
                }
            }).add(proStr);
        }

        objectObjectHashMap.keySet().removeIf(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return filterReqIds.contains(s);
            }
        });

        for (Map.Entry<String, List<String>> stringListEntry : objectObjectHashMap.entrySet()) {
            HashMap<String, String> e = new HashMap<>();
            e.put("req_id", stringListEntry.getKey());
            e.put("link", String.join("$M$", stringListEntry.getValue()));
            listLogs.add(e);
        }
        return listLogs;
    }


    public static List<LogEntry> parseLogs(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // step1：先根据日志中的核心关键词进行日志拆分成多行，日志的格式是2023-10-06 12:34:56.789这种时间戳，然后呢回车换行，一定要一起判断才能是一行完整的日志。
        List<String> logLines = splitLogsByTimestamp(content);

        List<LogEntry> logs = new ArrayList<>();
        for (String logLine : logLines) {

            if (logLine == null || logLine.trim().isEmpty()) {
                continue;
            }
            int i = logLine.indexOf("[");
            if (i < 0) {
                continue;
            }
            String time = logLine.substring(0, i);
            String log = logLine.substring(i);
            LogEntry logEntity = parseSingleLog(log);
            if (logEntity == null || logEntity.getLevel() == null || logEntity.getMessage() == null) {
                continue;
            }
            logEntity.timestamp = time;
            logs.add(logEntity);
        }
        return logs;
    }

    private static LogEntry parseSingleLog(String logEntry) {
        // 定义日志的正则模式，使用命名捕获组，只匹配到最后一个 -
        String pattern = "\\[(?<traceid>[^,]+),(?<rpcid>[^\\]]+)\\] \\[(?<thread>[^\\]]+)\\] (?<level>INFO|WARN|ERROR|DEBUG)\\s+(?<logger>[^\\s]+)\\s+-";
        Pattern r = Pattern.compile(pattern);
        Matcher matcher = r.matcher(logEntry);
        if (!matcher.find()) {
            return null;
        }
        return new LogEntry(null, matcher.group("traceid").trim(), matcher.group("rpcid").trim(), matcher.group("thread").trim(), matcher.group("level").trim(), matcher.group("logger").trim(), logEntry.substring(matcher.end()).trim());
    }

    private static List<String> splitLogsByTimestamp(String logsContent) {
        List<String> logLines = new ArrayList<>();
        String[] lines = logsContent.split("\n");
        StringBuilder currentLog = new StringBuilder();
        for (String line : lines) {
            Matcher matcher = timestampPattern.matcher(line);
            if (matcher.find() && currentLog.length() > 0) {
                logLines.add(currentLog.toString());
                currentLog.setLength(0);
            }
            currentLog.append(line).append("\n");
        }

        if (currentLog.length() > 0) {
            logLines.add(currentLog.toString());
        }

        return logLines;
    }


    public static class LogEntry {
        String timestamp;
        String traceid;
        String rpcid;
        String thread;
        String level;
        String logger;
        String message;

        public LogEntry(String timestamp, String traceid, String rpcid, String thread, String level, String logger, String message) {
            this.timestamp = timestamp;
            this.traceid = traceid;
            this.rpcid = rpcid;
            this.thread = thread;
            this.level = level;
            this.logger = logger;
            this.message = message;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getTraceid() {
            return traceid;
        }

        public String getRpcid() {
            return rpcid;
        }

        public String getThread() {
            return thread;
        }

        public String getLevel() {
            return level;
        }

        public String getLogger() {
            return logger;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "LogEntry{" +
                    "timestamp='" + timestamp + '\'' +
                    ", traceid='" + traceid + '\'' +
                    ", rpcid='" + rpcid + '\'' +
                    ", thread='" + thread + '\'' +
                    ", level='" + level + '\'' +
                    ", logger='" + logger + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}
