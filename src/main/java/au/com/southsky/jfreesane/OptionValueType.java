package au.com.southsky.jfreesane;

/**
 * Instances of this enum are returned by {@link SaneOption#getType} that indicate the type of value
 * that the option has.
 */
public enum OptionValueType implements SaneEnum {
  /**
   * The option's value is a boolean and can be written with {@link SaneOption#setBooleanValue} and
   * read by {@link SaneOption#getBooleanValue}.
   */
  BOOLEAN(0),

  /**
   * The option's value is an integer and can be written with {@link SaneOption#setIntegerValue} and
   * read by {@link SaneOption#getIntegerValue}.
   */
  INT(1),

  /**
   * The option's value is of SANE's fixed-precision type and can be written with
   * {@link SaneOption#setFixedValue} and read by {@link SaneOption#getFixedValue}.
   */
  FIXED(2),

  /**
   * The option's value is a string and can be written with {@link SaneOption#setStringValue} and
   * read by {@link SaneOption#getStringValue}.
   */
  STRING(3), BUTTON(4), GROUP(5);

  private int typeNo;

  private OptionValueType(int typeNo) {
    this.typeNo = typeNo;
  }

  @Override
  public int getWireValue() {
    return typeNo;
  }
}