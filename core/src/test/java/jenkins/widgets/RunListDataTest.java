package jenkins.widgets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunListDataTest {

    private static final Predicate<Run<?, ?>> ALL = build -> true;

    private int pageSize;
    private long scanBudget;

    private final RunListData data = new RunListData() {
        @Override
        protected void calculate(Run<?, ?> build, JSONObject element) {
            element.put("id", build.getExternalizableId());
        }
    };

    @BeforeEach
    void saveTunables() {
        pageSize = RunListData.PAGE_SIZE;
        scanBudget = RunListData.SCAN_BUDGET_MS;
    }

    @AfterEach
    void restoreTunables() {
        RunListData.PAGE_SIZE = pageSize;
        RunListData.SCAN_BUDGET_MS = scanBudget;
    }

    @Test
    void pagesThroughAllRunsOnce() {
        RunListData.PAGE_SIZE = 2;
        List<Run<?, ?>> runs = runs(50, 40, 30, 20, 10);

        JSONObject page = data.page(runs, ALL, Long.MAX_VALUE);
        assertThat(ids(page), contains("r50", "r40"));
        assertEquals(40L, page.getLong("next"));
        assertFalse(page.getBoolean("partial"));

        page = data.page(runs, ALL, 40);
        assertThat(ids(page), contains("r30", "r20"));
        assertEquals(20L, page.getLong("next"));

        page = data.page(runs, ALL, 20);
        assertThat(ids(page), contains("r10"));
        assertEquals(JSONNull.getInstance(), page.get("next"));
    }

    @Test
    void doesNotSplitRunsWithTheSameTimestamp() {
        RunListData.PAGE_SIZE = 2;
        List<Run<?, ?>> runs = new ArrayList<>(runs(50));
        runs.add(run("a40", 40));
        runs.add(run("b40", 40));
        runs.add(run("c40", 40));
        runs.addAll(runs(30));

        JSONObject page = data.page(runs, ALL, Long.MAX_VALUE);
        assertThat(ids(page), contains("r50", "a40", "b40", "c40"));
        assertEquals(40L, page.getLong("next"));

        page = data.page(runs, ALL, 40);
        assertThat(ids(page), contains("r30"));
        assertEquals(JSONNull.getInstance(), page.get("next"));
    }

    @Test
    void scanBudgetEndsPageEarlyButMakesProgress() {
        RunListData.SCAN_BUDGET_MS = 0;
        List<Run<?, ?>> runs = runs(30, 20, 10);

        JSONObject page = data.page(runs, ALL, Long.MAX_VALUE);
        assertThat(ids(page), contains("r30"));
        assertEquals(30L, page.getLong("next"));
        assertTrue(page.getBoolean("partial"));

        page = data.page(runs, ALL, 30);
        assertThat(ids(page), contains("r20"));
        assertTrue(page.getBoolean("partial"));

        page = data.page(runs, ALL, 20);
        assertThat(ids(page), contains("r10"));
        assertEquals(JSONNull.getInstance(), page.get("next"));
        assertFalse(page.getBoolean("partial"));
    }

    @Test
    void filterSkipsRuns() {
        RunListData.PAGE_SIZE = 2;
        List<Run<?, ?>> runs = runs(50, 40, 30, 20, 10);
        Predicate<Run<?, ?>> filter = build -> build.getTimeInMillis() != 40;

        JSONObject page = data.page(runs, filter, Long.MAX_VALUE);
        assertThat(ids(page), contains("r50", "r30"));
        assertEquals(30L, page.getLong("next"));
        assertEquals(2, page.getInt("pageSize"));
    }

    @Test
    void scanBudgetCoversRejectedRuns() {
        RunListData.SCAN_BUDGET_MS = 0;
        List<Run<?, ?>> runs = runs(30, 20, 10);
        Predicate<Run<?, ?>> none = build -> false;

        JSONObject page = data.page(runs, none, Long.MAX_VALUE);
        assertThat(ids(page), empty());
        assertEquals(30L, page.getLong("next"));
        assertTrue(page.getBoolean("partial"));

        page = data.page(runs, none, 30);
        assertThat(ids(page), empty());
        assertEquals(20L, page.getLong("next"));

        page = data.page(runs, none, 20);
        assertThat(ids(page), empty());
        assertEquals(JSONNull.getInstance(), page.get("next"));
        assertFalse(page.getBoolean("partial"));
    }

    @Test
    void limitFillsTheRestOfAPageWithoutSplittingTimestamps() {
        RunListData.PAGE_SIZE = 4;
        List<Run<?, ?>> runs = runs(50, 40, 40, 30, 20);

        JSONObject page = data.page(runs, ALL, Long.MAX_VALUE, 2);
        assertThat(ids(page), contains("r50", "r40", "r40"));
        assertEquals(40L, page.getLong("next"));
        assertFalse(page.getBoolean("partial"));
        assertEquals(4, page.getInt("pageSize"));
    }

    @Test
    void reachingTheCursorDoesNotUseTheBudget() {
        RunListData.SCAN_BUDGET_MS = 50;
        List<Run<?, ?>> runs = new ArrayList<>();
        for (long time = 100; time > 30; time -= 10) {
            Run<?, ?> run = mock(Run.class);
            long t = time;
            when(run.getTimeInMillis()).thenAnswer(invocation -> {
                Thread.sleep(20);
                return t;
            });
            runs.add(run);
        }
        runs.addAll(runs(30, 20, 10));

        JSONObject page = data.page(runs, ALL, 31);
        assertThat(ids(page), contains("r30", "r20", "r10"));
        assertFalse(page.getBoolean("partial"));
    }

    @Test
    void emptyList() {
        JSONObject page = data.page(List.of(), ALL, Long.MAX_VALUE);
        assertThat(ids(page), empty());
        assertEquals(JSONNull.getInstance(), page.get("next"));
        assertFalse(page.getBoolean("partial"));
    }

    private static List<Run<?, ?>> runs(long... times) {
        List<Run<?, ?>> runs = new ArrayList<>();
        for (long time : times) {
            runs.add(run("r" + time, time));
        }
        return runs;
    }

    private static Run<?, ?> run(String id, long time) {
        Run<?, ?> run = mock(Run.class);
        when(run.getTimeInMillis()).thenReturn(time);
        when(run.getExternalizableId()).thenReturn(id);
        return run;
    }

    private static List<String> ids(JSONObject page) {
        List<String> ids = new ArrayList<>();
        for (Object element : (JSONArray) page.get("builds")) {
            ids.add(((JSONObject) element).getString("id"));
        }
        return ids;
    }
}
