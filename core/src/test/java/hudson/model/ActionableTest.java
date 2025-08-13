/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class ActionableTest {

    private final Actionable thing = new ActionableImpl();

    @SuppressWarnings("deprecation")
    @Test
    void replaceAction() {
        CauseAction a1 = new CauseAction();
        ParametersAction a2 = new ParametersAction();
        thing.addAction(a1);
        thing.addAction(a2);
        CauseAction a3 = new CauseAction();
        thing.replaceAction(a3);
        assertEquals(Arrays.asList(a2, a3), thing.getActions());
    }

    static class ActionableOverride extends Actionable {
        ArrayList<Action> specialActions = new ArrayList<>();

        @Override
        public String getDisplayName() {
            return "nope";
        }

        @Override
        public String getSearchUrl() {
            return "morenope";
        }

        @SuppressWarnings("deprecation")
        @Override
        @NonNull
        public List<Action> getActions() {
            return specialActions;
        }
    }

    @Issue("JENKINS-39555")
    @Test
    void testExtensionOverrides() {
        ActionableOverride myOverridden = new ActionableOverride();
        InvisibleAction invis = new InvisibleAction() {
        };
        myOverridden.addAction(invis);
        assertArrayEquals(new Object[]{invis}, myOverridden.specialActions.toArray());
        assertArrayEquals(new Object[]{invis}, myOverridden.getActions().toArray());

        myOverridden.getActions().remove(invis);
        assertArrayEquals(new Object[]{}, myOverridden.specialActions.toArray());
        assertArrayEquals(new Object[]{}, myOverridden.getActions().toArray());

        myOverridden.addAction(invis);
        myOverridden.removeAction(invis);
        assertArrayEquals(new Object[]{}, myOverridden.specialActions.toArray());
        assertArrayEquals(new Object[]{}, myOverridden.getActions().toArray());

        InvisibleAction invis2 = new InvisibleAction() {};
        myOverridden.addOrReplaceAction(invis2);
        assertArrayEquals(new Object[]{invis2}, myOverridden.specialActions.toArray());
        assertArrayEquals(new Object[]{invis2}, myOverridden.getActions().toArray());

        myOverridden.addOrReplaceAction(invis);
        myOverridden.addOrReplaceAction(invis);
        assertArrayEquals(new Object[]{invis2, invis}, myOverridden.specialActions.toArray());
        assertArrayEquals(new Object[]{invis2, invis}, myOverridden.getActions().toArray());
    }

    @SuppressWarnings("deprecation")
    @Test
    void addOrReplaceAction() {
        CauseAction a1 = new CauseAction();
        ParametersAction a2 = new ParametersAction();
        thing.addAction(a1);
        thing.addAction(a2);
        CauseAction a3 = new CauseAction();
        assertTrue(thing.addOrReplaceAction(a3));
        assertEquals(Arrays.asList(a2, a3), thing.getActions());
        assertFalse(thing.addOrReplaceAction(a3));
        assertEquals(Arrays.asList(a2, a3), thing.getActions());
        thing.addAction(a1);
        assertEquals(Arrays.asList(a2, a3, a1), thing.getActions());
        assertTrue(thing.addOrReplaceAction(a3));
        assertEquals(Arrays.asList(a2, a3), thing.getActions());
    }

    @SuppressWarnings("deprecation")
    @Test
    void replaceActions() {
        CauseAction a1 = new CauseAction();
        ParametersAction a2 = new ParametersAction();
        thing.addAction(a1);
        thing.addAction(a2);
        CauseAction a3 = new CauseAction();
        assertTrue(thing.replaceActions(CauseAction.class, a3));
        assertEquals(Arrays.asList(a2, a3), thing.getActions());
        assertFalse(thing.replaceActions(CauseAction.class, a3));
        assertEquals(Arrays.asList(a2, a3), thing.getActions());
    }

    @SuppressWarnings("deprecation")
    @Test
    void removeAction() {
        CauseAction a1 = new CauseAction();
        ParametersAction a2 = new ParametersAction();
        thing.addAction(a1);
        thing.addAction(a2);
        assertEquals(Arrays.asList(a1, a2), thing.getActions());
        assertThat(thing.removeAction(a1), is(true));
        assertEquals(List.of(a2), thing.getActions());
        assertThat(thing.removeAction(a1), is(false));
        assertEquals(List.of(a2), thing.getActions());
        assertThat(thing.removeAction(null), is(false));
        assertEquals(List.of(a2), thing.getActions());
    }

    @SuppressWarnings("deprecation")
    @Test
    void removeActions() {
        CauseAction a1 = new CauseAction();
        ParametersAction a2 = new ParametersAction();
        thing.addAction(a1);
        thing.addAction(a2);
        assertEquals(Arrays.asList(a1, a2), thing.getActions());
        assertThat(thing.removeActions(CauseAction.class), is(true));
        assertEquals(List.of(a2), thing.getActions());
        assertThat(thing.removeActions(CauseAction.class), is(false));
        assertEquals(List.of(a2), thing.getActions());
    }

    @SuppressWarnings("deprecation")
    @Test
    void addAction() {
        CauseAction a1 = new CauseAction();
        ParametersAction a2 = new ParametersAction();
        assertEquals(Collections.<Action>emptyList(), thing.getActions());
        thing.addAction(a1);
        assertEquals(List.of(a1), thing.getActions());
        thing.addAction(a2);
        assertEquals(Arrays.asList(a1, a2), thing.getActions());
    }

    @Test
    void addAction_null() {
        assertThrows(IllegalArgumentException.class, () -> thing.addAction(null));
    }

    @Test
    void replaceAction_null() {
        assertThrows(IllegalArgumentException.class, () -> thing.replaceAction(null));
    }

    @Test
    void replaceActions_null() {
        assertThrows(IllegalArgumentException.class, () -> thing.replaceActions(CauseAction.class, null));
    }

    @Test
    void replaceActions_null_null() {
        assertThrows(IllegalArgumentException.class, () -> thing.replaceActions(null, null));
    }

    @Test
    void addOrReplaceAction_null() {
        assertThrows(IllegalArgumentException.class, () -> thing.addOrReplaceAction(null));
    }

    @Test
    void removeAction_null() {
        assertFalse(thing.removeAction(null));
    }

    @Test
    void removeActions_null() {
        assertThrows(IllegalArgumentException.class, () -> thing.removeActions(null));
    }

    private static class ActionableImpl extends Actionable {
        @Override public String getDisplayName() {
            return null;
        }

        @Override public String getSearchUrl() {
            return null;
        }
    }
}
