package com.fling.listener;

import com.fling.FlingHelper;
import com.fling.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
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
                new Thread(() -> {
                    while (true){
                        CodingGuidelinesHelper.refreshDoc(project);
                        FlingHelper.refresh(project);
                        try {
                            Thread.sleep(24*3600*1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }).start();
            } catch (Exception e) {
                FlingHelper.notify(project, NotificationType.ERROR, "Refresh coding-guidelines failed," + e.getClass().getSimpleName() + ", " + e.getMessage());
            }
        });
        return null;
    }
}