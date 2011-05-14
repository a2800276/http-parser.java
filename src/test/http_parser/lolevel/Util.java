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

  static void test_message(Message mes) {
    int raw_len = mes.raw.length;
    for (int msg1len = 0; msg1len != raw_len; ++msg1len) {
      mes.reset();
      ByteBuffer msg1 = ByteBuffer.wrap(mes.raw, 0, msg1len);
      ByteBuffer msg2 = ByteBuffer.wrap(mes.raw, msg1len, mes.raw.length - msg1len);

      HTTPParser parser = new HTTPParser(mes.type);
      ParserSettings settings = mes.settings();
      if (msg1len !=0) {
        parser.execute(settings, msg1);
        if (mes.upgrade && parser.upgrade) {
          // Messages have a settings() that checks itself...
          check(1 == mes.num_called);
          continue; 
        }
        check(msg1.position() == msg1.limit());
      }
      parser.execute(settings, msg2);
      if (mes.upgrade && parser.upgrade) {
        check(1 == mes.num_called);
        continue; 
      }

      check(msg2.position() == msg2.limit());
      
      ByteBuffer empty = Util.empty();
      parser.execute(settings, empty);
      
      if (mes.upgrade && parser.upgrade) {
        check(1 == mes.num_called);
        continue;
      }
      check(empty.position() == empty.limit());
      check(0 == empty.limit());
      check(1 == mes.num_called);

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
