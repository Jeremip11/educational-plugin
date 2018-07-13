package com.jetbrains.edu.learning

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.coursecreator.configuration.YamlFormatSynchronizer
import com.jetbrains.edu.learning.EduUtils.*
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.Section
import com.jetbrains.edu.learning.courseFormat.ext.configurator
import com.jetbrains.edu.learning.courseFormat.ext.findSourceDir
import com.jetbrains.edu.learning.courseFormat.tasks.Task

abstract class EduVirtualFileListener : VirtualFileListener {

  protected fun VirtualFile.fileInfo(project: Project): FileInfo? {
    if (path.contains(CCUtils.GENERATED_FILES_FOLDER)) return null
    if (YamlFormatSynchronizer.isConfigFile(this)) return null
    val courseDir = EduUtils.getCourseDir(project)
    if (!FileUtil.isAncestor(courseDir.path, path, true)) return null
    val course = StudyTaskManager.getInstance(project).course ?: return null
    if (course.configurator?.excludeFromArchive(path) == true) return null

    if (isDirectory) {
      EduUtils.getSection(this, course)?.let { return FileInfo.SectionDirectory(course, it) }
      EduUtils.getLesson(this, course)?.let { return FileInfo.LessonDirectory(course, it) }
      EduUtils.getTask(this, course)?.let { return FileInfo.TaskDirectory(course, it) }
    } else {
      val task = EduUtils.getTaskForFile(project, this) ?: return null
      val taskDir = task.getTaskDir(project) ?: return null

      val taskRelativePath = EduUtils.pathRelativeToTask(project, this)

      if (EduUtils.isTaskDescriptionFile(name)
          || taskRelativePath.contains(EduNames.WINDOW_POSTFIX)
          || taskRelativePath.contains(EduNames.WINDOWS_POSTFIX)
          || taskRelativePath.contains(EduNames.ANSWERS_POSTFIX)) {
        return null
      }

      if (isTestsFile(project, this)) return FileInfo.FileInTask(task, taskRelativePath, NewFileType.TEST_FILE)
      val sourceDir = task.findSourceDir(taskDir)
      if (sourceDir != null) {
        if (VfsUtilCore.isAncestor(sourceDir, this, true)) return FileInfo.FileInTask(task, taskRelativePath, NewFileType.TASK_FILE)
      }
      return FileInfo.FileInTask(task, taskRelativePath, NewFileType.ADDITIONAL_FILE)
    }

    return null
  }

  protected sealed class FileInfo {
    data class SectionDirectory(val course: Course, val section: Section) : FileInfo()
    data class LessonDirectory(val course: Course, val lesson: Lesson) : FileInfo()
    data class TaskDirectory(val course: Course, val task: Task) : FileInfo()
    data class FileInTask(val task: Task, val pathInTask: String, val type: NewFileType) : FileInfo()
  }

  protected enum class NewFileType {
    TASK_FILE,
    TEST_FILE,
    ADDITIONAL_FILE
  }
}
