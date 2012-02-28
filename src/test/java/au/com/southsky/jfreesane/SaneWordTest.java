package au.com.southsky.jfreesane;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * This class implements tests for {@link SaneWord}.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneWordTest {
  @Test
  public void testFixedPrecisionValue() {
    assertEquals(216.069, SaneWord.forFixedPrecision(216.069).fixedPrecisionValue(), 0.0001);
  }

  @Test
  public void fromArrayWithOffset() {
    byte[] array = new byte[] {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4};
    assertEquals(0, SaneWord.fromBytes(array, 0).integerValue());
    assertEquals(1, SaneWord.fromBytes(array, 4).integerValue());
    assertEquals(2, SaneWord.fromBytes(array, 8).integerValue());
    assertEquals(3, SaneWord.fromBytes(array, 12).integerValue());
    assertEquals(4, SaneWord.fromBytes(array, 16).integerValue());
  }
}
