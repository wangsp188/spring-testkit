package com.fling.coding_guidelines;

import com.fling.FlingHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class CodingGuidelinesIconProvider implements LineMarkerProvider {

    public static final Icon DOC_ICON = IconLoader.getIcon("/icons/doc.svg", CodingGuidelinesIconProvider.class);

    public static final Icon MARKDOWN_ICON = IconLoader.getIcon("/icons/markdown.svg", CodingGuidelinesIconProvider.class);
    public static final Icon URL_ICON = IconLoader.getIcon("/icons/url.svg", CodingGuidelinesIconProvider.class);


    private JDialog dialog ;
    {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dialog = new JDialog();
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                // Calculate size to be 80% of the screen size
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int width = (int) (screenSize.width * 0.8);
                int height = (int) (screenSize.height * 0.8);
                dialog.setSize(width, height);
                // Center the dialog on the screen
                dialog.setLocationRelativeTo(null);
                //注册到
            }
        });

    }



    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!CodingGuidelinesHelper.hasDoc(element)) {
            return null;
        }
        return new LineMarkerInfo<>(
                element,
                element.getTextRange(),
                DOC_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "see see doc";
                    }
                },
                new GutterIconNavigationHandler() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            FlingHelper.notify(elt.getProject(), NotificationType.ERROR, "Cannot display UI elements in a headless environment.");
                        }

                        List<CodingGuidelinesHelper.Doc> docs = CodingGuidelinesHelper.findDoc(elt);
                        if (CollectionUtils.isEmpty(docs)) {
                            FlingHelper.notify(elt.getProject(), NotificationType.ERROR, "can not find adapter docs");
                            return;
                        }
                        if (docs.size() == 1) {
                            navigateToDocumentation(docs.get(0), elt.getProject());
                        } else {
                            //在图标旁边显示一个tips可选列表，看起来是个表格。
//                            表格有两列可见，title，还一列显示[查看]按钮
//                            点击查看按钮暂时弹出一个提示即可
                            // 创建显示文档标题和查看按钮的表格
                            // 创建显示文档标题和查看按钮的表格
                            DefaultActionGroup actionGroup = new DefaultActionGroup(); // 创建一个动作组
                            for (CodingGuidelinesHelper.Doc doc : docs) {
                                //显示的一个图标加上标题
                                AnAction documentation = new AnAction(doc.getTitle(), doc.getTitle(), doc.getType() == CodingGuidelinesHelper.DocType.url ? URL_ICON : MARKDOWN_ICON) {
                                    @Override
                                    public void actionPerformed(@NotNull AnActionEvent e) {
                                        navigateToDocumentation(doc, elt.getProject());
                                    }
                                };
                                actionGroup.add(documentation); // 将动作添加到动作组中
                            }
                            // 创建弹出菜单并显示
                            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance().createActionPopupMenu("DocumentationPopup", actionGroup).getComponent();
                            popupMenu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private void navigateToDocumentation(CodingGuidelinesHelper.Doc doc, @NotNull Project project) {
        if (doc == null) {
            return;
        }
        if (doc.getType() == CodingGuidelinesHelper.DocType.url) {
            try {
                URI uri = new URI(doc.getHref());
                Desktop.getDesktop().browse(uri);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        if (doc.getType() == CodingGuidelinesHelper.DocType.markdown) {
            //补全代码，弹出一个小框利用content渲染
            String markdownContent = CodingGuidelinesHelper.loadContent(doc);
            showFormattedMarkdown(doc.getTitle(), markdownContent);
        }

    }



    public void showFormattedMarkdown(String title, String markdown) {
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

        dialog.setTitle(title == null ? "Document" : title);
        Container contentPane = dialog.getContentPane();
        Component[] components = contentPane.getComponents();
        if (components!=null) {
            for (int i = 0; i < components.length; i++) {
                contentPane.remove(components[i]);
            }
        }
        contentPane.add(scrollPane);
        // Display the dialog
        dialog.setVisible(true);
    }

}