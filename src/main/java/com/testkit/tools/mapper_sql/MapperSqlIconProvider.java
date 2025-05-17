package com.testkit.tools.mapper_sql;

import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
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
    public LineMarkerInfo<XmlToken> getLineMarkerInfo(@NotNull PsiElement element) {
        // 仅在 XML 标签名称的 Token 上注册
        if (!(element instanceof XmlToken)) {
            return null;
        }

        XmlToken token = (XmlToken) element;
        if (token.getTokenType() != XmlTokenType.XML_NAME) {
            return null; // 仅处理标签名称的 Token
        }

        PsiElement parent = token.getParent();
        if (!(parent instanceof XmlTag)) {
            return null;
        }

        XmlTag tag = (XmlTag) parent;
        if(!isSqlTag(tag)){
            return null;
        }
        if (!RuntimeHelper.isEnableMapperSql()) {
            return null;
        }
        return new LineMarkerInfo<>(
                token,
                token.getTextRange(),
                MAPPER_SQL_ICON,
                new Function<XmlToken, String>() {
                    @Override
                    public String fun(XmlToken element) {
                        return "Build SQL";
                    }
                },
                new GutterIconNavigationHandler<XmlToken>() {
                    @Override
                    public void navigate(MouseEvent e, XmlToken elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        TestkitToolWindowFactory.switch2Tool(project, PluginToolEnum.MAPPER_SQL, elt.getParent());
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