package au.com.southsky.jfreesane;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import au.com.southsky.jfreesane.SaneOption.OptionUnits;
import au.com.southsky.jfreesane.SaneSession.SaneParameters;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Wraps an {@link InputStream} to provide some methods for deserializing SANE-related types.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class SaneInputStream extends InputStream {
  private static final Logger logger = Logger.getLogger(SaneInputStream.class.getName());

  private final SaneSession saneSession;
  private InputStream wrappedStream;
  private OptionGroup currentGroup;

  public SaneInputStream(SaneSession saneSession, InputStream wrappedStream) {
    this.saneSession = saneSession;
    this.wrappedStream = wrappedStream;
  }

  @Override
  public int read() throws IOException {
    return wrappedStream.read();
  }

  public List<SaneDevice> readDeviceList() throws IOException, SaneException {
    // Status first
    SaneStatus status = readStatus();

    if (!SaneStatus.STATUS_GOOD.equals(status)) {
      throw new SaneException(status);
    }

    // now we're reading an array, decode the length of the array (which
    // includes the null if the array is non-empty)
    int length = readWord().integerValue() - 1;

    if (length <= 0) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<SaneDevice> result = ImmutableList.builder();

    for (int i = 0; i < length; i++) {
      SaneDevice device = readSaneDevicePointer();
      if (device == null) {
        throw new IllegalStateException("null pointer encountered when not expected");
      }

      result.add(device);
    }

    // read past a trailing byte in the response that I haven't figured
    // out yet...
    readWord();

    return result.build();
  }

  /**
   * Reads a single {@link SaneDevice} definition pointed to by the pointer at the current location
   * in the stream. Returns {@code null} if the pointer is a null pointer.
   */
  private SaneDevice readSaneDevicePointer() throws IOException {
    if (!readPointer()) {
      // TODO(sjr): why is there always a null pointer here?
      // return null;
    }

    // now we assume that there's a sane device ready to parse
    return readSaneDevice();
  }

  /**
   * Reads a single pointer and returns {@code true} if it was non-null.
   */
  private boolean readPointer() throws IOException {
    return readWord().integerValue() != 0;
  }

  private SaneDevice readSaneDevice() throws IOException {
    String deviceName = readString();
    String deviceVendor = readString();
    String deviceModel = readString();
    String deviceType = readString();

    return new SaneDevice(this.saneSession, deviceName, deviceVendor, deviceModel, deviceType);
  }

  public String readString() throws IOException {
    // read the length
    int length = readWord().integerValue();

    if (length == 0) {
      return "";
    }

    // now read all the bytes
    byte[] input = new byte[length];
    if (read(input) != input.length) {
      throw new IllegalStateException("truncated input while reading string");
    }

    // skip the null terminator
    return new String(input, 0, input.length - 1, Charsets.ISO_8859_1);
  }

  public SaneParameters readSaneParameters() throws IOException {
    int frame = readWord().integerValue();
    boolean lastFrame = readWord().integerValue() == 1;
    int bytesPerLine = readWord().integerValue();
    int pixelsPerLine = readWord().integerValue();
    int lines = readWord().integerValue();
    int depth = readWord().integerValue();

    return new SaneSession.SaneParameters(frame, lastFrame, bytesPerLine, pixelsPerLine, lines,
        depth);
  }

  public SaneStatus readStatus() throws IOException {
    return SaneStatus.fromWireValue(readWord().integerValue());
  }

  public SaneWord readWord() throws IOException {
    return SaneWord.fromStream(this);
  }

  public SaneOptionDescriptor readOptionDescriptor() throws IOException {
    // discard pointer
    readWord();

    String optionName = readString();
    String optionTitle = readString();
    String optionDescription = readString();
    int typeInt = readWord().integerValue();
    // TODO: range check here
    OptionValueType valueType = SaneEnums.valueOf(OptionValueType.class, typeInt);

    if (valueType == OptionValueType.GROUP) {
      // a new group applies!
      currentGroup = new OptionGroup(optionTitle);
    }

    int unitsInt = readWord().integerValue();
    // TODO: range check here
    OptionUnits units = SaneEnums.valueOf(OptionUnits.class, unitsInt);

    int size = readWord().integerValue();

    // constraint type

    int capabilityWord = readWord().integerValue();
    int constraintTypeInt = readWord().integerValue();
    // TODO: range check here
    OptionValueConstraintType constraintType = SaneEnums.valueOf(
        OptionValueConstraintType.class, constraintTypeInt);

    // decode the constraint

    List<String> stringConstraints = null;
    List<SaneWord> valueConstraints = null;
    RangeConstraint rangeConstraint = null;

    switch (constraintType) {
    case NO_CONSTRAINT:
      // inputStream.readWord(); // discard empty list
      break;
    case STRING_LIST_CONSTRAINT:
      stringConstraints = Lists.newArrayList();

      int n = readWord().integerValue();
      for (int i = 0; i < n; i++) {
        String stringConstraint = readString();

        // the last element is a null terminator, don't add that
        if (i < n - 1) {
          stringConstraints.add(stringConstraint);
        }
      }

      break;
    case VALUE_LIST_CONSTRAINT:
      valueConstraints = Lists.newArrayList();
      n = readWord().integerValue();
      for (int i = 0; i < n; i++) {
        // first element is list length, don't add that
        SaneWord value = readWord();

        if (i != 0) {
          valueConstraints.add(value);
        }
      }

      break;
    case RANGE_CONSTRAINT:
      // TODO: still don't understand the 6 values

      SaneWord w0 = readWord();
      SaneWord w1 = readWord();
      SaneWord w2 = readWord();
      SaneWord w3 = readWord();
      // int w4 = inputStream.readWord().integerValue();

      switch (valueType) {
      case INT:
      case FIXED:
        rangeConstraint = new RangeConstraint(w1, w2, w3);
        break;
      default:
        logger.log(Level.WARNING, "Ignoring invalid option type/constraint combination: "
            + "value_type={0},constraint_type={1} for option {2}. "
            + "Option will be treated by jfreesane as unconstrained", new Object[] { valueType,
            constraintType, optionName });
      }
      break;
    default:
      throw new IllegalStateException("Unknown constraint type");
    }

    return new SaneOptionDescriptor(optionName, optionTitle, optionDescription, currentGroup,
        valueType, units, size, SaneEnums.enumSet(OptionCapability.class, capabilityWord),
        constraintType, rangeConstraint, stringConstraints, valueConstraints);
  }
}
