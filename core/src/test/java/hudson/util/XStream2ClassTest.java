package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class XStream2ClassTest {
  public static class BuildConfig {
    private String name;
    private int maxBuilds;
    private List<String> goals;

    public BuildConfig() {}

    public BuildConfig(String name, int maxBuilds, List<String> goals) {
      this.name = name;
      this.maxBuilds = maxBuilds;
      this.goals = goals;
    }
  }

  public record TestConfig(String name, int value, boolean enabled, List<String> anything) {}

  @Test
  public void testClassMarshalAndUnmarshal() throws Exception {
    BuildConfig original = new BuildConfig("my-app", 10, Arrays.asList("clean", "test"));

    XStream2 xstream = new XStream2();
    String xml = xstream.toXML(original);

    System.out.println("Normal object here\n");
    System.out.println(xml);

    BuildConfig restored = (BuildConfig) xstream.fromXML(xml);

    System.out.println(restored.toString());

    assertEquals("my-app", restored.name);
    assertEquals(10, restored.maxBuilds);
    assertEquals(Arrays.asList("clean", "test"), restored.goals);

    TestConfig record = new TestConfig("test-config", 42, true, Arrays.asList("lalala", "whateverrrr", "yaaaa"));

    String xmlRec = xstream.toXML(record);
    System.out.println("RECORD");
    System.out.println(xmlRec);

    TestConfig restoredRec = (TestConfig) xstream.fromXML(xmlRec);
    System.out.println(restoredRec.toString());

    assert restoredRec.name().equals("test-config");
    assert restoredRec.value() == 42;
    assert restoredRec.enabled();
  }
}
