package com.testkit.tools.spring_cache;

import com.testkit.RuntimeHelper;
import com.testkit.SettingsStorageHelper;
import com.testkit.tools.PluginToolEnum;
import com.testkit.tools.ToolHelper;
import com.testkit.view.TestkitToolWindowFactory;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;  
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class SpringCacheIconProvider implements LineMarkerProvider {

    public static final Icon CACHEABLE_ICON = IconLoader.getIcon("/icons/cacheable.svg",SpringCacheIconProvider.class);

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
                        return "Build keys";
                    }
                },
                new GutterIconNavigationHandler(){
                    @Override
                    public void navigate(MouseEvent e, PsiElement elt) {
                        if (GraphicsEnvironment.isHeadless()) {
                            throw new HeadlessException("Cannot display UI elements in a headless environment.");
                        }
                        Project project = elt.getProject();
                        TestkitToolWindowFactory.switch2Tool(project, PluginToolEnum.SPRING_CACHE, elt);
                    }
                },
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private boolean hasCacheAnnotation(PsiModifierList modifierList) {
        // 1. 检查方法是否为 public
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // 3. 获取方法和类
        PsiElement parent = modifierList.getParent();
        if (!(parent instanceof PsiMethod)) {
            return false;
        }
        PsiMethod psiMethod = (PsiMethod) parent;
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        if(!RuntimeHelper.hasAppMeta(psiMethod.getProject().getName()) || !SettingsStorageHelper.isEnableSideServer(psiMethod.getProject())){
            return false;
        }

        // 4. 检查类是否含有 Spring 可注册 Bean 的注解
        if (!ToolHelper.isSpringBean(containingClass)) {
            return false;
        }

        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        return modifierList.hasAnnotation("org.springframework.cache.annotation.Cacheable") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CacheEvict") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CachePut") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.Caching");
    }
}