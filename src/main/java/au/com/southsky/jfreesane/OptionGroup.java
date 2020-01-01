package au.com.southsky.jfreesane;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of options. The SANE backend may group options together. These may be handy
 * if, for example, a JFreeSane user wants to present the options to the user in logical groups.
 *
 * @author James Ring (sjr@jdns.org)
 */
public class OptionGroup {
  private final String title;
  private List<SaneOption> options = new ArrayList<>();

  public OptionGroup(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

  public OptionValueType getValueType() {
    return OptionValueType.GROUP;
  }

  /**
   * Returns an immutable copy of the options in this group.
   */
  public List<SaneOption> getOptions() {
    return new ArrayList<>(options);
  }

  /**
   * Adds an option to the group.
   */
  void addOption(SaneOption option) {
    Preconditions.checkState(option.getGroup() == this);
    options.add(option);
  }

  @Override
  public String toString() {
    return "OptionGroup{" + "title='" + title + '\'' + ", options=" + options + '}';
  }
}
