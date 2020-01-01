package au.com.southsky.jfreesane;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class represents a SANE device option. An option may be active or inactive (see
 * {@link #isActive}). Active options may be read (see {@link #isReadable}) and modified (see
 * {@link #isWriteable}).
 *
 * <p>
 * Options have a type (see {@link #getType}), in order to read or write an option's value, you must
 * call the getter or setter method corresponding to the option's type. For example, for an option
 * of type {@link OptionValueType#STRING}, you will call {@link #setStringValue} or
 * {@link #getStringValue}.
 *
 * <p>
 * Options may have constraints that impose restrictions on the range of values the option may take.
 * Constraints have a type which may be obtained using {@link #getConstraintType}. You may read the
 * actual constraints by calling the constraint getter method corresponding to the constraint type.
 * For example, an option of type {@link OptionValueType#INT} may have a constraint of type
 * {@link OptionValueConstraintType#VALUE_LIST_CONSTRAINT}, which you may obtain by calling
 * {@link #getIntegerValueListConstraint}.
 *
 * @author James Ring (sjr@jdns.org)
 */
public final class SaneOption {

  private static final Logger logger = Logger.getLogger(SaneOption.class.getName());

  private enum OptionAction implements SaneEnum {
    GET_VALUE(0),
    SET_VALUE(1),
    SET_AUTO(2);

    private final int actionNo;

    OptionAction(int actionNo) {
      this.actionNo = actionNo;
    }

    @Override
    public int getWireValue() {
      return actionNo;
    }
  }

  /**
   * Instances of this enum are returned by {@link SaneOption#getUnits} indicating what units, if
   * any, the value has.
   */
  public enum OptionUnits implements SaneEnum {
    /**
     * The option has no units.
     */
    UNIT_NONE(0),

    /**
     * The option unit is pixels.
     */
    UNIT_PIXEL(1),

    /**
     * The option unit is bits.
     */
    UNIT_BIT(2),

    /**
     * The option unit is millimeters.
     */
    UNIT_MM(3),

    /**
     * The option unit is dots per inch.
     */
    UNIT_DPI(4),

    /**
     * The option unit is a percentage.
     */
    UNIT_PERCENT(5),

    /**
     * The option unit is microseconds.
     */
    UNIT_MICROSECOND(6);

    private final int wireValue;

    OptionUnits(int wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public int getWireValue() {
      return wireValue;
    }
  }

  /**
   * Represents the information that the SANE daemon returns about the effect of modifying an
   * option.
   */
  public enum OptionWriteInfo implements SaneEnum {
    /**
     * The value passed to SANE was accepted, but the SANE daemon has chosen a different value than
     * the one specified.
     */
    INEXACT(1),

    /**
     * Setting the option may have resulted in changes to other options and the client should
     * re-read options whose values it needs.
     */
    RELOAD_OPTIONS(2),

    /**
     * Setting the option may have caused a parameter set by the user to have changed.
     */
    RELOAD_PARAMETERS(4);

    private final int wireValue;

    OptionWriteInfo(int wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public int getWireValue() {
      return wireValue;
    }
  }

  private final SaneDevice device;
  private final int optionNumber;
  private final SaneOptionDescriptor descriptor;

  SaneOption(SaneDevice device, int optionNumber, SaneOptionDescriptor descriptor) {
    this.device = device;
    this.optionNumber = optionNumber;
    this.descriptor = descriptor;

    if (descriptor.getGroup() != null && getValueType() != OptionValueType.GROUP) {
      descriptor.getGroup().addOption(this);
    }
  }

  static List<SaneOption> optionsFor(SaneDevice device) throws IOException {
    Preconditions.checkState(device.isOpen(), "you must open() the device first");
    SaneSession session = device.getSession();

    SaneInputStream inputStream = session.getInputStream();
    SaneOutputStream outputStream = session.getOutputStream();

    // send SANE_NET_GET_OPTION_DESCRIPTORS

    outputStream.write(SaneRpcCode.SANE_NET_GET_OPTION_DESCRIPTORS);

    // select device

    outputStream.write(device.getHandle().getHandle());
    outputStream.flush();

    // first word of response is number of option entries
    int length = inputStream.readWord().integerValue() - 1;

    if (length <= 0) {
      return new ArrayList<>(0);
    }

    List<SaneOption> options = new ArrayList<>(length);
    for (int i = 0; i <= length; i++) {
      SaneOption option = SaneOption.fromStream(inputStream, device, i);

      if (option.getValueType() == OptionValueType.GROUP) {
        device.addOptionGroup(option.getGroup());
      } else {
        // http://code.google.com/p/jfreesane/issues/detail?id=1
        // The first option always has an empty name. Sometimes we see options after the first option
        // that have empty names. Elsewhere we assume that option names are unique, so this option is
        // omitted
        if (i > 0 && (option.getName() == null || option.getName().isEmpty())) {
          logger.fine(String.format("ignoring null or empty option with id %d: %s", i, option));
          continue;
        }

        if (option.isWriteable() && option.isHardSelectable()) {
          // This option is invalid, it can't be both hardware and software selectable.
          continue;
        }

        if (option.isWriteable() && !option.isReadable()) {
          // Can't have a write-only option.
          continue;
        }

        if (!(option.isWriteable() || option.isReadable() || option.isHardSelectable())) {
          // Useless option, skip it.
          continue;
        }

        options.add(option);
      }
    }

    return options;
  }

  private static SaneOption fromStream(
      SaneInputStream inputStream, SaneDevice device, int optionNumber) throws IOException {
    return new SaneOption(device, optionNumber, inputStream.readOptionDescriptor());
  }

  public SaneDevice getDevice() {
    return device;
  }

  public String getName() {
    return descriptor.getName();
  }

  public String getTitle() {
    return descriptor.getTitle();
  }

  public String getDescription() {
    return descriptor.getDescription();
  }

  public OptionGroup getGroup() {
    return descriptor.getGroup();
  }

  public OptionValueType getType() {
    return descriptor.getValueType();
  }

  public OptionUnits getUnits() {
    return descriptor.getUnits();
  }

  public int getSize() {
    return descriptor.getSize();
  }

  public int getValueCount() {
    switch (descriptor.getValueType()) {
      case BOOLEAN:
      case STRING:
        return 1;
      case INT:
      case FIXED:
        return getSize() / SaneWord.SIZE_IN_BYTES;
      case BUTTON:
      case GROUP:
        throw new IllegalStateException(
            "Option type '" + descriptor.getValueType() + "' has no value count");
      default:
        throw new IllegalStateException("Option type '" + descriptor.getValueType() + "' unknown");
    }
  }

  /**
   * Returns {@code true} if this option has a constraint other than
   * {@link OptionValueConstraintType#NO_CONSTRAINT}.
   */
  public boolean isConstrained() {
    return !OptionValueConstraintType.NO_CONSTRAINT.equals(descriptor.getConstraintType());
  }

  public OptionValueConstraintType getConstraintType() {
    return descriptor.getConstraintType();
  }

  public RangeConstraint getRangeConstraints() {
    return descriptor.getRangeConstraints();
  }

  public List<String> getStringConstraints() {
    return descriptor.getStringConstraints();
  }

  public List<SaneWord> getWordConstraints() {
    return descriptor.getWordConstraints();
  }

  public List<Integer> getIntegerValueListConstraint() {
    return descriptor
        .getWordConstraints()
        .stream()
        .map(SaneWord::integerValue)
        .collect(Collectors.toList());
  }

  public List<Double> getFixedValueListConstraint() {
    return descriptor
        .getWordConstraints()
        .stream()
        .map(SaneWord::fixedPrecisionValue)
        .collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return String.format(
        "Option: %s, %s, value type: %s, units: %s",
        descriptor.getName(),
        descriptor.getTitle(),
        descriptor.getValueType(),
        descriptor.getUnits());
  }

  private OptionValueType getValueType() {
    return descriptor.getValueType();
  }

  /**
   * Reads the current boolean value option. This option must be of type
   * {@link OptionValueType#BOOLEAN}.
   *
   * @throws IOException if a problem occurred while talking to SANE
   */
  public boolean getBooleanValue() throws IOException, SaneException {
    Preconditions.checkState(getValueType() == OptionValueType.BOOLEAN, "option is not a boolean");
    Preconditions.checkState(getValueCount() == 1, "option is a boolean array, not boolean");

    ControlOptionResult result = readOption();
    return SaneWord.fromBytes(result.getValue()).integerValue() != 0;
  }

  /**
   * Reads the current Integer value option. We do not cache value from previous get or set
   * operations so each get involves a round trip to the server.
   *
   * TODO: consider caching the returned value for "fast read" later
   *
   * @return the value of the option
   * @throws IOException if a problem occurred while talking to SANE
   */
  public int getIntegerValue() throws IOException, SaneException {
    // check for type agreement
    Preconditions.checkState(getValueType() == OptionValueType.INT, "option is not an integer");
    Preconditions.checkState(getValueCount() == 1, "option is an integer array, not integer");

    // Send RCP corresponding to:
    //
    // SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
    // SANE_Action a, void *v,
    // SANE_Int * i);

    ControlOptionResult result = readOption();
    Preconditions.checkState(result.getType() == OptionValueType.INT);
    Preconditions.checkState(
        result.getValueSize() == SaneWord.SIZE_IN_BYTES,
        "unexpected value size " + result.getValueSize() + ", expecting " + SaneWord.SIZE_IN_BYTES);

    // TODO: handle resource authorisation
    // TODO: check status -- may have to reload options!!
    return SaneWord.fromBytes(result.getValue()).integerValue(); // the
    // value
  }

  public List<Integer> getIntegerArrayValue() throws IOException, SaneException {
    ControlOptionResult result = readOption();
    Preconditions.checkState(result.getType() == OptionValueType.INT);

    List<Integer> values = new ArrayList<>();
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      values.add(SaneWord.fromBytes(result.getValue(), i).integerValue());
    }

    return values;
  }

  /**
   * Returns the value of this option interpreted as a LATIN-1 (SANE's default encoding) encoded
   * string.
   *
   * @throws IOException if a problem occurs reading the value from the SANE backend
   */
  public String getStringValue() throws IOException, SaneException {
    return getStringValue(StandardCharsets.ISO_8859_1);
  }

  public String getStringValue(Charset encoding) throws IOException, SaneException {
    Preconditions.checkState(getValueType() == OptionValueType.STRING, "option is not a string");
    ControlOptionResult result = readOption();

    byte[] value = result.getValue();

    // string is null terminated
    int length = 0;
    while (length < value.length && value[length] != 0) {
      length++;
    }

    // trim the trailing null character
    return new String(result.getValue(), 0, length, encoding);
  }

  public double getFixedValue() throws IOException, SaneException {
    Preconditions.checkState(
        getValueType() == OptionValueType.FIXED, "option is not of fixed precision type");

    ControlOptionResult result = readOption();
    return SaneWord.fromBytes(result.getValue()).fixedPrecisionValue();
  }

  public List<Double> getFixedArrayValue() throws IOException, SaneException {
    ControlOptionResult result = readOption();
    Preconditions.checkState(result.getType() == OptionValueType.FIXED);

    List<Double> values = new ArrayList<>();
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      values.add(SaneWord.fromBytes(result.getValue(), i).fixedPrecisionValue());
    }

    return values;
  }

  private ControlOptionResult readOption() throws IOException, SaneException {
    // check that this option is readable
    Preconditions.checkState(isReadable(), "option is not readable");
    Preconditions.checkState(isActive(), "option is not active");

    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneRpcCode.SANE_NET_CONTROL_OPTION);
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(optionNumber));
    out.write(OptionAction.GET_VALUE);

    out.write(getValueType());
    out.write(SaneWord.forInt(getSize()));

    int elementCount;

    switch (getValueType()) {
      case BOOLEAN:
      case FIXED:
      case INT:
        elementCount = getSize() / SaneWord.SIZE_IN_BYTES;
        break;
      case STRING:
        elementCount = getSize();
        break;
      default:
        throw new IllegalStateException("Unsupported type " + getValueType());
    }

    out.write(SaneWord.forInt(elementCount));

    for (int i = 0; i < getSize(); i++) {
      out.write(0); // why do we need to provide a value
      // buffer in an RPC call ???
    }

    out.flush();

    // read result
    return ControlOptionResult.fromSession(device.getSession());
  }

  /**
   * Sets the value of the current option to the supplied boolean value. Option value must be of
   * boolean type. SANE may ignore your preference, so if you need to ensure the value has been set
   * correctly, you should examine the return value of this method.
   *
   * @return the value that the option now has according to SANE
   */
  public boolean setBooleanValue(boolean value) throws IOException, SaneException {
    ControlOptionResult result = writeOption(SaneWord.forInt(value ? 1 : 0));
    Preconditions.checkState(result.getType() == OptionValueType.BOOLEAN);

    return SaneWord.fromBytes(result.getValue()).integerValue() != 0;
  }

  public void setButtonValue() throws IOException, SaneException {
    writeButtonOption();
  }

  /**
   * Sets the value of the current option to the supplied fixed-precision value. Option value must
   * be of fixed-precision type.
   */
  public double setFixedValue(double value) throws IOException, SaneException {
    Preconditions.checkArgument(
        value >= -32768 && value <= 32767.9999, "value %d is out of range", value);
    SaneWord wordValue = SaneWord.forFixedPrecision(value);
    ControlOptionResult result = writeOption(wordValue);
    Preconditions.checkState(
        result.getType() == OptionValueType.FIXED,
        "setFixedValue is not appropriate for option of type " + result.getType());

    return SaneWord.fromBytes(result.getValue()).fixedPrecisionValue();
  }

  private static SaneWord fixedValueToWord(double value) {
    Preconditions.checkArgument(
        value >= -32768 && value <= 32767.9999, "value %f is out of range", value);
    return SaneWord.forFixedPrecision(value);
  }

  /**
   * Sets the value of the current option to the supplied list of fixed-precision values. Option
   * value must be of fixed-precision type and {@link #getValueCount} must be more than 1.
   */
  public List<Double> setFixedValue(List<Double> value) throws IOException, SaneException {
    List<SaneWord> wordValues =
        value.stream().map(SaneOption::fixedValueToWord).collect(Collectors.toList());

    ControlOptionResult result = writeWordListOption(wordValues);

    List<Double> newValues = new ArrayList<>(result.getValueSize() / SaneWord.SIZE_IN_BYTES);
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      newValues.add(SaneWord.fromBytes(result.getValue(), i).fixedPrecisionValue());
    }

    return newValues;
  }

  public String setStringValue(String newValue) throws IOException, SaneException {
    // check for type agreement
    Preconditions.checkState(getValueType() == OptionValueType.STRING);
    Preconditions.checkState(getValueCount() == 1);
    Preconditions.checkState(isWriteable());

    // new value must be STRICTLY less than size(), as SANE includes the
    // trailing null
    // that we will add later in its size
    Preconditions.checkState(
        newValue.length() < getSize(),
        "string value '"
            + newValue
            + "' (length="
            + newValue.length()
            + ") exceeds maximum size of "
            + (getSize() - 1)
            + " byte(s) for option "
            + getName());

    ControlOptionResult result = writeOption(newValue);
    Preconditions.checkState(result.getType() == OptionValueType.STRING);

    // TODO(sjr): maybe this should go somewhere common?
    String optionValueFromServer =
        new String(result.getValue(), 0, result.getValueSize() - 1, StandardCharsets.ISO_8859_1);

    Preconditions.checkState(
        result.getInfo().contains(OptionWriteInfo.INEXACT) ^ newValue.equals(optionValueFromServer),
        "new option value does not match when it should");

    return optionValueFromServer;
  }

  /**
   * Set the value of the current option to the supplied value. Option value must be of integer type
   *
   * TODO: consider caching the returned value for "fast read" later
   *
   * @param newValue for the option
   * @return the value actually set
   * @throws IOException
   */
  public int setIntegerValue(int newValue) throws IOException, SaneException {
    Preconditions.checkState(getValueCount() == 1, "option is an array");

    // check that this option is readable
    Preconditions.checkState(isWriteable());

    // Send RPC corresponding to:
    //
    // SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
    // SANE_Action a, void *v,
    // SANE_Int * i);

    ControlOptionResult result = writeOption(Collections.singletonList(newValue));
    Preconditions.checkState(result.getType() == OptionValueType.INT);
    Preconditions.checkState(result.getValueSize() == SaneWord.SIZE_IN_BYTES);

    return SaneWord.fromBytes(result.getValue()).integerValue();
  }

  public List<Integer> setIntegerValue(List<Integer> newValue) throws IOException, SaneException {
    ControlOptionResult result = writeOption(newValue);

    List<Integer> newValues = new ArrayList<>(result.getValueSize() / SaneWord.SIZE_IN_BYTES);
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      newValues.add(SaneWord.fromBytes(result.getValue(), i).integerValue());
    }

    return newValues;
  }

  private ControlOptionResult writeWordListOption(List<SaneWord> value)
      throws IOException, SaneException {
    Preconditions.checkState(isWriteable(), "option is not writeable");
    Preconditions.checkState(isActive(), "option is not active");

    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneRpcCode.SANE_NET_CONTROL_OPTION);
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(optionNumber));
    out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
    out.write(getValueType());

    out.write(SaneWord.forInt(value.size() * SaneWord.SIZE_IN_BYTES));

    // Write the pointer to the words
    out.write(SaneWord.forInt(value.size()));

    for (SaneWord element : value) {
      // and the words themselves
      out.write(element);
    }

    out.flush();

    ControlOptionResult result = handleWriteResponse();
    if (result.getInfo().contains(OptionWriteInfo.RELOAD_OPTIONS)
        || result.getInfo().contains(OptionWriteInfo.RELOAD_PARAMETERS)) {
      device.invalidateOptions();
      device.listOptions();
    }

    return result;
  }

  private ControlOptionResult writeOption(String value) throws IOException, SaneException {
    Preconditions.checkState(getValueType() == OptionValueType.STRING);
    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneRpcCode.SANE_NET_CONTROL_OPTION);
    out.write(SaneWord.forInt(device.getHandle().getHandle().integerValue()));
    out.write(SaneWord.forInt(this.optionNumber));
    out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
    out.write(getValueType());

    // even if the string is empty, we still write out at least 1 byte (null
    // terminator)
    out.write(SaneWord.forInt(value.length() + 1));

    // write(String) takes care of writing the size for us
    out.write(value);
    out.flush();

    return handleWriteResponse();
  }

  private ControlOptionResult writeOption(SaneWord word) throws IOException, SaneException {
    return writeWordListOption(Collections.singletonList(word));
  }

  private ControlOptionResult writeOption(List<Integer> value) throws IOException, SaneException {
    Preconditions.checkState(isActive(), "option %s is not active", getName());
    Preconditions.checkState(isWriteable(), "option %s is not writeable", getName());
    Preconditions.checkState(
        getValueType() == OptionValueType.INT,
        "option %s is %s-typed, you must use the corresponding methods to set the value",
        getName(),
        getValueType());
    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneRpcCode.SANE_NET_CONTROL_OPTION);
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(optionNumber));
    out.write(OptionAction.SET_VALUE);
    out.write(getValueType());
    out.write(SaneWord.forInt(getSize()));
    out.write(SaneWord.forInt(value.size()));
    for (Integer element : value) {
      out.write(SaneWord.forInt(element));
    }
    out.flush();

    return handleWriteResponse();
  }

  private ControlOptionResult writeButtonOption() throws IOException, SaneException {
    Preconditions.checkState(getValueType() == OptionValueType.BUTTON);
    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneRpcCode.SANE_NET_CONTROL_OPTION);
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(this.optionNumber));
    out.write(OptionAction.SET_VALUE);
    out.write(getValueType());
    out.write(SaneWord.forInt(0));
    out.write(SaneWord.forInt(0)); // only one value follows
    out.flush();

    return handleWriteResponse();
  }

  private ControlOptionResult handleWriteResponse() throws IOException, SaneException {
    ControlOptionResult result = ControlOptionResult.fromSession(device.getSession());

    if (result.getInfo().contains(OptionWriteInfo.RELOAD_OPTIONS)) {
      device.invalidateOptions();
    }

    return result;
  }

  public boolean isActive() {
    return !descriptor.getOptionCapabilities().contains(OptionCapability.INACTIVE);
  }

  public boolean isReadable() {
    return descriptor.getOptionCapabilities().contains(OptionCapability.SOFT_DETECT);
  }

  public boolean isWriteable() {
    return descriptor.getOptionCapabilities().contains(OptionCapability.SOFT_SELECT);
  }

  boolean isHardSelectable() {
    return descriptor.getOptionCapabilities().contains(OptionCapability.HARD_SELECT);
  }

  /**
   * Represents the result of calling {@code SANE_NET_CONTROL_OPTION} (RPC code 5).
   */
  private static final class ControlOptionResult {
    private final int status;
    private final Set<OptionWriteInfo> info;
    private final OptionValueType type;
    private final int valueSize;
    private final byte[] value;
    private final String resource;

    private ControlOptionResult(
        int status, int info, OptionValueType type, int valueSize, byte[] value, String resource) {
      this.status = status;
      this.info = SaneEnums.enumSet(OptionWriteInfo.class, info);
      this.type = type;
      this.valueSize = valueSize;
      this.value = value;
      this.resource = resource;
    }

    private static ControlOptionResult fromSession(SaneSession session)
        throws IOException, SaneException {
      SaneInputStream stream = session.getInputStream();

      // Expected record format:
      // SANE_Status status
      // SANE_Word info
      // SANE_Word value_type
      // SANE_Word value_size
      // void *value
      // SANE_String *resource
      // See http://sane-project.org/html/doc017.html#s5.2.6.
      SaneWord status = stream.readWord();

      if (status.integerValue() != 0) {
        throw SaneException.fromStatusWord(status);
      }

      int info = stream.readWord().integerValue();

      OptionValueType type =
          SaneEnums.valueOf(OptionValueType.class, stream.readWord().integerValue());

      int valueSize = stream.readWord().integerValue();

      // read the pointer
      int pointer = stream.readWord().integerValue();
      byte[] value = null;
      if (pointer != 0) {
        value = new byte[valueSize];

        if (ByteStreams.readAllBytes(stream, value) != valueSize) {
          throw new IOException("truncated read while getting value");
        }
      }

      String resource = stream.readString();

      if (!resource.isEmpty()) {
        if (!session.authorize(resource)) {
          throw new SaneException(SaneStatus.STATUS_ACCESS_DENIED);
        }

        status = stream.readWord();
        info = stream.readWord().integerValue();
        type = SaneEnums.valueOf(OptionValueType.class, stream.readWord().integerValue());
        valueSize = stream.readWord().integerValue();

        // read the pointer
        pointer = stream.readWord().integerValue();
        value = null;
        if (pointer != 0) {
          value = new byte[valueSize];

          if (stream.read(value) != valueSize) {
            throw new IOException("truncated read while getting value");
          }
        }

        if (status.integerValue() != 0) {
          throw SaneException.fromStatusWord(status);
        }
      }

      return new ControlOptionResult(status.integerValue(), info, type, valueSize, value, resource);
    }

    public int getStatus() {
      return status;
    }

    public Set<OptionWriteInfo> getInfo() {
      return Collections.unmodifiableSet(info);
    }

    public OptionValueType getType() {
      return type;
    }

    public int getValueSize() {
      return valueSize;
    }

    public byte[] getValue() {
      return value;
    }

    public String getResource() {
      return resource;
    }
  }
}
