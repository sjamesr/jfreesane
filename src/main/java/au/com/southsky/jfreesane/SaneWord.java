package au.com.southsky.jfreesane;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * Represents a SANE word type.
 * 
 * @author James Ring (sjr@jdns.org)
 */
public class SaneWord {
  public static final int SIZE_IN_BYTES = 4;

  private final byte[] value;

  private SaneWord(byte[] value) {
    this.value = value;
  }

  public static SaneWord fromStream(InputStream input) throws IOException {
    byte[] newValue = new byte[SIZE_IN_BYTES];
    if (input.read(newValue) != newValue.length) {
      throw new IOException("input stream was truncated while reading a word");
    }

    return new SaneWord(newValue);
  }

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

  public static SaneWord forSaneVersion(int major, int minor, int build) {
    int result = (major & 0xff) << 24;
    result |= (minor & 0xff) << 16;
    result |= (build & 0xffff) << 0;
    return forInt(result);
  }

  public byte[] getValue() {
    return Arrays.copyOf(value, value.length);
  }

  public int integerValue() {
    try {
      return new DataInputStream(new ByteArrayInputStream(value)).readInt();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public double fixedPrecisionValue() {
    return (double) integerValue() / (1 << 16);
  }
  
  public static SaneWord fromBytes(byte[] byteValue) {
    Preconditions.checkArgument(byteValue.length == SIZE_IN_BYTES);
    return new SaneWord(Arrays.copyOf(byteValue, SIZE_IN_BYTES));
  }
  
  @Override
  public String toString() {
    return Arrays.toString(value);
  }

  public static SaneWord forFixedPrecision(double value) {
    return SaneWord.forInt((int) (value * (1 << 16)));
  }
}