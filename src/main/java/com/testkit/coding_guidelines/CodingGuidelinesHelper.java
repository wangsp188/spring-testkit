package com.testkit.coding_guidelines;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.adapter.*;
import com.testkit.tools.mapper_sql.MapperSqlIconProvider;
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
import com.intellij.psi.xml.XmlTag;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

public class CodingGuidelinesHelper {

    private static final List<ElementGuidelinesAdapter> classSources = Arrays.asList(ClassAnnotationAdapter.getInstance(), ClassTypeAdapter.getInstance());
    private static final List<ElementGuidelinesAdapter> methodSources = Arrays.asList(MethodAnnotationAdapter.getInstance(), DeclaredMethodAdapter.getInstance());
    private static final List<ElementGuidelinesAdapter> fieldSources = Arrays.asList(ClassTypeAdapter.getInstance(), FieldAnnotationAdapter.getInstance());
    public static final String DOC_TITLE_DESC = "Doc's title , can be null. //This is used when the href attribute is a link";
    public static final String DOC_HREF_DESC = "Doc's link , can not be null. //A link to doc may either point to the local markdown document The path relative to the current directory, such as doc/hello.md, or use the web link directly to https://www.baidu.com";
    public static final String CODING_GUIDELINES = "/coding-guidelines";

    private volatile static Map<Project, Map<DocSource, Map<String, Doc>>> projectDocs = new HashMap<>();

    private volatile static Map<Project, List<Doc>> homeDocs = new HashMap<>();
    private volatile static Map<Project, Map<DocScope, Doc>> scopeDocs = new HashMap<>();


    public static boolean hasDoc(PsiElement element) {
        if (element instanceof XmlTag && MapperSqlIconProvider.isSqlTag((XmlTag) element)) {
            Map<DocScope, Doc> docScopeDocMap = scopeDocs.get(element.getProject());
            return docScopeDocMap != null && docScopeDocMap.get(DocScope.sql) != null;
        }

        Map<DocSource, Map<String, Doc>> sourceMap = projectDocs.get(element.getProject());
        if (MapUtils.isEmpty(sourceMap)) {
            return false;
        }

        List<ElementGuidelinesAdapter> adapters = null;
        if (element instanceof PsiClass) {
            adapters = classSources;
        } else if (element instanceof PsiMethod) {
            adapters = methodSources;
        } else if (element instanceof PsiField) {
            adapters = fieldSources;
        } else {
            return false;
        }
        for (ElementGuidelinesAdapter adapter : adapters) {
            Collection<Doc> doc = adapter.find(element, sourceMap.get(adapter.getDocSource()));
            if (doc != null && !doc.isEmpty()) {
                return true;
            }
        }

        return false;
    }


    public static List<Doc> findDoc(PsiElement element) {
        if (element instanceof XmlTag && MapperSqlIconProvider.isSqlTag((XmlTag) element)) {
            Doc scopeDoc = CodingGuidelinesHelper.getScopeDoc(element.getProject(), DocScope.sql);
            if (scopeDoc == null) {
                return Collections.emptyList();
            }
            return List.of(scopeDoc);
        }
        Map<DocSource, Map<String, Doc>> sourceMap = projectDocs.get(element.getProject());
        if (MapUtils.isEmpty(sourceMap)) {
            return null;
        }
        List<ElementGuidelinesAdapter> adapters = null;
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
        for (ElementGuidelinesAdapter adapter : adapters) {
            Collection<Doc> doc = adapter.find(element, sourceMap.get(adapter.getDocSource()));
            if (doc != null && !doc.isEmpty()) {
                docs.addAll(doc);
            }
        }
        return docs;
    }


    public static List<Doc> getHomeDocs(Project project) {
        if (project == null) {
            return null;
        }
        return homeDocs.get(project);
    }

    public static Collection<Doc> getScopeDocs(Project project) {
        if (project == null) {
            return null;
        }
        Map<DocScope, Doc> docScopeDocMap = scopeDocs.get(project);
        return docScopeDocMap == null ? new ArrayList<>() : docScopeDocMap.values();
    }

