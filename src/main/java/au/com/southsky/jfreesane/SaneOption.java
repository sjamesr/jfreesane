package au.com.southsky.jfreesane;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
public class SaneOption {

  private static Group currentGroup = null;

  private enum OptionAction implements SaneEnum {
    GET_VALUE(0), SET_VALUE(1), SET_AUTO(2);

    private int actionNo;

    OptionAction(int actionNo) {
      this.actionNo = actionNo;
    }

    @Override
    public int getWireValue() {
      return actionNo;
    }
  }

  public enum OptionUnits implements SaneEnum {
    UNIT_NONE(0), UNIT_PIXEL(1), UNIT_BIT(2), UNIT_MM(3), UNIT_DPI(4), UNIT_PERCENT(
        5), UNIT_MICROSECOND(6);

    private final int wireValue;

    OptionUnits(int wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public int getWireValue() {
      return wireValue;
    }
  }

  public enum OptionCapability implements SaneEnum {
    SOFT_SELECT(1, "Option value may be set by software"), HARD_SELECT(
        2, "Option value may be set by user intervention at the scanner"), SOFT_DETECT(
        4, "Option value may be read by software"), EMULATED(
        8, "Option value may be detected by software"), AUTOMATIC(
        16, "Capability is emulated by driver"), INACTIVE(
        32, "Capability not currently active"), ADVANCED(64, "Advanced user option");

    private int capBit;
    private String description;

    OptionCapability(int capBit, String description) {
      this.capBit = capBit;
      this.description = description;
    }

    public String description() {
      return description;
    }

    @Override
    public int getWireValue() {
      return capBit;
    }
  }

