package http_parser.lolevel;


public class Test {
  public static void main (String [] args) {
    UnitTest.test();
    TestHeaderOverflowError.test();
    TestNoOverflowLongBody.test();
    Responses.test();
    //Requests.test();
    Upgrade.test();
  }

}
