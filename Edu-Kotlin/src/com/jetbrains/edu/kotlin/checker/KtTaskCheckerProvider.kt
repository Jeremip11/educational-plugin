package com.jetbrains.edu.kotlin.checker

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.edu.kotlin.KtConfigurator.Companion.TESTS_KT
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checker.CheckUtils
import com.jetbrains.edu.learning.checker.gradle.*
import com.jetbrains.edu.learning.courseFormat.ext.testDir
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import org.jetbrains.kotlin.idea.run.KotlinJUnitRunConfigurationProducer
import org.jetbrains.kotlin.idea.run.KotlinRunConfigurationProducer
import org.jetbrains.kotlin.psi.KtElement

class KtTaskCheckerProvider : GradleTaskCheckerProvider() {

  override fun getEduTaskChecker(task: EduTask, project: Project): GradleEduTaskChecker {
    return object : GradleEduTaskChecker(task, project) {
      override fun check(): CheckResult {


        val testDirName = task.testDir!!
        val testDir = task.getTaskDir(project)!!.findFileByRelativePath(testDirName)!!
        val testFile = testDir.findChild(TESTS_KT)!!
        val testName = runInEdtAndGet {
          runReadAction {
            val psiFile = PsiManager.getInstance(project).findFile(testFile)!!
            KotlinJUnitRunConfigurationProducer.getTestClass(psiFile)!!.qualifiedName!!
          }
        }
        val taskName = "${getGradleProjectName(task)}:test"
        val cmd = generateGradleCommandLine(
          project,
          "test",
          *arrayOf("--tests", testName)
        ) ?: return CheckResult.FAILED_TO_CHECK

        return try {
          return parseTestsOutput(cmd.createProcess(), cmd.commandLineString, "test")
        }
        catch (e: ExecutionException) {
          Logger.getInstance(GradleEduTaskChecker::class.java).info(CheckUtils.FAILED_TO_CHECK_MESSAGE, e)
          CheckResult.FAILED_TO_CHECK
        }
      }
    }
  }

  override fun mainClassForFile(project: Project, file: VirtualFile): String? {
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val ktElements = PsiTreeUtil.findChildrenOfType(psiFile, KtElement::class.java)
    val container = KotlinRunConfigurationProducer.getEntryPointContainer(ktElements.first())
    return KotlinRunConfigurationProducer.getStartClassFqName(container)
  }
}
