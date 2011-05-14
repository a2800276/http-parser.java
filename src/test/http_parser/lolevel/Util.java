package http_parser.lolevel;

import java.nio.*;

import http_parser.*;

public class Util {

  static final ParserSettings SETTINGS_NULL = new ParserSettings();

  static ByteBuffer buffer(String str) {
    return ByteBuffer.wrap(str.getBytes());
  }

  static void check(boolean betterBtrue) {
    if (!betterBtrue) {
      throw new RuntimeException("!");
    }
  }

  static void p (Object o) {
    System.out.println(o);
  }
}
