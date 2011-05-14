package http_parser.lolevel;

import java.nio.*;
import java.util.*;

import static http_parser.lolevel.Util.*;
import http_parser.*;

public class Responses {

  public static void test (http_parser.ParserType type) {
    HTTPParser parser = new HTTPParser(type);
    ByteBuffer buf    = getBytes(type);
    
    int numbytes = buf.limit();

    parser.execute(Util.SETTINGS_NULL, buf);

    check(numbytes == buf.position());

    buf      = buffer("header-key: header-value\r\n");
    numbytes = buf.limit();
    for (int i = 0; i!= 1000; ++i) {
      parser.execute(Util.SETTINGS_NULL, buf);
      check(numbytes == buf.position());

      buf.rewind();

    }
  }
  
  static ByteBuffer getBytes (http_parser.ParserType type) {
    if (http_parser.ParserType.HTTP_BOTH == type) {
      throw new RuntimeException("only HTTP_REQUEST and HTTP_RESPONSE");
    }

    if (http_parser.ParserType.HTTP_REQUEST == type) {
      return buffer("GET / HTTP/1.1\r\n"); 
    }
    return buffer("HTTP/1.0 200 OK\r\n");
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


  public static void test () {
    List<Message> all = TestLoaderNG.load("tests.dumped");
    List<Message> responses = new LinkedList<Message>();
    for (Message m : all) {
      if (ParserType.HTTP_RESPONSE == m.type) {
        responses.add(m);
      }
    }
    for (Message m : responses) {
      test_message(m);
    }
    
    for (int i = 0; i!= responses.size(); ++i) {
      if (!responses.get(i).should_keep_alive) continue;
      for (int j = 0; j!=responses.size(); ++j) {
        if (!responses.get(j).should_keep_alive) continue;
        for (int k = 0; k!= responses.size(); ++k) {
//          test_multiple3(responses.get(i), responses.get(j), responses.get(k));
        }
      }
    }
  }


}
