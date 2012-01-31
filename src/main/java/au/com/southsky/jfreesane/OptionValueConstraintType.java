// Copyright 2012 Google Inc. All Rights Reserved.

package au.com.southsky.jfreesane;

/**
 * Represents the types of constraints that a {@link SaneOption} may be subjected to.
 *
 * @author James Ring (sjr@jdns.org)
 */
public enum OptionValueConstraintType implements SaneEnum {
  /**
   * The option has no constraints on its value.
   */
  NO_CONSTRAINT(0, "No constraint"),

  /**
   * The option's value is constrained to some range of values.
   */
  RANGE_CONSTRAINT(1, ""),

  /**
   * The option's value is constrained to some list of values.
   */
  VALUE_LIST_CONSTRAINT(2, ""),

  /**
   * The option's value type is {@link OptionValueType#STRING} and its value is constrained to some
   * list of string values.
   */
  STRING_LIST_CONSTRAINT(3, "");

  private final int wireValue;
  private final String description;

  private OptionValueConstraintType(int wireValue, String description) {
    this.wireValue = wireValue;
    this.description = description;
  }

  /**
   * Returns the description of the option as provided by the SANE backend.
   */
  public String description() {
    return description;
  }

  @Override
  public int getWireValue() {
    return wireValue;
  }
}