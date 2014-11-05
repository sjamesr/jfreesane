package au.com.southsky.jfreesane;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

/**
 * Represents a conversation taking place with a SANE daemon.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneSession implements Closeable {

  private static final int DEFAULT_PORT = 6566;

  private final Socket socket;
  private final SaneOutputStream outputStream;
  private final SaneInputStream inputStream;
  private SanePasswordProvider passwordProvider = SanePasswordProvider.usingDotSanePassFile();

  private SaneSession(Socket socket) throws IOException {
    this.socket = socket;
    this.outputStream = new SaneOutputStream(socket.getOutputStream());
    this.inputStream = new SaneInputStream(this, socket.getInputStream());
  }

  /**
   * Returns the current password provider. By default, this password provider
   * will be supplied by {@link SanePasswordProvider#usingDotSanePassFile}, but
   * you may override that with {@link #setPasswordProvider}.
   */
  public SanePasswordProvider getPasswordProvider() {
    return passwordProvider;
  }

  public void setPasswordProvider(SanePasswordProvider passwordProvider) {
    this.passwordProvider = passwordProvider;
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host on the default SANE port.
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress) throws IOException {
    return withRemoteSane(saneAddress, DEFAULT_PORT);
  }

  /**
   * Establishes a connection to the SANE daemon running on the given host at the given port.
   */
  public static SaneSession withRemoteSane(InetAddress saneAddress, int port) throws IOException {
    Socket socket = new Socket(saneAddress, port);
    SaneSession session = new SaneSession(socket);
    session.initSane();
    return session;
  }

  /**
   * Returns the device with the give name. Opening the device will fail if the named device does
   * not exist.
   *
   * @return a new {@link SaneDevice} with the given name associated with the current session, never
   *         {@code null}
   * @throws IOException
   *           if an error occurs while communicating with the SANE daemon
   */
  public SaneDevice getDevice(String name) throws IOException {
    return new SaneDevice(this, name, "", "", "");
  }

  /**
   * Lists the devices known to the SANE daemon.
   *
   * @return a list of devices that may be opened, see {@link SaneDevice#open}
   * @throws IOException
   *           if an error occurs while communicating with the SANE daemon
   * @throws SaneException
   *           if the SANE backend returns an error in response to this request
   */
  public List<SaneDevice> listDevices() throws IOException, SaneException {
    outputStream.write(SaneRpcCode.SANE_NET_GET_DEVICES);
    return inputStream.readDeviceList();
  }

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

    SaneWord status = inputStream.readWord();

    if (status.integerValue() != 0) {
      throw new SaneException(SaneStatus.fromWireValue(status.integerValue()));
    }

    SaneWord handle = inputStream.readWord();
    String resource = inputStream.readString();

    if (!resource.isEmpty()) {
      authorize(resource);
      status = inputStream.readWord();
      if (status.integerValue() != 0) {
        throw new SaneException(SaneStatus.fromWireValue(status.integerValue()));
      }
      handle = inputStream.readWord();
      resource = inputStream.readString();
    }

    return new SaneDeviceHandle(status, handle, resource);
  }

  BufferedImage acquireImage(SaneDeviceHandle handle) throws IOException, SaneException {
    SaneImage.Builder builder = new SaneImage.Builder();
    SaneParameters parameters = null;

    do {
      outputStream.write(SaneRpcCode.SANE_NET_START);
      outputStream.write(handle.getHandle());

      {
        int status = inputStream.readWord().integerValue();
        if (status != 0) {
          throw new SaneException(SaneStatus.fromWireValue(status));
        }
      }

      int port = inputStream.readWord().integerValue();
      SaneWord byteOrder = inputStream.readWord();
      String resource = inputStream.readString();

      if (!resource.isEmpty()) {
        authorize(resource);
        {
          int status = inputStream.readWord().integerValue();
          if (status != 0) {
            throw new SaneException(SaneStatus.fromWireValue(status));
          }
        }
        port = inputStream.readWord().integerValue();
        byteOrder = inputStream.readWord();
        resource = inputStream.readString();
      }

      // Ask the server for the parameters of this scan
      outputStream.write(SaneRpcCode.SANE_NET_GET_PARAMETERS);
      outputStream.write(handle.getHandle());

      Socket imageSocket = null;

      try {
        imageSocket = new Socket(socket.getInetAddress(), port);
        int status = inputStream.readWord().integerValue();

        if (status != 0) {
          throw new IOException("Unexpected status (" + status + ") in get_parameters");
        }

        parameters = inputStream.readSaneParameters();
        @SuppressWarnings("resource")
        FrameInputStream frameStream = new FrameInputStream(parameters,
            imageSocket.getInputStream(), 0x4321 == byteOrder.integerValue());
        builder.addFrame(frameStream.readFrame());
      } finally {
        if (imageSocket != null) {
          imageSocket.close();
        }
      }
    } while (!parameters.isLastFrame());

    SaneImage image = builder.build();
    return image.toBufferedImage();
  }

  void closeDevice(SaneDeviceHandle handle) throws IOException {
    // RPC code
    outputStream.write(SaneRpcCode.SANE_NET_CLOSE);
    outputStream.write(handle.getHandle());

    // read the dummy value from the wire, if it doesn't throw an exception
    // we assume the close was successful
    inputStream.readWord();
  }

  void cancelDevice(SaneDeviceHandle handle) throws IOException {
    // RPC code
    outputStream.write(SaneRpcCode.SANE_NET_CANCEL);
    outputStream.write(handle.getHandle());

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

    inputStream.readWord();
    inputStream.readWord();
  }

  /**
   * Authorize the resource for access.
   *
   * @throws IOException
   *           if an error occurs while communicating with the SANE daemon
   */
  void authorize(String resource) throws IOException {
    if (passwordProvider == null) {
      throw new IOException("Authorization failed - no password provider present "
          + "(you must call setPasswordProvider)");
    }
    // RPC code FOR SANE_NET_AUTHORIZE
    outputStream.write(SaneRpcCode.SANE_NET_AUTHORIZE);
    outputStream.write(resource);

    if (!passwordProvider.canAuthenticate(resource)) {
      // the password provider has indicated that there's no way it can provide
      // credentials for this request.
      throw new IOException("Authorization failed - the password provider is "
          + "unable to provide a password for the resource [" + resource + "]");
    }

    outputStream.write(passwordProvider.getUsername(resource));
    writePassword(resource, passwordProvider.getPassword(resource));
    // Read reply - from network
    inputStream.readWord();
  }

  /**
   * Write password to outputstream depending on resource provided by saned.
   * 
   * @param resource as provided by sane in authorization request
   * @param password
   * @throws IOException 
   */
  private void writePassword(String resource, String password) throws IOException {
    String[] resourceParts = resource.split("\\$MD5\\$");
    if (resourceParts.length == 1) {
      // Write in clean
      outputStream.write(password);
    } else {
      outputStream.write("$MD5$" + Encoder.derivePassword(resourceParts[1], password));
    }
  }

  public SaneOutputStream getOutputStream() {
    return outputStream;
  }

  public SaneInputStream getInputStream() {
    return inputStream;
  }
}
