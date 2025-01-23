package com.testkit.coding_guidelines;

import com.testkit.TestkitHelper;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.util.Function;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class CodingGuidelinesIconProvider implements LineMarkerProvider {

    public static final Icon DOC_ICON = IconLoader.getIcon("/icons/doc.svg", CodingGuidelinesIconProvider.class);

    public static final Icon MARKDOWN_ICON = IconLoader.getIcon("/icons/markdown.svg", CodingGuidelinesIconProvider.class);
    public static final Icon URL_ICON = IconLoader.getIcon("/icons/url.svg", CodingGuidelinesIconProvider.class);



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
                            TestkitHelper.notify(elt.getProject(), NotificationType.ERROR, "Cannot display UI elements in a headless environment.");
                        }

                        List<CodingGuidelinesHelper.Doc> docs = CodingGuidelinesHelper.findDoc(elt);
                        if (CollectionUtils.isEmpty(docs)) {
                            TestkitHelper.notify(elt.getProject(), NotificationType.ERROR, "can not find adapter docs");
                            return;
                        }
                        if (docs.size() == 1) {
                            CodingGuidelinesHelper.navigateToDocumentation(docs.get(0));
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
                                        CodingGuidelinesHelper.navigateToDocumentation(doc);
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


}