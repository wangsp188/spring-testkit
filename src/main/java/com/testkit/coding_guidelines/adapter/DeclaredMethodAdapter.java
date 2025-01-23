package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.apache.commons.collections.MapUtils;

import java.util.Map;
import java.util.Set;

public class DeclaredMethodAdapter implements ElementGuidelinesAdapter {

    private static DeclaredMethodAdapter adapter = new DeclaredMethodAdapter();


    public static ElementGuidelinesAdapter getInstance(){
        return adapter;
    }



    @Override
    public CodingGuidelinesHelper.DocSource getDocSource() {
        return CodingGuidelinesHelper.DocSource.declared_method;
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

        Set<String> keys = docs.keySet();

        // 递归查找超类方法
        PsiMethod[] superMethods = psiMethod.findSuperMethods();
        if(superMethods==null || superMethods.length==0){
            try {
                String key = psiMethod.getContainingClass().getQualifiedName() + "#" + psiMethod.getName();
                if (keys.contains(key)) {
                    return docs.get(key);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        for (PsiMethod superMethod : superMethods) {
            try {
                String key = superMethod.getContainingClass().getQualifiedName() + "#" + psiMethod.getName();
                if (keys.contains(key)) {
                    return docs.get(key);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
