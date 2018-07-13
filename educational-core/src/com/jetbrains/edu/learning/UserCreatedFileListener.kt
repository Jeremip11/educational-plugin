package com.jetbrains.edu.learning

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileEvent

class UserCreatedFileListener(private val project: Project) : EduVirtualFileListener() {

  override fun fileCreated(event: VirtualFileEvent) {
    if (project.isDisposed) return
    val (task, pathInTask, type) = event.file.fileInfo(project) as? FileInfo.FileInTask ?: return
    if (type == NewFileType.TASK_FILE && task.getTaskFile(pathInTask) == null) {
      task.addTaskFile(pathInTask).isUserCreated = true
    }
  }

  override fun fileDeleted(event: VirtualFileEvent) {
    if (project.isDisposed) return
    val (task, pathInTask, type) = event.file.fileInfo(project) as? FileInfo.FileInTask ?: return
    if (type == NewFileType.TASK_FILE) {
      task.getTaskFiles().remove(pathInTask)
    }
  }
}
