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
import org.kohsuke.stapler.DataBoundConstructor;

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
      return Messages.LabelsMonitor_DisplayName();
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
    return data;
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
