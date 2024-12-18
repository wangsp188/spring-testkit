package com.fling.doc.adapter;

import com.fling.doc.DocHelper;
import com.intellij.psi.PsiElement;

import java.util.Map;

public interface ElementDocAdapter {

    DocHelper.DocSource getDocSource();

    DocHelper.Doc find(PsiElement element, Map<String, DocHelper.Doc> docs);
}
