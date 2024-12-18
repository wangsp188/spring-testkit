package com.fling.listener;

import com.fling.FlingHelper;
import com.fling.doc.DocHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlingProjectListener implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DumbService.getInstance(project).smartInvokeLater(() -> {
            try {
                DocHelper.refreshDoc(project);
                FlingHelper.refresh(project);
            } catch (Exception e) {
                FlingHelper.notify(project, NotificationType.ERROR, "Refresh project doc failed," + e.getClass().getSimpleName() + ", " + e.getMessage());
            }
        });
        return null;
    }
}