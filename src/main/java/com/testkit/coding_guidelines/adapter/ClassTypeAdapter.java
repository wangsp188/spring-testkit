package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.apache.commons.collections.MapUtils;

import java.util.Map;

public class ClassTypeAdapter implements ElementGuidelinesAdapter {

    private static ClassTypeAdapter adapter = new ClassTypeAdapter();


    public static ElementGuidelinesAdapter getInstance() {
        return adapter;
    }


    @Override
    public CodingGuidelinesHelper.DocSource getDocSource() {
        return CodingGuidelinesHelper.DocSource.class_type;
    }

    @Override
    public CodingGuidelinesHelper.Doc find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs) {
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
        for (Map.Entry<String, CodingGuidelinesHelper.Doc> entry : docs.entrySet()) {
            if (InheritanceUtil.isInheritor(psiClass, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

}
