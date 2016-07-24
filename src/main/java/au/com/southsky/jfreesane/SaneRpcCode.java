package au.com.southsky.jfreesane;

/**
 * Represents the opcodes of SANE RPC API calls.
 *
 * @author James Ring (sjr@jdns.org)
 */
enum SaneRpcCode implements SaneEnum {
  SANE_NET_INIT(0),
  SANE_NET_GET_DEVICES(1),
  SANE_NET_OPEN(2),
  SANE_NET_CLOSE(3),
  SANE_NET_GET_OPTION_DESCRIPTORS(4),
  SANE_NET_CONTROL_OPTION(5),
  SANE_NET_GET_PARAMETERS(6),
  SANE_NET_START(7),
  SANE_NET_CANCEL(8),
  SANE_NET_AUTHORIZE(9),
  SANE_NET_EXIT(10);

  private final int wireValue;

  SaneRpcCode(int wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public int getWireValue() {
    return wireValue;
  }
}
