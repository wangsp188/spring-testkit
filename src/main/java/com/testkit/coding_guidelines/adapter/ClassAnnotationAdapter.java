package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.apache.commons.collections.MapUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
    public Set<CodingGuidelinesHelper.Doc> find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs) {
        if (!(element instanceof PsiClass)) {
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }
        LinkedHashSet<CodingGuidelinesHelper.Doc> set = new LinkedHashSet<>();
        PsiClass psiClass = (PsiClass) element;
        for (Map.Entry<String, CodingGuidelinesHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (psiClass.hasAnnotation(annotation)) {
                set.add(entry.getValue());
            }
        }
        return set;
    }
}
