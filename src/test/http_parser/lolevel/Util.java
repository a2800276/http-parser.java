package http_parser.lolevel;

import java.nio.*;

import http_parser.*;

public class Util {

  static final ParserSettings SETTINGS_NULL = new ParserSettings();

  static ByteBuffer buffer(String str) {
    return ByteBuffer.wrap(str.getBytes());
  }

  static ByteBuffer empty() {
    return ByteBuffer.wrap(new byte[0]);
  }

  static void check(boolean betterBtrue) {
    if (!betterBtrue) {
      throw new RuntimeException("!");
    }
  }

  static void p (Object o) {
    System.out.println(o);
  }

  static Settings settings() {
    return new Settings();
  }

  static class Settings extends ParserSettings {
    public int numCalled;
    Settings() {
      this.on_message_complete = new HTTPCallback() {
        public int cb (HTTPParser parser) {
          numCalled++;
          return 0;
        }
      }; 
    }
    int numCalled () {
      return this.numCalled;
    }
  }
}
