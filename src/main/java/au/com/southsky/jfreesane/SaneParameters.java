package au.com.southsky.jfreesane;

import com.google.common.base.MoreObjects;

public class SaneParameters {
  private final FrameType frameType;
  private final boolean lastFrame;
  private final int bytesPerLine;
  private final int pixelsPerLine;
  private int lineCount;
  private final int depthPerPixel;

  public SaneParameters(
      int frame, boolean lastFrame, int bytesPerLine, int pixelsPerLine, int lines, int depth) {
    this.frameType = SaneEnums.valueOf(FrameType.class, frame);
    this.lastFrame = lastFrame;
    this.bytesPerLine = bytesPerLine;
    this.pixelsPerLine = pixelsPerLine;
    this.lineCount = lines;
    this.depthPerPixel = depth;
  }

  public FrameType getFrameType() {
    return frameType;
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

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(SaneParameters.class).add("frameType", frameType)
        .add("isLastFrame", lastFrame).add("bytesPerLine", bytesPerLine)
        .add("pixelsPerLine", pixelsPerLine).add("lineCount", lineCount)
        .add("depthPerPixel", depthPerPixel).toString();
  }
}