package au.com.southsky.jfreesane;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

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
		try {
			outputStream.write(SaneWord.forInt(10));
			outputStream.close();
		} finally {
			// Seems like an oversight that Socket is not Closeable?
			Closeables.closeQuietly(new Closeable() {
				@Override
				public void close() throws IOException {
					socket.close();
				}
			});
		}
	}

	SaneDeviceHandle openDevice(SaneDevice device) throws IOException {
		outputStream.write(SaneWord.forInt(2));
		outputStream.write(device.getName());

		SaneWord status = inputStream.readWord();

		if (status.integerValue() != 0) {
			throw new IOException("unexpected status (" + status.integerValue()
					+ ") while opening device");
		}

		SaneWord handle = inputStream.readWord();
		String resource = inputStream.readString();

		return new SaneDeviceHandle(status, handle, resource);
	}

	BufferedImage acquireImage(SaneDeviceHandle handle) throws IOException {
		outputStream.write(SaneWord.forInt(7));
		outputStream.write(handle.getHandle());

		{
			int status = inputStream.readWord().integerValue();
			if (status != 0) {
				throw new IOException("Unexpected status (" + status
						+ ") on image acquisition");
			}
		}

		int port = inputStream.readWord().integerValue();
		SaneWord byteOrder = inputStream.readWord();
		String resource = inputStream.readString();

		// TODO(sjr): use the correct byte order, also need to maybe
		// authenticate to the resource

		// Ask the server for the parameters of this scan
		outputStream.write(SaneWord.forInt(6));
		outputStream.write(handle.getHandle());

		Socket imageSocket = new Socket(socket.getInetAddress(), port);

		{
			int status = inputStream.readWord().integerValue();
			if (status != 0) {
				throw new IOException("Unexpected status (" + status
						+ ") in get_parameters");
			}

		}

		SaneParameters parameters = inputStream.readSaneParameters();
		SaneRecordInputStream recordStream = new SaneRecordInputStream(
				parameters, imageSocket.getInputStream());

		BufferedImage image = recordStream.readImage();
		imageSocket.close();

		return image;
	}

	void closeDevice(SaneDeviceHandle handle) throws IOException {
		outputStream.write(SaneWord.forInt(3));
		outputStream.write(handle.getHandle());

		// read the dummy value from the wire, if it doesn't throw an exception
		// we assume the close was successful
		inputStream.readWord();
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

	public class SaneInputStream extends InputStream {
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

			// read past a trailing byte in the response that I haven't figured
			// out yet...
			inputStream.readWord();

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

			return new SaneDevice(SaneSession.this, deviceName, deviceVendor,
					deviceModel, deviceType);
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

		public SaneParameters readSaneParameters() throws IOException {
			int frame = readWord().integerValue();
			boolean lastFrame = readWord().integerValue() == 1;
			int bytesPerLine = readWord().integerValue();
			int pixelsPerLine = readWord().integerValue();
			int lines = readWord().integerValue();
			int depth = readWord().integerValue();

			return new SaneParameters(frame, lastFrame, bytesPerLine,
					pixelsPerLine, lines, depth);
		}

		public SaneWord readWord() throws IOException {
			return SaneWord.fromStream(this);
		}
	}

	public static class SaneOutputStream extends OutputStream {
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

	public static class SaneWord {
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

	static class SaneDeviceHandle {
		private final SaneWord status;
		private final SaneWord handle;
		private final String resource;

		private SaneDeviceHandle(SaneWord status, SaneWord handle,
				String resource) {
			this.status = status;
			this.handle = handle;
			this.resource = resource;
		}

		public SaneWord getStatus() {
			return status;
		}

		public SaneWord getHandle() {
			return handle;
		}

		public String getResource() {
			return resource;
		}

		public boolean isAuthorizationRequired() {
			return !Strings.isNullOrEmpty(resource);
		}
	}

	public class SaneParameters {
		private final int frame;
		private final boolean lastFrame;
		private final int bytesPerLine;
		private final int pixelsPerLine;
		private final int lineCount;
		private final int depthPerPixel;

		public SaneParameters(int frame, boolean lastFrame, int bytesPerLine,
				int pixelsPerLine, int lines, int depth) {
			this.frame = frame;
			this.lastFrame = lastFrame;
			this.bytesPerLine = bytesPerLine;
			this.pixelsPerLine = pixelsPerLine;
			this.lineCount = lines;
			this.depthPerPixel = depth;
		}

		public int getFrame() {
			return frame;
		}

		public boolean isLastFrame() {
			return lastFrame;
		}

		public int getBytesPerLine() {
			return bytesPerLine;
		}

		public int getPixelsPerLine() {
			return pixelsPerLine;
		}

		public int getLineCount() {
			return lineCount;
		}

		public int getDepthPerPixel() {
			return depthPerPixel;
		}
	}

	private static class SaneRecordInputStream extends InputStream {
		private final SaneParameters parameters;
		private final InputStream underlyingStream;

		public SaneRecordInputStream(SaneParameters parameters,
				InputStream underlyingStream) {
			this.parameters = parameters;
			this.underlyingStream = underlyingStream;
		}

		@Override
		public int read() throws IOException {
			return underlyingStream.read();
		}

		public BufferedImage readImage() throws IOException {
			byte[] bigArray = new byte[parameters.getBytesPerLine()
					* parameters.getLineCount()];

			byte[] record;

			int offset = 0;
			while ((record = readRecord()) != null) {
				System.arraycopy(record, 0, bigArray, offset, record.length);
				offset += record.length;
			}

			WritableRaster raster = Raster.createInterleavedRaster(
					new DataBufferByte(bigArray, 0), parameters
							.getPixelsPerLine(), parameters.getLineCount(),
					parameters.getPixelsPerLine() * 3, 3,
					new int[] { 0, 1, 2 }, null);

			BufferedImage result = new BufferedImage(new ComponentColorModel(
					ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), null,
					false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE),
					raster, false, null);
			result.flush();

			return result;
		}

		public byte[] readRecord() throws IOException {
			DataInputStream inputStream = new DataInputStream(this);
			long length = inputStream.readInt();

			if (length == 0xffffffff) {
				System.out.println("Reached end of records");
				return null;
			}

			if (length > Integer.MAX_VALUE) {
				throw new IllegalStateException("TODO: support massive records");
			}

			byte[] record = new byte[(int) length];

			int result = read(record);
			if (result != length) {
				throw new IllegalStateException("read too few bytes (" + result
						+ "), was expecting " + length);
			}

			System.out.println("Read a record of " + result + " bytes");
			return record;
		}
	}

	public SaneOutputStream getOutputStream() {
		return outputStream;
	}

	public SaneInputStream getInputStream() {
		return inputStream;
	}

}
