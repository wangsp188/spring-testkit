package com.fling.doc.adapter;

import com.fling.doc.DocHelper;
import com.intellij.psi.*;
import org.apache.commons.collections.MapUtils;

import java.util.Map;

public class FieldAnnotationAdapter implements ElementDocAdapter{

    private static FieldAnnotationAdapter adapter = new FieldAnnotationAdapter();


    public static ElementDocAdapter getInstance(){
        return adapter;
    }



    @Override
    public DocHelper.DocSource getDocSource() {
        return DocHelper.DocSource.field_annotation;
    }

    @Override
    public DocHelper.Doc find(PsiElement element, Map<String, DocHelper.Doc> docs) {
        if(!(element instanceof PsiField)){
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }

        PsiField fieldType = (PsiField) element;
        for (Map.Entry<String, DocHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (fieldType.hasAnnotation(annotation)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
