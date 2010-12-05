package http_parser.lolevel;
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

import http_parser.HTTPMethod;
import http_parser.ParserType;

public class TestLoaderNG {
	String fn;
	public TestLoaderNG(String filename) {
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
				else if ("body".equals(key))         {curr.body = toByteArray(value);} //!
				else if ("body_size".equals(key))    {curr.body_size = Integer.parseInt(value);}
				else if (key.startsWith("header"))   {
					String [] h = getHeader(value); 
					curr.header.put(h[0], h[1]);
				} 
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

	String [] getHeader(String value) {
		// { "Host": "0.0.0.0=5000"}
		Pattern p = Pattern.compile("\\{ ?\"([^\"]*)\": ?\"([^\"]*)\"}");
		Matcher m = p.matcher(value);
		if (!m.matches()) {
			p(value);
			throw new RuntimeException("something wrong");
		}
		String [] result = new String[2];
		MatchResult r = m.toMatchResult();
		result[0] = r.group(1).trim();
		result[1] = r.group(2); //.trim();
		return result;
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
					break;
				default:
					bytes.add(b);
			}
			
		}

		byts = new byte[bytes.size()];
		int i = 0;
		for (Byte b : bytes) {
			byts[i++]=b;
		}
		return byts;
	}

	public static void main(String [] args) throws Throwable {
		TestLoaderNG  l = new TestLoaderNG(args[0]);
		List<Test> ts = l.load();

		for (Test t : ts) {
			t.execute_permutations();
		//	t.execute();
		//	System.exit(0);
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
		byte [] body;
		int body_size;
		Map<String,String> header;
		boolean should_keep_alive;
		boolean upgrade;
		int http_major;
		int http_minor;

    boolean message_begin_called;
    boolean message_complete_called;
    boolean headers_complete_called;


		Map<String,String> parsed_header;
		String currHField;
		String currHValue;
		byte [] pbody;
    
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("type: "); b.append(type);b.append("\n");
      b.append("method: "); b.append(method);b.append("\n");
      b.append("status_code: "); b.append(status_code);b.append("\n");
      b.append("request_path: "); b.append(request_path);b.append("\n");
      b.append("request_url: "); b.append(request_url);b.append("\n");
      b.append("fragment: "); b.append(fragment);b.append("\n");
      b.append("query_string: "); b.append(query_string);b.append("\n");
      b.append("body:\n"); b.append(new String(body));b.append("\n");
      b.append("should_keep_alive: "); b.append(should_keep_alive);b.append("\n");
      b.append("upgrade: "); b.append(upgrade);b.append("\n");
      b.append("http_major: "); b.append(http_major);b.append("\n");
      b.append("http_minor: "); b.append(http_minor);b.append("\n");
      b.append("message_complete_called: "); b.append(message_complete_called);b.append("\n");
      return b.toString();
    }

		Test () {
			this.header        = new HashMap<String, String>();
      reset();
		}
    /*
     *prepare this Test Instance for reuse.
     * */
    void reset () {
			this.parsed_header = new HashMap<String, String>();
      this.pbody         = null;
    
    }
		void check (boolean val, String mes) {
			if (!val) {
				//p(name+" : "+mes);
				throw new RuntimeException(name+" : "+mes);
			}
		}

		String str (ByteBuffer b, int pos, int len) {
				byte [] by = new byte[len];
				int saved = b.position();
				b.position(pos);
				b.get(by);
				b.position(saved);
				return new String(by);
		}
		
		HTTPDataCallback getCB (final String value, final String mes, final TestSettings settings) {
			return new HTTPDataCallback() {
				public int cb (HTTPParser p, ByteBuffer b, int pos, int len){
//   if ("url".equals(mes)){
//    p("pos"+pos);
//    p("len"+len);
//    if (8==pos && 5 == len && "connect request".equals(name)) {
//      //throw new RuntimeException(name);
//    }
//   }
					String str      = str(b, pos, len);
					String prev_val = settings.map.get(mes);
					settings.map.put(mes, prev_val + str);
					//check(value.equals(str), "incorrect "+mes+": "+str);
					if (-1 == pos) {
						throw new RuntimeException("he?");
					}
					return 0;
				}
			};
		}
			
		void execute () {
			p(name);
			ByteBuffer   buf = ByteBuffer.wrap(raw);
			HTTPParser     p = new HTTPParser();
			TestSettings s = settings();

			

			p.execute(s, buf);
			if (!s.success) {
				throw new RuntimeException("Test: "+name+"failed");
			}
		} // execute

		void execute_permutations() {
			/*
			|-|---------------|	
			|--|--------------|	
			|---|-------------|	
			(...)
			|---------------|-|	
			|-----------------|	
			*/
			p(name);
			for (int i = 2; i != raw.length; ++i) {
 //  p(i);
				HTTPParser   p = new HTTPParser();
				TestSettings s = settings();
				ByteBuffer buf = ByteBuffer.wrap(raw);
				int olimit = buf.limit();
				buf.limit(i);
				
				parse(p,s,buf);

				buf.position(i);
				buf.limit(olimit);

				parse(p,s,buf);
				parse(p,s,buf);

				if (!s.success) {
          p(this);
					throw new RuntimeException("Test: "+name+" failed");
				}
        reset();
			}
			//System.exit(0);
		} // execute_permutations
		void parse(HTTPParser p, ParserSettings s, ByteBuffer b) {
			//p("About to parse: "+b.position() + "->" + b.limit());
			p.execute(s, b);
		}

		TestSettings settings() {
			final TestSettings s = new TestSettings(); 
			s.on_path         = getCB(request_path, "path", s);
			s.on_query_string = getCB(query_string, "query_string", s);
			s.on_url          = getCB(request_url,  "url", s);
			s.on_fragment     = getCB(fragment,     "fragment", s);
      s.on_message_begin = new HTTPCallback() {
        public int cb (HTTPParser p) {
          message_begin_called = true;
          return -1;
        }
      };
			s.on_header_field = new HTTPDataCallback() {
				public int cb (HTTPParser p, ByteBuffer b, int pos, int len){
					if (null != currHValue && null == currHField) {
							throw new RuntimeException(name+": shouldn't happen");
					}
					if (null != currHField) {
            if (null == currHValue) {
              currHField += str(b,pos,len);
              return 0;
            } else {
              parsed_header.put(currHField, currHValue);
              currHField = null;
              currHValue = null;
            }
					}
          currHField = str(b,pos,len);
					return 0;
				}
			};
			s.on_header_value = new HTTPDataCallback() {
				public int cb (HTTPParser p, ByteBuffer b, int pos, int len){
					if (null == currHField) {
						throw new RuntimeException(name+" :shouldn't happen field");
					}
          if (null == currHValue) {
					  currHValue = str(b,pos,len);
          } else {
            currHValue += str(b, pos, len);
          }
					return 0;
				}
			};
			s.on_headers_complete = new HTTPCallback() {
				public int cb (HTTPParser p) {
					headers_complete_called = true;
					String parsed_path  = s.map.get("path");
					String parsed_query = s.map.get("query_string");
					String parsed_url   = s.map.get("url");
					String parsed_frag  = s.map.get("fragment");
					
					if (!request_path.equals(parsed_path)) {
						throw new RuntimeException(name+": invalid path: "+parsed_path+" should be: "+request_path);
					}
					if (!query_string.equals(parsed_query)) {
						throw new RuntimeException(name+": invalid query: "+parsed_query+" should be: "+query_string);
					}
					if (!request_url.equals(parsed_url)) {
						throw new RuntimeException(">"+name+"<: invalid url: >"+parsed_url+"< should be: >"+request_url+"<");
					}
					if (!fragment.equals(parsed_frag)) {
						throw new RuntimeException(name+": invalid fragement: "+parsed_frag+" should be: "+fragment);
					}
					if (null != currHValue || null != currHField) {
						if (null == currHField || null == currHValue) {
							throw new RuntimeException("shouldn't happen");
						}
					}
					if (null != currHField) {
            //p(currHField);
            //p(">"+currHValue+"<");
						parsed_header.put(currHField, currHValue);
						currHField = null;
						currHValue = null;
					}
					

					return 0;
				}
			};
		//	s.on_headers_complete = new HTTPCallback() {
		//		public int cb (HTTPParser p) {
		//			p("Complete:"+name);
		//			return 0;
		//		}
		//	};

			s.on_body = new HTTPDataCallback() {
				public int cb (HTTPParser p, ByteBuffer b, int pos, int len){
					int l   = pbody == null ? len : len + pbody.length;
					int off = pbody == null ?   0 : pbody.length;
					byte [] nbody = new byte[l];

					if (null != pbody) {
						System.arraycopy(pbody, 0, nbody, 0, pbody.length);
					}

					int saved = b.position();
					b.position(pos);
					b.get(nbody, off, len);
					b.position(saved);
					pbody = nbody;
				  return 0;
				}
			};

			s.on_message_complete = new HTTPCallback() {
				public int cb(HTTPParser p) {
          message_complete_called = true;
					if (   p.http_minor  != http_minor
							|| p.http_major  != http_major
							|| p.status_code != status_code ) {
					
							throw new RuntimeException("major/minor/status_code mismatch");
					}

          //check headers

					if (header.keySet().size() != parsed_header.keySet().size()) {
            p(parsed_header);
						throw new RuntimeException(name+": different amount of headers");
					}
					for (String key : header.keySet()) {
						String pvalue = parsed_header.get(key);
						if (!header.get(key).equals(pvalue)) {
							throw new RuntimeException(name+" : different values for :"+key+" is >"+pvalue+"< should: >"+header.get(key)+"<");
						}
					}
          //check body
					if (null == pbody && (null == body || body.length == 0 || body.length == 1)) {
						s.success = true;
						return 0;
					}
					if (null == pbody) {
						throw new RuntimeException(name+": no body, should be: "+new String(body));
					}
					if (pbody.length != body.length) {
						p(pbody.length);
						p(body.length);
            p(new String(pbody));
            p(new String(body));
						throw new RuntimeException(name+": incorrect body length");
					}
					for (int i = 0 ; i!= body.length; ++i) {
						if (pbody[i] != body[i]) {
							throw new RuntimeException("different body");
						}
					}
					s.success = true;
					return 0;
				}
			};
			return s;
		} // settings
	}
	class TestSettings extends ParserSettings {
		public boolean success;
		Map<String, String> map;
		TestSettings () {
			map = new HashMap<String, String>();
			map.put("path", "");
			map.put("query_string", "");
			map.put("url", "");
			map.put("fragment", "");
		}
	}
}
