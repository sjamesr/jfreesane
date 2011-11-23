package au.com.southsky.jfreesane;

import static org.junit.Assert.*;

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
}
