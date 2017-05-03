/*
 * The MIT License
 *
 * Copyright 2014 Red Hat, Inc.
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
package hudson.model.queue;

import hudson.model.FreeStyleProject;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import static org.junit.Assert.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.TestCase;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 * @author Lucinka Votypkova
 */
public class FutureImplTest extends TestCase{
    
    @Test
    public void testSetAsCancelled() throws Exception{
        FreeStyleProject project = Mockito.mock(FreeStyleProject.class);
        FutureImpl future= new FutureImpl(project);
        WaitForStartThread waitforStart = new WaitForStartThread(future);
        waitforStart.start();
        future.setAsCancelled();
        assertTrue("Task should be canceled.", future.isCancelled());
        assertTrue("Start task should be canceled too.", future.start.isCancelled());
        Thread.sleep(1000);
        assertTrue("Method waitForStart() should throw CancellationException when item is cancelled in queue.",waitforStart.cancelled);
    }
    
    public class WaitForStartThread extends Thread{
        public boolean cancelled =false;
        private QueueTaskFuture future;
        
        public WaitForStartThread(QueueTaskFuture future){
            this.future=future;
        }
            
        public void run(){
            try {
                    future.waitForStart();
            } catch (InterruptedException ex) {
                    Logger.getLogger(FutureImplTest.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                    Logger.getLogger(FutureImplTest.class.getName()).log(Level.SEVERE, null, ex);
            }catch (CancellationException ex) {
                cancelled = true; 
            }
        }
    }
    
}
