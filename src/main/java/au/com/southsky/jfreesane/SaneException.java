package au.com.southsky.jfreesane;

/**
 * Represents an application level exception thrown by Sane.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneException extends Exception {
  private final SaneStatus status;

  public SaneException(SaneStatus status) {
    super(status == null ? "no status" : status.toString());
    this.status = status;
  }

  public SaneException(String message) {
    super(message);
    status = null;
  }

  public static SaneException fromStatusWord(SaneWord statusWord) {
    SaneStatus status = SaneStatus.fromWireValue(statusWord);
    if (status != null) {
      return new SaneException(status);
    } else {
      return new SaneException("unknown status (" + statusWord.integerValue() + ")");
    }
  }

  /** Returns the reason that this exception was thrown, or {@code null} if none is known. */
  public SaneStatus getStatus() {
    return status;
  }
}
