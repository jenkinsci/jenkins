package jenkins.agents;

import static jenkins.agents.CloudSet.moveTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import org.junit.Test;

public class CloudSetTest {
    @Test
    public void reorder() {
        List<String> list = List.of("a", "b", "c");
        assertThat(moveTo(list, 0, list.get(0)), equalTo(List.of("a", "b", "c")));
        assertThat(moveTo(list, 0, list.get(1)), equalTo(List.of("b", "a", "c")));
        assertThat(moveTo(list, 0, list.get(2)), equalTo(List.of("c", "a", "b")));
        assertThat(moveTo(list, 1, list.get(0)), equalTo(List.of("b", "a", "c")));
        assertThat(moveTo(list, 1, list.get(1)), equalTo(List.of("a", "b", "c")));
        assertThat(moveTo(list, 1, list.get(2)), equalTo(List.of("a", "c", "b")));
        assertThat(moveTo(list, 2, list.get(0)), equalTo(List.of("b", "c", "a")));
        assertThat(moveTo(list, 2, list.get(1)), equalTo(List.of("a", "c", "b")));
        assertThat(moveTo(list, 2, list.get(2)), equalTo(List.of("a", "b", "c")));
    }
}
