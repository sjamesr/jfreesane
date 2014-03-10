package au.com.southsky.jfreesane;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import au.com.southsky.jfreesane.SaneClientAuthentication.ClientCredential;

import com.google.common.io.Files;

public class SaneClientAuthenticationTest {

	private File testusers;
	private final String NL = String.format("%n");
	
	@Before
	public void onSetup() throws IOException {
		testusers = createTempFile();
	}
	
	@After
	public void tearDown() {
		testusers.delete();
	}
	
	@Test
	public void testSaneClientAuthentication() {
		SaneClientAuthentication sca = new SaneClientAuthentication();
		Assert.assertNotNull(sca);
	}

	@Test
	public void testSaneClientAuthenticationWithMissingFileDoesNotFail() {
		String filepath = "NONE_EXISTENT_PATH_"+UUID.randomUUID().toString();
		SaneClientAuthentication sca = new SaneClientAuthentication(filepath);
		Assert.assertNotNull(sca);
	}
	
	@Test
	public void testSaneClientAuthenticationWithTempFileSucceeds() {
		SaneClientAuthentication sca = new SaneClientAuthentication(testusers.getPath());
		Assert.assertNotNull(sca);
		Assert.assertEquals(3, sca.credentials.size());
	}

	@Test
	public void testInitialise() throws IOException {
		List<ClientCredential>credentials = null;
		SaneClientAuthentication sca = new SaneClientAuthentication();
		credentials = sca.initialise(testusers);
		Assert.assertNotNull(credentials);
		Assert.assertEquals(3,  credentials.size());
		Assert.assertEquals("pixma", credentials.get(0).backend);
		Assert.assertEquals("net", credentials.get(1).backend);
		Assert.assertEquals("mustek", credentials.get(2).backend);
		
		Assert.assertEquals("sane-user", credentials.get(0).username);
		Assert.assertEquals("other-user", credentials.get(1).username);
		Assert.assertEquals("user", credentials.get(2).username);
		
		Assert.assertEquals("password", credentials.get(0).password);
		Assert.assertEquals("strongPassword", credentials.get(1).password);
		Assert.assertEquals("", credentials.get(2).password);
	}

	@Test
	public void testCanAuthenticateNullResourceFailure() {
		SaneClientAuthentication sca = new SaneClientAuthentication();
		Assert.assertFalse(sca.canAuthenticate(null));
	}
	
	@Test
	public void testCanAuthenticateFalse() {
		String rcString = "missing$MD5$iamarandomstring";
		SaneClientAuthentication sca = new SaneClientAuthentication(testusers.getPath());
		Assert.assertFalse(sca.canAuthenticate(rcString));
	}
	
	@Test
	public void testCanAuthenticateTrue() {
		String rcString = "pixma$MD5$iamarandomstring";
		SaneClientAuthentication sca = new SaneClientAuthentication(testusers.getPath());
		Assert.assertTrue(sca.canAuthenticate(rcString));
	}
	
	@Test
	public void testCanAuthenticateTrueWithoutMD5() {
		String rcString = "mustek";
		SaneClientAuthentication sca = new SaneClientAuthentication(testusers.getPath());
		Assert.assertTrue(sca.canAuthenticate(rcString));
	}
	
	@Test
	public void testCanAuthenticateFailTooManyBackends() {
		String rcString = "pixma";
		SaneClientAuthentication sca = new SaneClientAuthentication(testusers.getPath());
		sca.credentials.add( new SaneClientAuthentication.ClientCredential("pixma","second-user","password"));
		Assert.assertFalse( sca.canAuthenticate(rcString));
	}

	private File createTempFile() throws IOException {
		StringBuffer users = new StringBuffer();
		users.append("pixma:sane-user:password").append(NL);
		users.append("net:other-user:strongPassword").append(NL);
		users.append("mustek:user:").append(NL);
		File testfile = File.createTempFile("sanepass", null);
		Files.write(users.toString(), testfile, Charset.defaultCharset());
		return testfile;
	}
}

