package com.testkit.coding_guidelines.adapter;

import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ElementGuidelinesAdapter {

    CodingGuidelinesHelper.DocSource getDocSource();

    Collection<CodingGuidelinesHelper.Doc> find(PsiElement element, Map<String, CodingGuidelinesHelper.Doc> docs);
}
