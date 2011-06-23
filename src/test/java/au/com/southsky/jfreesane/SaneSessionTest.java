package au.com.southsky.jfreesane;

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
		// open the first device we get
		List<SaneDevice> devices = session.listDevices();
		SaneDevice device = devices.get(0);
		try {
			device.open();
		} finally {
			Closeables.close(device, false);
		}
	}
	
	@Test
	public void imageAcquisitionSucceeds() throws Exception {
		// open the first device we get
		List<SaneDevice> devices = session.listDevices();
		SaneDevice device = devices.get(0);
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
			Closeables.close(device, false);
		}
	}
}
