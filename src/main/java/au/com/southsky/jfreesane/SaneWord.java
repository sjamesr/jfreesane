package au.com.southsky.jfreesane;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

/**
 * Represents a SANE word type. JFreeSane chooses to represent the SANE word type as an array of
 * {@link #SIZE_IN_BYTES} bytes.
 *
 * <p>
 * See <a href="http://www.sane-project.org/html/doc011.html#s4.2.1">the SANE specification</a> for
 * a thorough discussion about the SANE word type.
 *
 * @author James Ring (sjr@jdns.org)
 */
public final class SaneWord {

  /**
   * The number of bytes used to represent a SANE word.
   */
  public static final int SIZE_IN_BYTES = 4;

  private static final int PRECISION = 1 << 16;

  /**
   * A function that, when applied to a {@link SaneWord} instance, returns the integer value of that
   * SANE word.
   *
   * @see SaneWord#integerValue
   */
  public static final Function<SaneWord, Integer> TO_INTEGER_FUNCTION = new Function<SaneWord, Integer>() {
    @Override
    public Integer apply(SaneWord word) {
      return word.integerValue();
    }
  };

  /**
   * A function that, when applied to a {@link SaneWord} instance, returns the SANE fixed precision
   * value of that SANE word.
   *
   * @see SaneWord#fixedPrecisionValue
   */
  public static final Function<SaneWord, Double> TO_FIXED_FUNCTION = new Function<SaneWord, Double>() {
    @Override
    public Double apply(SaneWord word) {
      return word.fixedPrecisionValue();
    }
  };

  private final byte[] value;

  private SaneWord(byte[] value) {
    this.value = value;
  }

  /**
   * Returns a new {@code SaneWord} by consuming {@link #SIZE_IN_BYTES} bytes from the given
   * {@link InputStream}.
   */
  public static SaneWord fromStream(InputStream input) throws IOException {
    byte[] newValue = new byte[SIZE_IN_BYTES];
    if (input.read(newValue) != newValue.length) {
      throw new IOException("input stream was truncated while reading a word");
    }

    return new SaneWord(newValue);
  }

  /**
   * Returns a new {@code SaneWord} representing the given integer value.
   */
  public static SaneWord forInt(int value) {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream(SIZE_IN_BYTES);
    DataOutputStream stream = new DataOutputStream(byteStream);
    try {
      stream.writeInt(value);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    return new SaneWord(byteStream.toByteArray());
  }

  /**
   * Returns a new {@code SaneWord} representing the given SANE version.
   *
   * @param major
   *          the SANE major version
   * @param minor
   *          the SANE minor version
   * @param build
   *          the SANE build identifier
   */
  public static SaneWord forSaneVersion(int major, int minor, int build) {
    int result = (major & 0xff) << 24;
    result |= (minor & 0xff) << 16;
    result |= (build & 0xffff) << 0;
    return forInt(result);
  }

  /**
   * Returns a copy of the underlying byte array representing this {@link SaneWord}. The length of
   * the array is {@link #SIZE_IN_BYTES} bytes.
   */
  public byte[] getValue() {
    return Arrays.copyOf(value, value.length);
  }

  /**
   * Treats this {@link SaneWord} as an integer and returns the represented value.
   */
  public int integerValue() {
    try {
      return new DataInputStream(new ByteArrayInputStream(value)).readInt();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the value of this {@link SaneWord} treated as a SANE fixed precision value.
   */
  public double fixedPrecisionValue() {
    return (double) integerValue() / PRECISION;
  }

  /**
   * Creates a new {@link SaneWord} from a copy of the given byte array. The array must be of length
   * {@link #SIZE_IN_BYTES}, anything else will cause a runtime exception to be thrown.
   */
  public static SaneWord fromBytes(byte[] byteValue) {
    return fromBytes(byteValue, 0);
  }

  /**
   * Creates a new {@link SaneWord} from a copy of the given bytes within the array.
   * {@code offset + SIZE_IN_BYTES} must be a valid index (i.e. there must be enough bytes in the
   * array at the given offset), otherwise a runtime exception is thrown.
   */
  public static SaneWord fromBytes(byte[] byteValue, int offset) {
    Preconditions.checkArgument(offset >= 0, "offset must be positive or zero");
    Preconditions.checkArgument(offset + SIZE_IN_BYTES <= byteValue.length);
    return new SaneWord(Arrays.copyOfRange(byteValue, offset, offset + SIZE_IN_BYTES));
  }

  @Override
  public String toString() {
    return Arrays.toString(value);
  }

  /**
   * Creates a new {@link SaneWord} from the given double. If {@code value} cannot be exactly
   * represented in SANE's fixed precision scheme, then
   * {@code SaneWord.forFixedPrecision(someValue).fixedPrecisionValue()} will not necessarily yield
   * {@code someValue}.
   */
  public static SaneWord forFixedPrecision(double value) {
    return SaneWord.forInt((int) (value * PRECISION));
  }
}
