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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

/**
 * Represents a SANE image, which are composed of one or more {@link Frame frames}.
 */
class SaneImage {
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

  BufferedImage toBufferedImage() {
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
        int offsetWithinByte = 1 << (Byte.SIZE - (x % Byte.SIZE) - 1);

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

  private static class WriteOnce<T> {
    private T value = null;

    public void set(T value) {
      if (this.value == null) {
        this.value = value;
      } else if (!value.equals(this.value)) {
        throw new IllegalArgumentException("Cannot overwrite with a different value");
      }
    }

    public T get() {
      return value;
    }
  }
}