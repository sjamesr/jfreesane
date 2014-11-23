package au.com.southsky.jfreesane;

/**
 * A no-op implementation of {@link ScanListener}. You may extend this subclass
 * and provide implementations of methods corresponding to events of interest to
 * you.
 */
public class ScanListenerAdapter implements ScanListener {
  @Override
  public void scanningStarted(SaneDevice device) {
  }

  @Override
  public void frameAcquisitionStarted(SaneDevice device, SaneParameters parameters,
      int currentFrame, int likelyTotalFrames) {
  }

  @Override
  public void readRecord(SaneDevice device, int totalBytesRead, int imageSize) {
  }

  @Override
  public void scanningFinished(SaneDevice device) {
  }
}
