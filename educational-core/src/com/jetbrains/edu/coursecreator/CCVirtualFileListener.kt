package com.jetbrains.edu.coursecreator

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.util.Function
import com.jetbrains.edu.coursecreator.configuration.YamlFormatSynchronizer
import com.jetbrains.edu.coursecreator.stepik.StepikCourseChangeHandler
import com.jetbrains.edu.learning.EduVirtualFileListener
import com.jetbrains.edu.learning.EduVirtualFileListener.NewFileType.*
import com.jetbrains.edu.learning.courseFormat.ext.configurator

class CCVirtualFileListener(private val project: Project) : EduVirtualFileListener() {

  override fun fileCreated(event: VirtualFileEvent) {
    if (project.isDisposed) return
    val createdFile = event.file
    val (task, pathInTask, type) = createdFile.fileInfo(project) as? FileInfo.FileInTask ?: return
    // We intentionally don't load actual text of files
    // because we load actual text of files only when we really need it:
    // course archive creation, loading to Stepik, etc.
    //
    // We don't add info about new file if it's already in task.
    // Generally, we need such checks because of tests.
    // In real life, we create project files before project opening and virtual file listener initialization,
    // so we shouldn't get such situation.
    // But in tests, we use `courseWithFiles` which triggers virtual file listener because
    // sometimes listener is initialized in `setUp` method and `courseWithFiles` creates course files after it.
    // In such cases, we need these checks to prevent replacing correct task file
    // with empty (without placeholders, hints, etc.) one.
    when (type) {
      TASK_FILE -> {
        if (task.getTaskFile(pathInTask) == null) {
          task.addTaskFile(pathInTask)
        }
      }
      TEST_FILE -> {
        if (pathInTask !in task.testsText) {
          task.addTestsTexts(pathInTask, "")
        }
      }
      ADDITIONAL_FILE -> {
        if (pathInTask !in task.additionalFiles) {
          task.addAdditionalFile(pathInTask, "")
        }
      }
    }
    YamlFormatSynchronizer.saveItem(task)
    StepikCourseChangeHandler.changed(task)
  }

  override fun fileDeleted(event: VirtualFileEvent) {
    if (project.isDisposed) return
    val removedFile = event.file
    val fileInfo = removedFile.fileInfo(project) ?: return
    
    when (fileInfo) {
      is FileInfo.SectionDirectory -> deleteSection(fileInfo, removedFile)
      is FileInfo.LessonDirectory -> deleteLesson(fileInfo, removedFile)
      is FileInfo.TaskDirectory -> deleteTask(fileInfo, removedFile)
      is FileInfo.FileInTask -> deleteFileInTask(fileInfo)
    }
  }

  private fun deleteLesson(info: FileInfo.LessonDirectory, removedLessonFile: VirtualFile) {
    val (course, removedLesson) = info
    val section = removedLesson.section
    val parentDir = removedLessonFile.parent
    if (section != null) {
      CCUtils.updateHigherElements(parentDir.children, Function { section.getLesson(it.name) }, removedLesson.index, -1)
      section.removeLesson(removedLesson)
      StepikCourseChangeHandler.contentChanged(section)
      YamlFormatSynchronizer.saveItem(section)
    } else {
      CCUtils.updateHigherElements(parentDir.children, Function { course.getItem(it.name) }, removedLesson.index, -1)
      course.removeLesson(removedLesson)
      StepikCourseChangeHandler.contentChanged(course)
      YamlFormatSynchronizer.saveItem(course)
    }
  }

  private fun deleteSection(info: FileInfo.SectionDirectory, removedFile: VirtualFile) {
    val (course, removedSection) = info
    val parentDir = removedFile.parent
    CCUtils.updateHigherElements(parentDir.children, Function { course.getItem(it.name) }, removedSection.index, -1)
    course.removeSection(removedSection)
    YamlFormatSynchronizer.saveItem(course)
    StepikCourseChangeHandler.contentChanged(course)
  }

  private fun deleteTask(info: FileInfo.TaskDirectory, removedTask: VirtualFile) {
    val (course, task) = info
    val lessonDir = removedTask.parent ?: error("`$removedTask` parent shouldn't be null")
    val lesson = task.lesson
    CCUtils.updateHigherElements(lessonDir.children, Function { lesson.getTask(it.name) }, task.index, -1)
    lesson.getTaskList().remove(task)
    YamlFormatSynchronizer.saveItem(lesson)
    StepikCourseChangeHandler.contentChanged(lesson)

    val configurator = course.configurator
    if (configurator != null) {
      ApplicationManager.getApplication().invokeLater { configurator.courseBuilder.refreshProject(project) }
    }
  }

  private fun deleteFileInTask(info: FileInfo.FileInTask) {
    val (task, pathInTask, type) = info
    when (type) {
      TASK_FILE -> task.getTaskFiles().remove(pathInTask)
      TEST_FILE -> task.testsText.remove(pathInTask)
      ADDITIONAL_FILE -> task.additionalFiles.remove(pathInTask)
    }
    YamlFormatSynchronizer.saveItem(task)
    StepikCourseChangeHandler.changed(task)
  }
}
