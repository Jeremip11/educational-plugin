package com.jetbrains.edu.learning;

import com.google.common.collect.Lists;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.util.DocumentUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import com.intellij.util.text.MarkdownUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils;
import com.jetbrains.edu.learning.editor.EduEditor;
import com.jetbrains.edu.learning.handlers.AnswerPlaceholderDeleteHandler;
import com.jetbrains.edu.learning.stepic.OAuthDialog;
import com.jetbrains.edu.learning.stepic.StepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import com.jetbrains.edu.learning.stepic.StepicUserWidget;
import com.jetbrains.edu.learning.twitter.TwitterPluginConfigurator;
import com.jetbrains.edu.learning.ui.taskDescription.TaskDescriptionToolWindow;
import com.jetbrains.edu.learning.ui.taskDescription.TaskDescriptionToolWindowFactory;
import com.petebevin.markdown.MarkdownProcessor;
import org.apache.commons.codec.binary.Base64;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.jetbrains.edu.learning.navigation.NavigationUtils.navigateToTask;

public class EduUtils {

  private EduUtils() {
  }

  public static final Comparator<StudyItem> INDEX_COMPARATOR = Comparator.comparingInt(StudyItem::getIndex);
  private static final Logger LOG = Logger.getInstance(EduUtils.class.getName());
  private static final String ourPrefix = "<html><head><script type=\"text/x-mathjax-config\">\n" +
                                          "            MathJax.Hub.Config({\n" +
                                          "                tex2jax: {\n" +
                                          "                    inlineMath: [ ['$','$'], [\"\\\\(\",\"\\\\)\"] ],\n" +
                                          "                    displayMath: [ ['$$','$$'], [\"\\\\[\",\"\\\\]\"] ],\n" +
                                          "                    processEscapes: true,\n" +
                                          "                    processEnvironments: true\n" +
                                          "                },\n" +
                                          "                displayAlign: 'center',\n" +
                                          "                \"HTML-CSS\": {\n" +
                                          "                    styles: {'#mydiv': {\"font-size\": %s}},\n" +
                                          "                    preferredFont: null,\n" +
                                          "                    linebreaks: { automatic: true }\n" +
                                          "                }\n" +
                                          "            });\n" +
                                          "</script><script type=\"text/javascript\"\n" +
                                          " src=\"http://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS_HTML-full\">\n" +
                                          " </script></head><body><div id=\"mydiv\">";

  private static final String ourPostfix = "</div></body></html>";

