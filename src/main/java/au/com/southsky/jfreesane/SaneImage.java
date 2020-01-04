package au.com.southsky.jfreesane;

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a SANE image, which are composed of one or more {@link Frame frames}.
 */
final class SaneImage {
  private static final Set<FrameType> singletonFrameTypes =
      Collections.unmodifiableSet(EnumSet.of(FrameType.GRAY, FrameType.RGB));

  private static final Set<FrameType> redGreenBlueFrameTypes =
      Collections.unmodifiableSet(EnumSet.of(FrameType.RED, FrameType.GREEN, FrameType.BLUE));

  private final List<Frame> frames;
  private final int depthPerPixel;
  private final int width;
  private final int height;
  private final int bytesPerLine;

  private static int frameSortOrder(Frame frame) {
    switch (frame.getType()) {
      case RED:
        return 0;
      case GREEN:
        return 1;
      case BLUE:
        return 2;
      case RGB:
        return 3;
      case GRAY:
        return 4;
      default:
        throw new IllegalArgumentException("unknown frame type " + frame.getType());
    }
  }

  private SaneImage(
      List<Frame> frames, int depthPerPixel, int width, int height, int bytesPerLine) {
    // this ensures that in the 3-frame situation, they are always
    // arranged in the following order: red, green, blue
    this.frames =
        frames
            .stream()
            .sorted(Comparator.comparing(SaneImage::frameSortOrder))
            .collect(Collectors.toList());
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

  BufferedImage toBufferedImage() {
    DataBuffer buffer = asDataBuffer();

    if (getFrames().size() == redGreenBlueFrameTypes.size()) {
      // 3 frames, one or two bytes per sample, 3 samples per pixel
      WritableRaster raster =
          Raster.createBandedRaster(
              buffer,
              getWidth(),
              getHeight(),
              getBytesPerLine(),
              new int[] {0, 1, 2},
              new int[] {0, 0, 0},
              new Point(0, 0));

      ColorModel model =
          new ComponentColorModel(
              ColorSpace.getInstance(ColorSpace.CS_sRGB),
              false,
              false,
              Transparency.OPAQUE,
              DataBuffer.TYPE_BYTE);

      return new BufferedImage(model, raster, false, null);
    }

    // Otherwise we're in a one-frame situation
    if (depthPerPixel == 1) {
      if (getFrames().get(0).getType() == FrameType.GRAY) {
        return decodeSingleBitGrayscaleImage(buffer);
      } else {
        return decodeSingleBitColorImage();
      }
    }

    if (getDepthPerPixel() == 8 || getDepthPerPixel() == 16) {
      ColorSpace colorSpace;
      int[] bandOffsets;
      int scanlineStride;

      if (getFrames().get(0).getType() == FrameType.GRAY) {
        bandOffsets = new int[] {0};
        colorSpace = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        scanlineStride = 1;
      } else /* RGB */ {
        colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        bandOffsets = new int[] {0, 1, 2};
        scanlineStride = 3;
      }

      WritableRaster raster =
          Raster.createInterleavedRaster(
              buffer,
              width,
              height,
              bytesPerLine * Byte.SIZE / depthPerPixel,
              scanlineStride,
              bandOffsets,
              new Point(0, 0));

      ColorModel model =
          new ComponentColorModel(
              colorSpace,
              false,
              false,
              Transparency.OPAQUE,
              getDepthPerPixel() == 8 ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT);

      return new BufferedImage(model, raster, false, null);
    }

    throw new IllegalStateException("Unsupported SaneImage type");
  }

  private BufferedImage decodeSingleBitGrayscaleImage(DataBuffer buffer) {
    WritableRaster raster = Raster.createPackedRaster(buffer, width, height, 1, new Point(0, 0));
    return new BufferedImage(
        new IndexColorModel(
            1,
            2,
            new byte[] {(byte) 0xff, 0},
            new byte[] {(byte) 0xff, 0},
            new byte[] {(byte) 0xff, 0}),
        raster,
        false,
        null);
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
        int offsetWithinByte = 1 << (Byte.SIZE - (x % Byte.SIZE) - 1);

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
    if (depthPerPixel == 1 || depthPerPixel == 8) {
      byte[][] buffers = new byte[getFrames().size()][];

      for (int i = 0; i < getFrames().size(); i++) {
        buffers[i] = getFrames().get(i).getData();
      }

      return new DataBufferByte(buffers, getFrames().get(0).getData().length);
    } else {
      short[][] buffers = new short[getFrames().size()][];
      int stride = Short.SIZE / Byte.SIZE;

      for (int i = 0; i < getFrames().size(); i++) {
        byte[] bank = getFrames().get(i).getData();
        buffers[i] = new short[bank.length / stride];
        for (int j = 0; j < buffers[i].length; j++) {
          buffers[i][j] = (short) ((bank[stride * j] & 0xFF) << Byte.SIZE);
          buffers[i][j] |= (short) (bank[stride * j + 1] & 0xFF);
        }
      }

      return new DataBufferUShort(buffers, getFrames().get(0).getData().length / stride);
    }
  }

  public static class Builder {
    private final List<Frame> frames = new ArrayList<>();
    private final Set<FrameType> frameTypes = EnumSet.noneOf(FrameType.class);

    private final WriteOnce<Integer> depthPerPixel = new WriteOnce<>();
    private final WriteOnce<Integer> width = new WriteOnce<>();
    private final WriteOnce<Integer> height = new WriteOnce<>();
    private final WriteOnce<Integer> bytesPerLine = new WriteOnce<>();

    public void addFrame(Frame frame) {
      Preconditions.checkArgument(
          !frameTypes.contains(frame.getType()), "Image already contains a frame of this type");
      Preconditions.checkArgument(
          frameTypes.isEmpty() || !singletonFrameTypes.contains(frame.getType()),
          "The frame type is singleton but this image contains another frame");
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

    public void setPixelDepth(int newDepth) {
      Preconditions.checkArgument(newDepth > 0, "depth must be positive");
      this.depthPerPixel.set(newDepth);
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

  private static class WriteOnce<T> {
    private T value = null;

    public void set(T newValue) {
      if (this.value == null) {
        this.value = newValue;
      } else if (!newValue.equals(this.value)) {
        throw new IllegalArgumentException("Cannot overwrite with a different value");
      }
    }

    public T get() {
      return value;
    }
  }
}
