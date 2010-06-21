package http_parser;
// name  : 200 trailing space on chunked body
// raw   : "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n25  \r\nThis is the data in the first chunk\r\n\r\n1C\r\nand this is the second one\r\n\r\n0  \r\n\r\n"
// type  : HTTP_RESPONSE
// method: HTTP_DELETE
// status code :200
// request_path:
// request_url :
// fragment    :
// query_string:
// body        :"This is the data in the first chunk\r\nand this is the second one\r\n"
// body_size   :65
// header_0 :{ "Content-Type": "text/plain"}
// header_1 :{ "Transfer-Encoding": "chunked"}
// should_keep_alive         :1
// upgrade                   :0
// http_major                :1
// http_minor                :1


import java.io.FileReader;
import java.io.BufferedReader;

import java.util.*;
import java.util.regex.*;

import java.nio.ByteBuffer;

public class TestLoader {
	String fn;
	public TestLoader(String filename) {
		this.fn = filename;
	}
	static void p(Object o) {
		System.out.println(o);
	}
	public List<Test> load () throws Throwable {
		List<Test>    list = new LinkedList<Test>();
		BufferedReader buf = new BufferedReader(new FileReader(fn));
		String        line = null;
		Test          curr = new Test();
		Pattern    pattern = Pattern.compile("(\\S+)\\s*:(.*)");
		while (null != (line = buf.readLine()) ){
			if ("".equals(line.trim())) {
				list.add (curr);
				curr = new Test();
				continue;
			}
			Matcher m = pattern.matcher(line);
			if (m.matches()) {
				// you can not be fucking serious!?
				// this has got to be the most retarded regex 
				// interface in the history of the world ...
				// (though I'm sure there's worse c++ regexp libs...)
				MatchResult r = m.toMatchResult();
				String    key = r.group(1).trim();
				String  value = r.group(2).trim();
				     if ("name".equals(key))         {curr.name = value;}
				else if ("raw".equals(key))          {curr.raw = toByteArray(value);} //!
				else if ("type".equals(key))         {curr.type = ParserType.parse(value);}
				else if ("method".equals(key))       {curr.method = HTTPMethod.parse(value);}
				else if ("status_code".equals(key))  {curr.status_code = Integer.parseInt(value);}
				else if ("request_path".equals(key)) {curr.request_path = value;}
				else if ("request_url".equals(key))  {curr.request_url = value;}
				else if ("fragment".equals(key))     {curr.fragment = value;}
				else if ("query_string".equals(key)) {curr.query_string = value;}
				else if ("body".equals(key))         {curr.body = value;} //!
				else if ("body_size".equals(key))    {curr.body_size = Integer.parseInt(value);}
				else if ("header".equals(key))       {curr.header = null;} //!
				else if ("should_keep_alive".equals(key)) 
				                                     {curr.should_keep_alive = (1 == Integer.parseInt(value));}
				else if ("upgrade".equals(key))      {curr.upgrade           = (1 == Integer.parseInt(value));}
				else if ("http_major".equals(key))   {curr.http_major = Integer.parseInt(value);}
				else if ("http_minor".equals(key))   {curr.http_minor = Integer.parseInt(value);}
			} else {
				p("WTF?"+line);
			}

		}
		return list;
	}
	
	static final byte BSLASH = 0x5c;
	static final byte QUOT   = 0x22;
	static final byte CR     = 0x0d;
	static final byte LF     = 0x0a;
	static final byte n      = 0x6e;
	static final byte r      = 0x72;
	
	static final Byte[] JAVA_GENERICS_ROCK_HARD = new Byte[0];


	static byte [] toByteArray (String quotedString) {
		ArrayList<Byte> bytes = new ArrayList<Byte>();
		String s = quotedString.substring(1, quotedString.length()-1);
		byte [] byts = s.getBytes(java.nio.charset.Charset.forName("ASCII"));
		boolean escaped = false;
		for (byte b : byts) {
			switch (b) {
				case BSLASH:
					escaped = true;
					break;
				case n:
					if (escaped) {
						bytes.add(LF);
						escaped = false;
					} else {
						bytes.add(b);
					}
					break;
				case r:
					if (escaped) {
						escaped = false;
						bytes.add(CR);
					} else {
						bytes.add(b);
					}
					break;
				case QUOT:
					escaped = false;
					bytes.add(QUOT);
				default:
					bytes.add(b);
			}
			
		}
		//Byte [] fuckyou = bytes.toArray(JAVA_GENERICS_ROCK_HARD);
		//return (byte[])fuckyou;
		byts = new byte[bytes.size()];
		int i = 0;
		for (Byte b : bytes) {
			byts[i++]=b;
			// OMG, WFTBBQ!?
		}
		return byts;
	}

	public static void main(String [] args) throws Throwable {
		TestLoader  l = new TestLoader(args[0]);
		List<Test> ts = l.load();

		for (Test t : ts) {
			t.execute();
		}
	}


	class Test {
		String name; 
		byte [] raw; 
		ParserType type; 
		HTTPMethod method;
		int status_code;
		String request_path; // byte [] ?
		String request_url;
		String fragment ;
		String query_string;
		String body;
		int body_size;
		String [][] header;
		boolean should_keep_alive;
		boolean upgrade;
		int http_major;
		int http_minor;

		void execute () {
			ByteBuffer   buf = ByteBuffer.wrap(raw);
			HTTPParser     p = new HTTPParser();
			ParserSettings s = new ParserSettings();

										 s.on_url = new HTTPDataCallback() {
			public int cb (HTTPParser p, ByteBuffer b, int pos, int len) {
				p(p);
				p(b);
				p(pos);
				p(len);

				byte [] by = new byte[len];
				int saved = b.position();
				b.position(pos);
				b.get(by);
				b.position(saved);

				System.out.println(new String(by));

				if (-1 == pos) {
					throw new RuntimeException("he?");
				}
				return 0;
			}
			
										 };

			p.execute(s, buf, -1);
		}
	}
}
