package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.UUID;

import junit.framework.Assert;

import org.junit.Test;

import au.com.southsky.jfreesane.SaneClientAuthentication.ClientCredential;

import com.google.common.io.CharSource;

public class SaneClientAuthenticationTest {

  @Test
  public void testSaneClientAuthenticationWithMissingFileDoesNotFail() {
    String filepath = "NON_EXISTENT_PATH_" + UUID.randomUUID().toString();
    SaneClientAuthentication sca = new SaneClientAuthentication(filepath);
    sca.getCredentialForResource("");
  }

  @Test
  public void testInitialize() {
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());

    ClientCredential pixmaCreds = sca.getCredentialForResource("pixma");
    Assert.assertEquals("pixma", pixmaCreds.getBackend());
    Assert.assertEquals("sane-user", pixmaCreds.getUsername());
    Assert.assertEquals("password", pixmaCreds.getPassword());

    ClientCredential netCreds = sca.getCredentialForResource("net");
    Assert.assertEquals("net", netCreds.getBackend());
    Assert.assertEquals("other-user", netCreds.getUsername());
    Assert.assertEquals("strongPassword", netCreds.getPassword());

    ClientCredential mustekCreds = sca.getCredentialForResource("mustek");
    Assert.assertEquals("mustek", mustekCreds.getBackend());
    Assert.assertEquals("user", mustekCreds.getUsername());
    Assert.assertEquals("", mustekCreds.getPassword());
  }

  @Test
  public void testCanAuthenticateNullResourceFailure() {
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Assert.assertFalse(sca.canAuthenticate(null));
  }

  @Test
  public void testCanAuthenticateFalse() {
    String rcString = "missing$MD5$iamarandomstring";
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Assert.assertFalse(sca.canAuthenticate(rcString));
  }

  @Test
  public void testCanAuthenticateTrue() {
    String rcString = "pixma$MD5$iamarandomstring";
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Assert.assertTrue(sca.canAuthenticate(rcString));
  }

  @Test
  public void testCanAuthenticateTrueWithoutMD5() {
    String rcString = "mustek";
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Assert.assertTrue(sca.canAuthenticate(rcString));
  }

  private CharSource getTestConfigurationSource() {
    final StringBuilder users = new StringBuilder();
    users.append("sane-user:password:pixma\n");
    users.append("other-user:strongPassword:net\n");
    users.append("user::mustek\n");
    users.append("user1::bad-backend\n");
    users.append("user2::bad-backend\n");
    return new CharSource() {
      @Override
      public Reader openStream() throws IOException {
        return new StringReader(users.toString());
      }
    };
  }
}
