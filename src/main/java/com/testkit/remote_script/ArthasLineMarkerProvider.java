package com.testkit.remote_script;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.testkit.RuntimeHelper;
import com.testkit.TestkitHelper;
import com.testkit.tools.ToolHelper;
import com.testkit.view.TestkitToolWindow;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Arthas Line Marker Provider
 * 
 * 在方法旁显示 Arthas 图标，点击后弹出菜单：
 * - TimeTunnel (复现请求)
 * - View Remote Code (反编译)
 */
public class ArthasLineMarkerProvider implements LineMarkerProvider {


    @Override
    public LineMarkerInfo<PsiIdentifier> getLineMarkerInfo(@NotNull PsiElement element) {
        // Only register on identifier (leaf element)
        if (!(element instanceof PsiIdentifier)) {
            return null;
        }

        PsiIdentifier identifier = (PsiIdentifier) element;
        PsiElement parent = identifier.getParent();

        // Check if parent is PsiMethod (method-level marker)
        if (parent instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) parent;
            String verifyMsg = verifyMethod(method);
            if (verifyMsg != null) {
                return null;
            }

            return new LineMarkerInfo<>(
                    identifier,
                    identifier.getTextRange(),
                    TestkitToolWindow.arthasIcon,
                    elem -> "Arthas Tools (TimeTunnel / Jad)",
                    new ArthasMethodGutterHandler(),
                    GutterIconRenderer.Alignment.LEFT
            );
        }

        // Check if parent is PsiClass (class-level marker)
        if (parent instanceof PsiClass) {
            PsiClass psiClass = (PsiClass) parent;
            String verifyMsg = verifyClass(psiClass);
            if (verifyMsg != null) {
                return null;
            }

            return new LineMarkerInfo<>(
                    identifier,
                    identifier.getTextRange(),
                    TestkitToolWindow.arthasIcon,
                    elem -> "View Remote Code (Jad)",
                    new ArthasClassGutterHandler(),
                    GutterIconRenderer.Alignment.LEFT
            );
        }

