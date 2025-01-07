package com.fling.coding_guidelines.adapter;

import com.fling.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.apache.commons.collections.MapUtils;
import java.util.Map;

public class ClassAnnotationAdapter implements ElementGuidelinesAdapter {

    private static ClassAnnotationAdapter adapter = new ClassAnnotationAdapter();


    public static ElementGuidelinesAdapter getInstance() {
        return adapter;
    }

    @Override
    public CodingGuidelinesHelper.DocSource getDocSource() {
        return CodingGuidelinesHelper.DocSource.class_annotation;
    }

    @Override
    public CodingGuidelinesHelper.Doc find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs) {
        if (!(element instanceof PsiClass)) {
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }
        PsiClass psiClass = (PsiClass) element;
        for (Map.Entry<String, CodingGuidelinesHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (psiClass.hasAnnotation(annotation)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
