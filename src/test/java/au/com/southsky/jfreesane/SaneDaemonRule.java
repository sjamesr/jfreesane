package au.com.southsky.jfreesane;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaneDaemonRule extends ExternalResource {
  private static final String SANE_TEST_SERVER_ENV_VAR = "SANE_TEST_SERVER_ADDRESS";

  private static final Logger logger = Logger.getLogger(SaneDaemonRule.class.getName());
  private final TemporaryFolder tempDir;

  private ProcessBuilder saneDaemonBuilder;

  private Process saneDaemon;
  private InetSocketAddress sockAddr;

  private final List<DeviceAuth> passwords = new ArrayList<>();

  public SaneDaemonRule(TemporaryFolder tempFolderRule) {
    this.tempDir = tempFolderRule;
  }

  @Override
  protected void before() throws Throwable {
    String address = System.getenv(SANE_TEST_SERVER_ENV_VAR);
    if (address != null) {
      URI hostAndPort = URI.create("my://" + address);
      int port = hostAndPort.getPort();
      if (port == -1) {
        port = 6566;
      }
      sockAddr = new InetSocketAddress(InetAddress.getLocalHost(), port);
      logger.info(
          "not starting sane daemon, using existing user-specified SANE daemon at " + sockAddr);
      return;
    }

    saneDaemonBuilder = new ProcessBuilder("saned", "-l", "-p", "0", "-e", "-d", "8");
    saneDaemonBuilder.directory(tempDir.getRoot());
  }

  /**
   * Starts the sane daemon and returns a new session connected to it. If the session was already
   * started by a previous call to {@code getSession}, returns a new session connected to the
   * existing sane daemon.
   */
  public SaneSession getSession() throws IOException, InterruptedException {
    if (saneDaemon == null && saneDaemonBuilder != null) {
      logger.fine("using " + tempDir.getRoot() + " as a working directory");

      if (!passwords.isEmpty()) {
        var saneUsersContent = new StringBuilder();

        for (DeviceAuth auth : passwords) {
          saneUsersContent
              .append(auth.username)
              .append(":")
              .append(auth.password)
              .append(":")
              .append(auth.deviceName)
              .append("\n");
        }

        Path path = tempDir.getRoot().toPath().resolve("saned.users");
        Files.writeString(path, saneUsersContent.toString());
        logger.info("wrote " + passwords.size() + " entries to " + path);
      }

      logger.info("starting a local sane daemon: " + saneDaemonBuilder.command());
      saneDaemon = saneDaemonBuilder.start();
      SaneLogListener t = new SaneLogListener(saneDaemon.getErrorStream());
      t.start();

      logger.fine("Waiting up to 10s for SANE to tell us which port to use");

      sockAddr = t.getSaneSocketAddress(10, TimeUnit.SECONDS);
      if (sockAddr == null) {
        throw new IllegalStateException(
            "gave up waiting for sane to tell us what port to listen on");
      }
      logger.fine("We should try " + sockAddr);
    }

    return SaneSession.withRemoteSane(sockAddr, 0, TimeUnit.MILLISECONDS, 0, TimeUnit.MILLISECONDS);
  }

  /**
   * Instructs the rule to protect the named device with the given username and password. This will
   * cause an entry to be added to the {@code saned.users} file in the sane configuration.
   */
  public void protectDevice(String name, String username, String password) {
    if (saneDaemonBuilder == null) {
      logger.warning("Calling protectDevice has no effect on an existing SANE daemon.");
      logger.warning(
          "You should either remove the "
              + SANE_TEST_SERVER_ENV_VAR
              + " enviroment variable, or configure the existing SANE daemon with the desired username and password.");
    }

    DeviceAuth auth = new DeviceAuth(name, username, password);
    passwords.add(auth);
  }

  @Override
  protected void after() {
    if (saneDaemon == null) {
      return;
    }

    if (saneDaemon.isAlive()) {
      logger.fine("Stopping SANE daemon");
      saneDaemon.descendants().forEach(ProcessHandle::destroy);
      saneDaemon.destroy();
    }

    try {
      if (saneDaemon.waitFor(10, TimeUnit.SECONDS)) {
        logger.info("SANE daemon exited with status " + saneDaemon.exitValue());
      } else {
        logger.info("Timed out waiting for SANE daemon to exit, forcibly terminating.");
      }
    } catch (InterruptedException e) {
      logger.info("Interrupted while waiting for SANE daemon to exit, forcibly terminating.");
      Thread.currentThread().interrupt();
    } finally {
      // This is a nop if the process is gone already.
      saneDaemon.destroyForcibly();
    }
  }

  private static final class SaneLogListener extends Thread {
    private static final Pattern PORT_TYPE_PATTERN =
        Pattern.compile("\\[(\\d+)] socket .* using IPv([4|6])$");
    private static final Pattern PORT_PATTERN =
        Pattern.compile("\\[(\\d+)] selected ephemeral port: (\\d+)$");

    private final InputStream in;
    private final BlockingQueue<InetSocketAddress> sockAddrQueue = new LinkedBlockingQueue<>(2);

    private final Map<Integer, Integer> portTypeMap = new HashMap<>(2);

    private SaneLogListener(InputStream in) {
      this.in = in;
    }

    /**
     * Waits for SANE to tell us what socket address to try, or {@code null} if none was found in
     * time.
     */
    public InetSocketAddress getSaneSocketAddress(long timeout, TimeUnit timeoutUnits)
        throws InterruptedException {
      return sockAddrQueue.poll(timeout, timeoutUnits);
    }

    @Override
    public void run() {
      try (InputStreamReader inr = new InputStreamReader(in, StandardCharsets.UTF_8);
          BufferedReader r = new BufferedReader(inr)) {
        String msg;

        while ((msg = r.readLine()) != null) {
          {
            Matcher m = PORT_TYPE_PATTERN.matcher(msg);
            if (m.find()) {
              logger.fine("SANE socket #" + m.group(1) + " is IPv" + m.group(2));
              portTypeMap.put(Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2)));
            }
          }

          {
            Matcher m = PORT_PATTERN.matcher(msg);
            if (m.find()) {
              logger.fine("SANE says listening on port " + m.group(2));
              Integer portType = portTypeMap.get(Integer.valueOf(m.group(1)));
              if (portType == null) {
                logger.fine("ignoring port " + m.group(1) + ", no port type known");
              } else {
                try {
                  sockAddrQueue.put(
                      new InetSocketAddress(
                          Inet6Address.getLocalHost(), Integer.parseInt(m.group(2))));
                } catch (InterruptedException e) {
                  logger.fine("log listener interrupted");
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            }
          }
        }
      } catch (IOException e) {
        logger.log(Level.WARNING, "SANE log reader thread threw an exception", e);
      }
    }
  }

  private static class DeviceAuth {
    private final String deviceName;
    private final String username;
    private final String password;

    DeviceAuth(String deviceName, String username, String password) {
      this.deviceName = deviceName;
      this.username = username;
      this.password = password;
    }

    public String getDeviceName() {
      return deviceName;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }
  }
}
