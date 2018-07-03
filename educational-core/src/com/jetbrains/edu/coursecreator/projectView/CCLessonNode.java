package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.projectView.LessonNode;
import com.jetbrains.edu.learning.projectView.TaskNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCLessonNode extends LessonNode {
  public CCLessonNode(@NotNull Project project,
                      PsiDirectory value,
                      ViewSettings viewSettings,
                      @NotNull Lesson lesson) {
    super(project, value, viewSettings, lesson);
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    AbstractTreeNode node = super.modifyChildNode(childNode);
    if (node != null) {
      return node;
    }
    return CCCourseViewUtil.produceConfigNodeOrNull(myProject, myViewSettings, childNode);
  }

  @NotNull
  @Override
  protected TaskNode createTaskNode(PsiDirectory directory, Task task) {
    return new CCTaskNode(myProject, directory, myViewSettings, task);
  }
}
