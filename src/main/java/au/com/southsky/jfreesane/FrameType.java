package au.com.southsky.jfreesane;

/**
 * Represents the various types of image frames in SANE.
 */
enum FrameType implements SaneEnum {
  GRAY(0),
  RGB(1),
  RED(2),
  GREEN(3),
  BLUE(4);

  private final int wireValue;

  FrameType(int wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public int getWireValue() {
    return wireValue;
  }
}
