package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.apache.commons.collections.MapUtils;

import java.util.Map;

public class MethodAnnotationAdapter implements ElementGuidelinesAdapter {

    private static MethodAnnotationAdapter adapter = new MethodAnnotationAdapter();


    public static ElementGuidelinesAdapter getInstance(){
        return adapter;
    }



    @Override
    public CodingGuidelinesHelper.DocSource getDocSource() {
        return CodingGuidelinesHelper.DocSource.method_annotation;
    }

    @Override
    public CodingGuidelinesHelper.Doc find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs) {
        if(!(element instanceof PsiMethod)){
            return null;
        }
        if (MapUtils.isEmpty(docs)) {
            return null;
        }

        PsiMethod psiMethod = (PsiMethod) element;
        for (Map.Entry<String, CodingGuidelinesHelper.Doc> entry : docs.entrySet()) {
            String annotation = entry.getKey();
            if (psiMethod.hasAnnotation(annotation)) {
                return entry.getValue();
            }
        }
        return null;

    }
}
