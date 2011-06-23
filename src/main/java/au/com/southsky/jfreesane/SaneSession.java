package au.com.southsky.jfreesane;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Represents a conversation taking place with a SANE daemon.
 * 
 * @author James Ring (sjr@jdns.org)
 */
public class SaneSession implements Closeable {

	private static final int DEFAULT_PORT = 6566;

	private final Socket socket;
	private final SaneOutputStream outputStream;
	private final SaneInputStream inputStream;

	private SaneSession(Socket socket) throws IOException {
		this.socket = socket;
		this.outputStream = new SaneOutputStream(socket.getOutputStream());
		this.inputStream = new SaneInputStream(socket.getInputStream());
	}

	/**
	 * Establishes a connection to the SANE daemon running on the given host on
	 * the default SANE port.
	 */
	public static SaneSession withRemoteSane(InetAddress saneAddress)
			throws IOException {
		Socket socket = new Socket(saneAddress, DEFAULT_PORT);

		return new SaneSession(socket);
	}

	public List<SaneDevice> listDevices() throws IOException {
		initSane();

		outputStream.write(SaneWord.forInt(1));
		return inputStream.readDeviceList();
	}

	@Override
	public void close() throws IOException {
		outputStream.write(SaneWord.forInt(10));
		outputStream.close();
	}

	private void initSane() throws IOException {
		// RPC code
		outputStream.write(SaneWord.forInt(0));

		// version number
		outputStream.write(SaneWord.forSaneVersion(1, 0, 3));

		// username
		outputStream.write(System.getProperty("user.name"));

		inputStream.readWord();
		inputStream.readWord();
	}

	private static class SaneInputStream extends InputStream {
		private InputStream wrappedStream;

		public SaneInputStream(InputStream wrappedStream) {
			this.wrappedStream = wrappedStream;
		}

		@Override
		public int read() throws IOException {
			return wrappedStream.read();
		}

		public List<SaneDevice> readDeviceList() throws IOException {
			// Status first
			readWord().integerValue();

			// now we're reading an array, decode the length of the array (which
			// includes the null if the array is non-empty)
			int length = readWord().integerValue() - 1;

			if (length <= 0) {
				return ImmutableList.of();
			}

			ImmutableList.Builder<SaneDevice> result = ImmutableList.builder();

			for (int i = 0; i < length; i++) {
				SaneDevice device = readSaneDevicePointer();
				if (device == null) {
					throw new IllegalStateException(
							"null pointer encountered when not expected");
				}

				result.add(device);
			}

			return result.build();
		}

		/**
		 * Reads a single {@link SaneDevice} definition pointed to by the
		 * pointer at the current location in the stream. Returns {@code null}
		 * if the pointer is a null pointer.
		 */
		private SaneDevice readSaneDevicePointer() throws IOException {
			if (!readPointer()) {
				// TODO(sjr): why is there always a null pointer here?
				// return null;
			}

			// now we assume that there's a sane device ready to parse
			return readSaneDevice();
		}

		/**
		 * Reads a single pointer and returns {@code true} if it was non-null.
		 */
		private boolean readPointer() throws IOException {
			return readWord().integerValue() != 0;
		}

		private SaneDevice readSaneDevice() throws IOException {
			String deviceName = readString();
			String deviceVendor = readString();
			String deviceModel = readString();
			String deviceType = readString();

			return new SaneDevice(deviceName, deviceVendor, deviceModel,
					deviceType);
		}

		public String readString() throws IOException {
			// read the length
			int length = readWord().integerValue();

			if (length == 0) {
				return "";
			}

			// now read all the bytes
			byte[] input = new byte[length];
			if (read(input) != input.length) {
				throw new IllegalStateException(
						"truncated input while reading string");
			}
			
			// skip the null terminator
			return new String(input, 0, input.length - 1);
		}

		public SaneWord readWord() throws IOException {
			return SaneWord.fromStream(this);
		}
	}

	private static class SaneOutputStream extends OutputStream {
		private OutputStream wrappedStream;

		public SaneOutputStream(OutputStream wrappedStream) {
			this.wrappedStream = wrappedStream;
		}

		@Override
		public void close() throws IOException {
			wrappedStream.close();
		}

		@Override
		public void flush() throws IOException {
			wrappedStream.flush();
		}

		@Override
		public void write(int b) throws IOException {
			wrappedStream.write(b);
		}

		public void write(String string) throws IOException {
			if (string.length() > 0) {
				write(SaneWord.forInt(string.length() + 1));
				for (char c : string.toCharArray()) {
					if (c == 0) {
						throw new IllegalArgumentException(
								"null characters not allowed");
					}

					write(c);
				}
			}

			write(0);
		}

		public void write(SaneWord word) throws IOException {
			write(word.getValue());
		}
	}

	private static class SaneWord {
		private final byte[] value;

		private SaneWord(byte[] value) {
			this.value = value;
		}

		public static SaneWord fromStream(InputStream input) throws IOException {
			byte[] newValue = new byte[4];
			if (input.read(newValue) != newValue.length) {
				throw new IllegalArgumentException(
						"input stream was truncated while reading a word");
			}

			return new SaneWord(newValue);
		}

		public static SaneWord forInt(int value) {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream(4);
			DataOutputStream stream = new DataOutputStream(byteStream);
			try {
				stream.writeInt(value);
			} catch (IOException e) {
				throw new IllegalArgumentException(e);
			}
			return new SaneWord(byteStream.toByteArray());
		}

		public static SaneWord forSaneVersion(int major, int minor, int build) {
			int result = (major & 0xff) << 24;
			result |= (minor & 0xff) << 16;
			result |= (build & 0xffff) << 0;
			return forInt(result);
		}

		public byte[] getValue() {
			return Arrays.copyOf(value, value.length);
		}

		public int integerValue() {
			try {
				return new DataInputStream(new ByteArrayInputStream(value))
						.readInt();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
