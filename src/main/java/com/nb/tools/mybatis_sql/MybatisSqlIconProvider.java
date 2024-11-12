package com.nb.tools.mybatis_sql;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.nb.tools.PluginToolEnum;
import com.nb.view.WindowHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class MybatisSqlIconProvider implements LineMarkerProvider {

    private static final Icon CALL_METHOD_ICON = IconLoader.getIcon("/icons/no-bug.svg", MybatisSqlIconProvider.class);


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
                CALL_METHOD_ICON,
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "build sql";
                    }
                },
                new GutterIconNavigationHandler() {
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        WindowHelper.switch2Tool(project, PluginToolEnum.MYBATIS_SQL, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private boolean isSqlTag(XmlTag tag) {
        if ("select".equals(tag.getName()) || "insert".equals(tag.getName()) || "update".equals(tag.getName()) || "delete".equals(tag.getName())){
            return true;
        }
        return false;
    }

}