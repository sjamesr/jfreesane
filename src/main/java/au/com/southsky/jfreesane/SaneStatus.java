package au.com.southsky.jfreesane;

/**
 * Represents the status codes that can be returned by the Sane backend.
 *
 * @author James Ring (sjr@jdns.org)
 */
public enum SaneStatus implements SaneEnum {
  STATUS_GOOD(0), STATUS_UNSUPPORTED(1), STATUS_CANCELLED(2), STATUS_DEVICE_BUSY(3), STATUS_INVAL(
      4), STATUS_EOF(5), STATUS_JAMMED(6), STATUS_NO_DOCS(7), STATUS_COVER_OPEN(8), STATUS_IO_ERROR(
      9), STATUS_NO_MEM(10), STATUS_ACCESS_DENIED(11);

  private final int wireValue;

  /**
   * Returns the status represented by the given wire type, or {@code null} if the status is not
   * known.
   */
  public static SaneStatus fromWireValue(int wireValue) {
    return SaneEnums.valueOf(SaneStatus.class, wireValue);
  }

  private SaneStatus(int wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public int getWireValue() {
    return wireValue;
  }
}