    public static Doc getScopeDoc(Project project, DocScope scope) {
        if (project == null || scope == null) {
            return null;
        }
        Map<DocScope, Doc> docScopeDocMap = scopeDocs.get(project);
        return docScopeDocMap == null ? null : docScopeDocMap.get(scope);
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
                refreshPresetDoc(project);
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
                String docDirectoryPath = projectPath + CODING_GUIDELINES;
                // 获取 doc 目录的 VirtualFile 对象
                VirtualFile docDir = LocalFileSystem.getInstance().findFileByPath(docDirectoryPath);
                if (docDir == null || !docDir.exists()) {
                    // 创建 doc 目录
                    try {
                        new File(docDirectoryPath).mkdirs();
                        docDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(docDirectoryPath);
                        docDir.isDirectory();
                    } catch (Throwable e) {
                        TestkitHelper.notify(project, NotificationType.ERROR, "Failed to create coding-guidelines directory," + docDirectoryPath);
                        return;
                    }
                } else if (!docDir.isDirectory()) {
                    TestkitHelper.notify(project, NotificationType.WARNING, "Coding-guidelines directory is exist");
                    return;
                }

                JSONObject preSetDemos = new JSONObject();
                Doc doc = new Doc();
                doc.setHref(DOC_HREF_DESC);
                doc.setTitle(DOC_TITLE_DESC);
                preSetDemos.put("home", List.of(doc));
                //初始化pre-set
                for (DocScope value : DocScope.values()) {
                    Doc doc1 = value.demoDoc();
                    if (doc1 == null) {
                        continue;
                    }
                    preSetDemos.put(value.name().replace("_", "-"), doc1);
                }
                // 查找指定名称的文件
                VirtualFile presetFile = docDir.findChild("pre-set.json");
                if (presetFile == null || !presetFile.exists()) {
                    // 创建文件，内容是 {}
                    try {
                        presetFile = docDir.createChildData(null, "pre-set.json");
                        // 写入 {} 到文件
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(presetFile.getPath()))) {
                            writer.write(preSetDemos.toString(SerializerFeature.PrettyFormat));
                        }
                    } catch (IOException e) {
                        TestkitHelper.notify(project, NotificationType.ERROR, "Failed to create coding-guidelines config file: pre-set.json");
                    }
                }else{
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(presetFile.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder content = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append(System.lineSeparator());
                        }
                        JSONObject old = JSON.parseObject(content.toString().trim());
                        Object o = old.get("home");
                        if(o == null || !(o instanceof Collection<?>)){
                            preSetDemos.put("home", o);
                        }

                        for (DocScope value : DocScope.values()) {
                            Doc nowObj = old.getObject(value.name().replace("_", "-"),Doc.class);
                            if (nowObj==null) {
                                continue;
                            }
                            preSetDemos.put(value.name().replace("_", "-"), nowObj);
                        }
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(presetFile.getPath()))) {
                            writer.write(preSetDemos.toString(SerializerFeature.PrettyFormat));
                        }
                    }catch (Throwable e){
                        TestkitHelper.notify(project, NotificationType.ERROR, "Failed to modify coding-guidelines config file: pre-set.json");
                    }
                }

                for (DocSource docSource : DocSource.values()) {
                    // 查找指定名称的文件
                    VirtualFile targetFile = docDir.findChild(docSource.name().replace("_", "-") + ".json");
                    if (targetFile == null || !targetFile.exists()) {
                        // 创建文件，内容是 {}
                        try {
                            targetFile = docDir.createChildData(null, docSource.name().replace("_", "-") + ".json");
                            // 写入 {} 到文件
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile.getPath()))) {
                                writer.write(docSource.demoStr());
                            }
                        } catch (IOException e) {
                            TestkitHelper.notify(project, NotificationType.ERROR, "Failed to create coding-guidelines config file: " + docSource.name().replace("_", "-"));
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
     * @param project 当前项目
     * @return 文件内容，若文件不存在或读取失败则返回 null
     */
    public static void refreshPresetDoc(Project project) {

        Map<DocScope, Doc> tmpScenes = new HashMap<>();
        List<Doc> tmpHomes = new ArrayList<>();
        try {
            if (project == null) {
                return;  // 参数无效时返回 null
            }
            // 获取项目根目录
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                // 如果项目根目录路径为空，返回 null
                return;
            }

            // 构建 doc 目录的路径
            String docDirectoryPath = projectPath + CODING_GUIDELINES;

            // 获取 doc 目录的 VirtualFile 对象
            VirtualFile docDir = LocalFileSystem.getInstance().findFileByPath(docDirectoryPath);
            if (docDir == null || !docDir.exists() || !docDir.isDirectory()) {
                return;  // 如果 doc 目录不存在或者不是目录，返回 null
            }

            // 查找指定名称的文件
            VirtualFile targetFile = docDir.findChild("pre-set.json");
            if (targetFile == null || !targetFile.exists()) {
                return;  // 如果文件不存在，返回 null
            }

            // 读取文件内容
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(targetFile.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append(System.lineSeparator());
                }
                JSONObject object = JSON.parseObject(content.toString().trim());
                if (object == null) {
                    return;
                }
                List<Doc> homes = object.getObject("home", new TypeReference<List<Doc>>() {
                });
                if (homes != null) {
                    tmpHomes = homes.stream()
                            .filter(new Predicate<Doc>() {
                                @Override
                                public boolean test(Doc doc) {
                                    return doc != null && StringUtils.isNotBlank(doc.getHref());
                                }
                            })
                            .map(doc -> {
                                String href = doc.getHref();
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
                                return doc;
                            }).toList();
                }


                for (DocScope docScope : DocScope.values()) {
                    Doc doc = object.getObject(docScope.name().replace("_", "-"), Doc.class);
                    if (doc == null || StringUtils.isBlank(doc.getHref())) {
                        continue;
                    }
                    String href = doc.getHref();
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
                    tmpScenes.put(docScope, doc);
                }
            } catch (Throwable e) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Fail load coding-guidelines, source:pre-set.json , " + e.getClass().getSimpleName() + ", " + e.getMessage());
            }
        } finally {
            scopeDocs.put(project, tmpScenes);
            homeDocs.put(project, tmpHomes);
        }
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
        String docDirectoryPath = projectPath + CODING_GUIDELINES;

        // 获取 doc 目录的 VirtualFile 对象
        VirtualFile docDir = LocalFileSystem.getInstance().findFileByPath(docDirectoryPath);
        if (docDir == null || !docDir.exists() || !docDir.isDirectory()) {
            return null;  // 如果 doc 目录不存在或者不是目录，返回 null
        }

        // 查找指定名称的文件
        VirtualFile targetFile = docDir.findChild(strategy.name().replace("_", "-") + ".json");
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
            TestkitHelper.notify(project, NotificationType.ERROR, "Fail load coding-guidelines, source:" + strategy.name().replace("_", "-") + ".json , " + e.getClass().getSimpleName() + ", " + e.getMessage());
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

    public static void navigateToDocumentation(Doc doc) {
        if (doc == null) {
            return;
        }
        if (doc.getType() == DocType.url) {
            try {
                URI uri = new URI(doc.getHref());
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (doc.getType() == DocType.markdown) {
            //补全代码，弹出一个小框利用content渲染
            String markdownContent = loadContent(doc);
            showFormattedMarkdown(doc.getTitle(), markdownContent);
        }

    }

    private static void showFormattedMarkdown(String title, String markdown) {
        // Parse the markdown to HTML
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String htmlContent = renderer.render(document);

        // Display HTML content in JEditorPane
        JEditorPane editorPane = new JEditorPane("text/html", htmlContent);
        editorPane.setEditable(false);
        // Add a HyperlinkListener to handle clicks on hyperlinks
        editorPane.addHyperlinkListener(event -> {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(event.getEventType())) {
                try {
                    Desktop.getDesktop().browse(event.getURL().toURI());
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });

        // Use a scroll pane to contain the editor pane
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);


        JDialog dialog = new JDialog();
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        // Calculate size to be 80% of the screen size
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.8);
        int height = (int) (screenSize.height * 0.8);
        dialog.setSize(width, height);
        // Center the dialog on the screen
        dialog.setLocationRelativeTo(null);

        dialog.setTitle(title == null ? "Document" : title);
        Container contentPane = dialog.getContentPane();
        Component[] components = contentPane.getComponents();
        if (components != null) {
            for (int i = 0; i < components.length; i++) {
                contentPane.remove(components[i]);
            }
        }
        contentPane.add(scrollPane);
        // Display the dialog
        dialog.setVisible(true);
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


    public static enum DocScope {
        sql() {
            @Override
            public Doc demoDoc() {
                Doc doc = new Doc();
                doc.setTitle("SQL调优概览");
                doc.setHref("https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000002013770");
                return doc;
            }
        };

        public Doc demoDoc() {
            Doc doc = new Doc();
            doc.setHref(DOC_HREF_DESC);
            doc.setTitle(DOC_TITLE_DESC);
            return doc;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Doc doc = (Doc) o;
            return type == doc.type && Objects.equals(href, doc.href) && Objects.equals(title, doc.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, href,title);
        }
    }
}
