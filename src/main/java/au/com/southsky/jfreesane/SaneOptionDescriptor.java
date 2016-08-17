package au.com.southsky.jfreesane;

import java.util.List;
import java.util.Set;

import au.com.southsky.jfreesane.SaneOption.OptionUnits;
import java.util.Objects;

/**
 * Describes a SANE option.
 *
 * @author James Ring (sjr@jdns.org)
 */
class SaneOptionDescriptor {
  private final String name;
  private final String title;
  private final String description;
  private final OptionGroup group;
  private final OptionValueType valueType;
  private final OptionUnits units;
  private final int size;
  private final Set<OptionCapability> optionCapabilities;
  private final OptionValueConstraintType constraintType;
  private final RangeConstraint rangeConstraints;
  private final List<String> stringContraints;
  // TODO: wrong level of abstraction
  private final List<SaneWord> wordConstraints;

  SaneOptionDescriptor(
      String name,
      String title,
      String description,
      OptionGroup group,
      OptionValueType valueType,
      OptionUnits units,
      int size,
      Set<OptionCapability> optionCapabilities,
      OptionValueConstraintType constraintType,
      RangeConstraint rangeConstraints,
      List<String> stringContraints,
      List<SaneWord> wordConstraints) {
    this.name = name;
    this.title = title;
    this.description = description;
    this.group = group;
    this.valueType = valueType;
    this.units = units;
    this.size = size;
    this.optionCapabilities = optionCapabilities;
    this.constraintType = constraintType;
    this.rangeConstraints = rangeConstraints;
    this.stringContraints = stringContraints;
    this.wordConstraints = wordConstraints;
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

  public OptionGroup getGroup() {
    return group;
  }

  public OptionValueType getValueType() {
    return valueType;
  }

  public OptionUnits getUnits() {
    return units;
  }

  public int getSize() {
    return size;
  }

  public Set<OptionCapability> getOptionCapabilities() {
    return optionCapabilities;
  }

  public OptionValueConstraintType getConstraintType() {
    return constraintType;
  }

  public RangeConstraint getRangeConstraints() {
    return rangeConstraints;
  }

  public List<String> getStringConstraints() {
    return stringContraints;
  }

  public List<SaneWord> getWordConstraints() {
    return wordConstraints;
  }

  @Override
  public int hashCode() {
    int hash = 3;
    hash = 73 * hash + Objects.hashCode(this.name);
    hash = 73 * hash + Objects.hashCode(this.title);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SaneOptionDescriptor other = (SaneOptionDescriptor) obj;
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    if (!Objects.equals(this.title, other.title)) {
      return false;
    }
    return true;
  }
}
