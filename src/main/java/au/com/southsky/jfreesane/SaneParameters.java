package au.com.southsky.jfreesane;

public class SaneParameters {
  private final FrameType frame;
  private final boolean lastFrame;
  private final int bytesPerLine;
  private final int pixelsPerLine;
  private int lineCount;
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

  void setLineCount(int lineCount) {
    this.lineCount = lineCount;
  }

  public int getDepthPerPixel() {
    return depthPerPixel;
  }
}