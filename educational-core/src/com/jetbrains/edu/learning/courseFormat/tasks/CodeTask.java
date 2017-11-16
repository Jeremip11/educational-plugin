package com.jetbrains.edu.learning.courseFormat.tasks;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.EduSettings;
import com.jetbrains.edu.learning.actions.CheckAction;
import com.jetbrains.edu.learning.checker.CheckResult;
import com.jetbrains.edu.learning.checker.CheckUtils;
import com.jetbrains.edu.learning.checker.TaskChecker;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.stepic.StepicAdaptiveConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;

public class CodeTask extends Task {
  @SuppressWarnings("unused") //used for deserialization
  public CodeTask() {}

  public CodeTask(@NotNull final String name) {
    super(name);
  }

  @Override
  public String getTaskType() {
    return "code";
  }

  @Override
  public TaskChecker getChecker(@NotNull Project project) {
    return new TaskChecker<CodeTask>(this, project) {
      @Override
      public void onTaskFailed(@NotNull String message) {
        super.onTaskFailed("Wrong solution");
        CheckUtils.showTestResultsToolWindow(myProject, message);
      }

      @Override
      public CheckResult checkOnRemote() {
        StepicUser user = EduSettings.getInstance().getUser();
        if (user == null) {
          return new CheckResult(StudyStatus.Unchecked, CheckAction.FAILED_CHECK_LAUNCH);
        }
        return StepicAdaptiveConnector.checkCodeTask(myProject, myTask, user);
      }
    };
  }
}
