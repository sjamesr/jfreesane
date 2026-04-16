package au.com.southsky.jfreesane;

import static org.junit.Assert.assertEquals;

import com.google.common.truth.Truth;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * This class implements tests for {@link SaneWord}.
 *
 * @author James Ring (sjr@jdns.org)
 */
@RunWith(JUnit4.class)
public class SaneWordTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

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

  @Test
  public void fromStream() throws Exception {
    byte[] array = new byte[] {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4};
    InputStream stream = new ByteArrayInputStream(array);
    Truth.assertThat(SaneWord.fromStream(stream).integerValue()).isEqualTo(0);
    Truth.assertThat(SaneWord.fromStream(stream).integerValue()).isEqualTo(1);
    Truth.assertThat(SaneWord.fromStream(stream).integerValue()).isEqualTo(2);
    Truth.assertThat(SaneWord.fromStream(stream).integerValue()).isEqualTo(3);
    Truth.assertThat(SaneWord.fromStream(stream).integerValue()).isEqualTo(4);
    expectedException.expect(IOException.class);
    SaneWord.fromStream(stream);
  }
}
