package au.com.southsky.jfreesane;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
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
}
