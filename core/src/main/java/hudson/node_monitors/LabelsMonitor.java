package hudson.node_monitors;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.remoting.Callable;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Displays the labels that are defined for an agent.
 */
public class LabelsMonitor extends NodeMonitor
{

  private boolean includeDynamic = false;
  private int maxLabelCount = 5;

  public LabelsMonitor() {}

  @DataBoundConstructor
  public LabelsMonitor(boolean includeDynamic, int maxLabelCount)
  {
    this.includeDynamic = includeDynamic;
    if (maxLabelCount == 0) {
      maxLabelCount = 1;
    }
    this.maxLabelCount = maxLabelCount;
  }

  @Override
  public String getColumnCaption()
  {
    if (!isIgnored())
      return getDescriptor().getDisplayName();
    return null;
  }

  public boolean getIncludeDynamic()
  {
    return includeDynamic;
  }

  public int getMaxLabelCount()
  {
    return maxLabelCount;
  }

  @Override
  public Object data(Computer c) {
    Node n = c.getNode();
    Set<LabelAtom> data = Collections.emptySet();
    if (n != null)
    {
      data = Label.parse(n.getLabelString());
      if (includeDynamic)
      {
        data.addAll(n.getDynamicLabels());
      }
    }
    return new Data(c.getName(), data);
  }

  @Restricted(DoNotUse.class)
  @ExportedBean(defaultVisibility = 0)
  public static class Data {
    private final String computerName;
    private final Set<LabelAtom> labels;

    private Data(String computerName, Set<LabelAtom> labels) {
      this.computerName = computerName;
      this.labels = labels;
    }

    public String getComputerName()
    {
      return computerName;
    }

    public Set<LabelAtom> getLabels()
    {
      return Collections.unmodifiableSet(labels);
    }
  }

  @Extension
  @Symbol("labels")
  public static class DescriptorImpl extends AbstractAsyncNodeMonitorDescriptor<Set<LabelAtom>> {

    @Override
    public String getDisplayName() {
        return Messages.LabelsMonitor_DisplayName();
    }

    @Override
    protected Callable<Set<LabelAtom>, IOException> createCallable(Computer c)
    {
      return null;
    }
  }
}
