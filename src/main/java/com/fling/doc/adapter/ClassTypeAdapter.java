package com.fling.doc.adapter;

import com.fling.doc.DocHelper;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.apache.commons.collections.MapUtils;

import java.util.Map;

public class ClassTypeAdapter implements ElementDocAdapter {

    private static ClassTypeAdapter adapter = new ClassTypeAdapter();


    public static ElementDocAdapter getInstance() {
        return adapter;
    }


    @Override
    public DocHelper.DocSource getDocSource() {
        return DocHelper.DocSource.class_type;
    }

    @Override
    public DocHelper.Doc find(PsiElement element, Map<String, DocHelper.Doc> docs) {
        if (MapUtils.isEmpty(docs)) {
            return null;
        }

        PsiClass psiClass = null;
        if (element instanceof PsiClass) {
            psiClass = (PsiClass) element;
        } else if (element instanceof PsiField) {
            PsiType fieldType = ((PsiField) element).getType();
            if (fieldType instanceof PsiClassType) {
                psiClass = ((PsiClassType) fieldType).resolve(); // Resolves the class type to a PsiClass
            }
        }
        if (psiClass == null) {
            return null;
        }
        for (Map.Entry<String, DocHelper.Doc> entry : docs.entrySet()) {
            if (InheritanceUtil.isInheritor(psiClass, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

}
