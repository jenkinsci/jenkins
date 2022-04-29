package hudson.node_monitors;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.LabelFinder;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
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
      return "Labels";
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
        data.addAll(getDynamicLabels(n));
      }
    }
    return new Data(c, Collections.unmodifiableSet(data));
  }

  public static class Data {
    final Computer computer;
    final Set<LabelAtom> labels;

    public Data(Computer computer, Set<LabelAtom> labels) {
      this.computer = computer;
      this.labels = labels;
    }

    public Computer getComputer()
    {
      return computer;
    }

    public Set<LabelAtom> getLabels()
    {
      return labels;
    }
  }

  private HashSet<LabelAtom> getDynamicLabels(Node n) {
    HashSet<LabelAtom> result = new HashSet<>();
    for (LabelFinder labeler : LabelFinder.all()) {
        // Filter out any bad(null) results from plugins
        // for compatibility reasons, findLabels may return LabelExpression and not atom.
        for (Label label : labeler.findLabels(n))
            if (label instanceof LabelAtom) result.add((LabelAtom) label);
    }
    return result;
  }

  @Extension
  @Symbol("labels")
  public static class DescriptorImpl extends AbstractNodeMonitorDescriptor<Set<LabelAtom>> {

    @Override
    public String getDisplayName() {
        return "Labels";
    }

    @Override
    protected Set<LabelAtom> monitor(Computer c) throws IOException, InterruptedException
    {
      return null;
    }
  }
}
