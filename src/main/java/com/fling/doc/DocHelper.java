package com.fling.doc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fling.FlingHelper;
import com.fling.doc.adapter.*;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DocHelper {

    private static final List<ElementDocAdapter> classSources = Arrays.asList(ClassAnnotationAdapter.getInstance(), ClassTypeAdapter.getInstance());
    private static final List<ElementDocAdapter> methodSources = Arrays.asList(MethodAnnotationAdapter.getInstance(), DeclaredMethodAdapter.getInstance());
    private static final List<ElementDocAdapter> fieldSources = Arrays.asList(ClassTypeAdapter.getInstance(), FieldAnnotationAdapter.getInstance());
    public static final String DOC_TITLE_DESC = "Doc's title , can be null. //This is used when the href attribute is a link";
    public static final String DOC_HREF_DESC = "Doc's link , can not be null. //A link to doc may either point to the local markdown document The path relative to the current directory, such as doc/hello.md, or use the web link directly to https://www.baidu.com";


    private volatile static Map<Project, Map<DocSource, Map<String, Doc>>> projectDocs = new HashMap<>();

    public static boolean hasDoc(PsiElement element) {
        Map<DocSource, Map<String, Doc>> sourceMap = projectDocs.get(element.getProject());
        if (MapUtils.isEmpty(sourceMap)) {
            return false;
        }

        List<ElementDocAdapter> adapters = null;
        if (element instanceof PsiClass) {
            adapters = classSources;
        } else if (element instanceof PsiMethod) {
            adapters = methodSources;
        } else if (element instanceof PsiField) {
            adapters = fieldSources;
        } else {
            return false;
        }
        for (ElementDocAdapter adapter : adapters) {
            Doc doc = adapter.find(element, sourceMap.get(adapter.getDocSource()));
            if (doc != null) {
                return true;
            }
        }

        return false;
    }


    public static List<Doc> findDoc(PsiElement element) {
        Map<DocSource, Map<String, Doc>> sourceMap = projectDocs.get(element.getProject());
        if (MapUtils.isEmpty(sourceMap)) {
            return null;
        }
        List<ElementDocAdapter> adapters = null;
        if (element instanceof PsiClass) {
            adapters = classSources;
        } else if (element instanceof PsiMethod) {
            adapters = methodSources;
        } else if (element instanceof PsiField) {
            adapters = fieldSources;
        } else {
            return null;
        }
        ArrayList<Doc> docs = new ArrayList<>();
        for (ElementDocAdapter adapter : adapters) {
            Doc doc = adapter.find(element, sourceMap.get(adapter.getDocSource()));
            if (doc != null) {
                docs.add(doc);
            }
        }
        return docs;
    }


    public static void refreshDoc(Project project) {
        // 使用 Application.runWriteAction 进行写操作
        Application application = ApplicationManager.getApplication();
        application.runReadAction(new Runnable() {
            @Override
            public void run() {
                Map<DocSource, Map<String, Doc>> sourceMap = projectDocs.computeIfAbsent(project, k -> new HashMap<>());
                for (DocSource docSource : DocSource.values()) {
                    sourceMap.put(docSource, refreshDoc(project, docSource));
                }
            }
        });
    }

    public static void initDocDirectory(Project project) {
        // 获取项目根目录
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            // 如果项目根目录路径为空，返回 null
            return;
        }
        // 使用 Application.runWriteAction 进行写操作
        Application application = ApplicationManager.getApplication();
        application.runWriteAction(new Runnable() {
            @Override
            public void run() {
                // 构建 doc 目录的路径
                String docDirectoryPath = projectPath + "/doc";
                // 获取 doc 目录的 VirtualFile 对象
                VirtualFile docDir = LocalFileSystem.getInstance().findFileByPath(docDirectoryPath);
                if (docDir == null || !docDir.exists()) {
                    // 创建 doc 目录
                    try {
                        new File(docDirectoryPath).mkdirs();
                        docDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(docDirectoryPath);
                        docDir.isDirectory();
                    } catch (Throwable e) {
                        FlingHelper.notify(project, NotificationType.ERROR, "Failed to create project doc directory," + docDirectoryPath);
                        return;
                    }
                } else if (!docDir.isDirectory()) {
                    FlingHelper.notify(project, NotificationType.WARNING, "Project doc directory is exist");
                    return;
                }

                for (DocSource docSource : DocSource.values()) {
                    // 查找指定名称的文件
                    VirtualFile targetFile = docDir.findChild(docSource.name() + ".json");
                    if (targetFile == null || !targetFile.exists()) {
                        // 创建文件，内容是 {}
                        try {
                            targetFile = docDir.createChildData(null, docSource.name() + ".json");
                            // 写入 {} 到文件
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile.getPath()))) {
                                writer.write(docSource.demoStr());
                            }
                        } catch (IOException e) {
                            FlingHelper.notify(project, NotificationType.ERROR, "Failed to create doc config file: " + docSource.name());
                        }
                    }
                }
            }
        });
    }


    public static Map<DocSource, Map<String, Doc>> getProjectDocs(Project project) {
        return projectDocs.get(project);
    }


    /**
     * 从项目根目录的 doc 目录下查找指定名称的文件，并返回文件内容。
     *
     * @param project  当前项目
     * @param strategy 文件名称（例如：class_annotation.json）
     * @return 文件内容，若文件不存在或读取失败则返回 null
     */
    public static Map<String, Doc> refreshDoc(Project project, DocSource strategy) {
        if (project == null || strategy == null) {
            return null;  // 参数无效时返回 null
        }

        // 获取项目根目录
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            // 如果项目根目录路径为空，返回 null
            return null;
        }

        // 构建 doc 目录的路径
        String docDirectoryPath = projectPath + "/doc";

        // 获取 doc 目录的 VirtualFile 对象
        VirtualFile docDir = LocalFileSystem.getInstance().findFileByPath(docDirectoryPath);
        if (docDir == null || !docDir.exists() || !docDir.isDirectory()) {
            return null;  // 如果 doc 目录不存在或者不是目录，返回 null
        }

        // 查找指定名称的文件
        VirtualFile targetFile = docDir.findChild(strategy.name() + ".json");
        if (targetFile == null || !targetFile.exists()) {
            return null;  // 如果文件不存在，返回 null
        }

        // 读取文件内容
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(targetFile.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            Map<String, Doc> boMap = JSON.parseObject(content.toString().trim(), new TypeReference<Map<String, Doc>>() {
            });
            if (boMap == null) {
                return null;
            }

            Iterator<Map.Entry<String, Doc>> iterator = boMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Doc> next = iterator.next();
                Doc doc = next.getValue();
                String href = doc.getHref();
                if (StringUtils.isBlank(href)) {
                    iterator.remove();
                    continue;
                }
                href = href.trim();
                if (href.startsWith("http")) {
                    doc.setType(DocType.url);
                    doc.setDirectory(null);
                    doc.setTitle(doc.getTitle() == null ? "Document" : doc.getTitle());
                    doc.setHref(href);
                } else {
                    doc.setType(DocType.markdown);
                    // 处理本地文档路径
                    String[] pathParts = href.split("/");
                    StringBuilder directoryBuilder = new StringBuilder();
                    for (int i = 0; i < pathParts.length - 1; i++) {
                        directoryBuilder.append("/").append(pathParts[i]);
                    }
                    String docName = pathParts[pathParts.length - 1];
                    doc.setTitle(docName);
                    doc.setDirectory(docDirectoryPath + directoryBuilder);
                    doc.setHref(null);
                }
            }
            return boMap;  // 返回文件内容，去除末尾多余的换行
        } catch (Throwable e) {
            FlingHelper.notify(project, NotificationType.ERROR, "Fail load project doc, source:" + strategy.name() + ".json , " + e.getClass().getSimpleName() + ", " + e.getMessage());
            return null;
        }
    }

    public static String loadContent(Doc doc) {
        String filePath = doc.getDirectory() + "/" + doc.getTitle();
        String fileContent = getFileContent(filePath);
        if (fileContent == null) {
            return "can not load local file:" + filePath;
        }
        return fileContent.replaceAll("\\]\\((?!http)(.*?)\\)", "](file://" + doc.getDirectory() + "/$1)");
    }

    /**
     * 根据路径获取文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容
     */
    private static String getFileContent(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;  // 如果文件不存在，返回 null
        }
        // 读取文件内容
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            return content.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static enum DocSource {
        class_annotation() {
            @Override
            public String demoStr() {
                Doc doc = new Doc();
                doc.setTitle(DOC_TITLE_DESC);
                doc.setHref(DOC_HREF_DESC);
                HashMap<Object, Object> map = new HashMap<>();
                map.put("org.springframework.stereotype.Controller", doc);
                return JSON.toJSONString(map, SerializerFeature.PrettyFormat);
            }
        }, class_type() {
            @Override
            public String demoStr() {
                Doc doc = new Doc();
                doc.setTitle(DOC_TITLE_DESC);
                doc.setHref(DOC_HREF_DESC);
                HashMap<Object, Object> map = new HashMap<>();
                map.put("java.util.concurrent.Executor", doc);
                return JSON.toJSONString(map, SerializerFeature.PrettyFormat);
            }
        }, method_annotation() {
            @Override
            public String demoStr() {
                Doc doc = new Doc();
                doc.setTitle(DOC_TITLE_DESC);
                doc.setHref(DOC_HREF_DESC);
                HashMap<Object, Object> map = new HashMap<>();
                map.put("org.springframework.web.bind.annotation.RequestMapping", doc);
                return JSON.toJSONString(map, SerializerFeature.PrettyFormat);
            }
        }, declared_method() {
            @Override
            public String demoStr() {
                Doc doc = new Doc();
                doc.setTitle(DOC_TITLE_DESC);
                doc.setHref(DOC_HREF_DESC);
                HashMap<Object, Object> map = new HashMap<>();
                map.put("org.springframework.beans.factory.InitializingBean#afterPropertiesSet", doc);
                return JSON.toJSONString(map, SerializerFeature.PrettyFormat);
            }
        }, field_annotation() {
            @Override
            public String demoStr() {
                Doc doc = new Doc();
                doc.setTitle(DOC_TITLE_DESC);
                doc.setHref(DOC_HREF_DESC);
                HashMap<Object, Object> map = new HashMap<>();
                map.put("java.util.concurrent.Executor", doc);
                return JSON.toJSONString(map, SerializerFeature.PrettyFormat);
            }
        };


        public String demoStr() {
            return "{}";
        }
    }


    public static enum DocType {
        url, markdown
    }

    public static class Doc {

        private DocType type;
        private String title;
        private String href;
        private String directory;


        public String getHref() {
            return href;
        }

        public void setHref(String href) {
            this.href = href;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public DocType getType() {
            return type;
        }

        public void setType(DocType type) {
            this.type = type;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

    }
}
