package au.com.southsky.jfreesane;

/**
 * Implementations of this interface can receive notifications when certain
 * events happen during a page scan. To receive notifications, use the
 * {@link SaneDevice#acquireImage(ScanListener)} method.
 *
 * <p>
 * If you are only interested in a subset of these events, you may want to
 * extend {@link ScannerListenerAdapter} instead of implementing this interface.
 *
 * <p>
 * If you are using {@code ScanListener} to update a user interface, the
 * frequency of notifications (specifically from {@link #recordRead}) may be too
 * frequent. In this case, you can wrap your listener using one of the methods
 * in {@link RateLimitingScanListeners} to obtain a listener that drops
 * notifications occurring too frequently.
 *
 * <p>
 * Notifications will tend to occur as follows:
 *
 * <ol>
 * <li>{@code scanningStarted} -- once a page scan has commenced</li>
 * <li>{@code frameAcquisitionStarted} -- once for each frame. Typically there
 * is only one frame per page, but in older three-pass color scanners (where one
 * scan pass is made per color), there will be three frames per page</li>
 * <li>{@code recordRead} -- once for each record, one or more records make up a
 * frame</li>
 * <li>{@code scanningFinished} -- once a page has been scanned</li>
 * </ol>
 *
 * <p>
 * JFreeSane attempts to report accurate numbers to listeners, but this is not
 * possible in all cases. In general, you should not depend on data obtained
 * from the listener when this could affect program correctness.
 */
public interface ScanListener {
  /**
   * Called once scanning has begun.
   *
   * @param device
   *          the device that is now acquiring an image
   */
  void scanningStarted(SaneDevice device);

  /**
   * Called once frame acquisition has begun. Typically there is one frame per
   * page, but in older three-pass color scanners (where one scan pass is made
   * for each color) this method will be called three times during a successful
   * scan.
   *
   * @param device
   *          the device that is acquiring the frame
   * @param parameters
   *          the parameters of the acquisition
   * @param currentFrame
   *          the zero-based index of the frame in the current scan (normally 0,
   *          but will be 0, 1 and 2 in successive calls for three-pass
   *          scanners)
   * @param likelyTotalFrames
   *          the number of frames that are likely to be received in this scan.
   *          JFreeSane cannot know this for sure, so the number should not be
   *          relied upon for program correctness
   */
  void frameAcquisitionStarted(SaneDevice device, SaneParameters parameters, int currentFrame,
      int likelyTotalFrames);

  /**
   * Called once for each record in a frame. A frame may consist of many
   * records.
   *
   * @param device
   *          the device that acquired the record
   * @param totalBytesRead
   *          the number of bytes read so far in this frame
   * @param imageSizeBytes
   *          the total number of bytes in the frame. When this cannot be known
   *          in advance, this will be set to -1 (e.g. the scanner is a
   *          hand-held scanner or uses page height detection)
   */
  void recordRead(SaneDevice device, int totalBytesRead, int imageSizeBytes);

  /**
   * Called once the current page is done.
   *
   * @param device
   *          the device that is now finished acquiring a page
   */
  void scanningFinished(SaneDevice device);
}
