package au.com.southsky.jfreesane;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests JFreeSane's interactions with the backend.
 *
 * <p>This test starts a local sane daemon for each test case, giving tests the flexibility to
 * exercise various sane daemon configurations.
 *
 * <p>If the tests fail to start the sane daemon, you can set the {@code SANE_TEST_SERVER_ADDRESS}
 * environment variable to the address of a SANE server in "host[:port]" format.
 *
 * <p>If you can't create this test environment, feel free to add the {@link org.junit.Ignore}
 * annotation to the test class for local development.
 *
 * @author James Ring (sjr@jdns.org)
 */
@RunWith(JUnit4.class)
public class SaneSessionTest {

  private static final Logger log = Logger.getLogger(SaneSessionTest.class.getName());

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Rule public SaneDaemonRule saneDaemon = new SaneDaemonRule(tempFolder);

  @Test
  public void listDevicesSucceeds() throws Exception {
    List<SaneDevice> devices = saneDaemon.getSession().listDevices();
    log.info("Got " + devices.size() + " device(s): " + devices);
    // Sadly the test device apparently does not show up in the device list.
    // assertThat(devices).isNotEmpty();
  }

  @Test
  public void openDeviceSucceeds() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
    }
  }

  @Test
  public void optionGroupsArePopulated() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      assertThat(device.getOptionGroups()).isNotEmpty();
    }
  }

  @Test
  public void imageAcquisitionSucceeds() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      BufferedImage image = device.acquireImage();
      File file = File.createTempFile("image", ".png", tempFolder.getRoot());
      ImageIO.write(image, "png", file);
      System.out.println("Successfully wrote " + file);
    }
  }

  @Test
  public void listOptionsSucceeds() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
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
    }
  }

  @Test
  public void getOptionValueSucceeds() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
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
            System.out.print("=" + option.getStringValue(StandardCharsets.US_ASCII));
          }
        }

        System.out.println();
      }
    }
  }

  @Test
  public void setOptionValueSucceedsForString() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      SaneOption modeOption = device.getOption("mode");
      assertThat(modeOption.setStringValue("Gray")).isEqualTo("Gray");
    }
  }

  @Test
  public void adfAcquisitionSucceeds() throws Exception {
    SaneDevice device = saneDaemon.getSession().getDevice("test");
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
    SaneDevice device = saneDaemon.getSession().getDevice("test");
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

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      SaneOption modeOption = device.getOption("mode");
      assertEquals("Gray", modeOption.setStringValue("Gray"));
      BufferedImage image = device.acquireImage();

      File file = File.createTempFile("mono-image", ".png", tempFolder.getRoot());
      ImageIO.write(image, "png", file);
      System.out.println("Successfully wrote " + file);
    }
  }

  @Test
  public void readsAndSetsStringsCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      assertThat(device.getOption("mode").getStringValue(StandardCharsets.US_ASCII))
          .matches("Gray|Color");
      assertThat(device.getOption("mode").setStringValue("Gray")).isEqualTo("Gray");
      assertThat(device.getOption("mode").getStringValue(StandardCharsets.US_ASCII))
          .isEqualTo("Gray");
      assertThat(device.getOption("read-return-value").getStringValue(StandardCharsets.US_ASCII))
          .isEqualTo("Default");
    }
  }

  @Test
  public void readsFixedPrecisionCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();

      // this option gets rounded to the nearest whole number by the backend
      assertEquals(123, device.getOption("br-x").setFixedValue(123.456), 0.0001);
      assertEquals(123, device.getOption("br-x").getFixedValue(), 0.0001);
    }
  }

  @Test
  public void readsBooleanOptionsCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();

      SaneOption option = device.getOption("hand-scanner");
      assertThat(option.setBooleanValue(true)).isTrue();
      assertThat(option.getBooleanValue()).isTrue();
      assertThat(option.setBooleanValue(false)).isFalse();
      assertThat(option.getBooleanValue()).isFalse();
    }
  }

  @Test
  public void readsStringListConstraintsCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
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
    }
  }

  @Test
  public void readIntegerValueListConstraintsCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();

      SaneOption option = device.getOption("int-constraint-word-list");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.VALUE_LIST_CONSTRAINT, option.getConstraintType());
      assertEquals(
          Arrays.asList(-42, -8, 0, 17, 42, 256, 65536, 16777216, 1073741824),
          option.getIntegerValueListConstraint());
    }
  }

  @Test
  public void readFixedValueListConstraintsCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();

      SaneOption option = device.getOption("fixed-constraint-word-list");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.VALUE_LIST_CONSTRAINT, option.getConstraintType());
      List<Double> expected = Arrays.asList(-32.7d, 12.1d, 42d, 129.5d);
      List<Double> actual = option.getFixedValueListConstraint();
      assertEquals(expected.size(), actual.size());

      for (int i = 0; i < expected.size(); i++) {
        assertEquals(expected.get(i), actual.get(i), 0.00001);
      }
    }
  }

  @Test
  public void readIntegerConstraintRangeCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();

      SaneOption option = device.getOption("int-constraint-range");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.RANGE_CONSTRAINT, option.getConstraintType());
      assertEquals(4, option.getRangeConstraints().getMinimumInteger());
      assertEquals(192, option.getRangeConstraints().getMaximumInteger());
      assertEquals(2, option.getRangeConstraints().getQuantumInteger());
    }
  }

  @Test
  public void readFixedConstraintRangeCorrectly() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();

      SaneOption option = device.getOption("fixed-constraint-range");
      assertNotNull(option);
      assertEquals(OptionValueConstraintType.RANGE_CONSTRAINT, option.getConstraintType());
      assertEquals(-42.17, option.getRangeConstraints().getMinimumFixed(), 0.00001);
      assertEquals(32767.9999, option.getRangeConstraints().getMaximumFixed(), 0.00001);
      assertEquals(2.0, option.getRangeConstraints().getQuantumFixed(), 0.00001);
    }
  }

  @Test
  public void arrayOption() throws Exception {

    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      device.getOption("enable-test-options").setBooleanValue(true);

      SaneOption option = device.getOption("int-constraint-array-constraint-range");
      assertNotNull(option);
      assertThat(option.isConstrained()).isTrue();
      assertThat(option.getConstraintType()).isEqualTo(OptionValueConstraintType.RANGE_CONSTRAINT);
      assertEquals(OptionValueType.INT, option.getType());
      List<Integer> values = new ArrayList<>();

      RangeConstraint constraints = option.getRangeConstraints();
      for (int i = 0; i < option.getValueCount(); i++) {
        values.add(constraints.getMinimumInteger() + i * constraints.getQuantumInteger());
      }

      assertEquals(values, option.setIntegerValue(values));
      assertEquals(values, option.getIntegerArrayValue());
    }
  }

  @Test
  public void multipleListDevicesCalls() throws Exception {
    SaneSession session = saneDaemon.getSession();
    session.listDevices();
    session.listDevices();
  }

  @Test
  public void multipleGetDeviceCalls() throws Exception {
    SaneSession session = saneDaemon.getSession();
    session.getDevice("test");
    session.getDevice("test");
  }

  @Test
  public void multipleOpenDeviceCalls() throws Exception {
    SaneSession session = saneDaemon.getSession();

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
  public void secondOpenDeviceSucceedsAfterAFailure() throws Exception {
    SaneSession session = saneDaemon.getSession();

    {
      SaneDevice device = session.getDevice("nonexistent");
      try {
        device.open();
        fail("SaneException was expected but not thrown.");
      } catch (SaneException e) {
        // Expected.
      }
    }

    for (int i = 0; i < 10; i++) {
      SaneDevice device = session.getDevice("test");
      openAndCloseDevice(device);
    }
  }

  @Test
  public void handScanning() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      device.getOption("hand-scanner").setBooleanValue(true);
      device.acquireImage();
    }
  }

  @Test
  public void threePassScanning() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      assertEquals(
          "Color pattern", device.getOption("test-picture").setStringValue("Color pattern"));
      assertEquals("Color", device.getOption("mode").setStringValue("Color"));
      assertTrue(device.getOption("three-pass").setBooleanValue(true));
      for (int i = 0; i < 5; i++) {
        File file = File.createTempFile("three-pass", ".png", tempFolder.getRoot());
        ImageIO.write(device.acquireImage(), "png", file);
        System.out.println("Wrote three-pass test to " + file);
      }
    }
  }

  @Test
  public void reducedArea() throws Exception {
    try (SaneDevice device = saneDaemon.getSession().getDevice("test")) {
      device.open();
      device.getOption("mode").setStringValue("Color");
      device.getOption("resolution").setFixedValue(200);
      device.getOption("tl-x").setFixedValue(0.0);
      device.getOption("tl-y").setFixedValue(0.0);
      device.getOption("br-x").setFixedValue(105.0);
      device.getOption("br-y").setFixedValue(149.0);
      device.acquireImage();
    }
  }

  @Test
  public void passwordAuthentication() throws Exception {
    // assumes that test is a password-authenticated device
    SaneDevice device = saneDaemon.getSession().getDevice("test");
    device.open();
    device.acquireImage();
  }

  /**
   * This test assumes that you have protected the "test" device with a username of "testuser" and a
   * password other than "badpassword".
   */
  @Test
  public void invalidPasswordCausesAccessDeniedError() throws Exception {
    saneDaemon.protectDevice("test", "testuser", "goodpass");
    SaneSession session = saneDaemon.getSession();
    session.setPasswordProvider(
        SanePasswordProvider.forUsernameAndPassword("testuser", "badpassword"));
    try (SaneDevice device = session.getDevice("test")) {
      expectedException.expect(SaneException.class);
      expectedException.expectMessage("STATUS_ACCESS_DENIED");
      device.open();
    }
  }

  /**
   * Checks to ensure a STATUS_ACCESS_DENIED exception is raised if the authenticator is unable to
   * authenticate.
   */
  @Test
  public void cannotAuthenticateThrowsAccessDeniedError() throws Exception {
    saneDaemon.protectDevice("test", "someuser", "somepassword");
    SaneSession session = saneDaemon.getSession();
    session.setPasswordProvider(
        new SanePasswordProvider() {
          @Override
          public String getUsername(String resource) {
            return null;
          }

          @Override
          public String getPassword(String resource) {
            return null;
          }

          @Override
          public boolean canAuthenticate(String resource) {
            return false;
          }
        });

    try (SaneDevice device = session.getDevice("test")) {
      expectedException.expect(SaneException.class);
      expectedException.expectMessage("STATUS_ACCESS_DENIED");
      device.open();
    }
  }

  @Test
  public void passwordAuthenticationFromLocalFileSpecified() throws Exception {
    SaneSession session = saneDaemon.getSession();
    File passwordFile = tempFolder.newFile("sane.pass");
    Files.write(
        passwordFile.toPath(), "testuser:goodpass:test".getBytes(StandardCharsets.US_ASCII));
    session.setPasswordProvider(
        SanePasswordProvider.usingSanePassFile(passwordFile.getAbsolutePath()));
    SaneDevice device = session.getDevice("test");
    device.open();
    device.acquireImage();
  }

  @Test
  public void listenerReceivesScanStartedEvent() throws Exception {
    final CompletableFuture<SaneDevice> notifiedDevice = new CompletableFuture<>();
    final AtomicInteger frameCount = new AtomicInteger();
    final Set<FrameType> framesSeen = EnumSet.noneOf(FrameType.class);

    ScanListener listener =
        new ScanListenerAdapter() {
          @Override
          public void scanningStarted(SaneDevice device) {
            notifiedDevice.complete(device);
          }

          @Override
          public void frameAcquisitionStarted(
              SaneDevice device,
              SaneParameters parameters,
              int currentFrame,
              int likelyTotalFrames) {
            frameCount.incrementAndGet();
            framesSeen.add(parameters.getFrameType());
          }
        };

    SaneDevice device = saneDaemon.getSession().getDevice("test");
    device.open();
    device.getOption("resolution").setFixedValue(1200);
    device.getOption("mode").setStringValue("Color");
    device.getOption("three-pass").setBooleanValue(true);
    device.acquireImage(listener);
    assertThat(notifiedDevice.get()).isSameAs(device);
    assertThat(frameCount.get()).isEqualTo(3);
    assertThat(framesSeen).containsExactly(FrameType.RED, FrameType.GREEN, FrameType.BLUE);
  }

  @Test
  public void canSetOptionAfterFailingToSet() throws Exception {
    SaneDevice device = saneDaemon.getSession().getDevice("test");
    device.open();
    try {
      device.getOption("mode").setStringValue("Gray ");
      fail("expected SaneException, but none was thrown");
    } catch (SaneException e) {
      // expected
    }

    // The SANE session stream should be placed in a good state for another try. This should not
    // throw.
    assertThat(device.getOption("mode").setStringValue("Gray")).isEqualTo("Gray");
  }

  private void openAndCloseDevice(SaneDevice device) throws Exception {
    try {
      device.open();
      device.listOptions();
    } finally {
      device.close();
    }
  }
}
