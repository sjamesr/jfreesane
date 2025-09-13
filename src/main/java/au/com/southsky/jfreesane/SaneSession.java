package au.com.southsky.jfreesane;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a conversation taking place with a SANE daemon.
 *
 * @author James Ring (sjr@jdns.org)
 */
public final class SaneSession implements Closeable {

  private static final int READ_BUFFER_SIZE = 1 << 20; // 1mb
  private static final int DEFAULT_PORT = 6566;

  private final Socket socket;
  private final SaneOutputStream outputStream;
  private final SaneInputStream inputStream;
  private final int connectionTimeoutMillis;
  private final int socketTimeoutMillis;
  private SanePasswordProvider passwordProvider = SanePasswordProvider.usingDotSanePassFile();

  private SaneSession(Socket socket, int connectionTimeoutMillis, int socketTimeoutMillis)
      throws IOException {
    this.socket = socket;
    this.outputStream = new SaneOutputStream(socket.getOutputStream());
    this.inputStream = new SaneInputStream(this, socket.getInputStream());
    this.connectionTimeoutMillis = connectionTimeoutMillis;
    this.socketTimeoutMillis = socketTimeoutMillis;
  }

  /**
   * Returns the current password provider. By default, this password provider will be supplied by
   * {@link SanePasswordProvider#usingDotSanePassFile}, but you may override that with {@link
   * #setPasswordProvider}.
   */
  public SanePasswordProvider getPasswordProvider() {
    return passwordProvider;
  }

