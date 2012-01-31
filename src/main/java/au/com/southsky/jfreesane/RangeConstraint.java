// Copyright 2012 Google Inc. All Rights Reserved.

package au.com.southsky.jfreesane;

/**
 * Represents a restriction on the acceptable values of an option. A constrained option (see
 * {@link SaneOption#isConstrained}) whose constraint type is
 * {@link OptionValueConstraintType#RANGE_CONSTRAINT} will return an instance of {@code
 * RangeConstraint} from its {@link SaneOption#getRangeConstraints} method.
 *
 *
 * @author James Ring (sjr@jdns.org)
 */
public class RangeConstraint {
  private final SaneWord min;
  private final SaneWord max;
  private final SaneWord quantum;

  RangeConstraint(SaneWord min, SaneWord max, SaneWord quantum) {
    this.min = min;
    this.max = max;
    this.quantum = quantum;
  }

  public int getMinimumInteger() {
    return min.integerValue();
  }

  public int getMaximumInteger() {
    return max.integerValue();
  }

  public int getQuantumInteger() {
    return quantum.integerValue();
  }

  public double getMinimumFixed() {
    return min.fixedPrecisionValue();
  }

  public double getMaximumFixed() {
    return max.fixedPrecisionValue();
  }

  public double getQuantumFixed() {
    return quantum.fixedPrecisionValue();
  }
}