package com.jetbrains.edu.coursecreator.actions.stepik;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

public class CCShowChangedFiles extends DumbAwareAction {

  public CCShowChangedFiles() {
    super("Compare with Course on Stepik", "Show changed files comparing to the course on Stepik", null);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }

    Course course = StudyTaskManager.getInstance(project).getCourse();
    assert course != null;

    StringBuilder message = buildChangeMessage(course);
    Messages.showInfoMessage(message.toString(), "Course Changes");
  }

  // public for test
  @NotNull
  public static StringBuilder buildChangeMessage(Course course) {
    StringBuilder message = new StringBuilder();
    if (course.getStepikChangeStatus() != StepikChangeStatus.UP_TO_DATE) {
      appendChangeLine("", course, message);
    }

    for (StudyItem item : course.getItems()) {
      if (item.getStepikChangeStatus() != StepikChangeStatus.UP_TO_DATE) {
        appendChangeLine("", item, message);
      }

      if (item instanceof Section) {
        for (Lesson lesson : ((Section)item).getLessons()) {
          if (lesson.getStepikChangeStatus() != StepikChangeStatus.UP_TO_DATE) {
            appendChangeLine(item.getName() + "/", lesson, message);
          }
          for (Task task : lesson.taskList) {
            if (task.getStepikChangeStatus() != StepikChangeStatus.UP_TO_DATE) {
              String parentsLine = item.getName() + "/" + lesson.getName() + "/";
              appendChangeLine(parentsLine, task, message);
            }
          }
        }
      }

      if (item instanceof Lesson) {
        for (Task task : ((Lesson)item).taskList) {
          if (task.getStepikChangeStatus() != StepikChangeStatus.UP_TO_DATE) {
            appendChangeLine(item.getName() + "/", task, message);
          }
        }
      }
    }
    return message;
  }

  private static void appendChangeLine(@NotNull String parentsLine, @NotNull StudyItem item, @NotNull StringBuilder stringBuilder) {
    stringBuilder
      .append(parentsLine)
      .append(item.getName())
      .append(" ")
      .append(item.getStepikChangeStatus())
      .append("\n");
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course instanceof RemoteCourse && !course.isStudy()) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }
}
