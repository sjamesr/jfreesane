package au.com.southsky.jfreesane;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.SettableFuture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests JFreeSane's interactions with the backend.
 *
 * <p>
 * This test assumes a sane daemon is listening on port 6566 on the local host. The daemon must have
 * a password-protected device named 'test'. The username should be 'testuser' and the password
 * should be 'goodpass'.
 *
 * <p>
 * If you cannot run a SANE server locally, you can set the {@code SANE_TEST_SERVER_ADDRESS}
 * environment variable to the address of a SANE server in {@link HostAndPort} format.
 *
 * <p>
 * If you can't create this test environment, feel free to add the {@link org.junit.Ignore}
 * annotation to the test class.
 *
 * @author James Ring (sjr@jdns.org)
 */
@RunWith(JUnit4.class)
public class SaneSessionTest {

  private static final Logger log = Logger.getLogger(SaneSessionTest.class.getName());
  private SaneSession session;
  private static final Logger jfreesaneLogger = Logger.getLogger("au.com.southsky.jfreesane");
  private SanePasswordProvider correctPasswordProvider =
      SanePasswordProvider.forUsernameAndPassword("testuser", "goodpass");

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Before
  public void initSession() throws Exception {
    HostAndPort hostAndPort;
    String address = System.getenv("SANE_TEST_SERVER_ADDRESS");
    if (address == null) {
      address = "localhost";
    }
    hostAndPort = HostAndPort.fromString(address);
    this.session =
        SaneSession.withRemoteSane(
            InetAddress.getByName(hostAndPort.getHostText()), hostAndPort.getPortOrDefault(6566));
    session.setPasswordProvider(correctPasswordProvider);
  }

  @After
  public void closeSession() throws Exception {
    Closeables.close(session, false);
  }

  @Test
  public void listDevicesSucceeds() throws Exception {
    List<SaneDevice> devices = session.listDevices();
    log.info("Got " + devices.size() + " device(s): " + devices);
    // Sadly the test device apparently does not show up in the device list.
    // assertThat(devices).isNotEmpty();
  }

