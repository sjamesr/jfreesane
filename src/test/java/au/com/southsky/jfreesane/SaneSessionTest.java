package au.com.southsky.jfreesane;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import au.com.southsky.jfreesane.SaneOption.OptionValueType;

import com.google.common.io.Closeables;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGImageEncoder;

public class SaneSessionTest {

	private static final Logger log = Logger.getLogger(SaneSessionTest.class
			.getName());
	private SaneSession session;

	@Before
	public void initSession() throws Exception {
		this.session = SaneSession.withRemoteSane(InetAddress
				.getByName("sirius.localdomain"));
	}

	@After
	public void closeSession() throws Exception {
		Closeables.close(session, false);
	}

	@Test
	public void listDevicesSucceeds() throws Exception {
		List<SaneDevice> devices = session.listDevices();
		log.info("Got " + devices.size() + " device(s): " + devices);
		Assert.assertTrue(devices.size() > 0);
	}

	@Test
	public void openDeviceSucceeds() throws Exception {
		SaneDevice device = session.getDevice("test");
		try {
			device.open();
		} finally {
			Closeables.closeQuietly(device);
		}
	}

	@Test
	public void imageAcquisitionSucceeds() throws Exception {
		// open the first device we get
		SaneDevice device = session.getDevice("test");
		FileOutputStream stream = null;
		try {
			device.open();
			BufferedImage image = device.acquireImage();
			File file = File.createTempFile("image", ".jpg");
			stream = new FileOutputStream(file);
			JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(stream);
			encoder.encode(image);
			stream.flush();

			System.out.println("Successfully wrote " + file);
		} finally {
			Closeables.closeQuietly(stream);
			Closeables.closeQuietly(device);
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
			}
			Assert.assertTrue("Expected first option 'Number of options'",
					options.get(0).getTitle().equals("Number of options"));
		} finally {
			Closeables.closeQuietly(device);
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
				if (option.getType() == OptionValueType.INT
						&& option.getValueCount() == 1 && option.isActive()) {
					System.out.println(option.getTitle() + "="
							+ option.getIntegerValue());
				}
			}
		} finally {
			Closeables.closeQuietly(device);
		}
	}

	@Test
	public void setOptionValueSucceedsForString() throws Exception {
		SaneDevice device = session.getDevice("test");

		try {
			device.open();
			SaneOption modeOption = device.getOption("mode");
			assertEquals("Gray", modeOption.setStringValue("Gray"));
		} finally {
			Closeables.closeQuietly(device);
		}
	}

	@Test
	public void acquireMonoImage() throws Exception {
		SaneDevice device = session.getDevice("test");
		FileOutputStream stream = null;

		try {
			device.open();
			SaneOption modeOption = device.getOption("mode");
			assertEquals("Gray", modeOption.setStringValue("Gray"));
			BufferedImage image = device.acquireImage();

			File file = File.createTempFile("mono-image", ".jpg");
			stream = new FileOutputStream(file);
			JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(stream);
			encoder.encode(image);
			stream.flush();

			System.out.println("Successfully wrote " + file);

		} finally {
			Closeables.closeQuietly(stream);
			Closeables.closeQuietly(device);
		}
	}

	/**
	 * Tests that this SANE client produces images that match
	 * {@link "http://www.meier-geinitz.de/sane/test-backend/test-pictures.html"}
	 * .
	 */
	@Test
	public void producesCorrectImages() throws Exception {
		SaneDevice device = session.getDevice("test");
		// Solid black and white
		try {
			device.open();

			assertProducesCorrectImage(device, "Gray", 1, "Solid white");
			assertProducesCorrectImage(device, "Gray", 8, "Solid white");
			assertProducesCorrectImage(device, "Gray", 16, "Solid white");
			assertProducesCorrectImage(device, "Gray", 1, "Solid black");
			assertProducesCorrectImage(device, "Gray", 8, "Solid black");
			assertProducesCorrectImage(device, "Gray", 16, "Solid black");

			assertProducesCorrectImage(device, "Color", 1, "Solid white");
			assertProducesCorrectImage(device, "Color", 8, "Solid white");
			assertProducesCorrectImage(device, "Color", 16, "Solid white");
			assertProducesCorrectImage(device, "Color", 1, "Solid black");
			assertProducesCorrectImage(device, "Color", 8, "Solid black");
			assertProducesCorrectImage(device, "Color", 16, "Solid black");
			
/*			assertProducesCorrectImage(device, "Gray", 1, "Color pattern");
			assertProducesCorrectImage(device, "Color", 1, "Color pattern");
			
			assertProducesCorrectImage(device, "Gray", 8, "Color pattern");
			assertProducesCorrectImage(device, "Color", 8, "Color pattern"); */
		} finally {
			Closeables.closeQuietly(device);
		}
	}

	private void assertProducesCorrectImage(SaneDevice device, String mode,
			int sampleDepth, String testPicture) throws IOException {
		BufferedImage actualImage = acquireImage(device, mode, sampleDepth,
				testPicture);

		writeImage(mode, sampleDepth, testPicture, actualImage);

		if (testPicture.startsWith("Solid")) {
			assertImageSolidColor(testPicture.endsWith("black")
					? Color.black
					: Color.white, actualImage);
		} else {
			// compare with sample images
		}
	}

	private void writeImage(String mode, int sampleDepth, String testPicture,
			BufferedImage actualImage) throws IOException {
		File file = File.createTempFile(
				String.format("image-%s-%d-%s", mode, sampleDepth,
						testPicture.replace(' ', '_')), ".jpg");

		FileOutputStream stream = new FileOutputStream(file);
		JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(stream);
		encoder.encode(actualImage);
		stream.flush();

		System.out.println("Successfully wrote " + file);
	}

	private void assertImageSolidColor(Color color, BufferedImage image) {
		for (int x = 0; x < image.getWidth(); x++) {
			for (int y = 0; y < image.getHeight(); y++) {
				assertEquals(color.getRGB(), image.getRGB(x, y));
			}
		}
	}

	private BufferedImage acquireImage(SaneDevice device, String mode,
			int sampleDepth, String testPicture) throws IOException {
		device.getOption("mode").setStringValue(mode);
		device.getOption("depth").setIntegerValue(sampleDepth);
		device.getOption("test-picture").setStringValue(testPicture);

		return device.acquireImage();
	}

	private void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
		assertEquals("image widths differ", expected.getWidth(),
				actual.getWidth());
		assertEquals("image heights differ", expected.getHeight(),
				actual.getHeight());

		Raster expectedRaster = expected.getRaster();
		Raster actualRaster = actual.getRaster();

		for (int x = 0; x < expected.getWidth(); x++) {
			for (int y = 0; y < expected.getHeight(); y++) {
				int[] expectedPixels = expectedRaster.getPixel(x, y,
						(int[]) null);
				int[] actualPixels = actualRaster.getPixel(x, y, (int[]) null);

				// assert that all the samples are the same for the given pixel
				Assert.assertArrayEquals(expectedPixels, actualPixels);
			}
		}
	}
}
