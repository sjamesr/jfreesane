package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This class wraps a {@link OutputStream} and provides a handful of utilities to serialize
 * SANE-related types to the underlying stream.
 * 
 * @author James Ring (sjr@jdns.org)
 */
public class SaneOutputStream extends OutputStream {
  private final OutputStream wrappedStream;

  /**
   * Creates a new {@code SaneOutputStream} that wraps the given stream.
   */
  public SaneOutputStream(OutputStream wrappedStream) {
    this.wrappedStream = wrappedStream;
  }

  @Override
  public void close() throws IOException {
    wrappedStream.close();
  }

  @Override
  public void flush() throws IOException {
    wrappedStream.flush();
  }

  @Override
  public void write(int b) throws IOException {
    wrappedStream.write(b);
  }

  /**
   * Writes the given string to the underlying stream in SANE string format. The format is:
   * 
   * <ul>
   * <li>if the string is non-empty, a {@link SaneWord} representing the length of the string plus a
   * null terminator</li>
   * <li>if the string is non-empty, the bytes of the string (see {@link String#toCharArray})</li>
   * <li>unconditionally, a null terminator</li>
   * 
   * @param string
   * @throws IOException
   */
  public void write(String string) throws IOException {
    if (string.length() > 0) {
      write(SaneWord.forInt(string.length() + 1));
      for (char c : string.toCharArray()) {
        if (c == 0) {
          throw new IllegalArgumentException("null characters not allowed");
        }

        write(c);
      }
    }

    write(0);
  }

  /**
   * Writes the bytes of the given {@link SaneWord} to the underlying stream. See
   * {@link SaneWord#getValue}.
   */
  public void write(SaneWord word) throws IOException {
    write(word.getValue());
  }

  /**
   * Writes the wire value of the given {@link SaneEnum} to the underlying stream.
   */
  public void write(SaneEnum someEnum) throws IOException {
    write(SaneWord.forInt(someEnum.getWireValue()));
  }
}