  /**
   * Represents the information that the SANE daemon returns about the effect of modifying an
   * option.
   */
  public enum OptionWriteInfo implements SaneEnum {
    /**
     * The value passed to SANE was accepted, but the SANE daemon has chosen a slightly different
     * value than the one specified.
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
  private final String name;
  private final String title;
  private final String description;
  private final Group group;
  private final OptionValueType valueType;
  private final OptionUnits units;
  private final int size;
  private final Set<OptionCapability> optionCapabilities;
  private final OptionValueConstraintType constraintType;
  private final RangeConstraint rangeConstraints;
  private final List<String> stringContraints;
  // TODO: wrong level of abstraction
  private final List<SaneWord> wordConstraints;

  public SaneOption(SaneDevice device, int optionNumber, String name, String title,
      String description, Group group, OptionValueType type, OptionUnits units, int size,
      int capabilityWord, OptionValueConstraintType constraintType,
      RangeConstraint rangeConstraints, List<String> stringContraints,
      List<SaneWord> wordConstraints) {
    super();
    this.device = device;
    this.optionNumber = optionNumber;
    this.name = name;
    this.title = title;
    this.description = description;
    this.group = group;
    this.valueType = type;
    this.units = units;
    this.size = size;
    this.optionCapabilities = SaneEnums.enumSet(OptionCapability.class, capabilityWord);
    this.constraintType = constraintType;
    this.rangeConstraints = rangeConstraints;
    this.stringContraints = stringContraints;
    this.wordConstraints = wordConstraints;
  }

  public static List<SaneOption> optionsFor(SaneDevice device) throws IOException {
    Preconditions.checkState(device.isOpen(), "you must open() the device first");
    List<SaneOption> options = Lists.newArrayList();
    SaneSession session = device.getSession();

    SaneInputStream inputStream = session.getInputStream();
    SaneOutputStream outputStream = session.getOutputStream();

    // initialise the current group

    currentGroup = null;

    // send RPC 4

    outputStream.write(SaneWord.forInt(4));

    // select device

    outputStream.write(device.getHandle().getHandle());

    // first word of response in number of option entries

    int length = inputStream.readWord().integerValue() - 1;

    if (length <= 0) {
      return ImmutableList.of();
    }

    for (int i = 0; i <= length; i++) {
      SaneOption option = SaneOption.fromStream(inputStream, device, i);

      // We expect the first option to have an empty name. Subsequent options with empty names are
      // invalid
      if (option == null || (i > 0 && Strings.isNullOrEmpty(option.getName()))) {
        continue;
      }

      options.add(option);
    }

    return options;
  }

  private static SaneOption fromStream(
      SaneInputStream inputStream, SaneDevice device, int optionNumber) throws IOException {

    SaneOption option = null;

    // discard pointer

    inputStream.readWord();

    String optionName = inputStream.readString();
    String optionTitle = inputStream.readString();
    String optionDescription = inputStream.readString();
    int typeInt = inputStream.readWord().integerValue();
    // TODO: range check here
    OptionValueType valueType = SaneEnums.valueOf(OptionValueType.class, typeInt);

    int unitsInt = inputStream.readWord().integerValue();
    // TODO: range check here
    OptionUnits units = SaneEnums.valueOf(OptionUnits.class, unitsInt);

    int size = inputStream.readWord().integerValue();

    // constraint type

    int capabilityWord = inputStream.readWord().integerValue();
    int constraintTypeInt = inputStream.readWord().integerValue();
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

      int n = inputStream.readWord().integerValue();
      for (int i = 0; i < n; i++) {
        String stringConstraint = inputStream.readString();

        // the last element is a null terminator, don't add that
        if (i < n - 1) {
          stringConstraints.add(stringConstraint);
        }
      }

      break;
    case VALUE_LIST_CONSTRAINT:
      valueConstraints = Lists.newArrayList();
      n = inputStream.readWord().integerValue();
      for (int i = 0; i < n; i++) {
        // first element is list length, don't add that
        SaneWord value = inputStream.readWord();

        if (i != 0) {
          valueConstraints.add(value);
        }
      }

      break;
    case RANGE_CONSTRAINT:
      // TODO: still don't understand the 6 values

      SaneWord w0 = inputStream.readWord();
      SaneWord w1 = inputStream.readWord();
      SaneWord w2 = inputStream.readWord();
      SaneWord w3 = inputStream.readWord();
      // int w4 = inputStream.readWord().integerValue();

      switch (valueType) {
      case INT:
      case FIXED:
        rangeConstraint = new RangeConstraint(w1, w2, w3);
        break;
      default:
        throw new IllegalStateException("Integer or Fixed type expected for range constraint");
      }
      break;
    default:
      throw new IllegalStateException("Unknow constrint type");
    }

    // handle a change of group

    if (valueType == OptionValueType.GROUP) {
      currentGroup = new Group(optionTitle, valueType);
    } else {

      // TODO: lots

      option = new SaneOption(device, optionNumber, optionName, optionTitle, optionDescription,
          currentGroup, valueType, units, size, capabilityWord, constraintType, rangeConstraint,
          stringConstraints, valueConstraints);
    }

    return option;
  }

  public static class Group {

    private final String title;
    private final OptionValueType valueType;

    public Group(String title, OptionValueType valueType) {
      super();
      this.title = title;
      this.valueType = valueType;
    }

    public String getTitle() {
      return title;
    }

    public OptionValueType getValueType() {
      return valueType;
    }

  }

  public SaneDevice getDevice() {
    return device;
  }

  public String getName() {
    return name;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Group getGroup() {
    return group;
  }

  public OptionValueType getType() {
    return valueType;
  }

  public OptionUnits getUnits() {
    return units;
  }

  public int getSize() {
    return size;
  }

  public int getValueCount() {
    switch (valueType) {
    case BOOLEAN:
    case STRING:
      return 1;
    case INT:
    case FIXED:
      return size / SaneWord.SIZE_IN_BYTES;
    case BUTTON:
    case GROUP:
      throw new IllegalStateException("Option type '" + valueType + "' has no value count");
    default:
      throw new IllegalStateException("Option type '" + valueType + "' unknown");
    }
  }

  /**
   * Returns {@code true} if this option has a constraint other than
   * {@link OptionValueConstraintType#NO_CONSTRAINT}.
   */
  public boolean isConstrained() {
    return !OptionValueConstraintType.NO_CONSTRAINT.equals(constraintType);
  }

  public Set<OptionCapability> getCapabilities() {
    return EnumSet.copyOf(optionCapabilities);
  }

  public OptionValueConstraintType getConstraintType() {
    return constraintType;
  }

  public RangeConstraint getRangeConstraints() {
    return rangeConstraints;
  }

  public List<String> getStringContraints() {
    return stringContraints;
  }

  public List<SaneWord> getWordConstraints() {
    return wordConstraints;
  }

  public List<Integer> getIntegerValueListConstraint() {
    return Lists.transform(wordConstraints, SaneWord.TO_INTEGER_FUNCTION);
  }

  public List<Double> getFixedValueListConstraint() {
    return Lists.transform(wordConstraints, SaneWord.TO_FIXED_FUNCTION);
  }

