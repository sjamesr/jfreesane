package au.com.southsky.jfreesane;

import com.google.common.truth.Truth;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Acquires a series of images from the test device and compares them against reference images
 * obtained from the "scanimage" SANE util. By default, the SANE server running on localhost is
 * used, but this can be overridden by specifying the address in "host[:port]" format in the
 * SANE_TEST_SERVER_ADDRESS environment variable.
 */
@RunWith(Parameterized.class)
public class ImageAcquisitionTest {
  private SanePasswordProvider correctPasswordProvider =
      SanePasswordProvider.forUsernameAndPassword("testuser", "goodpass");

  @Before
  public void initSession() throws Exception {
    String address = System.getenv("SANE_TEST_SERVER_ADDRESS");
    if (address == null) {
      address = "localhost";
    }
    URI hostAndPort = URI.create("my://" + address);
    this.session =
        SaneSession.withRemoteSane(
            InetAddress.getByName(hostAndPort.getHost()),
            hostAndPort.getPort() == -1 ? 6566 : hostAndPort.getPort());
    session.setPasswordProvider(correctPasswordProvider);
  }

  @Parameterized.Parameters(name = "device={0},mode={1},depth={2},pattern={3}")
  public static Iterable<Object[]> parameters() {
    List<String> devices = Collections.singletonList("test");
    List<String> modes = Arrays.asList("Gray", "Color");
    List<Integer> depths = Arrays.asList(1, 8, 16);
    List<String> patterns = Arrays.asList("Solid white", "Solid black", "Color pattern");
    List<Object[]> result =
        new ArrayList<>(depths.size() * modes.size() * depths.size() * patterns.size());
    for (String dev : devices) {
      for (String mode : modes) {
        for (int depth : depths) {
          for (String pattern : patterns) {
            result.add(new Object[] {dev, mode, depth, pattern});
          }
        }
      }
    }
    return result;
  }

  private SaneSession session;

  private final String device;
  private final String mode;
  private final int depth;
  private final String pattern;

  public ImageAcquisitionTest(String device, String mode, int depth, String pattern) {
    this.device = device;
    this.mode = mode;
    this.depth = depth;
    this.pattern = pattern;
  }

  @Test
  public void testImageAcquisition() throws IOException, SaneException {
    Assume.assumeFalse("color tests at depth 1 are skipped", depth == 1 && "Color".equals(mode));
    BufferedImage expectedImage = getExpectedImage();

    try (SaneDevice dev = session.getDevice(device)) {
      dev.open();
      Truth.assertThat(dev.getOption("mode").setStringValue(mode)).isEqualTo(mode);
      Truth.assertThat(dev.getOption("depth").setIntegerValue(depth)).isEqualTo(depth);
      Truth.assertThat(dev.getOption("test-picture").setStringValue(pattern)).isEqualTo(pattern);
      BufferedImage actualImage = dev.acquireImage();
      assertImagesEqual(expectedImage, actualImage);
    }
  }

  /**
   * Reads the expected image from test resources. If those resources are not present, scanimage is
   * invoked to read the reference image.
   */
  private BufferedImage getExpectedImage() throws IOException {
    String resourceName = String.format("/%s-%d-%s.png", mode, depth, pattern.replace(" ", "_"));
    InputStream resource = ImageAcquisitionTest.class.getResource(resourceName).openStream();
    return ImageIO.read(resource);
  }

  private void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
    assertEquals("image widths differ", expected.getWidth(), actual.getWidth());
    assertEquals("image heights differ", expected.getHeight(), actual.getHeight());

    for (int x = 0; x < expected.getWidth(); x++) {
      for (int y = 0; y < expected.getHeight(); y++) {
        Truth.assertThat(actual.getRGB(x, y)).isEqualTo(expected.getRGB(x, y));
      }
    }
  }
}
