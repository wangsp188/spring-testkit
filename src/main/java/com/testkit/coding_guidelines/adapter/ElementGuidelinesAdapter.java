package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.PsiElement;

import java.util.Map;

public interface ElementGuidelinesAdapter {

    CodingGuidelinesHelper.DocSource getDocSource();

    CodingGuidelinesHelper.Doc find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs);
}
