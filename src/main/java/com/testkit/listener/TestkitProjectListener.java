package com.testkit.listener;

import com.testkit.TestkitHelper;
import com.testkit.coding_guidelines.CodingGuidelinesHelper;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestkitProjectListener implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        DumbService.getInstance(project).smartInvokeLater(() -> {
            try {
                new Thread(() -> {
                    while (true){
                        CodingGuidelinesHelper.refreshDoc(project);
                        TestkitHelper.refresh(project);
                        try {
                            Thread.sleep(24*3600*1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }).start();
            } catch (Exception e) {
                TestkitHelper.notify(project, NotificationType.ERROR, "Refresh coding-guidelines failed," + e.getClass().getSimpleName() + ", " + e.getMessage());
            }
        });
        return null;
    }
}