  @Override
  public String toString() {
    return String.format(
        "Option: %s, %s, value type: %s, units: %s", name, title, valueType, units);
  }

  /**
   * Reads the current boolean value option. This option must be of type
   * {@link OptionValueType#BOOLEAN}.
   *
   * @throws IOException
   *           if a problem occurred while talking to SANE
   */
  public boolean getBooleanValue() throws IOException {
    Preconditions.checkState(valueType == OptionValueType.BOOLEAN, "option is not a boolean");
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
   * @throws IOException
   *           if a problem occurred while talking to SANE
   */
  public int getIntegerValue() throws IOException {
    // check for type agreement
    Preconditions.checkState(valueType == OptionValueType.INT, "option is not an integer");
    Preconditions.checkState(getValueCount() == 1, "option is an integer array, not integer");

    // Send RCP corresponding to:
    //
    // SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
    // SANE_Action a, void *v,
    // SANE_Int * i);

    ControlOptionResult result = readOption();
    Preconditions.checkState(result.getType() == OptionValueType.INT);
    Preconditions.checkState(result.getValueSize() == SaneWord.SIZE_IN_BYTES,
        "unexpected value size " + result.getValueSize() + ", expecting " + SaneWord.SIZE_IN_BYTES);

    // TODO: handle resource authorisation
    // TODO: check status -- may have to reload options!!
    return SaneWord.fromBytes(result.getValue()).integerValue(); // the
    // value
  }

  public List<Integer> getIntegerArrayValue() throws IOException {
    ControlOptionResult result = readOption();
    Preconditions.checkState(result.getType() == OptionValueType.INT);

    List<Integer> values = Lists.newArrayList();
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      values.add(SaneWord.fromBytes(result.getValue(), i).integerValue());
    }

    return values;
  }

  /**
   * Returns the value of this option interpreted as a LATIN-1 (SANE's default encoding)
   * encoded string.
   *
   * @throws IOException if a problem occurs reading the value from the SANE backend
   */
  public String getStringValue() throws IOException {
    return getStringValue(Charsets.ISO_8859_1);
  }

  public String getStringValue(Charset encoding) throws IOException {
    Preconditions.checkState(valueType == OptionValueType.STRING, "option is not a string");
    ControlOptionResult result = readOption();

    byte[] value = result.getValue();

    // string is null terminated
    int length;
    for (length = 0; length < value.length && value[length] != 0; length++)
      ;

    // trim the trailing null character
    return new String(result.getValue(), 0, length, encoding);
  }

  public double getFixedValue() throws IOException {
    Preconditions.checkState(
        valueType == OptionValueType.FIXED, "option is not of fixed precision type");

    ControlOptionResult result = readOption();
    return SaneWord.fromBytes(result.getValue()).fixedPrecisionValue();
  }

  public List<Double> getFixedArrayValue() throws IOException {
    ControlOptionResult result = readOption();
    Preconditions.checkState(result.getType() == OptionValueType.FIXED);

    List<Double> values = Lists.newArrayList();
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      values.add(SaneWord.fromBytes(result.getValue(), i).fixedPrecisionValue());
    }

    return values;
  }

  private ControlOptionResult readOption() throws IOException {
    // check that this option is readable
    Preconditions.checkState(isReadable(), "option is not readable");
    Preconditions.checkState(isActive(), "option is not active");

    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneWord.forInt(5));
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(optionNumber));
    out.write(OptionAction.GET_VALUE);

    out.write(valueType);
    out.write(SaneWord.forInt(size));

    int elementCount;

    switch (valueType) {
    case BOOLEAN:
    case FIXED:
    case INT:
      elementCount = size / SaneWord.SIZE_IN_BYTES;
      break;
    case STRING:
      elementCount = size;
      break;
    default:
      throw new IllegalStateException("Unsupported type " + valueType);
    }

    out.write(SaneWord.forInt(elementCount));

    for (int i = 0; i < size; i++) {
      out.write(0);// why do we need to provide a value
      // buffer in an RPC call ???
    }

