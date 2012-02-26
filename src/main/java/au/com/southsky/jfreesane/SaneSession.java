package au.com.southsky.jfreesane;

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

/**
 * Represents a conversation taking place with a SANE daemon.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneSession implements Closeable {

  private static final Logger log = Logger.getLogger(SaneSession.class.getName());

  private enum FrameType implements SaneEnum {
    GRAY(0), RGB(1), RED(2), GREEN(3), BLUE(4);

    private final int wireValue;

    FrameType(int wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public int getWireValue() {
      return wireValue;
    }
  }

  private static final int DEFAULT_PORT = 6566;

  private final Socket socket;
  private final SaneOutputStream outputStream;
  private final SaneInputStream inputStream;

  private SaneSession(Socket socket) throws IOException {
    this.socket = socket;
    this.outputStream = new SaneOutputStream(socket.getOutputStream());
    this.inputStream = new SaneInputStream(this, socket.getInputStream());
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the default SANE port.
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress) throws IOException {
    return withRemoteSane(saneAddress, DEFAULT_PORT);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host at the given port.
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress, int port) throws IOException {
    Socket socket = new Socket(saneAddress, DEFAULT_PORT);
    SaneSession session = new SaneSession(socket);
    session.initSane();
    return session;
  }

  /**
   * Returns the device with the give name. Opening the device will fail if the named device does
   * not exist.
   *
   * @return a new {@link SaneDevice} with the given name associated with the current session, never
   *         {@code null}
   * @throws IOException
   *           if an error occurs while communicating with the SANE daemon
   */
  public SaneDevice getDevice(String name) throws IOException {
    return new SaneDevice(this, name, "", "", "");
  }

  /**
   * Lists the devices known to the SANE daemon.
   *
   * @return a list of devices that may be opened, see {@link SaneDevice#open}
   * @throws IOException
   *           if an error occurs while communicating with the SANE daemon
   * @throws SaneException
   *           if the SANE backend returns an error in response to this request
   */
  public List<SaneDevice> listDevices() throws IOException, SaneException {
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
      SaneStatus statusEnum = SaneEnums.valueOf(SaneStatus.class, status);
      if (statusEnum == null) {
        throw new IOException(
            "unexpected status " + status.integerValue() + " while opening device");
      } else {
        throw new IOException(
            "unexpected status " + status.integerValue() + " (" + statusEnum
                + ") while opening device");
      }
    }

    SaneWord handle = inputStream.readWord();
    String resource = inputStream.readString();

    return new SaneDeviceHandle(status, handle, resource);
  }

  BufferedImage acquireImage(SaneDeviceHandle handle) throws IOException, SaneException {
    SaneImage.Builder builder = new SaneImage.Builder();

    while (true) {
      outputStream.write(SaneWord.forInt(7));
      outputStream.write(handle.getHandle());

      {
        int status = inputStream.readWord().integerValue();
        if (status != 0) {
          throw new SaneException(SaneStatus.fromWireValue(status));
        }
      }

      int port = inputStream.readWord().integerValue();
      SaneWord byteOrder = inputStream.readWord();
      String resource = inputStream.readString();

      // TODO(sjr): maybe authenticate to the resource

      // Ask the server for the parameters of this scan
      outputStream.write(SaneWord.forInt(6));
      outputStream.write(handle.getHandle());

      Socket imageSocket = new Socket(socket.getInetAddress(), port);
      int status = inputStream.readWord().integerValue();

      if (status != 0) {
        throw new IOException("Unexpected status (" + status + ") in get_parameters");
      }

      SaneParameters parameters = inputStream.readSaneParameters();
      FrameInputStream frameStream = new FrameInputStream(
          parameters, imageSocket.getInputStream(), 0x4321 == byteOrder.integerValue());
      builder.addFrame(frameStream.readFrame());
      // imageSocket.close();

      if (parameters.isLastFrame()) {
        break;
      }
    }

    SaneImage image = builder.build();

    return image.toBufferedImage();
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

  public static class SaneParameters {
    private final FrameType frame;
    private final boolean lastFrame;
    private final int bytesPerLine;
    private final int pixelsPerLine;
    private final int lineCount;
    private final int depthPerPixel;

    public SaneParameters(
        int frame, boolean lastFrame, int bytesPerLine, int pixelsPerLine, int lines, int depth) {
      this.frame = SaneEnums.valueOf(FrameType.class, frame);
      this.lastFrame = lastFrame;
      this.bytesPerLine = bytesPerLine;
      this.pixelsPerLine = pixelsPerLine;
      this.lineCount = lines;
      this.depthPerPixel = depth;
    }

    public FrameType getFrame() {
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

  private static class FrameInputStream extends InputStream {
    private final SaneParameters parameters;
    private final InputStream underlyingStream;
    private final boolean bigEndian;

    public FrameInputStream(
        SaneParameters parameters, InputStream underlyingStream, boolean bigEndian) {
      this.parameters = parameters;
      this.underlyingStream = underlyingStream;
      this.bigEndian = bigEndian;
    }

    @Override
    public int read() throws IOException {
      return underlyingStream.read();
    }

    public Frame readFrame() throws IOException {
      byte[] bigArray = new byte[parameters.getBytesPerLine() * parameters.getLineCount()];

      int offset = 0;
      int bytesRead = 0;
      while ((bytesRead = readRecord(bigArray, offset)) >= 0) {
        offset += bytesRead;
      }

      if (offset != bigArray.length) {
        throw new IOException("truncated read");
      }

      // Now, if necessary, put the bytes in the correct order according
      // to the stream's endianness
      if (parameters.getDepthPerPixel() == 16 && !bigEndian) {
        if (bigArray.length % 2 != 0) {
          throw new IOException("expected a multiple of 2 frame length");
        }

        for (int i = 0; i < bigArray.length; i += 2) {
          byte swap = bigArray[i];
          bigArray[i] = bigArray[i + 1];
          bigArray[i + 1] = swap;
        }
      }

      return new Frame(parameters, bigArray);
    }

    private int readRecord(byte[] destination, int offset) throws IOException {
      DataInputStream inputStream = new DataInputStream(this);
      long length = inputStream.readInt();

      if (length == 0xffffffff) {
        log.fine("Reached end of records");
        return -1;
      }

      if (length > Integer.MAX_VALUE) {
        throw new IllegalStateException("TODO: support massive records");
      }

      int result = read(destination, offset, (int) length);
      if (result != length) {
        throw new IllegalStateException(
            "read too few bytes (" + result + "), was expecting " + length);
      }

      log.fine("Read a record of " + result + " bytes");
      return result;
    }
  }

  public SaneOutputStream getOutputStream() {
    return outputStream;
  }

  public SaneInputStream getInputStream() {
    return inputStream;
  }

  private static class Frame {
    private final SaneParameters parameters;
    private final byte[] data;

    public Frame(SaneParameters parameters, byte[] data) {
      this.parameters = parameters;
      this.data = data;
    }

    public FrameType getType() {
      return parameters.getFrame();
    }

    public byte[] getData() {
      return data;
    }

    public int getBytesPerLine() {
      return parameters.getBytesPerLine();
    }

    public int getWidth() {
      return parameters.getPixelsPerLine();
    }

    public int getHeight() {
      return parameters.getLineCount();
    }

    public int getPixelDepth() {
      return parameters.getDepthPerPixel();
    }
  }

  private static class SaneImage {
    private static final Set<FrameType> singletonFrameTypes = Sets.immutableEnumSet(
        FrameType.GRAY, FrameType.RGB);

    private static final Set<FrameType> redGreenBlueFrameTypes = Sets.immutableEnumSet(
        FrameType.RED, FrameType.GREEN, FrameType.BLUE);

    private final List<Frame> frames;
    private final int depthPerPixel;
    private final int width;
    private final int height;
    private final int bytesPerLine;

    private SaneImage(
        List<Frame> frames, int depthPerPixel, int width, int height, int bytesPerLine) {
      // this ensures that in the 3-frame situation, they are always
      // arranged in the following order: red, green, blue
      this.frames = Ordering.explicit(
          FrameType.RED, FrameType.GREEN, FrameType.BLUE, FrameType.RGB, FrameType.GRAY).onResultOf(
          new Function<Frame, FrameType>() {
            @Override
            public FrameType apply(Frame input) {
              return input.getType();
            }
          }).immutableSortedCopy(frames);
      this.depthPerPixel = depthPerPixel;
      this.width = width;
      this.height = height;
      this.bytesPerLine = bytesPerLine;
    }

    private List<Frame> getFrames() {
      return frames;
    }

    private int getDepthPerPixel() {
      return depthPerPixel;
    }

    private int getWidth() {
      return width;
    }

    private int getHeight() {
      return height;
    }

    private int getBytesPerLine() {
      return bytesPerLine;
    }

    public BufferedImage toBufferedImage() {
      DataBuffer buffer = asDataBuffer();

      if (getFrames().size() == redGreenBlueFrameTypes.size()) {
        // 3 frames, one or two bytes per sample, 3 samples per pixel
        WritableRaster raster = Raster.createBandedRaster(buffer, getWidth(), getHeight(),
            getBytesPerLine(), new int[] { 0, 1, 2 }, new int[] { 0, 0, 0 }, new Point(0, 0));

        ColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB),
            false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);

        return new BufferedImage(model, raster, false, null);
      }

      // Otherwise we're in a one-frame situation
      if (depthPerPixel == 1) {
        if (getFrames().get(0).getType() == FrameType.GRAY) {
          return decodeSingleBitGrayscaleImage();
        } else {
          return decodeSingleBitColorImage();
        }
      }

      if (getDepthPerPixel() == 8 || getDepthPerPixel() == 16) {
        ColorSpace colorSpace;
        int[] bandOffsets;

        int bytesPerSample = getDepthPerPixel() / Byte.SIZE;

        if (getFrames().get(0).getType() == FrameType.GRAY) {
          colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
          bandOffsets = new int[] { 0 };
        } else /* RGB */{
          colorSpace = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
          bandOffsets = new int[] { 0, 1 * bytesPerSample, 2 * bytesPerSample };
        }

        WritableRaster raster = Raster.createInterleavedRaster(buffer, width, height, bytesPerLine,
            bytesPerSample * bandOffsets.length, bandOffsets, new Point(0, 0));

        ColorModel model = new ComponentColorModel(
            colorSpace, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);

        return new BufferedImage(model, raster, false, null);
      }

      throw new IllegalStateException("Unsupported SaneImage type");
    }

    private BufferedImage decodeSingleBitGrayscaleImage() {
      byte[] data = frames.get(0).getData();
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int lineStartByte = y * bytesPerLine;
          int offsetWithinLine = x / Byte.SIZE;
          int offsetWithinByte = 1 << (x % Byte.SIZE);

          // for a GRAY frame of single bit depth, the value is
          // intensity: 1 is lowest intensity (black), 0 is highest
          // (white)
          int rgb = (data[lineStartByte + offsetWithinLine] & offsetWithinByte) == 0 ? 0xffffff : 0;
          image.setRGB(x, y, rgb);
        }
      }

      return image;
    }

    private BufferedImage decodeSingleBitColorImage() {
      byte[] data = frames.get(0).getData();
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

      int componentCount = 3; // red, green, blue. One bit per sample,
      // byte interleaved

      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int lineStartByte = y * bytesPerLine;
          int offsetWithinLine = (x / Byte.SIZE) * componentCount;
          int offsetWithinByte = 1 << (x % Byte.SIZE);

          boolean red = (data[lineStartByte + offsetWithinLine] & offsetWithinByte) != 0;
          boolean green = (data[lineStartByte + offsetWithinLine + 1] & offsetWithinByte) != 0;
          boolean blue = (data[lineStartByte + offsetWithinLine + 2] & offsetWithinByte) != 0;

          int rgb = red ? 0xff0000 : 0;
          rgb |= green ? 0x00ff00 : 0;
          rgb |= blue ? 0x0000ff : 0;

          image.setRGB(x, y, rgb);
        }
      }

      return image;
    }

    private DataBuffer asDataBuffer() {
      byte[][] buffers = new byte[getFrames().size()][];

      for (int i = 0; i < getFrames().size(); i++) {
        buffers[i] = getFrames().get(i).getData();
      }

      return new DataBufferByte(buffers, getFrames().get(0).getData().length);
    }

    public static class Builder {
      private final List<Frame> frames = Lists.newArrayList();
      private final Set<FrameType> frameTypes = EnumSet.noneOf(FrameType.class);

      private final WriteOnce<Integer> depthPerPixel = new WriteOnce<Integer>();
      private final WriteOnce<Integer> width = new WriteOnce<Integer>();
      private final WriteOnce<Integer> height = new WriteOnce<Integer>();
      private final WriteOnce<Integer> bytesPerLine = new WriteOnce<Integer>();

      public void addFrame(Frame frame) {
        Preconditions.checkArgument(
            !frameTypes.contains(frame.getType()), "Image already contains a frame of this type");
        Preconditions.checkArgument(
            frameTypes.isEmpty() || !singletonFrameTypes.contains(frame.getType()),
            "The frame type is singleton but this image " + "contains another frame");
        Preconditions.checkArgument(
            frames.isEmpty() || frames.get(0).getData().length == frame.getData().length,
            "new frame has an inconsistent size");
        setPixelDepth(frame.getPixelDepth());
        setBytesPerLine(frame.getBytesPerLine());
        setWidth(frame.getWidth());
        setHeight(frame.getHeight());
        frameTypes.add(frame.getType());
        frames.add(frame);
      }

      public void setPixelDepth(int depthPerPixel) {
        Preconditions.checkArgument(depthPerPixel > 0, "depth must be positive");
        this.depthPerPixel.set(depthPerPixel);
      }

      public void setWidth(int width) {
        this.width.set(width);
      }

      public void setHeight(int height) {
        this.height.set(height);
      }

      public void setBytesPerLine(int bytesPerLine) {
        this.bytesPerLine.set(bytesPerLine);
      }

      public SaneImage build() {
        Preconditions.checkState(!frames.isEmpty(), "no frames");
        Preconditions.checkState(depthPerPixel.get() != null, "setPixelDepth must be called");
        Preconditions.checkState(width.get() != null, "setWidth must be called");
        Preconditions.checkState(height.get() != null, "setHeight must be called");
        Preconditions.checkState(bytesPerLine.get() != null, "setBytesPerLine must be called");

        // does the image contains a single instance of a singleton
        // frame?
        if (frames.size() == 1 && singletonFrameTypes.contains(frames.get(0).getType())) {
          return new SaneImage(
              frames, depthPerPixel.get(), width.get(), height.get(), bytesPerLine.get());
        }

        // otherwise, does it contain a red, green and blue frame?
        if (frames.size() == redGreenBlueFrameTypes.size()
            && redGreenBlueFrameTypes.containsAll(frameTypes)) {
          return new SaneImage(
              frames, depthPerPixel.get(), width.get(), height.get(), bytesPerLine.get());
        }

        throw new IllegalStateException(
            "Image is not fully constructed. Frame types present: " + frameTypes);
      }
    }
  }

  private static class WriteOnce<T> {
    private T value = null;

    public void set(T value) {
      if (this.value == null) {
        this.value = value;
      } else if (!value.equals(this.value)) {
        throw new IllegalArgumentException("Cannot overwrite with a " + "different value");
      }
    }

    public T get() {
      return value;
    }
  }
}
