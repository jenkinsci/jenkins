package jenkins.bugs;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import hudson.model.Queue;
import hudson.model.queue.QueueTaskFuture;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;

public class BridgeMethodsTest {

    @Test
    @Issue("JENKINS-65605")
    public void checkBridgeMethod() {
        /*
         * we should have 2 methods getFuture() in hudson.model.Queue$WaitingItem but with different return types :
         * hudson.model.Queue$WaitingItem.getFuture()Ljava/util/concurrent/Future
         * hudson.model.Queue$WaitingItem.getFuture()Lhudson.model.queue.QueueTaskFuture;
         */
        Method[] methods = Queue.WaitingItem.class.getMethods();
        List<Method> collect = Arrays.stream(methods).filter(m -> m.getName().equals("getFuture") && m.getParameterCount() == 0).collect(Collectors.toList());

        assertThat(collect, allOf(iterableWithSize(2),
                                  hasItem(hasProperty("returnType", is(Future.class))),
                                  hasItem(hasProperty("returnType", is(QueueTaskFuture.class)))));
    }
}
