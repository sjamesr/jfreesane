package au.com.southsky.jfreesane;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.CharSource;
import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the authentication configuration used by SANE clients. The SANE utilities like
 * {@code scanimage} will read the {@code ~/.sane/pass} directory (if it exists), this class
 * provides an implementation of that behavior.
 *
 * <p>
 * Threadsafe.
 */
public class SaneClientAuthentication extends SanePasswordProvider {
  private static final Logger logger = Logger.getLogger(SaneClientAuthentication.class.getName());

  public static final String MARKER_MD5 = "$MD5$";

  private static final String DEFAULT_CONFIGURATION_PATH =
      Joiner.on(File.separator).join(System.getProperty("user.home"), ".sane", "pass");

  private final Table<String, String, String> credentials = HashBasedTable.create();
  private final CharSource configurationSource;
  private boolean initialized = false;

  public SaneClientAuthentication() {
    this(DEFAULT_CONFIGURATION_PATH);
  }

  public SaneClientAuthentication(final String path) {
    this(
        new CharSource() {
          @Override
          public Reader openStream() throws IOException {
            return new InputStreamReader(new FileInputStream(path), Charsets.US_ASCII);
          }
        });
  }

  /**
   * Returns a new {@code SaneClientAuthentication} whose configuration is represented by the
   * characters supplied by the given {@link CharSource}.
   */
  public SaneClientAuthentication(CharSource configurationSource) {
    this.configurationSource = configurationSource;
  }

  private synchronized void initializeIfRequired() {
    if (initialized) {
      return;
    }

    initialized = true;
    try {
      CharStreams.readLines(
          configurationSource.openStream(),
          new LineProcessor<Void>() {
            private int lineNumber = 0;

            @Override
            public boolean processLine(String line) throws IOException {
              lineNumber++;
              ClientCredential credential = ClientCredential.fromAuthString(line);
              if (credential == null) {
                logger.log(
                    Level.WARNING,
                    "ignoring invalid configuration format (line {0}): {1}",
                    new Object[] {lineNumber, line});
              } else {
                credentials.put(credential.backend, credential.username, credential.password);
                if (credentials.row(credential.backend).size() > 1) {
                  logger.log(
                      Level.WARNING,
                      "ignoring line {0}, we already have a configuration for resource [{1}]",
                      new Object[] {lineNumber, credential.backend});
                }
              }
              return true;
            }

            @Override
            public Void getResult() {
              return null;
            }
          });
    } catch (IOException e) {
      logger.log(Level.WARNING, "could not read auth configuration due to IOException", e);
    }
  }

  /**
   * Returns {@code true} if the configuration contains an entry for the given resource.
   */
  @Override
  public boolean canAuthenticate(String resource) {
    if (resource == null) {
      return false;
    }

    ClientCredential credential = getCredentialForResource(resource);
    return credential != null;
  }

  public ClientCredential getCredentialForResource(String rc) {
    initializeIfRequired();
    String resource = rc.contains(MARKER_MD5) ? rc.substring(0, rc.indexOf(MARKER_MD5)) : rc;

    Map<String, String> credentialsForResource = credentials.row(resource);
    for (Map.Entry<String, String> credential : credentialsForResource.entrySet()) {
      return new ClientCredential(resource, credential.getKey(), credential.getValue());
    }

    return null;
  }

  @Override
  public String getUsername(String resource) {
    return getCredentialForResource(resource).username;
  }

  @Override
  public String getPassword(String resource) {
    return getCredentialForResource(resource).password;
  }

  /**
   * Class to hold Sane client credentials organised by backend.
   *
   * @author paul
   */
  public static class ClientCredential {
    private final String backend;
    private final String username;
    private final String password;

    protected ClientCredential(String backend, String username, String password) {
      this.backend = backend;
      this.username = username;
      this.password = password;
    }

    public static ClientCredential fromAuthString(String authString) {
      List<String> fields = Splitter.on(":").splitToList(authString);
      if (fields.size() < 3) {
        return null;
      }

      return new ClientCredential(fields.get(2), fields.get(0), fields.get(1));
    }

    public String getBackend() {
      return backend;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }
  }
}