        return null;
    }

    private void logSkip(String type, String reason, PsiClass clazz, String methodName) {
        String className = clazz != null ? clazz.getName() : "?";
        String target = methodName != null ? className + "#" + methodName : className;
        System.out.println("not_support_arthas, " + reason + ":" + target);
    }
    
    /**
     * Verify if method should show Arthas icon
     */
    private String verifyMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        String methodName = method.getName();

        // 1. Check global Arthas support flag
        if (!RuntimeHelper.isArthasSupported()) {
            logSkip("method", "arthas_not_supported", containingClass, methodName);
            return "arthas_not_supported";
        }

        // 2. Must have containing class
        if (containingClass == null || containingClass.getName() == null) {
            logSkip("method", "no_containing_class", containingClass, methodName);
            return "no_containing_class";
        }

        // 3. Skip enum classes
        if (containingClass.isEnum()) {
            logSkip("method", "is_enum", containingClass, methodName);
            return "is_enum";
        }

        // 4. Skip methods without body (abstract/interface methods)
        if (method.getBody() == null) {
            logSkip("method", "no_method_body", containingClass, methodName);
            return "no_method_body";
        }

        // 5. Skip test module
        PsiFile psiFile = method.getContainingFile();
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            String filePath = virtualFile.getPath();
            if (filePath.contains("/src/test/java/")) {
                logSkip("method", "is_test_module", containingClass, methodName);
                return "is_test_module";
            }
        }

        return null;
    }

    /**
     * Verify if class should show Arthas icon
     */
    private String verifyClass(PsiClass psiClass) {
        // 1. Check global Arthas support flag
        if (!RuntimeHelper.isArthasSupported()) {
            logSkip("class", "arthas_not_supported", psiClass, null);
            return "arthas_not_supported";
        }

        // 2. Skip anonymous/local classes
        if (psiClass.getName() == null || psiClass.getQualifiedName() == null) {
            logSkip("class", "anonymous_class", psiClass, null);
            return "anonymous_class";
        }

        // 3. Skip enum classes
        if (psiClass.isEnum()) {
            logSkip("class", "is_enum", psiClass, null);
            return "is_enum";
        }

        // 5. Skip test module
        PsiFile psiFile = psiClass.getContainingFile();
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
            String filePath = virtualFile.getPath();
            if (filePath.contains("/src/test/java/")) {
                logSkip("class", "is_test_module", psiClass, null);
                return "is_test_module";
            }
        }

        return null;
    }

    /**
     * Get all remote script apps (from both visibleApps cache and tempApps)
     */
    public static List<RuntimeHelper.VisibleApp> getRemoteScriptApps(String projectName) {
        // 统一使用 getArthasEnabledApps 的过滤逻辑，都要求有 arthasPort
        return getArthasEnabledApps(projectName);
    }

    /**
     * Get Arthas-enabled runtime instances (require arthasPort)
     * 只从 visibleApps 获取，因为 tempApps 中的连接可能还没探活或探活失败，没有 arthasPort
     */
    public static List<RuntimeHelper.VisibleApp> getArthasEnabledApps(String projectName) {
        return RuntimeHelper.getVisibleApps(projectName).stream()
                .filter(RuntimeHelper.VisibleApp::isRemoteInstance)
                .filter(app -> RuntimeHelper.isArthasEnabled(app.toConnectionString()))
                .collect(Collectors.toList());
    }

    /**
     * Method-level gutter icon click handler (TimeTunnel + View Remote Code)
     */
    private static class ArthasMethodGutterHandler implements GutterIconNavigationHandler<PsiIdentifier> {

        @Override
        public void navigate(MouseEvent mouseEvent, PsiIdentifier psiElement) {
            PsiMethod method = (PsiMethod) psiElement.getParent();
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                TestkitHelper.notify(psiElement.getProject(), NotificationType.WARNING, "Can't find class");
                return;
            }

            Project project = psiElement.getProject();
            String projectName = project.getName();
            String className = containingClass.getQualifiedName();
            String methodName = method.getName();

            // Filter apps by module dependency
            List<String> relatedAppNames = RuntimeHelper.getAppMetas(projectName).stream()
                    .filter(appMeta -> ToolHelper.isDependency(method, project, appMeta.getModule()))
                    .map(RuntimeHelper.AppMeta::getApp)
                    .toList();

            // Get all remote script apps (for TT dialog) filtered by module dependency
            List<RuntimeHelper.VisibleApp> allRemoteApps = getRemoteScriptApps(projectName).stream()
                    .filter(app -> relatedAppNames.contains(app.getAppName()))
                    .toList();
            
            // Get Arthas-enabled instances (with arthasPort) for jad filtered by module dependency
            List<RuntimeHelper.VisibleApp> arthasApps = getArthasEnabledApps(projectName).stream()
                    .filter(app -> relatedAppNames.contains(app.getAppName()))
                    .toList();

            // Create popup menu
            DefaultActionGroup actionGroup = new DefaultActionGroup();

            // 1. Arthas tt option (pass all remote apps, not just arthas-enabled ones)
            AnAction timeTunnelAction = new AnAction("[tt] Capture Method Calls", 
                    "Record method invocations and replay them for debugging", null) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    TimeTunnelDialog dialog = new TimeTunnelDialog(project, className, methodName, allRemoteApps);
                    dialog.show();
                }
            };
            actionGroup.add(timeTunnelAction);

            // 2. Arthas jad submenu
            DefaultActionGroup viewCodeGroup = new DefaultActionGroup("[jad] Decode Method", true);
            viewCodeGroup.getTemplatePresentation().setIcon(null);
            
            // Always show "Add Connection" option at the top (filtered by module dependency)
            AnAction addConnectionAction = new AnAction("Add Connection...", "Add a new remote connection", TestkitToolWindow.connectionIcon) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    com.testkit.view.TestkitToolWindowFactory.showConnectionConfigPopup(project, null, false, relatedAppNames);
                }
            };
            viewCodeGroup.add(addConnectionAction);
            
            // Add existing instances
            if (!arthasApps.isEmpty()) {
                viewCodeGroup.addSeparator();
                for (RuntimeHelper.VisibleApp app : arthasApps) {
                    String displayName = String.format("[%s] %s", app.getRemotePartition(), app.getRemoteIp());
                    AnAction viewAction = new AnAction(displayName, "Decompile method from " + displayName, AllIcons.Webreferences.Server) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            RemoteDecodeDialog dialog = new RemoteDecodeDialog(project, className, methodName, app);
                            dialog.show();
                        }
                    };
                    viewCodeGroup.add(viewAction);
                }
            }
            actionGroup.add(viewCodeGroup);

            // Show popup menu
            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance()
                    .createActionPopupMenu("ArthasToolsPopup", actionGroup).getComponent();
            popupMenu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
        }
    }

    /**
     * Class-level gutter icon click handler (View Remote Code only)
     */
    private static class ArthasClassGutterHandler implements GutterIconNavigationHandler<PsiIdentifier> {

        @Override
        public void navigate(MouseEvent mouseEvent, PsiIdentifier psiElement) {
            PsiClass psiClass = (PsiClass) psiElement.getParent();

            Project project = psiElement.getProject();
            String projectName = project.getName();
            String className = psiClass.getQualifiedName();

            // Filter apps by module dependency
            List<String> relatedAppNames = RuntimeHelper.getAppMetas(projectName).stream()
                    .filter(appMeta -> ToolHelper.isDependency(psiClass, project, appMeta.getModule()))
                    .map(RuntimeHelper.AppMeta::getApp)
                    .toList();

            // Get Arthas-enabled instances (with arthasPort) for jad filtered by module dependency
            List<RuntimeHelper.VisibleApp> arthasApps = getArthasEnabledApps(projectName).stream()
                    .filter(app -> relatedAppNames.contains(app.getAppName()))
                    .toList();

            // Create popup menu
            DefaultActionGroup actionGroup = new DefaultActionGroup();

            // Arthas jad submenu (class-level, no method name)
            DefaultActionGroup viewCodeGroup = new DefaultActionGroup("[jad] Decode Class", true);
            viewCodeGroup.getTemplatePresentation().setIcon(null);
            
            // Always show "Add Connection" option at the top (filtered by module dependency)
            AnAction addConnectionAction = new AnAction("Add Connection...", "Add a new remote connection", TestkitToolWindow.connectionIcon) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    com.testkit.view.TestkitToolWindowFactory.showConnectionConfigPopup(project, null, false, relatedAppNames);
                }
            };
            viewCodeGroup.add(addConnectionAction);
            
            // Add existing instances
            if (!arthasApps.isEmpty()) {
                viewCodeGroup.addSeparator();
                for (RuntimeHelper.VisibleApp app : arthasApps) {
                    String displayName = String.format("[%s] %s", app.getRemotePartition(), app.getRemoteIp());
                    AnAction viewAction = new AnAction(displayName, "Decompile class from " + displayName, AllIcons.Webreferences.Server) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                            // Pass null as methodName to decompile entire class
                            RemoteDecodeDialog dialog = new RemoteDecodeDialog(project, className, null, app);
                            dialog.show();
                        }
                    };
                    viewCodeGroup.add(viewAction);
                }
            }
            actionGroup.add(viewCodeGroup);

            // Show popup menu
            JBPopupMenu popupMenu = (JBPopupMenu) ActionManager.getInstance()
                    .createActionPopupMenu("ArthasClassToolsPopup", actionGroup).getComponent();
            popupMenu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
        }
    }
}
