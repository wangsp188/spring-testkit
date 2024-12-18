package com.fling.doc.adapter;

import com.fling.doc.DocHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.apache.commons.collections.MapUtils;

import java.util.Map;

public class MethodAnnotationAdapter implements ElementDocAdapter{

    private static MethodAnnotationAdapter adapter = new MethodAnnotationAdapter();


    public static ElementDocAdapter getInstance(){
        return adapter;
    }



    @Override
    public DocHelper.DocSource getDocSource() {
        return DocHelper.DocSource.method_annotation;
    }

    @Override
    public DocHelper.Doc find(PsiElement element, Map<String, DocHelper.Doc> docs) {
        if(!(element instanceof PsiMethod)){
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }

        PsiMethod psiMethod = (PsiMethod) element;
        for (Map.Entry<String, DocHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (psiMethod.hasAnnotation(annotation)) {
                return entry.getValue();
            }
        }
        return null;

    }
}
