package au.com.southsky.jfreesane;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a SANE device within a session. SANE devices are obtained from a {@link SaneSession}.
 *
 * <p>
 * Definitely not thread-safe. If you're going to use this object from multiple threads, you must do
 * your own synchronization. Even performing read operations (like getting an option's value) must
 * be synchronized.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneDevice implements Closeable {
  private final SaneSession session;
  private final String name;
  private final String vendor;
  private final String model;
  private final String type;

  private SaneDeviceHandle handle;

  private List<SaneOption> options = null;
  private final List<OptionGroup> groups = new ArrayList<>();

  SaneDevice(SaneSession session, String name, String vendor, String model, String type) {
    this.session = session;
    this.name = name;
    this.vendor = vendor;
    this.model = model;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public String getVendor() {
    return vendor;
  }

  public String getModel() {
    return model;
  }

  public String getType() {
    return type;
  }

  /**
   * Returns {@code true} if the device is open.
   */
  public boolean isOpen() {
    return handle != null;
  }

  /**
   * Opens the device.
   *
   * @throws IOException if a problem occurred while talking to SANE
   * @throws au.com.southsky.jfreesane.SaneException
   * @throws IllegalStateException if the device is already open
   */
  public void open() throws IOException, SaneException {
    Preconditions.checkState(!isOpen(), "device is already open");
    handle = session.openDevice(this);
  }

  /**
   * Acquires a single image from the Sane daemon.
   *
   * @return a {@link BufferedImage} representing the image obtained from Sane
   * @throws IOException if an error occurred while talking to the backend
   * @throws SaneException if an application-level error was returned by the Sane daemon
   */
  public BufferedImage acquireImage() throws IOException, SaneException {
    return acquireImage(null);
  }

  /**
   * Acquires a single image from the Sane daemon. The given {@link ScanListener} will be notified
   * about updates during the scan.
   *
   * <p>
   * The scanning thread will be used to make calls to {@code listener}. Scanning will not proceed
   * until control returns to JFreeSane, so your implementation should not monopolize this thread
   * for longer than necessary.
   *
   * @param listener if not {@code null}, this object will receive notifications about scan progress
   * @return a {@link BufferedImage} representing the image obtained from Sane
   * @throws IOException if an error occurred while talking to the backend
   * @throws SaneException if an application-level error was returned by the Sane daemon
   */
  public BufferedImage acquireImage(ScanListener listener) throws IOException, SaneException {
    Preconditions.checkState(isOpen(), "device is not open");
    if (listener == null) {
      listener = new ScanListenerAdapter();
    }
    return session.acquireImage(this, listener);
  }

  /**
   * Cancel the current operation of a remote SANE device.
   *
   * @throws IOException if an error occurs talking to the SANE backend
   * @throws IllegalStateException if the device is not open
   */
  public void cancel() throws IOException {
    Preconditions.checkState(isOpen(), "device is not open");
    session.cancelDevice(handle);
  }

  /**
   * Closes the device.
   *
   * @throws IOException if an error occurs talking to the SANE backend
   * @throws IllegalStateException if the device is already closed
   */
  @Override
  public void close() throws IOException {
    if (!isOpen()) {
      throw new IOException("device is already closed");
    }

    session.closeDevice(handle);
    handle = null;
  }

  @Override
  public String toString() {
    return "SaneDevice [name="
        + name
        + ", vendor="
        + vendor
        + ", model="
        + model
        + ", type="
        + type
        + "]";
  }

  /**
   * Returns the handle by which this device is known to the SANE backend, or {@code null} if if the
   * device is not open (see {@link #isOpen}).
   */
  SaneDeviceHandle getHandle() {
    return handle;
  }

  /**
   * Returns the list of options applicable to this device.
   *
   * @return a list of {@link SaneOption} instances
   * @throws IOException if a problem occurred talking to the SANE backend
   */
  public List<SaneOption> listOptions() throws IOException {
    if (options == null) {
      groups.clear();
      options = SaneOption.optionsFor(this);
    }

    return new ArrayList<>(options);
  }

  void addOptionGroup(OptionGroup group) {
    groups.add(group);
  }

  /**
   * Returns the list of option groups for this device.
   */
  public List<OptionGroup> getOptionGroups() throws IOException {
    listOptions();
    return new ArrayList<>(groups);
  }

  /**
   * Returns the option with the given name for this device. If the option does not exist,
   * {@code null} is returned. Name matching is case-sensitive.
   */
  public SaneOption getOption(String optionName) throws IOException {
    listOptions();
    return options
        .stream()
        .filter(o -> Objects.equals(optionName, o.getName()))
        .findFirst()
        .orElse(null);
  }

  SaneSession getSession() {
    return session;
  }

  /**
   * Informs this device that its options are stale (e.g. when the server tells us we need to reload
   * options after an option was set).
   */
  void invalidateOptions() {
    options = null;
  }
}