  @Test
  public void openDeviceSucceeds() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
    } finally {
      device.close();
    }
  }

  @Test
  public void optionGroupsArePopulated() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();
      assertThat(device.getOptionGroups()).isNotEmpty();
    } finally {
      device.close();
    }
  }

  @Test
  public void imageAcquisitionSucceeds() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
      BufferedImage image = device.acquireImage();
      File file = File.createTempFile("image", ".png");
      ImageIO.write(image, "png", file);
      System.out.println("Successfully wrote " + file);
    } finally {
      device.close();
    }
  }

  @Test
  public void listOptionsSucceeds() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
      List<SaneOption> options = device.listOptions();
      Assert.assertTrue("Expect multiple SaneOptions", options.size() > 0);
      System.out.println("We found " + options.size() + " options");
      for (SaneOption option : options) {
        System.out.println(option.toString());
        if (option.getType() != OptionValueType.BUTTON) {
          System.out.println(option.getValueCount());
        }
      }
    } finally {
      device.close();
    }
  }

  @Test
  public void getOptionValueSucceeds() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
      List<SaneOption> options = device.listOptions();
      Assert.assertTrue("Expect multiple SaneOptions", options.size() > 0);
      // option 0 is always "Number of options"
      // must be greater than zero

      int optionCount = options.get(0).getIntegerValue();
      Assert.assertTrue("Option count must be > 0", optionCount > 0);

      // print out the value of all integer-valued options

      for (SaneOption option : options) {
        System.out.print(option.getTitle());

        if (!option.isActive()) {
          System.out.print(" [inactive]");
        } else {
          if (option.getType() == OptionValueType.INT
              && option.getValueCount() == 1
              && option.isActive()) {
            System.out.print("=" + option.getIntegerValue());
          } else if (option.getType() == OptionValueType.STRING) {
            System.out.print("=" + option.getStringValue(Charsets.US_ASCII));
          }
        }

        System.out.println();
      }
    } finally {
      device.close();
    }
  }

  @Test
  public void setOptionValueSucceedsForString() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();
      SaneOption modeOption = device.getOption("mode");
      assertThat(modeOption.setStringValue("Gray")).isEqualTo("Gray");
    } finally {
      device.close();
    }
  }

  @Test
  public void adfAcquisitionSucceeds() throws Exception {
    SaneDevice device = session.getDevice("test");
    device.open();
    assertThat(device.getOption("source").getStringConstraints())
        .contains("Automatic Document Feeder");
    device.getOption("source").setStringValue("Automatic Document Feeder");

    for (int i = 0; i < 20; i++) {
      try {
        device.acquireImage();
      } catch (SaneException e) {
        if (e.getStatus() == SaneStatus.STATUS_NO_DOCS) {
          // out of documents to read, that's fine
          break;
        } else {
          throw e;
        }
      }
    }
  }

  @Test
  public void acquireImageSucceedsAfterOutOfPaperCondition() throws Exception {
    SaneDevice device = session.getDevice("test");
    device.open();
    assertThat(device.getOption("source").getStringConstraints())
        .contains("Automatic Document Feeder");
    device.getOption("source").setStringValue("Automatic Document Feeder");

    expectedException.expect(SaneException.class);
    expectedException.expectMessage("STATUS_NO_DOCS");
    for (int i = 0; i < 20; i++) {
      device.acquireImage();
    }
  }

  @Test
  public void acquireMonoImage() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();
      SaneOption modeOption = device.getOption("mode");
      assertEquals("Gray", modeOption.setStringValue("Gray"));
      BufferedImage image = device.acquireImage();

      File file = File.createTempFile("mono-image", ".png");
      ImageIO.write(image, "png", file);
      System.out.println("Successfully wrote " + file);
    } finally {
      device.close();
    }
  }

  /**
   * Tests that this SANE client produces images that match
   * {@link "http://www.meier-geinitz.de/sane/test-backend/test-pictures.html"} .
   */
  @Test
  public void producesCorrectImages() throws Exception {
    SaneDevice device = session.getDevice("test");
    // Solid black and white
    try {
      device.open();
      device.getOption("br-x").setFixedValue(200);
      device.getOption("br-y").setFixedValue(200);

      /*
       * assertProducesCorrectImage(device, "Gray", 1, "Solid white");
       * assertProducesCorrectImage(device, "Gray", 8, "Solid white");
       * assertProducesCorrectImage(device, "Gray", 16, "Solid white");
       * assertProducesCorrectImage(device, "Gray", 1, "Solid black");
       * assertProducesCorrectImage(device, "Gray", 8, "Solid black");
       * assertProducesCorrectImage(device, "Gray", 16, "Solid black");
       *
       * assertProducesCorrectImage(device, "Color", 1, "Solid white");
       * assertProducesCorrectImage(device, "Color", 8, "Solid white");
       * assertProducesCorrectImage(device, "Color", 16, "Solid white");
       * assertProducesCorrectImage(device, "Color", 1, "Solid black");
       * assertProducesCorrectImage(device, "Color", 8, "Solid black");
       * assertProducesCorrectImage(device, "Color", 16, "Solid black");
       *
       * assertProducesCorrectImage(device, "Gray", 1, "Color pattern");
       * assertProducesCorrectImage(device, "Color", 1, "Color pattern");
       *
       * assertProducesCorrectImage(device, "Gray", 8, "Color pattern");
       * assertProducesCorrectImage(device, "Color", 8, "Color pattern");
       */

      assertProducesCorrectImage(device, "Gray", 1, "Grid");
      //      assertProducesCorrectImage(device, "Color", 1, "Color pattern");

      assertProducesCorrectImage(device, "Color", 8, "Color pattern");
      assertProducesCorrectImage(device, "Color", 16, "Color pattern");
    } finally {
      device.close();
    }
  }

  @Test
  public void readsAndSetsStringsCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();
      assertThat(device.getOption("mode").getStringValue(Charsets.US_ASCII)).matches("Gray|Color");
      assertThat(device.getOption("mode").setStringValue("Gray")).isEqualTo("Gray");
      assertThat(device.getOption("mode").getStringValue(Charsets.US_ASCII)).isEqualTo("Gray");
      assertThat(device.getOption("read-return-value").getStringValue(Charsets.US_ASCII))
          .isEqualTo("Default");
    } finally {
      device.close();
    }
  }

  @Test
  public void readsFixedPrecisionCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      // this option gets rounded to the nearest whole number by the backend
      assertEquals(123, device.getOption("br-x").setFixedValue(123.456), 0.0001);
      assertEquals(123, device.getOption("br-x").getFixedValue(), 0.0001);
    } finally {
      device.close();
    }
  }

  @Test
  public void readsBooleanOptionsCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      SaneOption option = device.getOption("hand-scanner");
      assertThat(option.setBooleanValue(true)).isTrue();
      assertThat(option.getBooleanValue()).isTrue();
      assertThat(option.setBooleanValue(false)).isFalse();
      assertThat(option.getBooleanValue()).isFalse();
    } finally {
      device.close();
    }
  }

  @Test
  public void readsStringListConstraintsCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      SaneOption option = device.getOption("string-constraint-string-list");
      assertThat(option).isNotNull();
      assertThat(option.getConstraintType())
          .isEqualTo(OptionValueConstraintType.STRING_LIST_CONSTRAINT);
      assertThat(option.getStringConstraints())
          .has()
          .exactly(
              "First entry",
              "Second entry",
              "This is the very long third entry. Maybe the frontend has an idea how to display it");
    } finally {
      device.close();
    }
  }

  @Test
  public void readIntegerValueListConstraintsCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      SaneOption option = device.getOption("int-constraint-word-list");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.VALUE_LIST_CONSTRAINT, option.getConstraintType());
      assertEquals(
          ImmutableList.of(-42, -8, 0, 17, 42, 256, 65536, 16777216, 1073741824),
          option.getIntegerValueListConstraint());
    } finally {
      device.close();
    }
  }

  @Test
  public void readFixedValueListConstraintsCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      SaneOption option = device.getOption("fixed-constraint-word-list");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.VALUE_LIST_CONSTRAINT, option.getConstraintType());
      List<Double> expected = ImmutableList.of(-32.7d, 12.1d, 42d, 129.5d);
      List<Double> actual = option.getFixedValueListConstraint();
      assertEquals(expected.size(), actual.size());

      for (int i = 0; i < expected.size(); i++) {
        assertEquals(expected.get(i), actual.get(i), 0.00001);
      }

    } finally {
      device.close();
    }
  }

  @Test
  public void readIntegerConstraintRangeCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      SaneOption option = device.getOption("int-constraint-range");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.RANGE_CONSTRAINT, option.getConstraintType());
      assertEquals(4, option.getRangeConstraints().getMinimumInteger());
      assertEquals(192, option.getRangeConstraints().getMaximumInteger());
      assertEquals(2, option.getRangeConstraints().getQuantumInteger());
    } finally {
      device.close();
    }
  }

  @Test
  public void readFixedConstraintRangeCorrectly() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();

      SaneOption option = device.getOption("fixed-constraint-range");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.RANGE_CONSTRAINT, option.getConstraintType());
      assertEquals(-42.17, option.getRangeConstraints().getMinimumFixed(), 0.00001);
      assertEquals(32767.9999, option.getRangeConstraints().getMaximumFixed(), 0.00001);
      assertEquals(2.0, option.getRangeConstraints().getQuantumFixed(), 0.00001);
    } finally {
      device.close();
    }
  }

  @Test
  public void arrayOption() throws Exception {
    SaneDevice device = session.getDevice("test");

    try {
      device.open();
      device.getOption("enable-test-options").setBooleanValue(true);

      SaneOption option = device.getOption("int-constraint-array-constraint-range");
      assertNotNull(option);
      assertThat(option.isConstrained()).isTrue();
      assertThat(option.getConstraintType()).isEqualTo(OptionValueConstraintType.RANGE_CONSTRAINT);
      assertEquals(OptionValueType.INT, option.getType());
      List<Integer> values = Lists.newArrayList();

      RangeConstraint constraints = option.getRangeConstraints();
      for (int i = 0; i < option.getValueCount(); i++) {
        values.add(constraints.getMinimumInteger() + i * constraints.getQuantumInteger());
      }

      assertEquals(values, option.setIntegerValue(values));
      assertEquals(values, option.getIntegerArrayValue());
    } finally {
      device.close();
    }
  }

  @Test
  @Ignore // This test fails on Travis with UNSUPPORTED.
  public void multipleListDevicesCalls() throws Exception {
    session.listDevices();
    session.listDevices();
  }

  @Test
  public void multipleGetDeviceCalls() throws Exception {
    session.getDevice("test");
    session.getDevice("test");
  }

  @Test
  public void multipleOpenDeviceCalls() throws Exception {
    {
      SaneDevice device = session.getDevice("test");
      openAndCloseDevice(device);
    }

    {
      SaneDevice device = session.getDevice("test");
      openAndCloseDevice(device);
    }
  }

  @Test
  public void handScanning() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
      device.getOption("hand-scanner").setBooleanValue(true);
      device.acquireImage();
    } finally {
      device.close();
    }
  }

  @Test
  public void threePassScanning() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
      assertEquals(
          "Color pattern", device.getOption("test-picture").setStringValue("Color pattern"));
      assertEquals("Color", device.getOption("mode").setStringValue("Color"));
      assertEquals(true, device.getOption("three-pass").setBooleanValue(true));
      for (int i = 0; i < 5; i++) {
        File file = File.createTempFile("three-pass", ".png");
        ImageIO.write(device.acquireImage(), "png", file);
        System.out.println("Wrote three-pass test to " + file);
      }
    } finally {
      device.close();
    }
  }

  @Test
  public void reducedArea() throws Exception {
    SaneDevice device = session.getDevice("test");
    try {
      device.open();
      device.getOption("mode").setStringValue("Color");
      device.getOption("resolution").setFixedValue(200);
      device.getOption("tl-x").setFixedValue(0.0);
      device.getOption("tl-y").setFixedValue(0.0);
      device.getOption("br-x").setFixedValue(105.0);
      device.getOption("br-y").setFixedValue(149.0);
      device.acquireImage();
    } finally {
      device.close();
    }
  }

  @Test
  public void passwordAuthentication() throws Exception {
    // assumes that test is a password-authenticated device
    SaneDevice device = session.getDevice("test");
    device.open();
    device.acquireImage();
  }

  /**
   * This test assumes that you have protected the "test" device with a username of "testuser" and a
   * password other than "badpassword".
   */
  @Test
  public void invalidPasswordCausesAccessDeniedError() throws Exception {
    session.setPasswordProvider(
        SanePasswordProvider.forUsernameAndPassword("testuser", "badpassword"));
    try {
      SaneDevice device = session.getDevice("test");
      expectedException.expect(SaneException.class);
      expectedException.expectMessage("STATUS_ACCESS_DENIED");
      device.open();
    } finally {
      // Restore the session's password provider.
      session.setPasswordProvider(correctPasswordProvider);
    }
  }

  @Test
  public void passwordAuthenticationFromLocalFileSpecified() throws Exception {
    File passwordFile = File.createTempFile("sane", ".pass");
    try {
      Files.write("testuser:goodpass:test", passwordFile, Charsets.ISO_8859_1);
      session.setPasswordProvider(
          SanePasswordProvider.usingSanePassFile(passwordFile.getAbsolutePath()));
      SaneDevice device = session.getDevice("test");
      device.open();
      device.acquireImage();
    } finally {
      passwordFile.delete();
    }
  }

  @Test
  public void listenerReceivesScanStartedEvent() throws Exception {
    final SettableFuture<SaneDevice> notifiedDevice = SettableFuture.create();
    final AtomicInteger frameCount = new AtomicInteger();

    ScanListener listener =
        new ScanListenerAdapter() {
          @Override
          public void scanningStarted(SaneDevice device) {
            notifiedDevice.set(device);
          }

          @Override
          public void frameAcquisitionStarted(
              SaneDevice device,
              SaneParameters parameters,
              int currentFrame,
              int likelyTotalFrames) {
            frameCount.incrementAndGet();
          }
        };

    SaneDevice device = session.getDevice("test");
    device.open();
    device.getOption("resolution").setFixedValue(1200);
    device.getOption("mode").setStringValue("Color");
    device.getOption("three-pass").setBooleanValue(true);
    device.acquireImage(listener);
    assertThat(notifiedDevice.get()).isSameAs(device);
    assertThat(frameCount.get()).isEqualTo(3);
  }

  private void openAndCloseDevice(SaneDevice device) throws Exception {
    try {
      device.open();
      device.listOptions();
    } finally {
      device.close();
    }
  }

  private void assertProducesCorrectImage(
      SaneDevice device, String mode, int sampleDepth, String testPicture)
      throws IOException, SaneException {
    BufferedImage actualImage = acquireImage(device, mode, sampleDepth, testPicture);

    writeImage(mode, sampleDepth, testPicture, actualImage);

    if (testPicture.startsWith("Solid")) {
      assertImageSolidColor(testPicture.endsWith("black") ? Color.black : Color.white, actualImage);
    }
    // TODO(sjr): compare with reference images.
  }

  private void writeImage(
      String mode, int sampleDepth, String testPicture, BufferedImage actualImage)
      throws IOException {
    File file =
        File.createTempFile(
            String.format("image-%s-%d-%s", mode, sampleDepth, testPicture.replace(' ', '_')),
            ".png");
    ImageIO.write(actualImage, "png", file);
    System.out.println("Successfully wrote " + file);
  }

  private void assertImageSolidColor(Color color, BufferedImage image) {
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        assertEquals(color.getRGB(), image.getRGB(x, y));
      }
    }
  }

  private BufferedImage acquireImage(
      SaneDevice device, String mode, int sampleDepth, String testPicture)
      throws IOException, SaneException {
    device.getOption("mode").setStringValue(mode);
    device.getOption("depth").setIntegerValue(sampleDepth);
    device.getOption("test-picture").setStringValue(testPicture);

    return device.acquireImage();
  }

  private void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
    assertEquals("image widths differ", expected.getWidth(), actual.getWidth());
    assertEquals("image heights differ", expected.getHeight(), actual.getHeight());

    Raster expectedRaster = expected.getRaster();
    Raster actualRaster = actual.getRaster();

    for (int x = 0; x < expected.getWidth(); x++) {
      for (int y = 0; y < expected.getHeight(); y++) {
        int[] expectedPixels = expectedRaster.getPixel(x, y, (int[]) null);
        int[] actualPixels = actualRaster.getPixel(x, y, (int[]) null);

        // assert that all the samples are the same for the given pixel
        Assert.assertArrayEquals(expectedPixels, actualPixels);
      }
    }
  }
}
