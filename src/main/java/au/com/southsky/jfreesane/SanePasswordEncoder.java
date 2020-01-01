/*
 * Copyright 2014 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.com.southsky.jfreesane;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Implements MD5 encoding of SANE passwords as specified by <a
 * href="http://www.sane-project.org/html/doc017.html#s5.2.10">Section 5.2.10</a> of the SANE
 * specification.
 */
final class SanePasswordEncoder {

  // Not to be instantiated.
  private SanePasswordEncoder() {}

  static byte[] encodedLatin1(char[] charArray) {
    return StandardCharsets.ISO_8859_1.encode(CharBuffer.wrap(charArray)).array();
  }

  private static String encodeAsHex(byte[] input) {
    StringBuilder hexString = new StringBuilder();
    for (byte b : input) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  static String derivePassword(String salt, String password) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(StandardCharsets.ISO_8859_1.encode(salt));
      md.update(StandardCharsets.ISO_8859_1.encode(CharBuffer.wrap(password)));
      return encodeAsHex(md.digest());
    } catch (NoSuchAlgorithmException ex) {
      // This is not expected, so convert to RuntimeException
      throw new RuntimeException(ex);
    }
  }
}
