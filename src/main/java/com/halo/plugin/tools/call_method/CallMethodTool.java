package com.halo.plugin.tools.call_method;

import com.intellij.psi.PsiElement;
import com.halo.plugin.tools.ActionTool;
import com.halo.plugin.tools.BasePluginTool;
import com.halo.plugin.tools.PluginToolEnum;
import com.halo.plugin.view.PluginToolWindow;

public class CallMethodTool extends BasePluginTool implements ActionTool {

    {
        this.tool = PluginToolEnum.CALL_METHOD;
    }

    public CallMethodTool(PluginToolWindow pluginToolWindow) {
        super(pluginToolWindow);
    }

    @Override
    public void onSwitchAction(PsiElement psiElement) {

    }
}
