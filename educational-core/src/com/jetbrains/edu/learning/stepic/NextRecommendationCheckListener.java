package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.checker.CheckListener;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

public class NextRecommendationCheckListener implements CheckListener {

  private StudyStatus myStatusBeforeCheck;

  @Override
  public void beforeCheck(@NotNull Project project, @NotNull Task task) {
    myStatusBeforeCheck = task.getStatus();
  }

  @Override
  public void afterCheck(@NotNull Project project, @NotNull Task task) {
    Course course = task.getLesson().getCourse();
    if (!(course instanceof RemoteCourse && course.isAdaptive())) {
      return;
    }
    if (myStatusBeforeCheck == StudyStatus.Solved) {
      return;
    }
    StudyStatus statusAfterCheck = task.getStatus();
    if (statusAfterCheck != StudyStatus.Solved) {
      return;
    }
    ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Backgroundable(project, StepicAdaptiveConnector.LOADING_NEXT_RECOMMENDATION, false,
                                                                                            PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        StepicAdaptiveConnector.addNextRecommendedTask(project, task.getLesson(), indicator, StepicAdaptiveConnector.NEXT_RECOMMENDATION_REACTION);
      }
    });
  }
}
