package au.com.southsky.jfreesane;

/**
 * Represents the parameters returned by the SANE server when it is about to acquire a new frame.
 *
 * <p>
 * All parameter values are determined in advance of frame acquisition, with the exception of
 * {@code lineCount}. The value of this parameter may not be known in advance, for example when the
 * scanner is a hand scanner or supports page height detection. JFreeSane will populate this value
 * after frame acquisition.
 */
public class SaneParameters {
  private final FrameType frameType;
  private final boolean lastFrame;
  private final int bytesPerLine;
  private final int pixelsPerLine;
  private int lineCount;
  private final int depthPerPixel;

  SaneParameters(
      int frame, boolean lastFrame, int bytesPerLine, int pixelsPerLine, int lines, int depth) {
    this.frameType = SaneEnums.valueOf(FrameType.class, frame);
    this.lastFrame = lastFrame;
    this.bytesPerLine = bytesPerLine;
    this.pixelsPerLine = pixelsPerLine;
    this.lineCount = lines;
    this.depthPerPixel = depth;
  }

  /**
   * Returns the type of frame being acquired.
   */
  public FrameType getFrameType() {
    return frameType;
  }

  /**
   * Returns if no more frames are expected.
   */
  public boolean isLastFrame() {
    return lastFrame;
  }

  /**
   * Returns the number of bytes per scan line.
   */
  public int getBytesPerLine() {
    return bytesPerLine;
  }

  /**
   * Returns the number of pixels per scan line.
   */
  public int getPixelsPerLine() {
    return pixelsPerLine;
  }

  /**
   * Returns the number of scan lines, or {@code -1} if this cannot be determined in advance. Once
   * frame acquisition succeeds, this value will be populated with the observed line count.
   */
  public int getLineCount() {
    return lineCount;
  }

  void setLineCount(int lineCount) {
    this.lineCount = lineCount;
  }

  /**
   * Returns the number of bits used to indicate the color of each pixel.
   */
  public int getDepthPerPixel() {
    return depthPerPixel;
  }

  @Override
  public String toString() {
    return "SaneParameters{"
        + "frameType="
        + frameType
        + ", lastFrame="
        + lastFrame
        + ", bytesPerLine="
        + bytesPerLine
        + ", pixelsPerLine="
        + pixelsPerLine
        + ", lineCount="
        + lineCount
        + ", depthPerPixel="
        + depthPerPixel
        + '}';
  }
}
