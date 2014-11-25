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

  @Override
  public void write(byte[] b) throws IOException {
    wrappedStream.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    wrappedStream.write(b, off, len);
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
    write(string.toCharArray());
  }

  /**
   * Writes the given char[] to the underlying stream in SANE string format. The format is:
   * 
   * <ul>
   * <li>if the char[] is non-empty, a {@link SaneWord} representing the length of the string plus a
   * null terminator</li>
   * <li>if the char[] is non-empty, the bytes of the char[]</li>
   * <li>unconditionally, a null terminator</li>
   * 
   * @param charArray character array to be written to the stream
   * @throws IOException
   */
  public void write(char[] charArray) throws IOException {
    if (charArray.length > 0) {
      byte[] encoded = SanePasswordEncoder.encodedLatin1(charArray);
      write(SaneWord.forInt(encoded.length + 1));
      write(encoded);
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