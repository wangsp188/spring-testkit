package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.*;
import org.apache.commons.collections.MapUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

public class FieldAnnotationAdapter implements ElementGuidelinesAdapter {

    private static FieldAnnotationAdapter adapter = new FieldAnnotationAdapter();


    public static ElementGuidelinesAdapter getInstance(){
        return adapter;
    }



    @Override
    public CodingGuidelinesHelper.DocSource getDocSource() {
        return CodingGuidelinesHelper.DocSource.field_annotation;
    }

    @Override
    public Collection<CodingGuidelinesHelper.Doc> find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs) {
        if(!(element instanceof PsiField)){
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }

        LinkedHashSet<CodingGuidelinesHelper.Doc> set = new LinkedHashSet<>();

        PsiField fieldType = (PsiField) element;
        for (Map.Entry<String, CodingGuidelinesHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (fieldType.hasAnnotation(annotation)) {
                set.add(entry.getValue());
            }
        }
        return set;
    }
}
