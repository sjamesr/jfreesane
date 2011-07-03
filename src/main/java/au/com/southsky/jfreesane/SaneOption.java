package au.com.southsky.jfreesane;

import java.io.IOException;
import java.util.List;

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

		public int actionNo() {
			return actionNo;
		}

		@Override
		public int getWireValue() {
			// TODO Auto-generated method stub
			return 0;
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

	public enum OptionCapability {
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

		public int capBit() {
			return capBit;
		}

		public String description() {
			return description;
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
	private final int capabilityWord;
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
		this.capabilityWord = capabilityWord;
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

		// discard a word

		inputStream.readWord();

		if (length <= 0) {
			return ImmutableList.of();
		}

		for (int i = 0; i < length; i++) {
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
			inputStream.readWord(); // discard empty list
			break;
		case STRING_LIST_CONSTRAINT:
			stringConstraints = Lists.newArrayList();
			int n = inputStream.readWord().integerValue();
			for (int i = 0; i < n; i++) {
				stringConstraints.add(inputStream.readString());
			}
			inputStream.readWord();
			break;
		case VALUE_LIST_CONSTRAINT:
			valueConstraints = Lists.newArrayList();
			n = inputStream.readWord().integerValue();
			for (int i = 0; i < n; i++) {
				valueConstraints.add(inputStream.readWord().integerValue());
			}
			inputStream.readWord(); // TODO: Is this necessary?
			break;
		case RANGE_CONSTRAINT:

			// TODO: still don't understand the 6 values

			int w0 = inputStream.readWord().integerValue();
			int w1 = inputStream.readWord().integerValue();
			int w2 = inputStream.readWord().integerValue();
			int w3 = inputStream.readWord().integerValue();
			int w4 = inputStream.readWord().integerValue();

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

	public int getCapabilityWord() {
		return capabilityWord;
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
		return String.format("Option: %s, value type: %s, units: %s", title,
				valueType, units);
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
		int result = 0;

		// check for type agreement

		Preconditions.checkState(valueType == OptionValueType.INT);

		// check that this option is readable

		Preconditions.checkState(isReadable());

		// Send RCP corresponding to:
		//
		// SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
		// SANE_Action a, void *v,
		// SANE_Int * i);

		SaneOutputStream out = this.device.getSession().getOutputStream();
		out.write(SaneWord
				.forInt(device.getHandle().getHandle().integerValue()));
		out.write(SaneWord.forInt(this.optionNumber));
		out.write(SaneWord.forInt(OptionAction.GET_VALUE.getWireValue()));
		out.write(SaneWord.forInt(valueType.getWireValue()));
		out.write(SaneWord.forInt(size));
		out.write(SaneWord.forInt(0)); // why do we need to provide a value
		// buffer in an RPC call ???

		// read result

		SaneInputStream in = this.device.getSession().getInputStream();
		SaneWord status = in.readWord();
		SaneWord returnedinfo = in.readWord(); // ignore??
		SaneWord returnedValueType = in.readWord(); // ignore
		SaneWord returnedValueSize = in.readWord(); // ignore
		result = in.readWord().integerValue();
		String resource = in.readString(); // TODO: handle resource
		// authorisation

		// TODO: check status

		return result;
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
		int result = 0;

		// check for type agreement

		Preconditions.checkState(valueType == OptionValueType.INT);

		// check that this option is readable

		Preconditions.checkState(isWriteable());

		// Send RCP corresponding to:
		//
		// SANE_Status sane_control_option (SANE_Handle h, SANE_Int n,
		// SANE_Action a, void *v,
		// SANE_Int * i);

		SaneOutputStream out = this.device.getSession().getOutputStream();
		out.write(SaneWord
				.forInt(device.getHandle().getHandle().integerValue()));
		out.write(SaneWord.forInt(this.optionNumber));
		out.write(SaneWord.forInt(OptionAction.SET_VALUE.getWireValue()));
		out.write(SaneWord.forInt(valueType.getWireValue()));
		out.write(SaneWord.forInt(size));
		out.write(SaneWord.forInt(size));
		out.write(SaneWord.forInt(newValue)); // why do we need to provide a
												// value
		// buffer in an RPC call ???

		// read result

		SaneInputStream in = this.device.getSession().getInputStream();
		SaneWord status = in.readWord();
		SaneWord returnedinfo = in.readWord(); // ignore??
		SaneWord returnedValueType = in.readWord(); // ignore
		SaneWord returnedValueSize = in.readWord(); // ignore
		result = in.readWord().integerValue();
		String resource = in.readString(); // TODO: handle resource
		// authorisation

		// TODO: check status

		return result;
	}

	public boolean isReadable() {
		return ((capabilityWord & OptionCapability.SOFT_DETECT.capBit()) > 0);
	}
	
	public boolean isWriteable() {
		return ((capabilityWord & OptionCapability.SOFT_SELECT.capBit()) > 0);
	}

}
