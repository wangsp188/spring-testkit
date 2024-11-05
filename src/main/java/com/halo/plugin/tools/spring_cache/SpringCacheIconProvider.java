package com.halo.plugin.tools.spring_cache;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;  
import com.intellij.openapi.editor.markup.GutterIconRenderer;  
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SpringCacheIconProvider implements LineMarkerProvider {

    private static final Icon CACHEABLE_ICON = new ImageIcon(SpringCacheIconProvider.class.getResource("/icons/cacheable.png"));

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
                new CacheableIconNavigationHandler(method),
                GutterIconRenderer.Alignment.RIGHT
        );
    }

    private boolean hasCacheAnnotation(PsiModifierList modifierList) {
        return modifierList.hasAnnotation("org.springframework.cache.annotation.Cacheable") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CacheEvict") ||
                modifierList.hasAnnotation("org.springframework.cache.annotation.CachePut");
    }
}