package au.com.southsky.jfreesane;

/**
 * This enum describes the capabilities possessed by an option. See <a
 * href="http://www.sane-project.org/html/doc011.html#s4.2.9.7">Sane Standard v1.0.4 section
 * 4.2.9.7</a> the official description of these values.
 *
 * @author James Ring (sjr@jdns.org)
 */
enum OptionCapability implements SaneEnum {
  /**
   * The option value may be set in software.
   */
  SOFT_SELECT(1),

  /**
   * This option may be selected by the user on the scanner (e.g. by flipping a switch).
   */
  HARD_SELECT(2),

  /**
   * This option may be read in software.
   */
  SOFT_DETECT(4),

  /**
   * The option is not directly supported by the scanner but is emulated by the SANE backend.
   */
  EMULATED(8),

  /**
   * The option value may be automatically set by the SANE backend if desired.
   */
  AUTOMATIC(16),

  /**
   * The option is inactive.
   */
  INACTIVE(32),

  /**
   * The option is intended for advanced users.
   */
  ADVANCED(64);

  private int capBit;

  OptionCapability(int capBit) {
    this.capBit = capBit;
  }

  @Override
  public int getWireValue() {
    return capBit;
  }
}
