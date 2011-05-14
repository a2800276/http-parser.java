package http_parser.lolevel;

import java.nio.*;
import java.util.*;

import static http_parser.lolevel.Util.*;
import http_parser.*;

public class Responses {

  
  


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
