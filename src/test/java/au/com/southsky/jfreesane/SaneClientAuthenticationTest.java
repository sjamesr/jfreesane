package au.com.southsky.jfreesane;

import au.com.southsky.jfreesane.SaneClientAuthentication.ClientCredential;
import com.google.common.io.CharSource;
import com.google.common.truth.Truth;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.UUID;

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
    Truth.assertThat(pixmaCreds.getBackend()).isEqualTo("pixma");
    Truth.assertThat(pixmaCreds.getUsername()).isEqualTo("sane-user");
    Truth.assertThat(pixmaCreds.getPassword()).isEqualTo("password");

    ClientCredential netCreds = sca.getCredentialForResource("net");
    Truth.assertThat(netCreds.getBackend()).isEqualTo("net");
    Truth.assertThat(netCreds.getUsername()).isEqualTo("other-user");
    Truth.assertThat(netCreds.getPassword()).isEqualTo("strongPassword");

    ClientCredential mustekCreds = sca.getCredentialForResource("mustek");
    Truth.assertThat(mustekCreds.getBackend()).isEqualTo("mustek");
    Truth.assertThat(mustekCreds.getUsername()).isEqualTo("user");
    Truth.assertThat(mustekCreds.getPassword()).isEmpty();
  }

  @Test
  public void testCanAuthenticateNullResourceFailure() {
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Truth.assertThat(sca.canAuthenticate(null)).isFalse();
  }

  @Test
  public void testCanAuthenticateFalse() {
    String rcString = "missing$MD5$iamarandomstring";
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Truth.assertThat(sca.canAuthenticate(rcString)).isFalse();
  }

  @Test
  public void testCanAuthenticateTrue() {
    String rcString = "pixma$MD5$iamarandomstring";
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Truth.assertThat(sca.canAuthenticate(rcString)).isTrue();
  }

  @Test
  public void testCanAuthenticateTrueWithoutMD5() {
    String rcString = "mustek";
    SaneClientAuthentication sca = new SaneClientAuthentication(getTestConfigurationSource());
    Truth.assertThat(sca.canAuthenticate(rcString)).isTrue();
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
      public Reader openStream() {
        return new StringReader(users.toString());
      }
    };
  }
}
