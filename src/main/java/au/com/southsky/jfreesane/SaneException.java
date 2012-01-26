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

  public SaneStatus getStatus() {
    return status;
  }
}
