package http_parser.lolevel;

import java.nio.ByteBuffer;


public class UnitTest {

	static void p(Object o) {System.out.println(o);}

  public static void testErrorFormat() {
    String bla = "This has an error in position 10 (the n in 'an')";
    ByteBuffer buf = ByteBuffer.wrap(bla.getBytes());
               buf.position(10); 
    HTTPParser p = new HTTPParser();
    try {
      p.error ("test error", buf, 0);
    } catch (Throwable t) {
      
      String mes = 
"This has an error in position 10 (the n in 'an')\n" +
"..........^";

      check_equals(mes, t.getMessage()); 
    }
   
    
    bla = "123456789A123456789B123456789C123456789D123456789E123456789F123456789G123456789H123456789I123456789J";
    buf = ByteBuffer.wrap(bla.getBytes());
    buf.position(50);
    try { p.error("test trim right and left", buf, 0); } 
    catch (Throwable t) {
      String mes = 
"56789B123456789C123456789D123456789E123456789F123456789G123456789H123456\n"+
"....................................^";
      check_equals(mes, t.getMessage());
    }


    buf.position(5);
    try { p.error("test trim right", buf, 0); } 
    catch (Throwable t) {
      String mes =
"123456789A123456789B123456789C123456789D123456789E123456789F123456789G12\n"+
".....^";
      check_equals(mes, t.getMessage());
        
    }
   


    int limit = buf.limit();
    buf.limit(10);
    try { p.error("all before, not enough after", buf, 0); } 
    catch (Throwable t) {
      String mes = 
"123456789A\n"+
".....^";
      check_equals(mes, t.getMessage());
        
      }


    buf.limit(limit);
    buf.position(90);
    
    try { p.error("test trim left", buf, 10); } 
    catch (RuntimeException t) {
      String mes = 
"9C123456789D123456789E123456789F123456789G123456789H123456789I123456789J\n"+
"..............................................................^";
      check_equals(mes, t.getMessage());       
    }
  }

  static void check_equals(Object supposed2be, Object is) {
    if (!supposed2be.equals(is)) {
      throw new RuntimeException(is + " is supposed to be "+supposed2be);
    }
  }


  public static void main (String [] args) {
    testErrorFormat();
  }  
}
