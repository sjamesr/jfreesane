// Copyright 2012 Google Inc. All Rights Reserved.

package au.com.southsky.jfreesane;

/**
 * Represents a SANE device handle. This class is used solely to implement JFreeSane, therefore it
 * is not visible to others.
 *
 * @author sjr@google.com (James Ring)
 */
class SaneDeviceHandle {
  private final SaneWord handle;

  /**
   * Constructs a new {@code SaneDeviceHandle}. This will typically be done in response to a call to
   * {@link SaneDevice#open}.
   *
   * @param handle the handle assigned to the device by the SANE daemon
   */
  SaneDeviceHandle(SaneWord handle) {
    this.handle = handle;
  }

  /**
   * Returns the handle that was assigned to the device by SANE in response to the {@code open}
   * request.
   *
   * @see SaneDevice#open
   */
  public SaneWord getHandle() {
    return handle;
  }
}
