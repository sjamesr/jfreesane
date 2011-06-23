package au.com.southsky.jfreesane;

import java.net.InetAddress;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

public class SaneSessionTest {

	private static final Logger log = Logger.getLogger(SaneSessionTest.class.getName());
	
	@Test
	public void connectionSucceeds() throws Exception {
		SaneSession.withRemoteSane(InetAddress.getByName("sirius.localdomain"))
				.close();
	}

	@Test
	public void listDevicesSucceeds() throws Exception {
		SaneSession session = SaneSession.withRemoteSane(InetAddress
				.getByName("sirius.localdomain"));
		List<SaneDevice> devices = session.listDevices();
		log.info("Got " + devices.size() + " device(s): " + devices); 
		Assert.assertTrue(devices.size() > 0);
		session.close();
	}
}
