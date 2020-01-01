package au.com.southsky.jfreesane;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.primitives.UnsignedInteger;

/**
 * Represents a reader of {@link Frame frames}.
 */
class FrameReader {
  private static final Logger log = Logger.getLogger(FrameReader.class.getName());

  private final SaneDevice device;
  private final SaneParameters parameters;
  private final InputStream underlyingStream;
  private final boolean bigEndian;
  private final ScanListener listener;

  FrameReader(
      SaneDevice device,
      SaneParameters parameters,
      InputStream underlyingStream,
      boolean bigEndian,
      ScanListener listener) {
    this.device = device;
    this.parameters = parameters;
    this.underlyingStream = underlyingStream;
    this.bigEndian = bigEndian;
    this.listener = listener;
  }

  public Frame readFrame() throws IOException, SaneException {
    log.log(Level.FINE, "Reading frame: {0}", this);
    ByteArrayOutputStream bigArray;
    int imageSize = parameters.getBytesPerLine() * parameters.getLineCount();

    // For hand-held scanners where the line count is not known, report an image
    // size of -1 to the user.
    int reportedImageSize = parameters.getLineCount() == -1 ? -1 : imageSize;

    if (parameters.getLineCount() > 0) {
      bigArray = new ByteArrayOutputStream(imageSize);
    } else {
      bigArray = new ByteArrayOutputStream(256);
    }

    int bytesRead;
    int totalBytesRead = 0;
    while ((bytesRead = readRecord(bigArray)) >= 0) {
      totalBytesRead += bytesRead;
      listener.recordRead(device, totalBytesRead, reportedImageSize);
    }

    if (imageSize > 0 && bigArray.size() < imageSize) {
      int difference = imageSize - bigArray.size();
      log.log(
          Level.WARNING,
          "truncated read (got {0}, expected {1} bytes)",
          new Object[] {bigArray.size(), imageSize});
      bigArray.write(new byte[difference]);
      log.log(Level.WARNING, "padded image with {0} null bytes", difference);
    }

    // Now, if necessary, put the bytes in the correct order according
    // to the stream's endianness
    byte[] outputArray = bigArray.toByteArray();
    if (parameters.getDepthPerPixel() == 16 && !bigEndian) {
      if (outputArray.length % 2 != 0) {
        throw new IOException("expected a multiple of 2 frame length");
      }

      for (int i = 0; i < outputArray.length; i += 2) {
        byte swap = outputArray[i];
        outputArray[i] = outputArray[i + 1];
        outputArray[i + 1] = swap;
      }
    }

    if (parameters.getLineCount() <= 0) {
      // register the real height
      parameters.setLineCount(outputArray.length / parameters.getBytesPerLine());
      log.log(Level.FINE, "Detected new frame line count: {0}", parameters.getLineCount());
    }

    return new Frame(parameters, outputArray);
  }

  private int readRecord(OutputStream destination) throws IOException, SaneException {
    DataInputStream inputStream = new DataInputStream(underlyingStream);
    int length = inputStream.readInt();

    if (length == 0xffffffff) {
      log.fine("Reached end of records");

      // Hack: saned may actually write a status record here, even
      // though the sane specification says that no more bytes should
      // be read in an end-of-records situation
      int status = inputStream.read();
      if (status != -1) {
        SaneStatus saneStatus = SaneStatus.fromWireValue(status);

        // An EOF condition is expected: that is what SANE told us!
        if (saneStatus != null && saneStatus != SaneStatus.STATUS_EOF) {
          throw new SaneException(saneStatus);
        }
      }

      return -1;
    }

    if (UnsignedInteger.fromIntBits(length).longValue() > Integer.MAX_VALUE) {
      throw new IllegalStateException("TODO: support massive records");
    }

    int bytesRead = ByteStreams.copy(inputStream, destination, length);
    log.log(Level.FINE, "Read a record of {0} bytes", bytesRead);
    return bytesRead;
  }
}
