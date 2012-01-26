package au.com.southsky.jfreesane;

/**
 * Enumerations that implement this interface may be serialized in the SANE network protocol. You
 * may use {@link SaneEnums#valueOf} to look up instances of this interface by their wire value.
 *
 * @author James Ring (sjr@jdns.org)
 */
public interface SaneEnum {
  int getWireValue();
}
