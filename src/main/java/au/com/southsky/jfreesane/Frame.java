package au.com.southsky.jfreesane;

/**
 * Represents one frame of a {@link SaneImage}. A SANE image is composed of one
 * or more of these frames.
 */
class Frame {
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