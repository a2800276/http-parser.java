package http_parser.lolevel;

import java.nio.*;
import java.util.*;

import static http_parser.lolevel.Util.*;
import http_parser.*;

import primitive.collection.ByteList;

public class Responses {

  
  static void test_multiple3(Message r1, Message r2, Message r3) {
    int message_count = 1;
    if (!r1.upgrade) {
      message_count++;
      if (!r2.upgrade) {
        message_count++;
      }
    }
    boolean has_upgrade = (message_count < 3 || r3.upgrade);
    
    ByteList blist = new ByteList();
             blist.addAll(r1.raw);
             blist.addAll(r2.raw);
             blist.addAll(r3.raw);

    byte [] raw = blist.toArray();
    ByteBuffer buf   = ByteBuffer.wrap(raw);
   
    Util.Settings settings = Util.settings(); 
    HTTPParser parser = new HTTPParser(r1.type);
              parser.execute(settings, buf);
    if (has_upgrade && parser.upgrade) {
      check(settings.numCalled == message_count); 
      return;
    }
    
    check(buf.position() == buf.limit() && buf.position() == raw.length);
    
    buf = Util.empty();    
    parser.execute(settings, buf);
    if (has_upgrade && parser.upgrade) {
      check(settings.numCalled == message_count); 
      return;
    }

    check(buf.position() == buf.limit() && buf.position() == 0);
    check(settings.numCalled == message_count); 
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
          test_multiple3(responses.get(i), responses.get(j), responses.get(k));
        }
      }
    }
  }


}
