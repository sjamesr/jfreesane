package au.com.southsky.jfreesane;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Represents a stream for reading image {@link Frame frames}.
 */
class FrameInputStream extends InputStream {
  private static final Logger log = Logger.getLogger(FrameInputStream.class.getName());

  private final SaneParameters parameters;
  private final InputStream underlyingStream;
  private final boolean bigEndian;

  public FrameInputStream(
      SaneParameters parameters, InputStream underlyingStream, boolean bigEndian) {
    this.parameters = parameters;
    this.underlyingStream = underlyingStream;
    this.bigEndian = bigEndian;
  }

  @Override
  public int read() throws IOException {
    return underlyingStream.read();
  }

  public Frame readFrame() throws IOException, SaneException {
    ByteArrayOutputStream bigArray;
    int imageSize = parameters.getBytesPerLine() * parameters.getLineCount();

    if (parameters.getLineCount() > 0) {
      bigArray = new ByteArrayOutputStream(imageSize);
    } else {
      bigArray = new ByteArrayOutputStream(256);
    }

    while (readRecord(bigArray) >= 0);

    if (imageSize > 0 && imageSize != bigArray.size()) {
      throw new IOException("truncated read");
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
    }

    return new Frame(parameters,outputArray);
  }

  private int readRecord(ByteArrayOutputStream destination) throws IOException, SaneException {
    DataInputStream inputStream = new DataInputStream(this);
    int length = inputStream.readInt();

    if (length == 0xffffffff) {
      log.fine("Reached end of records");

      // Hack: saned may actually write a status record here, even
      // though the sane specification says that no more bytes should
      // be read in an end-of-records situation
      int status = read();
      if (status != -1) {
        SaneStatus saneStatus = SaneStatus.fromWireValue(status);

        // An EOF condition is expected: that is what SANE told us!
        if (saneStatus != null && saneStatus != SaneStatus.STATUS_EOF) {
          throw new SaneException(saneStatus);
        }
      }

      return -1;
    }

    if (length > Integer.MAX_VALUE) {
      throw new IllegalStateException("TODO: support massive records");
    }

    byte[] buffer = new byte[length];
    int result = read(buffer, 0, length);
    if (result != length) {
      throw new IllegalStateException(
          "read too few bytes (" + result + "), was expecting " + length);
    }
    destination.write(buffer, 0, length);

    log.fine("Read a record of " + result + " bytes");
    return result;
  }
}