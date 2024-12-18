package com.fling.doc.adapter;

import com.fling.doc.DocHelper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.apache.commons.collections.MapUtils;
import java.util.Map;

public class ClassAnnotationAdapter implements ElementDocAdapter {

    private static ClassAnnotationAdapter adapter = new ClassAnnotationAdapter();


    public static ElementDocAdapter getInstance() {
        return adapter;
    }

    @Override
    public DocHelper.DocSource getDocSource() {
        return DocHelper.DocSource.class_annotation;
    }

    @Override
    public DocHelper.Doc find(PsiElement element, Map<String, DocHelper.Doc> docs) {
        if (!(element instanceof PsiClass)) {
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }
        PsiClass psiClass = (PsiClass) element;
        for (Map.Entry<String, DocHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (psiClass.hasAnnotation(annotation)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
