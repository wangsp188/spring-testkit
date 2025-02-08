package com.testkit.tools.mapper_sql;

import com.testkit.view.TestkitToolWindowFactory;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.testkit.tools.PluginToolEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class MapperSqlIconProvider implements LineMarkerProvider {

    public static final Icon MAPPER_SQL_ICON = IconLoader.getIcon("/icons/mapper-sql.svg", MapperSqlIconProvider.class);


    @Nullable
    @Override
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {
        if (!(element instanceof XmlTag)) {
            return null;
        }
        XmlTag tag = (XmlTag) element;
        if(!isSqlTag(tag)){
            return null;
        }
        return new LineMarkerInfo<>(
                tag,
                tag.getTextRange(),
                MAPPER_SQL_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "Build SQL";
                    }
                },
                new GutterIconNavigationHandler() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        TestkitToolWindowFactory.switch2Tool(project, PluginToolEnum.MAPPER_SQL, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    public static boolean isSqlTag(XmlTag tag) {
        if ("select".equals(tag.getName()) || "insert".equals(tag.getName()) || "update".equals(tag.getName()) || "delete".equals(tag.getName())){
            return true;
        }

        // 检查是否位于mapper节点内
        PsiElement parentElement = tag.getParent();
        if (!(parentElement instanceof XmlTag) || !"mapper".equals(((XmlTag) parentElement).getName())) {
            return false;
        }

        return false;
    }

}