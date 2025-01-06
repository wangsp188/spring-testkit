package com.fling;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ReqStorageHelper {

    private static final String STORE_DIR = ".spring-fling/store";


    public static List<GroupItems> getAppReqs(Project project, String app) {
        Map<String, List<GroupItems>> stringListMap = loadWebReqs(project);
        if (MapUtils.isEmpty(stringListMap)) {
            return null;
        }
        return stringListMap.get(app);
    }

    public static void delAppReq(Project project, String app, String group, ItemType type, String name, String title) {
        Map<String, List<GroupItems>> stringListMap = loadWebReqs(project);
        if (stringListMap == null) {
            return;
        }
        List<GroupItems> groupList = stringListMap.get(app);
        if (groupList == null) {
            return;
        }
        Optional<GroupItems> groupOptional = groupList.stream().filter(new Predicate<GroupItems>() {
            @Override
            public boolean test(GroupItems groupItems) {
                return Objects.equals(group, groupItems.getGroup());
            }
        }).findFirst();
        if (!groupOptional.isPresent()) {
            return;
        }
        GroupItems groupItems = groupOptional.get();
        Optional<Item> itemOptional = Optional.ofNullable(groupItems.getItems())
                .orElse(new ArrayList<>())
                .stream()
                .filter(new Predicate<Item>() {
                    @Override
                    public boolean test(Item item) {
                        return Objects.equals(item.getName(), name) && Objects.equals(item.getType(), type);
                    }
                }).findFirst();
        if (!itemOptional.isPresent()) {
            return;
        }

        Item item = itemOptional.get();
        Optional<SavedReq> reqOptional = Optional.ofNullable(item.getReqs())
                .orElse(new ArrayList<>())
                .stream()
                .filter(new Predicate<SavedReq>() {
                    @Override
                    public boolean test(SavedReq savedReq) {
                        return Objects.equals(title, savedReq.getTitle());
                    }
                }).findFirst();

        if (!reqOptional.isPresent()) {
            return;
        }
        List<SavedReq> reqs = item.getReqs();
        reqs.removeIf(new Predicate<SavedReq>() {
            @Override
            public boolean test(SavedReq savedReq) {
                return Objects.equals(title, savedReq.getTitle());
            }
        });
        if (CollectionUtils.isEmpty(reqs)) {
            List<Item> items = groupItems.getItems();
            items.removeIf(new Predicate<Item>() {
                @Override
                public boolean test(Item item) {
                    return Objects.equals(type, item.getType()) && Objects.equals(group, item.getGroup()) && Objects.equals(name, item.getName());
                }
            });
            if (CollectionUtils.isEmpty(items)) {
                groupList.removeIf(new Predicate<GroupItems>() {
                    @Override
                    public boolean test(GroupItems groupItems) {
                        return Objects.equals(group, groupItems.getGroup());
                    }
                });

                if (CollectionUtils.isEmpty(groupList)) {
                    stringListMap.remove(app);
                }
            }
        }
        saveWebReqs(project, stringListMap);
    }

    public static void saveAppReq(Project project, String app, String group, ReqStorageHelper.ItemType itemType, String name, Object meta, String oldTitle, ReqStorageHelper.SavedReq req) {
        Map<String, List<GroupItems>> stringListMap = loadWebReqs(project);
        if (stringListMap == null) {
            stringListMap = new HashMap<>();
        }
        List<GroupItems> itemsList = stringListMap.get(app);
        if (itemsList == null) {
            itemsList = new ArrayList<>();
            stringListMap.put(app, itemsList);
        }
        Optional<GroupItems> groupOptional = itemsList.stream().filter(new Predicate<GroupItems>() {
            @Override
            public boolean test(GroupItems groupItems) {
                return Objects.equals(group, groupItems.getGroup());
            }
        }).findFirst();

        GroupItems groupItems = null;
        if (groupOptional.isPresent()) {
            groupItems = groupOptional.get();
        } else {
            groupItems = new GroupItems();
            groupItems.setGroup(group);
            itemsList.add(groupItems);
        }
        Optional<Item> itemOptional = Optional.ofNullable(groupItems.getItems())
                .orElse(new ArrayList<>())
                .stream()
                .filter(new Predicate<Item>() {
                    @Override
                    public boolean test(Item item) {
                        return Objects.equals(item.getName(), name) && Objects.equals(item.getType(), itemType);
                    }
                }).findFirst();

        Item item = null;
        if (itemOptional.isPresent()) {
            item = itemOptional.get();
            item.setName(name);
            item.setType(itemType);
            item.setMeta(JSON.parseObject(JSON.toJSONString(meta, SerializerFeature.WriteMapNullValue)));
            item.setGroup(group);
        } else {
            item = new Item();
            item.setName(name);
            item.setType(itemType);
            item.setGroup(group);
            item.setMeta(JSON.parseObject(JSON.toJSONString(meta, SerializerFeature.WriteMapNullValue)));
            List<Item> items = groupItems.getItems();
            if (items == null) {
                items = new ArrayList<>();
                items.add(item);
                groupItems.setItems(items);
            } else {
                items.add(item);
            }
        }

        Optional<SavedReq> reqOptional = Optional.ofNullable(item.getReqs())
                .orElse(new ArrayList<>())
                .stream()
                .filter(new Predicate<SavedReq>() {
                    @Override
                    public boolean test(SavedReq savedReq) {
                        return Objects.equals(oldTitle, savedReq.getTitle());
                    }
                }).findFirst();

        if (reqOptional.isPresent()) {
            SavedReq savedReq = reqOptional.get();
            savedReq.setTitle(req.getTitle());
            savedReq.setArgs(req.getArgs());
        } else {
            List<SavedReq> reqs = item.getReqs();
            if (reqs == null) {
                reqs = new ArrayList<>();
                item.setReqs(reqs);
            }
            reqs.add(req);
        }
        saveWebReqs(project, stringListMap);
    }

    public static void saveAppGroupItems(Project project, String app, GroupItems importGroup) {
        if (importGroup == null) {
            return;
        }
        if (StringUtils.isBlank(importGroup.getGroup())) {
            throw new IllegalArgumentException("group cannot be empty");
        }
        if (CollectionUtils.isEmpty(importGroup.getItems())) {
            return;
        }
        List<Item> items = importGroup.getItems().stream().filter(new Predicate<Item>() {
            @Override
            public boolean test(Item item) {
                return CollectionUtils.isNotEmpty(item.getReqs()) && item.getType() != null && item.getGroup() != null && item.getMeta() != null && StringUtils.isNotBlank(item.getName());
            }
        }).collect(Collectors.toList());
        importGroup.setItems(items);

        for (Item item : items) {
            List<SavedReq> list = item.getReqs().stream()
                    .filter(new Predicate<SavedReq>() {
                        @Override
                        public boolean test(SavedReq savedReq) {
                            return StringUtils.isNotBlank(savedReq.getTitle());
                        }
                    }).collect(Collectors.toList());
            item.setReqs(list);
        }

        Map<String, List<GroupItems>> stringListMap = loadWebReqs(project);
        if (stringListMap == null) {
            stringListMap = new HashMap<>();
        }
        List<GroupItems> itemsList = stringListMap.get(app);
        if (itemsList == null || itemsList.isEmpty()) {
            itemsList = new ArrayList<>();
            itemsList.add(importGroup);
            stringListMap.put(app, itemsList);
            saveWebReqs(project, stringListMap);
            return;
        }
        Optional<GroupItems> groupOptional = itemsList.stream().filter(new Predicate<GroupItems>() {
            @Override
            public boolean test(GroupItems groups) {
                return Objects.equals(importGroup.getGroup(), groups.getGroup());
            }
        }).findFirst();

        if (groupOptional.isEmpty() || CollectionUtils.isEmpty(groupOptional.get().getItems())) {
            itemsList.add(importGroup);
            saveWebReqs(project, stringListMap);
            return;
        }
        List<Item> existItems = groupOptional.get().getItems();

        for (Item item : items) {
            item.setGroup(importGroup.getGroup());
            Optional<Item> pointItemOptional = existItems.stream().filter(new Predicate<Item>() {
                @Override
                public boolean test(Item i2) {
                    return Objects.equals(i2.getName(), item.getName()) && Objects.equals(i2.getType(), item.getType());
                }
            }).findFirst();
            if (pointItemOptional.isEmpty()) {
                existItems.add(item);
                continue;
            }
            //reqs
            Item pointItem = pointItemOptional.get();
            pointItem.setMeta(item.getMeta());
            List<SavedReq> pointReqs = pointItem.getReqs();
            List<SavedReq> reqs = item.getReqs();
            if (CollectionUtils.isEmpty(pointReqs)) {
                pointItem.setReqs(reqs);
                continue;
            }

            //migration reqs
            for (SavedReq req : reqs) {
                Optional<SavedReq> pointReqOptional = pointReqs.stream().filter(new Predicate<SavedReq>() {
                    @Override
                    public boolean test(SavedReq i3) {
                        return Objects.equals(i3.getTitle(), req.getTitle());
                    }
                }).findFirst();
                if (pointReqOptional.isEmpty()) {
                    pointReqs.add(req);
                } else {
                    pointReqOptional.get().setArgs(req.getArgs());
                }
            }
        }
        saveWebReqs(project, stringListMap);
    }


    private static Map<String, List<GroupItems>> loadWebReqs(Project project) {
        File configFile = getStoreFile(project);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                return JSON.parseObject(content, new TypeReference<Map<String, List<GroupItems>>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private synchronized static void saveWebReqs(Project project, Map<String, List<GroupItems>> groupItems) {
        File configFile = getStoreFile(project);
        try (FileWriter writer = new FileWriter(configFile)) {
            JSON.writeJSONString(writer, groupItems, SerializerFeature.WriteMapNullValue);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static File getStoreFile(Project project) {
        String projectPath = System.getProperty("user.home");
        if (StringUtils.isEmpty(projectPath)) {
            throw new IllegalArgumentException("Project base path is not set.");
        }
        Path configDirPath = Paths.get(projectPath, STORE_DIR);
        File configDir = configDirPath.toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        return new File(configDir, project.getName() + ".json");
    }


    public static class GroupItems {
        private String group;
        private List<Item> items = new ArrayList<>();

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }
    }

    public static enum ItemType {
        call_method,
        flexible_test
    }

    public static enum SubItemType {
        controller
    }

    public static class Item {

        /**
         * call-method
         * flexible-test
         */
        private ItemType type;
        private String group;
        private String name;

        private JSONObject meta;

        public <T> T metaObj(Class<T> type) {
            if (meta == null) {
                return null;
            }
            return JSON.parseObject(meta.toJSONString(), type);
        }

        public SubItemType fetchSubType() {
            if (type != ItemType.call_method) {
                return null;
            }
            return metaObj(CallMethodMeta.class).getSubType();
        }

        public JSONObject getMeta() {
            return meta;
        }

        public void setMeta(JSONObject meta) {
            this.meta = meta;
        }

        private List<SavedReq> reqs = new ArrayList<>();

        public ItemType getType() {
            return type;
        }

        public void setType(ItemType type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<SavedReq> getReqs() {
            return reqs;
        }

        public void setReqs(List<SavedReq> reqs) {
            this.reqs = reqs;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }


    }


    public static class SavedReq {

        private String title;

        private JSONObject args;

        public JSONObject getArgs() {
            return args;
        }

        public void setArgs(JSONObject args) {
            this.args = args;
        }


        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }


        public String toString() {
            return title;
        }
    }


    public static class CallMethodMeta {

        private String typeClass;
        private String beanName;
        private String methodName;
        private boolean useScript;
        private String argTypes;
        private List<String> argNames;

        private SubItemType subType;
        private ControllerCommandMeta controllerMeta;


        public String getTypeClass() {
            return typeClass;
        }

        public void setTypeClass(String typeClass) {
            this.typeClass = typeClass;
        }

        public String getBeanName() {
            return beanName;
        }

        public void setBeanName(String beanName) {
            this.beanName = beanName;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getArgTypes() {
            return argTypes;
        }

        public void setArgTypes(String argTypes) {
            this.argTypes = argTypes;
        }

        public boolean isUseScript() {
            return useScript;
        }

        public void setUseScript(boolean useScript) {
            this.useScript = useScript;
        }


        public List<String> getArgNames() {
            return argNames;
        }

        public void setArgNames(List<String> argNames) {
            this.argNames = argNames;
        }

        public SubItemType getSubType() {
            return subType;
        }

        public void setSubType(SubItemType subType) {
            this.subType = subType;
        }

        public ControllerCommandMeta getControllerMeta() {
            return controllerMeta;
        }

        public void setControllerMeta(ControllerCommandMeta controllerMeta) {
            this.controllerMeta = controllerMeta;
        }
    }

    public static class ControllerCommandMeta {
        private String httpMethod;
        private String path;
        private String jsonBodyKey;
        private Map<String, String> aliasmap;
        private List<String> pathKeys;


        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getJsonBodyKey() {
            return jsonBodyKey;
        }

        public void setJsonBodyKey(String jsonBodyKey) {
            this.jsonBodyKey = jsonBodyKey;
        }

        public Map<String, String> getAliasmap() {
            return aliasmap;
        }

        public void setAliasmap(Map<String, String> aliasmap) {
            this.aliasmap = aliasmap;
        }

        public List<String> getPathKeys() {
            return pathKeys;
        }

        public void setPathKeys(List<String> pathKeys) {
            this.pathKeys = pathKeys;
        }
    }


    public static class FlexibleTestMeta {

        private boolean useScript;
        private String code;
        private String methodName;
        private String argTypes;
        private List<String> argNames;


        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public boolean isUseScript() {
            return useScript;
        }

        public void setUseScript(boolean useScript) {
            this.useScript = useScript;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getArgTypes() {
            return argTypes;
        }

        public void setArgTypes(String argTypes) {
            this.argTypes = argTypes;
        }

        public List<String> getArgNames() {
            return argNames;
        }

        public void setArgNames(List<String> argNames) {
            this.argNames = argNames;
        }
    }
}