  public static void closeSilently(@Nullable final Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        // close silently
      }
    }
  }

  public static boolean isZip(String fileName) {
    return fileName.contains(".zip");
  }

  @Nullable
  public static <T> T getFirst(@NotNull final Iterable<T> container) {
    Iterator<T> iterator = container.iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    return iterator.next();
  }

  public static boolean indexIsValid(int index, @NotNull final Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static String getFileText(@Nullable final String parentDir, @NotNull final String fileName, boolean wrapHTML,
                                   @NotNull final String encoding) {
    final File inputFile = parentDir != null ? new File(parentDir, fileName) : new File(fileName);
    if (!inputFile.exists()) return null;
    final StringBuilder taskText = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), encoding));
      String line;
      while ((line = reader.readLine()) != null) {
        taskText.append(line).append("\n");
        if (wrapHTML) {
          taskText.append("<br>");
        }
      }
      return wrapHTML ? UIUtil.toHtml(taskText.toString()) : taskText.toString();
    }
    catch (IOException e) {
      LOG.info("Failed to get file text from file " + fileName, e);
    }
    finally {
      closeSilently(reader);
    }
    return null;
  }

  public static void updateAction(@NotNull final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    final Project project = e.getProject();
    if (project != null) {
      final EduEditor eduEditor = getSelectedStudyEditor(project);
      if (eduEditor != null) {
        presentation.setEnabledAndVisible(true);
      }
    }
  }

  public static void updateToolWindows(@NotNull final Project project) {
    final TaskDescriptionToolWindow taskDescriptionToolWindow = getStudyToolWindow(project);
    if (taskDescriptionToolWindow != null) {
      Task task = getTaskForCurrentSelectedFile(project);
      taskDescriptionToolWindow.updateTask(project, task);
      taskDescriptionToolWindow.updateCourseProgress(project);
    }
  }

  public static void initToolWindows(@NotNull final Project project) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    windowManager.getToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager().removeAllContents(false);
    TaskDescriptionToolWindowFactory factory = new TaskDescriptionToolWindowFactory();
    factory.createToolWindowContent(project, windowManager.getToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW));

  }

  @Nullable
  public static TaskDescriptionToolWindow getStudyToolWindow(@NotNull final Project project) {
    if (project.isDisposed()) return null;

    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW);
    if (toolWindow != null) {
      Content[] contents = toolWindow.getContentManager().getContents();
      for (Content content: contents) {
        JComponent component = content.getComponent();
        if (component != null && component instanceof TaskDescriptionToolWindow) {
          return (TaskDescriptionToolWindow)component;
        }
      }
    }
    return null;
  }

  public static void deleteFile(@Nullable final VirtualFile file) {
    if (file == null) {
      return;
    }
    try {
      file.delete(EduUtils.class);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }


  /**
   * shows pop up in the center of "check task" button in study editor
   */
  public static void showCheckPopUp(@NotNull final Project project, @NotNull final Balloon balloon) {
    final EduEditor eduEditor = getSelectedStudyEditor(project);
    Editor editor = eduEditor != null ? eduEditor.getEditor() : FileEditorManager.getInstance(project).getSelectedTextEditor();
    assert editor != null;
    balloon.show(computeLocation(editor), Balloon.Position.above);
    Disposer.register(project, balloon);
  }

  public static RelativePoint computeLocation(Editor editor){

    final Rectangle visibleRect = editor.getComponent().getVisibleRect();
    Point point = new Point(visibleRect.x + visibleRect.width + 10,
                            visibleRect.y + 10);
    return new RelativePoint(editor.getComponent(), point);
  }


  public static boolean isTestsFile(@NotNull Project project, @NotNull final String name) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return false;
    }
    EduConfigurator configurator = EduConfiguratorManager.forLanguage(course.getLanguageById());
    if (configurator == null) {
      return false;
    }
    String testFileName = configurator.getTestFileName();
    return name.equals(testFileName) ||
           name.startsWith(FileUtil.getNameWithoutExtension(testFileName)) && name.contains(EduNames.SUBTASK_MARKER);
  }

  @Nullable
  public static TaskFile getTaskFile(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return null;
    }
    VirtualFile taskDir = getTaskDir(file);
    if (taskDir == null) {
      return null;
    }
    //need this because of multi-module generation
    if (EduNames.SRC.equals(taskDir.getName())) {
      taskDir = taskDir.getParent();
      if (taskDir == null) {
        return null;
      }
    }
    final String taskDirName = taskDir.getName();
    if (taskDirName.contains(EduNames.TASK)) {
      final VirtualFile lessonDir = taskDir.getParent();
      if (lessonDir != null) {
        int lessonIndex = getIndex(lessonDir.getName(), EduNames.LESSON);
        List<Lesson> lessons = course.getLessons();
        if (!indexIsValid(lessonIndex, lessons)) {
          return null;
        }
        final Lesson lesson = lessons.get(lessonIndex);
        int taskIndex = getIndex(taskDirName, EduNames.TASK);
        final List<Task> tasks = lesson.getTaskList();
        if (!indexIsValid(taskIndex, tasks)) {
          return null;
        }
        final Task task = tasks.get(taskIndex);
        return task.getFile(pathRelativeToTask(file));
      }
    }
    return null;
  }

  public static void drawAllAnswerPlaceholders(Editor editor, TaskFile taskFile) {
    editor.getMarkupModel().removeAllHighlighters();
    final Project project = editor.getProject();
    if (project == null) return;
    if (!taskFile.isValid(editor.getDocument().getText())) return;
    final StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      final JBColor color = studyTaskManager.getColor(answerPlaceholder);
      AnswerPlaceholderPainter.drawAnswerPlaceholder(editor, answerPlaceholder, color);
    }

    final Document document = editor.getDocument();
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(document, new AnswerPlaceholderDeleteHandler(editor));
    AnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
  }

  @Nullable
  public static EduEditor getSelectedStudyEditor(@NotNull final Project project) {
    try {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final VirtualFile currentFile = FileEditorManagerEx.getInstanceEx(project).getCurrentFile();
        assert currentFile != null;
        final FileEditor[] fileEditors = FileEditorManagerEx.getInstanceEx(project).getEditors(currentFile);
        assert fileEditors.length == 1;
        final EduEditor eduEditor = new EduEditor(project, currentFile);
        Disposer.register(fileEditors[0], eduEditor);
        return eduEditor;
      }
      final FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(project).getSplitters().getCurrentWindow().
        getSelectedEditor().getSelectedEditorWithProvider().getFirst();
      if (fileEditor instanceof EduEditor) {
        return (EduEditor)fileEditor;
      }
    }
    catch (Exception e) {
      return null;
    }
    return null;
  }

  @Nullable
  public static Editor getSelectedEditor(@NotNull final Project project) {
    final EduEditor eduEditor = getSelectedStudyEditor(project);
    if (eduEditor != null) {
      return eduEditor.getEditor();
    }
    return null;
  }

  public static void deleteGuardedBlocks(@NotNull final Document document) {
    if (document instanceof DocumentImpl) {
      final DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      for (final RangeMarker block : blocks) {
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> document.removeGuardedBlock(block)));
      }
    }
  }

  public static boolean isRenameableOrMoveable(@NotNull final Project project, @NotNull final Course course, @NotNull final PsiElement element) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (project.getBaseDir().equals(virtualFile.getParent())) {
        return false;
      }
      TaskFile file = getTaskFile(project, virtualFile);
      if (file != null) {
        return false;
      }
      String name = virtualFile.getName();
      return !isTestsFile(project, name);
    }
    if (element instanceof PsiDirectory) {
      VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
      VirtualFile parent = virtualFile.getParent();
      if (parent == null) {
        return true;
      }
      if (project.getBaseDir().equals(parent)) {
        return false;
      }
      Lesson lesson = course.getLesson(parent.getName());
      if (lesson != null) {
        Task task = lesson.getTask(virtualFile.getName());
        if (task != null) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean canRenameOrMove(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null || project == null) {
      return false;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null || !course.isStudy()) {
      return false;
    }

    return !isRenameableOrMoveable(project, course, element);
  }

  public static String wrapTextToDisplayLatex(String taskTextFileHtml) {
    final String prefix = String.format(ourPrefix, EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize());
    return prefix + taskTextFileHtml + ourPostfix;
  }

  @Nullable
  public static TwitterPluginConfigurator getTwitterConfigurator(@NotNull final Project project) {
    TwitterPluginConfigurator[] extensions = TwitterPluginConfigurator.EP_NAME.getExtensions();
    for (TwitterPluginConfigurator extension: extensions) {
      if (extension.accept(project)) {
        return extension;
      }
    }
    return null;
  }

  @Nullable
  public static String getTaskText(@NotNull final Project project) {
    Task task = getCurrentTask(project);
    if (task == null) {
      return TaskDescriptionToolWindow.EMPTY_TASK_TEXT;
    }
    return task.getTaskDescription();
  }

  @Nullable
  public static TaskFile getSelectedTaskFile(@NotNull Project project) {
    VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
    TaskFile taskFile = null;
    for (VirtualFile file : files) {
      taskFile = getTaskFile(project, file);
      if (taskFile != null) {
        break;
      }
    }
    return taskFile;
  }

  @Nullable
  public static Task getCurrentTask(@NotNull final Project project) {
    final TaskFile taskFile = getSelectedTaskFile(project);
    if (taskFile != null) {
      return taskFile.getTask();
    }
    return !isAndroidStudio() ? null : findTaskFromTestFiles(project);
  }

  @Nullable
  public static Task getTaskForCurrentSelectedFile(@NotNull Project project) {
    VirtualFile[] files = FileEditorManager.getInstance(project).getSelectedFiles();
    Task task = null;
    for (VirtualFile file : files) {
      task = getTaskForFile(project, file);
      if (task != null) {
        break;
      }
    }
    return task;
  }

  @Nullable
  private static Task findTaskFromTestFiles(@NotNull Project project) {
    for (VirtualFile testFile : FileEditorManager.getInstance(project).getSelectedFiles()) {
      VirtualFile parentDir = testFile.getParent();
      if (EduNames.TEST.equals(parentDir.getName())) {
        VirtualFile srcDir = parentDir.getParent().findChild(EduNames.SRC);
        if (srcDir == null) {
          return null;
        }
        for (VirtualFile file : srcDir.getChildren()) {
          TaskFile taskFile = getTaskFile(project, file);
          if (taskFile != null) {
            return taskFile.getTask();
          }
        }
      }
    }
    return null;
  }

  public static boolean isStudyProject(@NotNull Project project) {
    return StudyTaskManager.getInstance(project).getCourse() != null;
  }

  public static boolean isStudentProject(@NotNull Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    return course != null && course.isStudy();
  }

  public static boolean hasJavaFx() {
    try {
      Class.forName("javafx.application.Platform");
      return true;
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Nullable
  public static Task getTask(@NotNull Project project, @NotNull VirtualFile taskVF) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return null;
    }
    VirtualFile lessonVF = taskVF.getParent();
    if (lessonVF == null) {
      return null;
    }
    Lesson lesson = course.getLesson(lessonVF.getName());
    if (lesson == null) {
      return null;
    }
    return lesson.getTask(taskVF.getName());
  }

  @Nullable
  public static VirtualFile getTaskDir(@NotNull VirtualFile taskFile) {
    VirtualFile parent = taskFile.getParent();

    while (parent != null) {
      String name = parent.getName();

      if (name.contains(EduNames.TASK) && parent.isDirectory()) {
        return parent;
      }
      if (EduNames.SRC.equals(name)) {
        return parent.getParent();
      }

      parent = parent.getParent();
    }
    return null;
  }

  @Nullable
  public static Task getTaskForFile(@NotNull Project project, @NotNull VirtualFile taskFile) {
    VirtualFile taskDir = getTaskDir(taskFile);
    if (taskDir == null) {
      return null;
    }
    return getTask(project, taskDir);
  }

  // supposed to be called under progress
  @Nullable
  public static <T> T execCancelable(@NotNull final Callable<T> callable) {
    final Future<T> future = ApplicationManager.getApplication().executeOnPooledThread(callable);

    while (!future.isCancelled() && !future.isDone()) {
      ProgressManager.checkCanceled();
      TimeoutUtil.sleep(500);
    }
    T result = null;
    try {
      result = future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn(e.getMessage());
    }
    return result;
  }

  @Nullable
  public static Task getTaskFromSelectedEditor(Project project) {
    final EduEditor editor = getSelectedStudyEditor(project);
    Task task = null;
    if (editor != null) {
      final TaskFile file = editor.getTaskFile();
      task = file.getTask();
    }
    return task;
  }

  public static String convertToHtml(@NotNull String content) {
    ArrayList<String> lines = ContainerUtil.newArrayList(content.split("\n|\r|\r\n"));
    if ((content.contains("<h") && content.contains("</h")) || (content.contains("<code>") && content.contains("</code>"))) {
      return content;
    }
    MarkdownUtil.replaceHeaders(lines);
    MarkdownUtil.replaceCodeBlock(lines);
    return new MarkdownProcessor().markdown(StringUtil.join(lines, "\n"));
  }

  @Nullable
  public static Document getDocument(String basePath, int lessonIndex, int taskIndex, String fileName) {
    String taskPath = FileUtil.join(basePath, EduNames.LESSON + lessonIndex, EduNames.TASK + taskIndex);
    VirtualFile taskFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.join(taskPath, fileName));
    if (taskFile == null) {
      taskFile = LocalFileSystem.getInstance().findFileByPath(FileUtil.join(taskPath, EduNames.SRC, fileName));
    }
    if (taskFile == null) {
      return null;
    }
    return FileDocumentManager.getInstance().getDocument(taskFile);
  }

  public static void showErrorPopupOnToolbar(@NotNull Project project, String content) {
    final Balloon balloon =
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(content, MessageType.ERROR, null).createBalloon();
    showCheckPopUp(project, balloon);
  }

  public static void selectFirstAnswerPlaceholder(@Nullable final EduEditor eduEditor, @NotNull final Project project) {
    if (eduEditor == null) return;
    final Editor editor = eduEditor.getEditor();
    IdeFocusManager.getInstance(project).requestFocus(editor.getContentComponent(), true);
    final List<AnswerPlaceholder> placeholders = eduEditor.getTaskFile().getActivePlaceholders();
    if (placeholders.isEmpty() || !eduEditor.getTaskFile().isValid(editor.getDocument().getText())) return;
    final AnswerPlaceholder placeholder = placeholders.get(0);
    Pair<Integer, Integer> offsets = getPlaceholderOffsets(placeholder, editor.getDocument());
    editor.getSelectionModel().setSelection(offsets.first, offsets.second);
    editor.getCaretModel().moveToOffset(offsets.first);
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
  }

  public static void registerStudyToolWindow(@Nullable final Course course, Project project) {
    if (course != null && EduNames.PYCHARM.equals(course.getCourseType())) {
      final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
      registerToolWindows(toolWindowManager, project);
      final ToolWindow studyToolWindow = toolWindowManager.getToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW);
      if (studyToolWindow != null) {
        studyToolWindow.show(null);
        initToolWindows(project);
      }
    }
  }

  private static void registerToolWindows(@NotNull final ToolWindowManager toolWindowManager, Project project) {
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW);
    if (toolWindow == null) {
      toolWindowManager.registerToolWindow(TaskDescriptionToolWindowFactory.STUDY_TOOL_WINDOW, true, ToolWindowAnchor.RIGHT, project, true);
    }
  }

  @Nullable public static AnswerPlaceholder getAnswerPlaceholder(int offset, List<AnswerPlaceholder> placeholders) {
    for (AnswerPlaceholder placeholder : placeholders) {
      int placeholderStart = placeholder.getOffset();
      int placeholderEnd = placeholderStart + placeholder.getRealLength();
      if (placeholderStart <= offset && offset <= placeholderEnd) {
        return placeholder;
      }
    }
    return null;
  }

  public static String pathRelativeToTask(VirtualFile file) {
    VirtualFile taskDir = getTaskDir(file);
    if (taskDir == null) return file.getName();
    VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
    if (srcDir != null) {
      taskDir = srcDir;
    }
    return FileUtil.getRelativePath(taskDir.getPath(), file.getPath(), '/');
  }

  public static Pair<Integer, Integer> getPlaceholderOffsets(@NotNull final AnswerPlaceholder answerPlaceholder,
                                                             @NotNull final Document document) {
    int startOffset = answerPlaceholder.getOffset();
    int delta = 0;
    final int length = answerPlaceholder.getRealLength();
    int nonSpaceCharOffset = DocumentUtil.getFirstNonSpaceCharOffset(document, startOffset, startOffset + length);
    if (nonSpaceCharOffset != startOffset) {
      delta = startOffset - nonSpaceCharOffset;
      startOffset = nonSpaceCharOffset;
    }
    final int endOffset = startOffset + length + delta;
    return Pair.create(startOffset, endOffset);
  }

  public static boolean isCourseValid(@Nullable Course course) {
    if (course == null) return false;
    if (course.isAdaptive()) {
      final List<Lesson> lessons = course.getLessons();
      if (lessons.size() == 1) {
        return !lessons.get(0).getTaskList().isEmpty();
      }
    }
    return true;
  }

  public static void createFromTemplate(@NotNull Project project,
                                        @NotNull VirtualFile taskDirectory,
                                        @NotNull String name) {
    FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(name);
    if (template == null) {
      LOG.info("Template " + name + " wasn't found");
      return;
    }
    try {
      GeneratorUtils.createChildFile(taskDirectory, name, template.getText());
    } catch (IOException e) {
      LOG.error(e);
    }
  }
  public static void openFirstTask(@NotNull final Course course, @NotNull final Project project) {
    LocalFileSystem.getInstance().refresh(false);
    final Lesson firstLesson = getFirst(course.getLessons());
    if (firstLesson == null) return;
    final Task firstTask = getFirst(firstLesson.getTaskList());
    if (firstTask == null) return;
    final VirtualFile taskDir = firstTask.getTaskDir(project);
    if (taskDir == null) return;
    final Map<String, TaskFile> taskFiles = firstTask.getTaskFiles();
    VirtualFile activeVirtualFile = null;
    for (Map.Entry<String, TaskFile> entry : taskFiles.entrySet()) {
      final String relativePath = entry.getKey();
      final TaskFile taskFile = entry.getValue();
      taskDir.refresh(false, true);
      final VirtualFile virtualFile = taskDir.findFileByRelativePath(relativePath);
      if (virtualFile != null) {
        if (!taskFile.getActivePlaceholders().isEmpty()) {
          activeVirtualFile = virtualFile;
        }
      }
    }
    if (activeVirtualFile != null) {
      final PsiFile file = PsiManager.getInstance(project).findFile(activeVirtualFile);
      ProjectView.getInstance(project).select(file, activeVirtualFile, false);
      final FileEditor[] editors = FileEditorManager.getInstance(project).openFile(activeVirtualFile, true);
      if (editors.length == 0) {
        return;
      }
      final FileEditor studyEditor = editors[0];
      if (studyEditor instanceof EduEditor) {
        selectFirstAnswerPlaceholder((EduEditor)studyEditor, project);
      }
      FileEditorManager.getInstance(project).openFile(activeVirtualFile, true);
    }
    else {
      String first = getFirst(taskFiles.keySet());
      if (first != null) {
        NewVirtualFile firstFile = ((VirtualDirectoryImpl)taskDir).refreshAndFindChild(first);
        if (firstFile != null) {
          FileEditorManager.getInstance(project).openFile(firstFile, true);
        }
      }
    }
  }

  public static void navigateToStep(@NotNull Project project, @NotNull Course course, int stepId) {
    if (stepId == 0 || course.isAdaptive()) {
      return;
    }
    Task task = getTask(course, stepId);
    if (task != null) {
      navigateToTask(project, task);
    }
  }

  @Nullable
  private static Task getTask(@NotNull Course course, int stepId) {
    for (Lesson lesson : course.getLessons()) {
      Task task = lesson.getTask(stepId);
      if (task != null) {
        return task;
      }
    }
    return null;
  }

  @Nullable
  public static StepicUserWidget getStepicWidget() {
    JFrame frame = WindowManager.getInstance().findVisibleFrame();
    if (frame instanceof IdeFrameImpl) {
      return (StepicUserWidget)((IdeFrameImpl)frame).getStatusBar().getWidget(StepicUserWidget.ID);
    }
    return null;
  }

  public static void showOAuthDialog() {
    OAuthDialog dialog = new OAuthDialog();
    if (dialog.showAndGet()) {
      StepicUser user = dialog.getStepicUser();
      EduSettings.getInstance().setUser(user);
    }
  }

  public static File getBundledCourseRoot(final String courseName, Class clazz) {
    @NonNls String jarPath = PathUtil.getJarPathForClass(clazz);
    if (jarPath.endsWith(".jar")) {
      final File jarFile = new File(jarPath);
      File pluginBaseDir = jarFile.getParentFile();
      File coursesDir = new File(pluginBaseDir, "courses");

      if (!coursesDir.exists()) {
        if (!coursesDir.mkdir()) {
          LOG.info("Failed to create courses dir");
          return coursesDir;
        }
      }
      try {
        ZipUtil.extract(jarFile, pluginBaseDir, (dir, name) -> name.equals(courseName));
      } catch (IOException e) {
        LOG.info("Failed to extract default course", e);
      }
      return coursesDir;
    }
    return new File(jarPath, "courses");
  }

  /**
   * Save current description into task if `StudyToolWindow` in editing mode
   * and exit from this mode. Otherwise do nothing.
   *
   * @param project current project
   */
  public static void saveToolWindowTextIfNeeded(@NotNull Project project) {
    TaskDescriptionToolWindow toolWindow = getStudyToolWindow(project);
    TaskDescriptionToolWindow.StudyToolWindowMode toolWindowMode = StudyTaskManager.getInstance(project).getToolWindowMode();
    if (toolWindow != null && toolWindowMode == TaskDescriptionToolWindow.StudyToolWindowMode.EDITING) {
      toolWindow.leaveEditingMode(project);
    }
  }

  public static void enableAction(@NotNull final AnActionEvent event, boolean isEnable) {
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(isEnable);
    presentation.setEnabled(isEnable);
  }

  /**
   * Gets number index in directory names like "task1", "lesson2"
   *
   * @param fullName    full name of directory
   * @param logicalName part of name without index
   * @return index of object
   */
  public static int getIndex(@NotNull final String fullName, @NotNull final String logicalName) {
    if (!fullName.startsWith(logicalName)) {
      return -1;
    }
    try {
      return Integer.parseInt(fullName.substring(logicalName.length())) - 1;
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private static void replaceWithTaskText(Document studentDocument, AnswerPlaceholder placeholder, int toSubtaskIndex) {
    AnswerPlaceholderSubtaskInfo info = placeholder.getSubtaskInfos().get(toSubtaskIndex);
    if (info == null) {
      return;
    }
    String replacementText;
    if (Collections.min(placeholder.getSubtaskInfos().keySet()) == toSubtaskIndex) {
      replacementText = info.getPlaceholderText();
    }
    else {
      Integer max = Collections.max(ContainerUtil.filter(placeholder.getSubtaskInfos().keySet(), i -> i < toSubtaskIndex));
      replacementText = placeholder.getSubtaskInfos().get(max).getPossibleAnswer();
    }
    replaceAnswerPlaceholder(studentDocument, placeholder, placeholder.getVisibleLength(toSubtaskIndex), replacementText);
  }

  @Nullable
  public static TaskFile createStudentFile(Project project, VirtualFile answerFile, @Nullable Task task, int targetSubtaskIndex) {
    try {
      if (task == null) {
        task = getTaskForFile(project, answerFile);
        if (task == null) {
          return null;
        }
        task = task.copy();
      }
      TaskFile taskFile = task.getTaskFile(pathRelativeToTask(answerFile));
      if (taskFile == null) {
        return null;
      }
      if (isImage(taskFile.name)) {
        taskFile.text = Base64.encodeBase64String(answerFile.contentsToByteArray());
        return taskFile;
      }
      Document document = FileDocumentManager.getInstance().getDocument(answerFile);
      if (document == null) {
        return null;
      }
      FileDocumentManager.getInstance().saveDocument(document);
      final LightVirtualFile studentFile = new LightVirtualFile("student_task", PlainTextFileType.INSTANCE, document.getText());
      Document studentDocument = FileDocumentManager.getInstance().getDocument(studentFile);
      if (studentDocument == null) {
        return null;
      }
      EduDocumentListener listener = new EduDocumentListener(taskFile, false);
      studentDocument.addDocumentListener(listener);
      taskFile.setTrackLengths(false);
      for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
        if (task instanceof TaskWithSubtasks) {
          int fromSubtask = ((TaskWithSubtasks)task).getActiveSubtaskIndex();
          placeholder.switchSubtask(studentDocument, fromSubtask, targetSubtaskIndex);
        }
      }
      for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
        replaceWithTaskText(studentDocument, placeholder, targetSubtaskIndex);
      }
      taskFile.setTrackChanges(true);
      studentDocument.removeDocumentListener(listener);
      taskFile.text = studentDocument.getImmutableCharSequence().toString();
      return taskFile;
    }
    catch (IOException e) {
      LOG.error("Failed to convert answer file to student one");
    }

    return null;
  }

  @Nullable
  public static String getTextFromInternalTemplate(@NotNull String templateName) {
    FileTemplate template = FileTemplateManager.getDefaultInstance().findInternalTemplate(templateName);
    if (template == null) {
      LOG.info("Failed to obtain internal template: " + templateName);
      return null;
    }
    return template.getText();
  }

  public static void runUndoableAction(Project project, String name, UndoableAction action, UndoConfirmationPolicy confirmationPolicy) {
    new WriteCommandAction(project, name) {
      protected void run(@NotNull final Result result) throws Throwable {
        action.redo();
        UndoManager.getInstance(project).undoableActionPerformed(action);
      }

      @Override
      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return confirmationPolicy;
      }
    }.execute();
  }

  public static boolean isAndroidStudio() {
    return "AndroidStudio".equals(PlatformUtils.getPlatformPrefix());
  }

  public static void runUndoableAction(Project project, String name, UndoableAction action) {
    runUndoableAction(project, name, action, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
  }

  public static boolean isImage(String fileName) {
    final String[] readerFormatNames = ImageIO.getReaderFormatNames();
    for (@NonNls String format : readerFormatNames) {
      final String ext = format.toLowerCase();
      if (fileName.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static Task getTask(@NotNull final VirtualFile directory, @NotNull final Course course) {
    VirtualFile lessonDir = directory.getParent();
    if (lessonDir == null) {
      return null;
    }
    Lesson lesson = course.getLesson(lessonDir.getName());
    if (lesson == null) {
      return null;
    }
    return lesson.getTask(directory.getName());
  }

  static void deleteWindowsFile(@NotNull final VirtualFile taskDir, @NotNull final String name) {
    final VirtualFile fileWindows = taskDir.findChild(name);
    if (fileWindows != null && fileWindows.exists()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          fileWindows.delete(taskDir);
        }
        catch (IOException e) {
          LOG.warn("Tried to delete non existed _windows file");
        }
      });
    }
  }

  public static void deleteWindowDescriptions(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile virtualFile = taskDir.findFileByRelativePath(name);
      if (virtualFile == null) {
        continue;
      }
      String windowsFileName = virtualFile.getNameWithoutExtension() + EduNames.WINDOWS_POSTFIX;
      VirtualFile parentDir = virtualFile.getParent();
      deleteWindowsFile(parentDir, windowsFileName);
    }
  }

  public static void replaceAnswerPlaceholder(@NotNull final Document document,
                                              @NotNull final AnswerPlaceholder answerPlaceholder,
                                              int length,
                                              String replacementText) {
    final int offset = answerPlaceholder.getOffset();
    CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      document.replaceString(offset, offset + length, replacementText);
      FileDocumentManager.getInstance().saveDocument(document);
    }));
  }

  public static void synchronize() {
    FileDocumentManager.getInstance().saveAllDocuments();
    SaveAndSyncHandler.getInstance().refreshOpenFiles();
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static VirtualFile flushWindows(@NotNull final TaskFile taskFile, @NotNull final VirtualFile file) {
    final VirtualFile taskDir = file.getParent();
    VirtualFile fileWindows = null;
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      LOG.debug("Couldn't flush windows");
      return null;
    }
    if (taskDir != null) {
      final String name = file.getNameWithoutExtension() + EduNames.WINDOWS_POSTFIX;
      deleteWindowsFile(taskDir, name);
      PrintWriter printWriter = null;
      try {
        fileWindows = taskDir.createChildData(taskFile, name);
        printWriter = new PrintWriter(new FileOutputStream(fileWindows.getPath()));
        for (AnswerPlaceholder answerPlaceholder : taskFile.getActivePlaceholders()) {
          int length = answerPlaceholder.getRealLength();
          int start = answerPlaceholder.getOffset();
          final String windowDescription = document.getText(new TextRange(start, start + length));
          printWriter.println("#educational_plugin_window = " + windowDescription);
        }
        ApplicationManager.getApplication().runWriteAction(() -> FileDocumentManager.getInstance().saveDocument(document));
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        if (printWriter != null) {
          printWriter.close();
        }
        synchronize();
      }
    }
    return fileWindows;
  }

  /**
   * @return null if process was canceled, otherwise not null list of courses
   */
  @Nullable
  public static List<Course> getCoursesUnderProgress() {
    try {
      return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
        List<Course> courses = execCancelable(() -> StepicConnector.getCourses(EduSettings.getInstance().getUser()));
        if (courses == null) return Lists.newArrayList();
        List<Course> bundledCourses = getBundledCourses();
        for (Course bundledCourse : bundledCourses) {
          if (courses.stream().anyMatch(course -> course.getName().equals(bundledCourse.getName()))) {
            continue;
          }
          courses.add(bundledCourse);
        }
        Collections.sort(courses, (c1, c2) -> Boolean.compare(c1.isAdaptive(), c2.isAdaptive()));
        return courses;
      }, "Getting Available Courses", true, null);
    } catch (ProcessCanceledException e) {
      return null;
    } catch (RuntimeException e) {
      return Lists.newArrayList();
    }
  }

  @NotNull
  private static List<Course> getBundledCourses() {
    final ArrayList<Course> courses = new ArrayList<>();
    final List<LanguageExtensionPoint<EduConfigurator<?>>> extensions = EduConfiguratorManager.allExtensions();
    for (LanguageExtensionPoint<EduConfigurator<?>> extension : extensions) {
      final EduConfigurator configurator = extension.getInstance();
      //noinspection unchecked
      final List<String> paths = configurator.getBundledCoursePaths();
      for (String path : paths) {
        final Course localCourse = getLocalCourse(path);
        if (localCourse != null) {
          courses.add(localCourse);
        }
      }
    }
    return courses;
  }

  @Nullable
  public static Course getLocalCourse(@NotNull final String zipFilePath) {
    try {
      final JBZipFile zipFile = new JBZipFile(zipFilePath);
      final JBZipEntry entry = zipFile.getEntry(EduNames.COURSE_META_FILE);
      if (entry == null) {
        return null;
      }
      byte[] bytes = entry.getData();
      final String jsonText = new String(bytes, CharsetToolkit.UTF8_CHARSET);
      Gson gson = new GsonBuilder()
          .registerTypeAdapter(Task.class, new SerializationUtils.Json.TaskAdapter())
          .registerTypeAdapter(Lesson.class, new SerializationUtils.Json.LessonAdapter())
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();
      return gson.fromJson(jsonText, Course.class);
    }
    catch (IOException e) {
      LOG.error("Failed to unzip course archive");
      LOG.error(e);
    }
    return null;
  }
}