  /**
   * Sets the {@link SanePasswordProvider password provider} to use if the SANE daemon asks for
   * credentials when accessing a resource. Throws {@link NullPointerException} if {@code
   * passwordProvider} is {@code null}.
   */
  public void setPasswordProvider(SanePasswordProvider passwordProvider) {
    this.passwordProvider = Preconditions.checkNotNull(passwordProvider);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the default SANE port
   * with no connection timeout.
   *
   * @param saneAddress the address of the SANE server
   * @return a {@code SaneSession} that is connected to the remote SANE server
   * @throws IOException if any error occurs while communicating with the SANE server
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress) throws IOException {
    return withRemoteSane(saneAddress, DEFAULT_PORT);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the default SANE port
   * with the given connection timeout.
   *
   * @param saneAddress the address of the SANE server
   * @param timeout the timeout for connections to the SANE server, zero implies no connection
   *     timeout, must not be greater than {@link Integer#MAX_VALUE} milliseconds.
   * @param timeUnit connection timeout unit
   * @param soTimeout the timeout for reads from the SANE server, zero implies no read timeout
   * @param soTimeUnit socket timeout unit
   * @return a {@code SaneSession} that is connected to the remote SANE server
   * @throws IOException if any error occurs while communicating with the SANE server
   */
  public static SaneSession withRemoteSane(
      InetAddress saneAddress, long timeout, TimeUnit timeUnit, long soTimeout, TimeUnit soTimeUnit)
      throws IOException {
    return withRemoteSane(saneAddress, DEFAULT_PORT, timeout, timeUnit, soTimeout, soTimeUnit);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the default SANE port
   * with the given connection timeout.
   *
   * @param saneAddress the address of the SANE server
   * @param timeout the timeout for connections to the SANE server, zero implies no connection
   *     timeout, must not be greater than {@link Integer#MAX_VALUE} milliseconds.
   * @param timeUnit connection timeout unit
   * @return a {@code SaneSession} that is connected to the remote SANE server
   * @throws IOException if any error occurs while communicating with the SANE server
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress, long timeout, TimeUnit timeUnit)
      throws IOException {
    return withRemoteSane(saneAddress, DEFAULT_PORT, timeout, timeUnit, 0, TimeUnit.MILLISECONDS);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the given port with no
   * connection timeout.
   *
   * @param saneAddress the address of the SANE server
   * @param port the port of the SANE server
   * @return a {@code SaneSession} that is connected to the remote SANE server
   * @throws IOException if any error occurs while communicating with the SANE server
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress, int port) throws IOException {
    return withRemoteSane(saneAddress, port, 0, TimeUnit.MILLISECONDS, 0, TimeUnit.MILLISECONDS);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the given port. If the
   * connection cannot be established within the given timeout, {@link
   * java.net.SocketTimeoutException} is thrown.
   *
   * @param saneAddress the address of the SANE server
   * @param port the port of the SANE server
   * @param timeout the timeout for connections to the SANE server, zero implies no connection
   *     timeout, must not be greater than {@link Integer#MAX_VALUE} milliseconds.
   * @param timeUnit connection timeout unit
   * @param soTimeout the timeout for reads from the SANE server, zero implies no read timeout
   * @param soTimeUnit socket timeout unit
   * @return a {@code SaneSession} that is connected to the remote SANE server
   * @throws IOException if any error occurs while communicating with the SANE server
   */
  public static SaneSession withRemoteSane(
      InetAddress saneAddress,
      int port,
      long timeout,
      TimeUnit timeUnit,
      long soTimeout,
      TimeUnit soTimeUnit)
      throws IOException {
    return withRemoteSane(
        new InetSocketAddress(saneAddress, port), timeout, timeUnit, soTimeout, soTimeUnit);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the given port. If the
   * connection cannot be established within the given timeout, {@link
   * java.net.SocketTimeoutException} is thrown.
   *
   * @param saneSocketAddress the socket address of the SANE server
   * @param timeout the timeout for connections to the SANE server, zero implies no connection
   *     timeout, must not be greater than {@link Integer#MAX_VALUE} milliseconds.
   * @param timeUnit connection timeout unit
   * @param soTimeout the timeout for reads from the SANE server, zero implies no read timeout
   * @param soTimeUnit socket timeout unit
   * @return a {@code SaneSession} that is connected to the remote SANE server
   * @throws IOException if any error occurs while communicating with the SANE server
   */
  public static SaneSession withRemoteSane(
      InetSocketAddress saneSocketAddress,
      long timeout,
      TimeUnit timeUnit,
      long soTimeout,
      TimeUnit soTimeUnit)
      throws IOException {
    long connectTimeoutMillis = timeUnit.toMillis(timeout);
    Preconditions.checkArgument(
        connectTimeoutMillis >= 0 && connectTimeoutMillis <= Integer.MAX_VALUE,
        "Timeout must be between 0 and Integer.MAX_VALUE milliseconds");
    // If the user specifies a non-zero timeout that rounds to 0 milliseconds,
    // set the timeout to 1 millisecond instead.
    if (timeout > 0 && connectTimeoutMillis == 0) {
      Logger.getLogger(SaneSession.class.getName())
          .log(
              Level.WARNING,
              "Specified timeout of {0} {1} rounds to 0ms and was clamped to 1ms",
              new Object[] {timeout, timeUnit});
    }
    Socket socket = new Socket();
    socket.setTcpNoDelay(true);
    long soTimeoutMillis = 0;

    if (soTimeUnit != null && soTimeout > 0) {
      soTimeoutMillis = soTimeUnit.toMillis(soTimeout);
      Preconditions.checkArgument(
          soTimeoutMillis >= 0 && soTimeoutMillis <= Integer.MAX_VALUE,
          "Socket timeout must be between 0 and Integer.MAX_VALUE milliseconds");
      socket.setSoTimeout((int) soTimeoutMillis);
    }
    socket.connect(saneSocketAddress, (int) connectTimeoutMillis);
    SaneSession session =
        new SaneSession(socket, (int) connectTimeoutMillis, (int) soTimeoutMillis);
    session.initSane();
    return session;
  }

  /**
   * Returns the device with the give name. Opening the device will fail if the named device does
   * not exist.
   *
   * @return a new {@link SaneDevice} with the given name associated with the current session, never
   *     {@code null}
   * @throws IOException if an error occurs while communicating with the SANE daemon
   */
  public SaneDevice getDevice(String name) throws IOException {
    return new SaneDevice(this, name, "", "", "");
  }

  /**
   * Lists the devices known to the SANE daemon.
   *
   * @return a list of devices that may be opened, see {@link SaneDevice#open}
   * @throws IOException if an error occurs while communicating with the SANE daemon
   * @throws SaneException if the SANE backend returns an error in response to this request
   */
  public List<SaneDevice> listDevices() throws IOException, SaneException {
    outputStream.write(SaneRpcCode.SANE_NET_GET_DEVICES);
    outputStream.flush();
    return inputStream.readDeviceList();
  }

  /**
   * Closes the connection to the SANE server. This is done immediately by closing the socket.
   *
   * @throws IOException if an error occurred while closing the connection
   */
  @Override
  public void close() throws IOException {
    try {
      outputStream.write(SaneRpcCode.SANE_NET_EXIT);
      outputStream.close();
    } finally {
      socket.close();
    }
  }

  SaneDeviceHandle openDevice(SaneDevice device) throws IOException, SaneException {
    outputStream.write(SaneRpcCode.SANE_NET_OPEN);
    outputStream.write(device.getName());
    outputStream.flush();

    SaneWord status = inputStream.readWord();
    SaneWord handle = inputStream.readWord();
    String resource = inputStream.readString();

    if (status.integerValue() != 0) {
      throw new SaneException(SaneStatus.fromWireValue(status.integerValue()));
    }

    if (!resource.isEmpty()) {
      if (!authorize(resource)) {
        throw new SaneException(SaneStatus.STATUS_ACCESS_DENIED);
      }
      status = inputStream.readWord();
      handle = inputStream.readWord();
      // Read the resource string
      inputStream.readString();
      if (status.integerValue() != 0) {
        throw new SaneException(SaneStatus.fromWireValue(status.integerValue()));
      }
    }

    return new SaneDeviceHandle(handle);
  }

  BufferedImage acquireImage(SaneDevice device, ScanListener listener)
      throws IOException, SaneException {
    SaneImage.Builder builder = new SaneImage.Builder();
    SaneParameters parameters = null;
    listener.scanningStarted(device);
    int currentFrame = 0;

    do {
      SaneDeviceHandle handle = device.getHandle();
      outputStream.write(SaneRpcCode.SANE_NET_START);
      outputStream.write(handle.getHandle());
      outputStream.flush();

      SaneWord startStatus = inputStream.readWord();

      int port = inputStream.readWord().integerValue();
      SaneWord byteOrder = inputStream.readWord();
      String resource = inputStream.readString();

      if (startStatus.integerValue() != 0) {
        throw SaneException.fromStatusWord(startStatus);
      }

      if (!resource.isEmpty()) {
        if (!authorize(resource)) {
          throw new SaneException(SaneStatus.STATUS_ACCESS_DENIED);
        }
        int status = inputStream.readWord().integerValue();
        port = inputStream.readWord().integerValue();
        byteOrder = inputStream.readWord();

        // Throw away the resource string, we don't attempt to authenticate again anyway.
        inputStream.readString();

        if (status != 0) {
          throw new SaneException(SaneStatus.fromWireValue(status));
        }
      }

      // Ask the server for the parameters of this scan
      outputStream.write(SaneRpcCode.SANE_NET_GET_PARAMETERS);
      outputStream.write(handle.getHandle());
      outputStream.flush();

      InetSocketAddress dataAddress = new InetSocketAddress(socket.getInetAddress(), port);
      try (Socket imageSocket = new Socket()) {
        imageSocket.setSoTimeout(socketTimeoutMillis);
        imageSocket.connect(dataAddress, connectionTimeoutMillis);
        int status = inputStream.readWord().integerValue();

        if (status != 0) {
          throw new IOException("Unexpected status (" + status + ") in get_parameters");
        }

        parameters = inputStream.readSaneParameters();

        // As a convenience to our listeners, try to figure out how many frames
        // will be read. Usually this will be 1, except in the case of older
        // three-pass color scanners.
        listener.frameAcquisitionStarted(
            device, parameters, currentFrame, getLikelyTotalFrameCount(parameters));
        FrameReader frameStream =
            new FrameReader(
                device,
                parameters,
                new BufferedInputStream(imageSocket.getInputStream(), READ_BUFFER_SIZE),
                0x4321 == byteOrder.integerValue(),
                listener);
        builder.addFrame(frameStream.readFrame());
      }

      currentFrame++;
    } while (!parameters.isLastFrame());

    listener.scanningFinished(device);
    SaneImage image = builder.build();
    return image.toBufferedImage();
  }

  private int getLikelyTotalFrameCount(SaneParameters parameters) {
    switch (parameters.getFrameType()) {
      case RED:
      case GREEN:
      case BLUE:
        return 3;
      default:
        return 1;
    }
  }

  void closeDevice(SaneDeviceHandle handle) throws IOException {
    // RPC code
    outputStream.write(SaneRpcCode.SANE_NET_CLOSE);
    outputStream.write(handle.getHandle());
    outputStream.flush();

    // read the dummy value from the wire, if it doesn't throw an exception
    // we assume the close was successful
    inputStream.readWord();
  }

  void cancelDevice(SaneDeviceHandle handle) throws IOException {
    // RPC code
    outputStream.write(SaneRpcCode.SANE_NET_CANCEL);
    outputStream.write(handle.getHandle());
    outputStream.flush();

    // read the dummy value from the wire, if it doesn't throw an exception
    // we assume the cancel was successful
    inputStream.readWord();
  }

  private void initSane() throws IOException {
    // RPC code
    outputStream.write(SaneRpcCode.SANE_NET_INIT);

    // version number
    outputStream.write(SaneWord.forSaneVersion(1, 0, 3));

    // username
    outputStream.write(System.getProperty("user.name"));
    outputStream.flush();

    inputStream.readWord();
    inputStream.readWord();
  }

  /**
   * Authorize the resource for access.
   *
   * @throws IOException if an error occurs while communicating with the SANE daemon
   */
  boolean authorize(String resource) throws IOException {
    if (passwordProvider == null) {
      throw new IOException(
          "Authorization failed - no password provider present "
              + "(you must call setPasswordProvider)");
    }

    if (passwordProvider.canAuthenticate(resource)) {
      // RPC code FOR SANE_NET_AUTHORIZE
      outputStream.write(SaneRpcCode.SANE_NET_AUTHORIZE);
      outputStream.write(resource);
      outputStream.write(passwordProvider.getUsername(resource));
      // TODO(sjamesr): resource is not currently used, see writePassword.
      writePassword(/* resource, */ passwordProvider.getPassword(resource));
      outputStream.flush();

      // Read dummy reply and discard (according to the spec, it is unused).
      inputStream.readWord();
      return true;
    }

    return false;
  }

  /** Write password to outputstream depending on resource provided by saned. */
  private void writePassword(/* String resource ,*/ String password) throws IOException {
    outputStream.write(password);

    // The code below always prints passwords in the clear, because Splitter.on takes
    // a separator string, not a regular expression. We can't fix it now due to a bug
    // in old versions of saned, which Linux distributions like Ubuntu still ship.
    // TODO(sjamesr): revive this code when Ubuntu gets a new sane-backends release,
    // see https://bugs.launchpad.net/ubuntu/+source/sane-backends/+bug/1858051.
    // TODO(sjamesr): when reviving, remove Guava dependency.
    /*
    List<String> resourceParts = Splitter.on("\\$MD5\\$").splitToList(resource);
    if (resourceParts.size() == 1) {
      // Write in clean
      outputStream.write(password);
    } else {
      outputStream.write(
          "$MD5$" + SanePasswordEncoder.derivePassword(resourceParts.get(1), password));
    }
    */
  }

  SaneOutputStream getOutputStream() {
    return outputStream;
  }

  SaneInputStream getInputStream() {
    return inputStream;
  }
}
