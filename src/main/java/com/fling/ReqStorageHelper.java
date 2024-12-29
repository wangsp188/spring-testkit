package com.fling;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.intellij.openapi.project.Project;
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
        List<GroupItems> itemsList = stringListMap.get(app);
        if (itemsList == null) {
            return;
        }
        Optional<GroupItems> groupOptional = itemsList.stream().filter(new Predicate<GroupItems>() {
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
        item.getReqs().remove(reqOptional.get());
        saveWebReqs(project, stringListMap);
    }

    public static void saveAppReq(Project project, String app, String group, ReqStorageHelper.ItemType itemType, String name, ReqStorageHelper.SavedReq req) {
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
            item.setGroup(group);
        } else {
            item = new Item();
            item.setName(name);
            item.setType(itemType);
            item.setGroup(group);
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
                        return Objects.equals(req.getTitle(), savedReq.getTitle());
                    }
                }).findFirst();

        if (reqOptional.isPresent()) {
            SavedReq savedReq = reqOptional.get();
            savedReq.setTitle(req.getTitle());
            savedReq.setMeta(req.getMeta());
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


    public static Map<String, List<GroupItems>> loadWebReqs(Project project) {
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
            JSON.writeJSONString(writer, groupItems);
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
        controller_command,
        flexible_test
    }

    public static class Item {

        /**
         * call-method
         * controller-command
         * flexible-test
         */
        private ItemType type;
        private String group;
        private String name;

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
        private JSONObject meta;


        public <T> T metaObj(Class<T> type) {
            if (meta == null) {
                return null;
            }
            return JSON.parseObject(meta.toJSONString(), type);
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public JSONObject getMeta() {
            return meta;
        }

        public void setMeta(JSONObject meta) {
            this.meta = meta;
        }


        public String toString() {
            return title;
        }
    }


    public static class CallMethodMeta {

        private String typeClass;
        private String beanName;
        private String methodName;
        private String argTypes;
        private boolean original;
        private String method;

        private JSONArray args;

        public JSONArray getArgs() {
            return args;
        }

        public void setArgs(JSONArray args) {
            this.args = args;
        }

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

        public boolean isOriginal() {
            return original;
        }

        public void setOriginal(boolean original) {
            this.original = original;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }
}
