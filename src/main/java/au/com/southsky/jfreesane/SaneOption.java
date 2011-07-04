package au.com.southsky.jfreesane;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import au.com.southsky.jfreesane.SaneSession.SaneInputStream;
import au.com.southsky.jfreesane.SaneSession.SaneOutputStream;
import au.com.southsky.jfreesane.SaneSession.SaneWord;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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

	public enum OptionValueType implements SaneEnum {
		BOOLEAN(0), INT(1), FIXED(2), STRING(3), BUTTON(4), GROUP(5);

		private int typeNo;

		OptionValueType(int typeNo) {
			this.typeNo = typeNo;
		}

		public int typeNo() {
			return typeNo;
		}

		@Override
		public int getWireValue() {
			return typeNo;
		}
	};

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
	};

	public enum OptionCapability implements SaneEnum {
		SOFT_SELECT(1, "Option value may be set by software"), HARD_SELECT(2,
				"Option value may be set by user intervention at the scanner"), SOFT_DETECT(
				4, "Option value may be read by software"), EMULATED(8,
				"Option value may be detected by software"), AUTOMATIC(16,
				"Capability is emulated by driver"), INACTIVE(32,
				"Capability not currently active"), ADVANCED(64,
				"Advanced user option");

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
	 * Represents the information that the SANE daemon returns about the effect
	 * of modifying an option.
	 */
	public enum OptionWriteInfo implements SaneEnum {
		/**
		 * The value passed to SANE was accepted, but the SANE daemon has chosen
		 * a slightly different value than the one specified.
		 */
		INEXACT(1),

		/**
		 * Setting the option may have resulted in changes to other options and
		 * the client should re-read options whose values it needs.
		 */
		RELOAD_OPTIONS(2),

		/**
		 * Setting the option may have caused a parameter set by the user to
		 * have changed.
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

	public enum OptionValueConstraintType implements SaneEnum {
		NO_CONSTRAINT(0, "No constraint"), RANGE_CONSTRAINT(1, ""), VALUE_LIST_CONSTRAINT(
				2, ""), STRING_LIST_CONSTRAINT(3, "");

		private final int wireValue;
		private final String description;

		OptionValueConstraintType(int wireValue, String description) {
			this.wireValue = wireValue;
			this.description = description;
		}

		public String description() {
			return description;
		}

		@Override
		public int getWireValue() {
			return wireValue;
		}
	}

	public static abstract class RangeConstraint {
		protected final int min;
		protected final int max;
		protected final int quantum;

		RangeConstraint(int min, int max, int quantum) {
			this.min = min;
			this.max = max;
			this.quantum = quantum;
		}

	}

	public static class IntegerRangeContraint extends RangeConstraint {

		IntegerRangeContraint(int min, int max, int quantum) {
			super(min, max, quantum);
		}

		public int getMin() {
			return min;
		}

		public int getMax() {
			return max;
		}

		public int getQuantum() {
			return quantum;
		}

	}

	public static class FixedRangeConstraint extends RangeConstraint {

		private static final float DIVISOR = 65536.0f;

		FixedRangeConstraint(int min, int max, int quantum) {
			super(min, max, quantum);
		}

		public float getMin() {
			return min / DIVISOR;
		}

		public float getMax() {
			return max / DIVISOR;
		}

		public float getQuantum() {
			return quantum / DIVISOR;
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
	private final List<Integer> wordConstraints;

	public SaneOption(SaneDevice device, int optionNumber, String name,
			String title, String description, Group group,
			OptionValueType type, OptionUnits units, int size,
			int capabilityWord, OptionValueConstraintType constraintType,
			RangeConstraint rangeConstraints, List<String> stringContraints,
			List<Integer> wordConstraints) {
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
		this.optionCapabilities = SaneEnums.enumSet(OptionCapability.class,
				capabilityWord);
		this.constraintType = constraintType;
		this.rangeConstraints = rangeConstraints;
		this.stringContraints = stringContraints;
		this.wordConstraints = wordConstraints;
	}

	public static List<SaneOption> optionsFor(SaneDevice device)
			throws IOException {

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
			if (option != null) {
				options.add(option);
			}
		}

		return options;
	}

	private static SaneOption fromStream(SaneInputStream inputStream,
			SaneDevice device, int optionNumber) throws IOException {

		SaneOption option = null;

		// discard pointer

		inputStream.readWord();

		String optionName = inputStream.readString();
		String optionTitle = inputStream.readString();
		String optionDescription = inputStream.readString();
		int typeInt = inputStream.readWord().integerValue();
		// TODO: range check here
		OptionValueType valueType = SaneEnums.valueOf(OptionValueType.class,
				typeInt);

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
		List<Integer> valueConstraints = null;
		RangeConstraint rangeConstraint = null;

		switch (constraintType) {
		case NO_CONSTRAINT:
			// inputStream.readWord(); // discard empty list
			break;
		case STRING_LIST_CONSTRAINT:
			stringConstraints = Lists.newArrayList();
			int n = inputStream.readWord().integerValue();
			for (int i = 0; i < n; i++) {
				stringConstraints.add(inputStream.readString());
			}
			// inputStream.readWord();
			break;
		case VALUE_LIST_CONSTRAINT:
			valueConstraints = Lists.newArrayList();
			n = inputStream.readWord().integerValue();
			for (int i = 0; i < n; i++) {
				valueConstraints.add(inputStream.readWord().integerValue());
			}
			// inputStream.readWord(); // TODO: Is this necessary?
			break;
		case RANGE_CONSTRAINT:

			// TODO: still don't understand the 6 values

			int w0 = inputStream.readWord().integerValue();
			int w1 = inputStream.readWord().integerValue();
			int w2 = inputStream.readWord().integerValue();
			int w3 = inputStream.readWord().integerValue();
			// int w4 = inputStream.readWord().integerValue();

			switch (valueType) {

			case INT:
				rangeConstraint = new IntegerRangeContraint(w1, w2, w3);
				break;
			case FIXED:
				rangeConstraint = new FixedRangeConstraint(w1, w2, w3);
				break;
			default:
				throw new IllegalStateException(
						"Integer or Fixed type expected for range constraint");
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

			option = new SaneOption(device, optionNumber, optionName,
					optionTitle, optionDescription, currentGroup, valueType,
					units, size, capabilityWord, constraintType,
					rangeConstraint, stringConstraints, valueConstraints);
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
			throw new IllegalStateException("Option type '" + valueType
					+ "' has no value count");
		default:
			throw new IllegalStateException("Option type '" + valueType
					+ "' unknown");
		}
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

	public List<Integer> getWordConstraints() {
		return wordConstraints;
	}

	public String toString() {
		return String.format("Option: %s, %s, value type: %s, units: %s", name,
				title, valueType, units);
	}

	/**
	 * Read the current Integer value option. We do not cache value from
	 * previous get or set operations so each get involves a round trip to the
	 * server.
	 * 
	 * TODO: consider caching the returned value for "fast read" later
	 * 
	 * @return
	 * @throws IOException
	 */
	public int getIntegerValue() throws IOException {
		// check for type agreement

		Preconditions.checkState(valueType == OptionValueType.INT,
				"option is not an integer");
		Preconditions.checkState(getValueCount() == 1,
				"option is an integer array, not integer");

		// check that this option is readable

		Preconditions.checkState(isReadable());

		// Send RCP corresponding to:
		//
		// SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
		// SANE_Action a, void *v,
		// SANE_Int * i);

		SaneOutputStream out = this.device.getSession().getOutputStream();
		out.write(SaneWord.forInt(5));
		out.write(SaneWord
				.forInt(device.getHandle().getHandle().integerValue()));
		out.write(SaneWord.forInt(this.optionNumber));
		out.write(SaneWord.forInt(OptionAction.GET_VALUE.getWireValue()));
		out.write(SaneWord.forInt(valueType.getWireValue()));
		out.write(SaneWord.forInt(size));
		out.write(SaneWord.forInt(1));
		out.write(SaneWord.forInt(0));// why do we need to provide a value
		// buffer in an RPC call ???

		// read result
		ControlOptionResult result = ControlOptionResult.fromStream(device.getSession().getInputStream());
		Preconditions.checkState(result.getValueSize() == SaneWord.SIZE_IN_BYTES, "unexpected value count");
		Preconditions.checkState(result.getType() == OptionValueType.INT);
		
		// TODO: handle resource authorisation
		// TODO: check status -- may have to reload options!!
		return SaneWord.fromBytes(result.getValue()).integerValue(); // the value
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
		String optionValueFromServer = new String(result.getValue(), 0,
				result.getValueSize() - 1 /* trim trailing null */);

		Preconditions.checkState(
				result.getInfo().contains(OptionWriteInfo.INEXACT)
						^ newValue.equals(optionValueFromServer),
				"new option value does not match when it should");

		return optionValueFromServer;
	}

	/**
	 * Set the value of the current option to the supplied value. Option value
	 * must be of integer type
	 * 
	 * TODO: consider caching the returned value for "fast read" later
	 * 
	 * @param newValue
	 *            for the option
	 * @return the value actually set
	 * @throws IOException
	 */
	public int setIntegerValue(int newValue) throws IOException {
		// check for type agreement
		Preconditions.checkState(valueType == OptionValueType.INT);
		Preconditions.checkState(getValueCount() == 1,
				"option is an integer array");

		// check that this option is readable

		Preconditions.checkState(isWriteable());

		// Send RPC corresponding to:
		//
		// SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
		// SANE_Action a, void *v,
		// SANE_Int * i);

		ControlOptionResult result = writeOption(newValue);
		Preconditions.checkState(result.getType() == OptionValueType.INT);
		Preconditions
				.checkState(result.getValueSize() == SaneWord.SIZE_IN_BYTES);

		return SaneWord.fromBytes(result.getValue()).integerValue();
	}

	private ControlOptionResult writeOption(String value) throws IOException {
		Preconditions.checkState(valueType == OptionValueType.STRING);
		SaneOutputStream out = device.getSession().getOutputStream();
		out.write(SaneWord.forInt(5) /* rpc #5 */);
		out.write(SaneWord
				.forInt(device.getHandle().getHandle().integerValue()));
		out.write(SaneWord.forInt(this.optionNumber));
		out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
		out.write(valueType);

		// even if the string is empty, we still write out at least 1 byte (null
		// terminator)
		out.write(SaneWord.forInt(value.length() + 1));

		// write(String) takes care of writing the size for us
		out.write(value);

		return ControlOptionResult.fromStream(device.getSession()
				.getInputStream());
	}

	private ControlOptionResult writeOption(int value) throws IOException {
		Preconditions.checkState(valueType == OptionValueType.INT);
		SaneOutputStream out = device.getSession().getOutputStream();
		out.write(SaneWord.forInt(5) /* rpc #5 */);
		out.write(SaneWord
				.forInt(device.getHandle().getHandle().integerValue()));
		out.write(SaneWord.forInt(this.optionNumber));
		out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
		out.write(SaneWord.forInt(valueType.getWireValue()));
		out.write(SaneWord.forInt(size));
		out.write(SaneWord.forInt(1)); // only one value follows
		out.write(SaneWord.forInt(value));

		return ControlOptionResult.fromStream(device.getSession()
				.getInputStream());
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
	 * Represents the result of calling {@code SANE_NET_CONTROL_OPTION} (RPC
	 * code 5).
	 */
	private static class ControlOptionResult {
		private final int status;
		private final Set<OptionWriteInfo> info;
		private final OptionValueType type;
		private final int valueSize;
		private final byte[] value;
		private final String resource;

		private ControlOptionResult(int status, int info, OptionValueType type,
				int valueSize, byte[] value, String resource) {
			this.status = status;
			this.info = SaneEnums.enumSet(OptionWriteInfo.class, info);
			this.type = type;
			this.valueSize = valueSize;
			this.value = value;
			this.resource = resource;
		}

		public static ControlOptionResult fromStream(SaneInputStream stream)
				throws IOException {
			int status = stream.readWord().integerValue();

			if (status != 0) {
				throw new IOException("unexpected status " + status);
			}

			int info = stream.readWord().integerValue();

			OptionValueType type = SaneEnums.valueOf(OptionValueType.class,
					stream.readWord().integerValue());

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

			return new ControlOptionResult(status, info, type, valueSize,
					value, resource);
		}

		public int getStatus() {
			return status;
		}

		public Set<OptionWriteInfo> getInfo() {
			return EnumSet.copyOf(info);
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
