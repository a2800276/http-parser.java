package http_parser.lolevel;

import http_parser.HTTPParserUrl;

public class ParseUrl {
  public static void test() {
    Url test;
    int rv;

    for (int i = 0; i < Url.URL_TESTS.length; i++) {
      
      HTTPParserUrl u = new HTTPParserUrl();
      HTTPParser p = new HTTPParser();
      test = Url.URL_TESTS[i];
      System.out.println(":: " + test.name);
      rv = p.parse_url(Util.buffer(test.url),test.is_connect,u);
      UnitTest.check_equals(rv, test.rv);
      if(test.rv == 0){
        UnitTest.check_equals(u, test.u);
      }
    }
  }
}
