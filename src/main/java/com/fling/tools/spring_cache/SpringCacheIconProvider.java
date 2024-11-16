package com.fling.tools.spring_cache;

import com.fling.tools.PluginToolEnum;
import com.fling.view.FlingToolWindowFactory;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;  
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class SpringCacheIconProvider implements LineMarkerProvider {

    private static final Icon CACHEABLE_ICON = IconLoader.getIcon("/icons/cacheable.svg",SpringCacheIconProvider.class);

    @Nullable  
    @Override  
    public LineMarkerInfo<PsiElement> getLineMarkerInfo(@NotNull PsiElement element) {  
        if (!(element instanceof PsiMethod)) {  
            return null;  
        }  

        PsiMethod method = (PsiMethod) element;  
        if (!hasCacheAnnotation(method.getModifierList())) {
            return null;
        }
        return new LineMarkerInfo<>(
                method,
                method.getTextRange(),
                CACHEABLE_ICON, // 这是你自定义的图标
                new Function<PsiElement, String>() {
                    @Override
                    public String fun(PsiElement element) {
                        return "This method is cacheable";
                    }
                },
                new GutterIconNavigationHandler(){
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        FlingToolWindowFactory.switch2Tool(project, PluginToolEnum.SPRING_CACHE, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private boolean hasCacheAnnotation(PsiModifierList modifierList) {
        return modifierList.hasAnnotation("org.springframework.cache.annotation.Cacheable") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CacheEvict") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CachePut");
    }
}