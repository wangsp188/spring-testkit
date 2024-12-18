package com.fling.doc.adapter;

import com.fling.doc.DocHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.apache.commons.collections.MapUtils;

import java.util.Map;
import java.util.Set;

public class DeclaredMethodAdapter implements ElementDocAdapter {

    private static DeclaredMethodAdapter adapter = new DeclaredMethodAdapter();


    public static ElementDocAdapter getInstance(){
        return adapter;
    }



    @Override
    public DocHelper.DocSource getDocSource() {
        return DocHelper.DocSource.declared_method;
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
