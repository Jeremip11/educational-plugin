package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class EduPsiNode extends PsiDirectoryNode implements EduNode {

  public EduPsiNode(@NotNull final Project project,
                    PsiDirectory value,
                    ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  protected static void updatePresentation(PresentationData data, String name, JBColor color, Icon icon, @Nullable String additionalInfo) {
    data.clearText();
    data.addText(name, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color));
    if (additionalInfo != null) {
      data.addText(" (" + additionalInfo + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    data.setIcon(icon);
  }

  @Override
  protected boolean hasProblemFileBeneath() {
    return false;
  }

  @Override
  public String getTestPresentation() {
    final PresentationData presentation = getPresentation();
    final List<ColoredFragment> fragments = presentation.getColoredText();
    final StringBuilder builder = new StringBuilder();
    for (ColoredFragment fragment : fragments) {
      builder.append(fragment.getText());
    }
    return getClass().getSimpleName() + " " + builder.toString();
  }
}