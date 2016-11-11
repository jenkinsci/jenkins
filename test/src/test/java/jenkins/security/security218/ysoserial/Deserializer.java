package jenkins.security.security218.ysoserial;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.Callable;

public class Deserializer implements Callable<Object> {
	private final byte[] bytes;

	public Deserializer(byte[] bytes) { this.bytes = bytes; }

	public Object call() throws Exception {
		return deserialize(bytes);
	}

	public static Object deserialize(final byte[] serialized) throws IOException, ClassNotFoundException {
		final ByteArrayInputStream in = new ByteArrayInputStream(serialized);
		return deserialize(in);
	}

	public static Object deserialize(final InputStream in) throws ClassNotFoundException, IOException {
		final ObjectInputStream objIn = new ObjectInputStream(in);
		return objIn.readObject();
	}

	public static void main(String[] args) throws ClassNotFoundException, IOException {
		final InputStream in = args.length == 0 ? System.in : new FileInputStream(new File(args[0]));
		Object object = deserialize(in);
	}
}