    // read result
    ControlOptionResult result = ControlOptionResult.fromStream(
        device.getSession().getInputStream());
    return result;
  }

  /**
   * Sets the value of the current option to the supplied boolean value. Option value must be of
   * boolean type. SANE may ignore your preference, so if you need to ensure the value has been set
   * correctly, you should examine the return value of this method.
   *
   * @return the value that the option now has according to SANE
   */
  public boolean setBooleanValue(boolean value) throws IOException {
    ControlOptionResult result = writeOption(SaneWord.forInt(value ? 1 : 0));
    Preconditions.checkState(result.getType() == OptionValueType.BOOLEAN);

    return SaneWord.fromBytes(result.getValue()).integerValue() != 0;
  }

  public void setButtonValue() throws IOException {
    writeButtonOption();
  }

  /**
   * Sets the value of the current option to the supplied fixed-precision value. Option value must
   * be of fixed-precision type.
   */
  public double setFixedValue(double value) throws IOException {
    Preconditions.checkArgument(
        value >= -32768 && value <= 32767.9999, "value " + value + " is out of range");
    SaneWord wordValue = SaneWord.forFixedPrecision(value);
    ControlOptionResult result = writeOption(wordValue);
    Preconditions.checkState(result.getType() == OptionValueType.FIXED);

    return SaneWord.fromBytes(result.getValue()).fixedPrecisionValue();
  }

  /**
   * Sets the value of the current option to the supplied list of fixed-precision values. Option
   * value must be of fixed-precision type and {@link #getValueCount} must be more than 1.
   */
  public List<Double> setFixedValue(List<Double> value) throws IOException {
    List<SaneWord> wordValues = Lists.transform(value, new Function<Double, SaneWord>() {
      @Override
      public SaneWord apply(Double input) {
        Preconditions.checkArgument(
            input >= -32768 && input <= 32767.9999, "value " + input + " is out of range");
        return SaneWord.forFixedPrecision(input);
      }
    });

    ControlOptionResult result = writeWordListOption(wordValues);

    List<Double> newValues = Lists.newArrayListWithCapacity(result.getValueSize() / SaneWord.SIZE_IN_BYTES);
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      newValues.add(SaneWord.fromBytes(result.getValue(), i).fixedPrecisionValue());
    }

    return newValues;
  }

  public String setStringValue(String newValue) throws IOException {
    // check for type agreement
    Preconditions.checkState(valueType == OptionValueType.STRING);
    Preconditions.checkState(getValueCount() == 1);
    Preconditions.checkState(isWriteable());

    // new value must be STRICTLY less than size(), as SANE includes the
    // trailing null
    // that we will add later in its size
    Preconditions.checkState(newValue.length() < getSize());

    ControlOptionResult result = writeOption(newValue);
    Preconditions.checkState(result.getType() == OptionValueType.STRING);

    // TODO(sjr): maybe this should go somewhere common?
    String optionValueFromServer = new String(
        result.getValue(), 0, result.getValueSize() - 1, Charsets.ISO_8859_1);

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
   * @param newValue
   *          for the option
   * @return the value actually set
   * @throws IOException
   */
  public int setIntegerValue(int newValue) throws IOException {
    Preconditions.checkState(getValueCount() == 1, "option is an array");

    // check that this option is readable
    Preconditions.checkState(isWriteable());

    // Send RPC corresponding to:
    //
    // SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
    // SANE_Action a, void *v,
    // SANE_Int * i);

    ControlOptionResult result = writeOption(ImmutableList.of(newValue));
    Preconditions.checkState(result.getType() == OptionValueType.INT);
    Preconditions.checkState(result.getValueSize() == SaneWord.SIZE_IN_BYTES);

    return SaneWord.fromBytes(result.getValue()).integerValue();
  }

  public List<Integer> setIntegerValue(List<Integer> newValue) throws IOException {
    ControlOptionResult result = writeOption(newValue);

    List<Integer> newValues = Lists.newArrayListWithCapacity(result.getValueSize() / SaneWord.SIZE_IN_BYTES);
    for (int i = 0; i < result.getValueSize(); i += SaneWord.SIZE_IN_BYTES) {
      newValues.add(SaneWord.fromBytes(result.getValue(), i).integerValue());
    }

    return newValues;
  }

  private ControlOptionResult writeWordListOption(List<SaneWord> value) throws IOException {
    Preconditions.checkState(isWriteable(), "option is not writeable");
    Preconditions.checkState(isActive(), "option is not active");

    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneWord.forInt(5));
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(optionNumber));
    out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
    out.write(valueType);

    out.write(SaneWord.forInt(value.size() * SaneWord.SIZE_IN_BYTES));

    // Write the pointer to the words
    out.write(SaneWord.forInt(value.size()));

    for (SaneWord element : value) {
      // and the words themselves
      out.write(element);
    }

    ControlOptionResult result = handleWriteResponse();
    if (result.getInfo().contains(OptionWriteInfo.RELOAD_OPTIONS)
        || result.getInfo().contains(OptionWriteInfo.RELOAD_PARAMETERS)) {
      device.invalidateOptions();
      device.listOptions();
    }

    return result;
  }

  private ControlOptionResult writeOption(String value) throws IOException {
    Preconditions.checkState(valueType == OptionValueType.STRING);
    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneWord.forInt(5) /* rpc #5 */);
    out.write(SaneWord.forInt(device.getHandle().getHandle().integerValue()));
    out.write(SaneWord.forInt(this.optionNumber));
    out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
    out.write(valueType);

    // even if the string is empty, we still write out at least 1 byte (null
    // terminator)
    out.write(SaneWord.forInt(value.length() + 1));

    // write(String) takes care of writing the size for us
    out.write(value);

    return handleWriteResponse();
  }

  private ControlOptionResult writeOption(SaneWord word) throws IOException {
    return writeWordListOption(ImmutableList.of(word));
  }

  private ControlOptionResult writeOption(List<Integer> value) throws IOException {
    Preconditions.checkState(isActive(), "option " + getName() + " is not active");
    Preconditions.checkState(isWriteable(), "option " + getName() + " is not writeable");
    Preconditions.checkState(valueType == OptionValueType.INT);
    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneWord.forInt(5) /* rpc #5 */);
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(this.optionNumber));
    out.write(OptionAction.SET_VALUE);
    out.write(valueType);
    out.write(SaneWord.forInt(size));
    out.write(SaneWord.forInt(value.size()));
    for (Integer element : value) {
      out.write(SaneWord.forInt(element));
    }

    return handleWriteResponse();
  }

  private ControlOptionResult writeButtonOption() throws IOException {
    Preconditions.checkState(valueType == OptionValueType.BUTTON);
    SaneOutputStream out = device.getSession().getOutputStream();
    out.write(SaneWord.forInt(5) /* rpc #5 */);
    out.write(device.getHandle().getHandle());
    out.write(SaneWord.forInt(this.optionNumber));
    out.write(OptionAction.SET_VALUE);
    out.write(valueType);
    out.write(SaneWord.forInt(0));
    out.write(SaneWord.forInt(0)); // only one value follows

    return handleWriteResponse();
  }

  private ControlOptionResult handleWriteResponse() throws IOException {
    ControlOptionResult result = ControlOptionResult.fromStream(
        device.getSession().getInputStream());

    if (result.getInfo().contains(OptionWriteInfo.RELOAD_OPTIONS)) {
      device.invalidateOptions();
    }

    return result;
  }

  public boolean isActive() {
    return !optionCapabilities.contains(OptionCapability.INACTIVE);
  }

  public boolean isReadable() {
    return optionCapabilities.contains(OptionCapability.SOFT_DETECT);
  }

  public boolean isWriteable() {
    return optionCapabilities.contains(OptionCapability.SOFT_SELECT);
  }

  /**
   * Represents the result of calling {@code SANE_NET_CONTROL_OPTION} (RPC code 5).
   */
  private static class ControlOptionResult {
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

    public static ControlOptionResult fromStream(SaneInputStream stream) throws IOException {
      int status = stream.readWord().integerValue();

      if (status != 0) {
        SaneStatus statusEnum = SaneStatus.fromWireValue(status);
        throw new IOException(String.format(
            "unexpected status %d%s", status, statusEnum != null ? " (" + statusEnum + ")" : ""));
      }

      int info = stream.readWord().integerValue();

      OptionValueType type = SaneEnums.valueOf(
          OptionValueType.class, stream.readWord().integerValue());

      int valueSize = stream.readWord().integerValue();

      // read the pointer
      int pointer = stream.readWord().integerValue();
      byte[] value = null;
      if (pointer == 0) {
        // there is no value
      } else {
        value = new byte[valueSize];

        if (stream.read(value) != valueSize) {
          throw new IOException("truncated read while getting value");
        }
      }

      String resource = stream.readString();

      return new ControlOptionResult(status, info, type, valueSize, value, resource);
    }

    public int getStatus() {
      return status;
    }

    public Set<OptionWriteInfo> getInfo() {
      return Sets.immutableEnumSet(info);
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
