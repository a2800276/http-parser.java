package http_parser;
import java.nio.ByteBuffer;
public class ParserSettings {
	
  HTTPCallback      on_message_begin;
  HTTPDataCallback 	on_path;
  HTTPDataCallback 	on_query_string;
  HTTPDataCallback 	on_url;
  HTTPDataCallback 	on_fragment;
  HTTPDataCallback 	on_header_field;
  HTTPDataCallback 	on_header_value;
  HTTPCallback      on_headers_complete;
  HTTPDataCallback 	on_body;
  HTTPCallback      on_message_complete;

	void call_on_message_begin (HTTPParser p) {
		call_on(on_message_begin, p);
	}

	void call_on_message_complete (HTTPParser p) {
		call_on(on_message_complete, p);
	}

	void call_on_header_field (HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_header_field, p, buf, pos, len);
	}
	void call_on_query_string (HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_query_string, p, buf, pos, len);
	}
	void call_on_fragment (HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_fragment, p, buf, pos, len);
	}
	void call_on_path (HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_path, p, buf, pos, len);
	}
	void call_on_header_value (HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_header_value, p, buf, pos, len);
	}
	void call_on_url (HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_url, p, buf, pos, len);
	}
	void call_on_body(HTTPParser p, ByteBuffer buf, int pos, int len) {
		call_on(on_body, p, buf, pos, len);
	}
	void call_on_headers_complete(HTTPParser p) {
		call_on(on_headers_complete, p);
	} 
	void call_on (HTTPCallback cb, HTTPParser p) {
		// cf. CALLBACK2 macro
		if (null != cb) {
			cb.cb(p);
		}
	}
	void call_on (HTTPDataCallback cb, HTTPParser p, ByteBuffer buf, int pos, int len) {
		if (null != cb && -1 != pos) {
			cb.cb(p,buf,pos,len);
		}
	}
}
