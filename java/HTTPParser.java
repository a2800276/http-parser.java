package http_parser;

import java.nio.ByteBuffer;
import static http_parser.HTTPParser.C.*;
import static http_parser.HTTPParser.State.*;

public class  HTTPParser {
	/* lots of unsigned chars here, not sure what
	   to about them, `bytes` in java suck...  */

	ParserType type;
	State state;
	HState header_state;
  boolean strict;

	int index; 
	int flags; // TODO

	int nread;
	int content_length;

	
  /** READ-ONLY **/
  int http_major;
  int http_minor;
  int status_code;   /* responses only */
  HTTPMethod method; /* requests only */

  /* true  = Upgrade header was present and the parser has exited because of that.
   * false = No upgrade header present.
   * Should be checked when http_parser_execute() returns in addition to
   * error checking.
   */
  boolean upgrade; // TODO boolean?

  /** PUBLIC **/
	// TODO : this is used in c to maintain application state.
	// is this even necessary? we have state in java ?
	// consider 
  // Object data; /* A pointer to get hook to the "connection" or "socket" object */
			

  /* 
   * technically we could combine all of these (except for url_mark) into one
   * variable, saving stack space, but it seems more clear to have them
   * separated. 
   */
  int header_field_mark = -1;
  int header_value_mark = -1;
  int fragment_mark = -1;
  int query_string_mark = -1;
  int path_mark = -1;
  int url_mark = -1;
	
  /**
   * Construct a Parser for ParserType.HTTP_BOTH, meaning it
   * determines whether it's parsing a request or a response.
   */
	public HTTPParser() {
		this(ParserType.HTTP_BOTH);
	}
	
  /**
   * Construct a Parser and initialise it to parse either 
   * requests or responses.
   */
	public HTTPParser(ParserType type) {
		this.type  = type;
		switch(type) {
			case HTTP_REQUEST:
				this.state = State.start_req;
				break;
			case HTTP_RESPONSE:
				this.state = State.start_res;
				break;
			case HTTP_BOTH:
				this.state = State.start_res_or_resp;
				break;
			default:
				throw new RuntimeException("can't happen, invalid ParserType enum");
		}
	}
	
  /*
   * Utility to facilitate System.out.println style debugging (the war god intended)
   */
	static void p(Object o) {System.out.println(o);}

  /** Execute the parser with the currently available data contained in
   * the buffer. The buffers position() and limit() need to be set
   * correctly (obviously) and a will be updated approriately when the
   * method returns to reflect the consumed data.
   */
  public void execute(ParserSettings settings, ByteBuffer data) {

    int p   = data.position();

    // In case the headers don't provide information about the content
    // length, `execute` needs to be called with an empty buffer to
    // indicate that all the data has been send be the client/server,
    // else there is no way of knowing the message is complete. 
    int len = (data.limit() - data.position());
		if (0 == len) {
			if (State.body_identity_eof == state) {
				settings.call_on_message_complete(this);
			}
		}

		
    // in case the _previous_ call to the parser only has data to get to
    // the middle of certain fields, we need to update marks to point at
    // the beginning of the current buffer.
    switch (state) {
      case header_field:
        header_field_mark = p;
        break;
      case header_value:
        header_value_mark = p;
        break;
      case req_fragment:
        fragment_mark = p;
        url_mark = p;
        break;
      case req_query_string:
        query_string_mark = p;
        url_mark = p;
        break;
      case req_path:
        path_mark = p;

      case req_host:
      case req_schema:
      case req_schema_slash:
      case req_schema_slash_slash:
      case req_port:
      case req_query_string_start:
      case req_fragment_start:
        url_mark = p;
        break;
    }

    // this is where the work gets done, traverse the available data...
    while (data.position() != data.limit()) {

            p = data.position();
      int  pe = data.limit();

      byte ch     = data.get();           // the current character to process.
      byte c      = -1;                   // utility variably used for up- and downcasing etc.
      int to_read =  0;                   // used to keep track of how much of body, etc. is left to read

      if (parsing_header(state)) {
        ++nread;
        if (nread > HTTP_MAX_HEADER_SIZE) {
          throw new RuntimeException("possible buffer overflow"); 
        }
      }

      switch (state) {
         /*
          * this state is used after a 'Connection: close' message
          * the parser will error out if it reads another message
          */
        case dead:
          throw new RuntimeException("Connection already closed");



        case start_res_or_resp:
          if (CR == ch || LF == ch){
            break;
          }
          flags = 0;
          content_length = -1;

          settings.call_on_message_begin(this);
          
          if (H == ch) { 
            state = State.res_or_resp_H;
          } else {
            type = ParserType.HTTP_REQUEST;  
            start_req_method_assign(ch);     // ... goto start_req_method_assign ...
            index = 1;
            state = State.req_method;
          }
          break;



        case res_or_resp_H:
          if (T == ch) {
            type  = ParserType.HTTP_RESPONSE;
            state = State.res_HT;
          } else {
            if (E != ch) {
              throw new RuntimeException("not 'E'");
            }
            type   = ParserType.HTTP_REQUEST;
            method = HTTPMethod.HTTP_HEAD;
            index  = 2;
            state  = State.req_method;
          }
          break;



        case start_res:
          flags = 0;
          content_length = -1;

          settings.call_on_message_begin(this);
          
          switch(ch) {
            case H:
              state = State.res_H;
              break;
            case CR:
            case LF:
              break;
            default:
              throw new RuntimeException("Not H or CR/LF");
          }
          break;



        case res_H:
          if (strict && T != ch) {
            throw new RuntimeException("Not T");
          }
          state = State.res_HT;
          break;
        case res_HT:
          if (strict && T != ch) {
            throw new RuntimeException("Not T");
          }
          state = State.res_HTT;
          break;
        case res_HTT:
          if (strict && P != ch) {
            throw new RuntimeException("Not P");
          }
          state = State.res_HTTP;
          break;
        case res_HTTP:
          if (strict && SLASH != ch) {
            throw new RuntimeException("Not '/'");
          }
          state = State.res_first_http_major;
          break;



        case res_first_http_major:
          if (!isDigit(ch)) {
            throw new RuntimeException("Not a digit");
          }
          http_major = (int) ch - 0x30;
          state = State.res_http_major;
          break;

        /* major HTTP version or dot */
        case res_http_major:
          if (DOT == ch) {
            state = State.res_http_minor;
            break;
          }
          if (!isDigit(ch)) {
            throw new RuntimeException("not a digit");			
          }
          http_major *= 10;
          http_major += (ch - 0x30);

          if (http_major > 999) {
            throw new RuntimeException("invalid http major version: "+http_major);
          }
          break;
          
        /* first digit of minor HTTP version */
        case res_first_http_minor:
          if (!isDigit(ch)) {
            throw new RuntimeException("not a digit");
          }
          http_minor = (int)ch - 0x30;
          state = State.res_http_minor;
          break;

        /* minor HTTP version or end of request line */
        case res_http_minor:
          if (SPACE == ch) {
            state = State.res_first_status_code;
            break;
          }
          if (!isDigit(ch)) {
            throw new RuntimeException("not a digit");			
          }
          http_minor *= 10;
          http_minor += (ch - 0x30);

          if (http_minor > 999) {
            throw new RuntimeException("invalid http minor version: "+http_major);
          }
          break;



        case res_first_status_code:
          if (!isDigit(ch)) {
            if (SPACE == ch) {
              break;
            }
            throw new RuntimeException("non-digit in status code");
          }
          status_code = (int)ch - 0x30;
          state = State.res_status_code;
          break;

        case res_status_code:
          if (!isDigit(ch)) {
            switch(ch) {
              case SPACE:
                state = State.res_status;
                break;
              case CR:
                state = State.res_line_almost_done;
                break;
              case LF:
                state = State.header_field_start;
                break;
              default:
                throw new RuntimeException("not a valid status code");
            }
            break;
          }
          status_code *= 10;
          status_code += (int)ch - 0x30;
          if (status_code > 999) {
            throw new RuntimeException("ridiculous status code:"+status_code);
          }
          break;

        case res_status:
          /* the human readable status. e.g. "NOT FOUND"
           * we are not humans so just ignore this 
           * we are not men, we are devo. */

           if (CR == ch) {
            state = State.res_line_almost_done;
            break;
           }
           if (LF == ch) { 
            state = State.header_field_start;
            break;
           }
           break;

        case res_line_almost_done:
          if (strict && LF != ch) {
            throw new RuntimeException("not LF");
          }
          state = State.header_field_start;
          break;



        case start_req:
          if (CR==ch || LF == LF) {
            break;
          }
          flags = 0;
          content_length = -1;
          settings.call_on_message_begin(this);
          start_req_method_assign(ch);
          index = 1;
          state = State.req_method;
          break;
        


        case req_method:
          if ( 0 == ch) {
            throw new RuntimeException("NULL in message");
          }
          
          byte [] arr = method.bytes;

          if (SPACE == ch && index == arr.length) {
            state = State.req_spaces_before_url;
          } else if (arr[index] == ch) {
            // wuhu!
          } else if (2 == index && HTTPMethod.HTTP_CONNECT  == method && P == ch) {
            method = HTTPMethod.HTTP_COPY;
          } else if (1 == index && HTTPMethod.HTTP_MKCOL    == method && O == ch) {
            method = HTTPMethod.HTTP_MOVE;
          } else if (1 == index && HTTPMethod.HTTP_POST     == method && R == ch) {
            method = HTTPMethod.HTTP_PROPFIND;
          } else if (1 == index && HTTPMethod.HTTP_POST     == method && U == ch) {
            method = HTTPMethod.HTTP_PUT;
          } else if (4 == index && HTTPMethod.HTTP_PROPFIND == method && P == ch) {
            method = HTTPMethod.HTTP_PROPPATCH;
          } else {
            throw new RuntimeException("Invalid HTTP method");
          }

          ++index;
          break;
      


        /******************* URL *******************/
        case req_spaces_before_url:
          if (SPACE == ch) {
            break;
          }
          if (SLASH == ch) {
            url_mark  = p;
            path_mark = p;
            state = State.req_path;
            break;
          }
          if (isAtoZ(ch)) {
            url_mark = p;
            state = State.req_schema;
            break;
          }
          throw new RuntimeException("Invalid, not sure what");

        case req_schema:
          if (isAtoZ(ch)){
            break;
          }
          if (COLON == ch) {
            state = State.req_schema_slash;
            break;
          } else if (DOT == ch) {
            state = State.req_host;
            break;
          }
          throw new RuntimeException("invalid char in schema: "+ch);

        case req_schema_slash:
          if (strict && SLASH != ch) {
            throw new RuntimeException("invalid char in schema, not /");
          }
          state = State.req_schema_slash_slash;
          break;

        case req_schema_slash_slash:
          if (strict && SLASH != ch) {
            throw new RuntimeException("invalid char in schema, not /");
          }
          state = State.req_host;
          break;
        
        case req_host:
          if (isAtoZ(ch)) {
            break;
          }	
          if (isDigit(ch) || DOT == ch || DASH == ch) break;
          switch (ch) {
            case COLON:
              state = State.req_port;
              break;
            case SLASH:
              path_mark = p;
              break;
            case SPACE:
              /* The request line looks like:
               *   "GET http://foo.bar.com HTTP/1.1"
               * That is, there is no path.
               */
              settings.call_on_url(this, data, url_mark, p-url_mark);
              url_mark = -1;
              state = State.req_http_start;
              break;
            default:
              throw new RuntimeException("host error in method line");
          }
          break;

        case req_port:
          if (isDigit(ch)) break;
          switch (ch) {
            case SLASH:
              path_mark = p; 
              state = State.req_path;
              break;
            case SPACE:
              /* The request line looks like:
               *   "GET http://foo.bar.com:1234 HTTP/1.1"
               * That is, there is no path.
               */
              settings.call_on_url(this,data,url_mark,p-url_mark);
              url_mark = -1;
              state = State.req_http_start;
              break;
            default:
              throw new RuntimeException("invalid port");
          }
          break;
      
        case req_path:
          if (usual(ch)) break;
          switch (ch) {
            case SPACE:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;

              settings.call_on_path(this,data,path_mark, p-path_mark);
              path_mark = -1;
              
              state = State.req_http_start;
              break;

            case CR:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;
              
              settings.call_on_path(this,data,path_mark, p-path_mark);
              path_mark = -1;
              
              http_minor = 9;
              state = State.res_line_almost_done;
              break;

            case LF:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;
              
              settings.call_on_path(this,data,path_mark, p-path_mark);
              path_mark = -1;
              
              http_minor = 9;
              state = State.header_field_start;
              break;

            case QMARK:
              settings.call_on_path(this,data,path_mark, p-path_mark);
              path_mark = -1;
              
              state = State.req_query_string_start;
              break;
            
            case HASH:
              settings.call_on_path(this,data,path_mark, p-path_mark);
              path_mark = -1;
              
              state = State.req_fragment_start;
              break;
            
            default:
              throw new RuntimeException("unexpected char in path");
          }
          break;
      
        case req_query_string_start:
          if (usual(ch)) {
            query_string_mark = p;
            state = State.req_query_string;
            break;
          }

          switch (ch) {
            case QMARK: break;
            case SPACE: 
              settings.call_on_url(this, data, url_mark, p-url_mark);
              url_mark = -1;
              state = State.req_http_start;
              break;
            case CR:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1; 
              http_minor = 9;
              state = State.res_line_almost_done;
              break;
            case LF:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;
              http_minor = 9;
              state = State.header_field_start;
              break;
            case HASH:
              state = State.req_fragment_start;
              break;
            default:
              throw new RuntimeException("unexpected char in path");
          }
          break;
        
        case req_query_string:
          if (usual(ch)) {
            break;
          }

          switch (ch) {
            case QMARK: break; // allow extra '?' in query string
            case SPACE: 
              settings.call_on_url(this, data, url_mark, p-url_mark);
              url_mark = -1;

              settings.call_on_query_string(this, data, query_string_mark, p-query_string_mark);
              query_string_mark = -1;

              state = State.req_http_start;
              break;
            case CR:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1; 

              settings.call_on_query_string(this, data, query_string_mark, p-query_string_mark);
              query_string_mark = -1;
              
              http_minor = 9;
              state = State.res_line_almost_done;
              break;
            case LF:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;

              settings.call_on_query_string(this, data, query_string_mark, p-query_string_mark);
              query_string_mark = -1;
              http_minor = 9;

              state = State.header_field_start;
              break;
            case HASH:
              settings.call_on_query_string(this, data, query_string_mark, p-query_string_mark);
              query_string_mark = -1;
              
              state = State.req_fragment_start;
              break;
            default:
              throw new RuntimeException("unexpected char in path");
          }
          break;

        case req_fragment_start:
          if (usual(ch)) {
            fragment_mark = p;
            state = State.req_fragment;
            break;
          }

          switch (ch) {
            case SPACE: 
              settings.call_on_url(this, data, url_mark, p-url_mark);
              url_mark = -1;
     
              state = State.req_http_start;
              break;
            case CR:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1; 

              http_minor = 9;
              state = State.res_line_almost_done;
              break;
            case LF:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;
              
              http_minor = 9;
              state = State.header_field_start;
              break;
            case QMARK:
              fragment_mark = p;
              state = State.req_fragment;
              break;
            case HASH:
              break;
            default:
              throw new RuntimeException("unexpected char in path");
          }
          break;

        case req_fragment:
          if (usual(ch)) {
            break;
          }

          switch (ch) {
            case SPACE: 
              settings.call_on_url(this, data, url_mark, p-url_mark);
              url_mark = -1;
          
              settings.call_on_fragment(this, data, fragment_mark, p-fragment_mark);
              fragment_mark = -1;
              
              state = State.req_http_start;
              break;
            case CR:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1; 
              
              settings.call_on_fragment(this, data, query_string_mark, p-query_string_mark);
              fragment_mark = -1;
              
              http_minor = 9;
              state = State.res_line_almost_done;
              break;
            case LF:
              settings.call_on_url(this,data,url_mark, p-url_mark);
              url_mark = -1;
              
              settings.call_on_fragment(this, data, query_string_mark, p-query_string_mark);
              fragment_mark = -1;
              
              http_minor = 9;
              state = State.header_field_start;
              break;
            case QMARK:
            case HASH:
              break;
            default:
              throw new RuntimeException("unexpected char in path");
          }
          break;
        /******************* URL *******************/



        /******************* HTTP 1.1 *******************/
        case req_http_start:
          switch (ch) {
            case H:
              state = State.req_http_H;
              break;
            case SPACE:
              break;
            default:
              throw new RuntimeException("error in req_http_H");
          }
          break;

        case req_http_H:
          if (strict && T != ch) {
            throw new RuntimeException("unexpected char");
          }
          state = State.req_http_HT;
          break;

        case req_http_HT:
          if (strict && T != ch) {
            throw new RuntimeException("unexpected char");
          }
          state = State.req_http_HTT;
          break;

        case req_http_HTT:
          if (strict && P != ch) {
            throw new RuntimeException("unexpected char");
          }
          state = State.req_http_HTTP;
          break;

        case req_http_HTTP:
            if (strict && SLASH != ch) {
              throw new RuntimeException("unexpected char");
            }
          state = req_first_http_major;
          break;

        /* first digit of major HTTP version */
        case req_first_http_major:
          if (!isDigit(ch)) {
            throw new RuntimeException("non digit in http major");
          }
          http_major = (int)ch - 0x30;
          state = State.req_http_major;
          break;

        /* major HTTP version or dot */
        case req_http_major:
          if (DOT == ch) {
            state = State.req_first_http_minor;
            break;
          }

          if (!isDigit(ch)) {
            throw new RuntimeException("non digit in http major");
          }

          http_major *= 10;
          http_major += (int)ch - 0x30;

          if (http_major > 999) {
            throw new RuntimeException("ridiculous http major");
          };
          break;
        
        /* first digit of minor HTTP version */
        case req_first_http_minor:
          if (!isDigit(ch)) {
            throw new RuntimeException("non digit in http minor");
          }
          http_minor = (int)ch - 0x30;
          state = State.req_http_minor;
          break;

        case req_http_minor:
          if (ch == CR) {
            state = State.req_line_almost_done;
            break;
          }

          if (ch == LF) {
            state = State.header_field_start;
            break;
          }

          /* XXX allow spaces after digit? */

          if (!isDigit(ch)) {
            throw new RuntimeException("non digit in http minor");
          }

          http_minor *= 10;
          http_minor += (int)ch - 0x30;

         
          if (http_major > 999) {
            throw new RuntimeException("ridiculous http minor");
          };
   
          break;

        /* end of request line */
        case req_line_almost_done:
        {
          if (ch != LF) {
            throw new RuntimeException("missing LF after request line");
          }
          state = State.header_field_start;
          break;
        }

        /******************* HTTP 1.1 *******************/



        /******************* Header *******************/
        case header_field_start:
        {
          if (ch == CR) {
            state = State.headers_almost_done;
            break;
          }

          if (ch == LF) {
            /* they might be just sending \n instead of \r\n so this would be
             * the second \n to denote the end of headers*/
            state = State.headers_almost_done;
            headers_almost_done(ch, settings);
            break;
          }

          c = upper(ch);

          if (c < A || Z < c) {
            throw new RuntimeException("invalid char in header");
          };

          header_field_mark = p;

          index = 0;
          state = State.header_field;

          switch (c) {
            case C: 
              header_state = HState.C;
              break;

            case P:
              header_state = HState.matching_proxy_connection;
              break;

            case T:
              header_state = HState.matching_transfer_encoding;
              break;

            case U:
              header_state = HState.matching_upgrade;
              break;

            default:
              header_state = HState.general;
              break;
          }
          break;
        }



        case header_field:
        {
          c = UPCASE[ch];

          if (0 != c) {  
            switch (header_state) {
              case general:
                break;

              case C:
                index++;
                header_state = (O == c ? HState.CO : HState.general);
                break;

              case CO:
                index++;
                header_state = (N == c ? HState.CON : HState.general);
                break;

              case CON:
                index++;
                switch (c) {
                  case N:
                    header_state = HState.matching_connection;
                    break;
                  case T:
                    header_state = HState.matching_content_length;
                    break;
                  default:
                    header_state = HState.general;
                    break;
                }
                break;

              /* connection */

              case matching_connection:
                index++;
                if (index > CONNECTION.length || c != CONNECTION[index]) {
                  header_state = HState.general;
                } else if (index == CONNECTION.length-1) {
                  header_state = HState.connection;
                }
                break;

              /* proxy-connection */

              case matching_proxy_connection:
                index++;
                if (index > PROXY_CONNECTION.length || c != PROXY_CONNECTION[index]) {
                  header_state = HState.general;
                } else if (index == PROXY_CONNECTION.length-1) {
                  header_state = HState.connection;
                }
                break;

              /* content-length */

              case matching_content_length:
                index++;
                if (index > CONTENT_LENGTH.length || c != CONTENT_LENGTH[index]) {
                  header_state = HState.general;
                } else if (index == CONTENT_LENGTH.length-1) {
                  header_state = HState.content_length;
                }
                break;

              /* transfer-encoding */

              case matching_transfer_encoding:
                index++;
                if (index > TRANSFER_ENCODING.length || c != TRANSFER_ENCODING[index]) {
                  header_state = HState.general;
                } else if (index == TRANSFER_ENCODING.length-1) {
                  header_state = HState.transfer_encoding;
                }
                break;

              /* upgrade */

              case matching_upgrade:
                index++;
                if (index > UPGRADE.length || c != UPGRADE[index]) {
                  header_state = HState.general;
                } else if (index == UPGRADE.length-1) {
                  header_state = HState.upgrade;
                }
                break;

              case connection:
              case content_length:
              case transfer_encoding:
              case upgrade:
                if (SPACE != ch) header_state = HState.general;
                break;

              default:
                throw new RuntimeException("Unknown Header State");
            } // switch: header_state
            break;
          } // 0 != c

          if (COLON == ch)  {
            settings.call_on_header_field(this, data, header_field_mark, p-header_field_mark);
            header_field_mark = -1;

            state = State.header_value_start;
            break;
          }

          if (CR == ch) {
            state = State.header_almost_done;
            settings.call_on_header_field(this, data, header_field_mark, p-header_field_mark);
            
            header_field_mark = -1;
            break;
          }

          if (ch == LF) {
            settings.call_on_header_field(this, data, header_field_mark, p-header_field_mark);
            header_field_mark = -1;
            
            state = State.header_field_start;
            break;
          }

          throw new RuntimeException("invalid header field");
        }



        case header_value_start:
        {
          if (SPACE == ch) break;

          header_value_mark = p;

          state = State.header_value;
          index = 0;

          c = UPCASE[ch];

          if (c == 0) {
            if (CR == ch) {
              settings.call_on_header_value(this, data, header_value_mark, p-header_value_mark);
              header_value_mark = -1;

              header_state = HState.general;
              state = State.header_almost_done;
              break;
            }

            if (LF == ch) {
              settings.call_on_header_value(this, data, header_value_mark, p-header_value_mark);
              header_value_mark = -1;
              
              state = State.header_field_start;
              break;
            }

            header_state = HState.general;
            break;
          }

          switch (header_state) {
            case upgrade:
              flags |= F_UPGRADE;
              header_state = HState.general;
              break;

            case transfer_encoding:
              /* looking for 'Transfer-Encoding: chunked' */
              if (C == c) {
                header_state = HState.matching_transfer_encoding_chunked;
              } else {
                header_state = HState.general;
              }
              break;

            case content_length:
              if (!isDigit(ch)) {
                throw new RuntimeException("Content-Length not numeric");
              } 
              content_length = (int)ch - 0x30;
              break;

            case connection:
              /* looking for 'Connection: keep-alive' */
              if (K == c) {
                header_state = HState.matching_connection_keep_alive;
              /* looking for 'Connection: close' */
              } else if (C == c) {
                header_state = HState.matching_connection_close;
              } else {
                header_state = HState.general;
              }
              break;

            default:
              header_state = HState.general;
              break;
          }
          break;
        } // header value start



        case header_value:
        {
          c = UPCASE[ch];
          if (c == 0) {
            if (CR == ch) {
              settings.call_on_header_value(this, data, header_value_mark, p-header_value_mark);
              header_value_mark = -1;

              state = State.header_almost_done;
              break;
            }

            if (LF == ch) {
              settings.call_on_header_value(this, data, header_value_mark, p-header_value_mark);
              header_value_mark = -1;
              
              header_almost_done(ch);
              break;
            }
            break;
          }

          switch (header_state) {
            case general:
              break;

            case connection:
            case transfer_encoding:
              throw new RuntimeException("Shouldn't be here");

            case content_length:
              if (!isDigit(ch)) {
                throw new RuntimeException("Content-Length not numeric");
              } 

              content_length *= 10;
              content_length += (int)ch - 0x30;
              break;

            /* Transfer-Encoding: chunked */
            case matching_transfer_encoding_chunked:
              index++;
              if (index > CHUNKED.length || c != CHUNKED[index]) {
                header_state = HState.general;
              } else if (index == CHUNKED.length-1) {
                header_state = HState.transfer_encoding_chunked;
              }
              break;

            /* looking for 'Connection: keep-alive' */
            case matching_connection_keep_alive:
              index++;
              if (index > KEEP_ALIVE.length || c != KEEP_ALIVE[index]) {
                header_state = HState.general;
              } else if (index == KEEP_ALIVE.length-1) {
                header_state = HState.connection_keep_alive;
              }
              break;

            /* looking for 'Connection: close' */
            case matching_connection_close:
              index++;
              if (index > CLOSE.length || c != CLOSE[index]) {
                header_state = HState.general;
              } else if (index == CLOSE.length-1) {
                header_state = HState.connection_close;
              }
              break;

            case transfer_encoding_chunked:
            case connection_keep_alive:
            case connection_close:
              if (SPACE != ch) header_state = HState.general;
              break;

            default:
              state = State.header_value;
              header_state = HState.general;
              break;
          }
          break;
        } // header_value



        case header_almost_done:
          header_almost_done(ch);
          break;

        case headers_almost_done:
          headers_almost_done(ch, settings);
          break;

        /******************* Header *******************/




        /******************* Body *******************/
        case body_identity:
          to_read = min(pe - p, content_length); //TODO change to use buffer? 

          if (to_read > 0) {
            settings.call_on_body(this, data, p, to_read); 
            data.position(p+to_read);
            content_length -= to_read;
            if (content_length == 0) {
              settings.call_on_message_complete(this);
              state = new_message(); 
            }
          }
          break;



        case body_identity_eof:
          to_read = pe - p;  // TODO change to use buffer ?
          if (to_read > 0) {
            settings.call_on_body(this, data, p, to_read); 
            data.position(p+to_read);
          }
          break;
        /******************* Body *******************/



        /******************* Chunk *******************/
        case chunk_size_start:
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }

          c = UNHEX[ch];
          if (c == -1) {
            throw new RuntimeException("invalid hex char in chunk content length");
          }
          content_length = c;
          state = State.chunk_size;
          break;



        case chunk_size:
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }

          if (CR == ch) {
            state = State.chunk_size_almost_done;
            break;
          }

          c = UNHEX[ch];

          if (c == -1) {
            if (SEMI == ch || SPACE == ch) {
              state = State.chunk_parameters;
              break;
            }
            throw new RuntimeException("invalid char in chunk length");
          }

          content_length *= 16;
          content_length += c;
          break;



        case chunk_parameters:
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }
          /* just ignore this shit. TODO check for overflow */
          if (CR == ch) {
            state = State.chunk_size_almost_done;
            break;
          }
          break;
          


        case chunk_size_almost_done:
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }
          if (strict && LF != ch) {
            throw new RuntimeException("expected LF at end of chunk size");
          }

          if (0 == content_length) {
            flags |= F_TRAILING;
            state = State.header_field_start;
          } else {
            state = State.chunk_data;
          }
          break;



        case chunk_data:
        {
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }

          to_read = min(pe-p, content_length);
          if (to_read > 0) {
            settings.call_on_body(this, data, p, to_read);
            data.position(p+to_read);
          }

          if (to_read == content_length) {
            state = State.chunk_data_almost_done;
          }

          content_length -= to_read;
          break;
        }



        case chunk_data_almost_done:
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }
          if (strict && CR != ch) {
            throw new RuntimeException("chunk data terminated invcorrectly, expected CR");
          }
          state = State.chunk_data_done;
          break;



        case chunk_data_done:
          if (0 == (flags & F_CHUNKED)) {
            throw new RuntimeException("not chunked");
          }
          if (strict && LF != ch) {
            throw new RuntimeException("chunk data terminated invcorrectly, expected LF");
          }
          state = State.chunk_size_start;
          break;
        /******************* Chunk *******************/
    
        
        
        default:
          throw new RuntimeException("unhandled state");
          
      } // switch
    } // while

    p = data.position();

	  settings.call_on_header_field(this, data, header_field_mark, p-header_field_mark);
    settings.call_on_header_value(this, data, header_value_mark, p-header_value_mark);
    settings.call_on_fragment    (this, data, fragment_mark,     p-fragment_mark);
    settings.call_on_query_string(this, data, query_string_mark, p-query_string_mark);
    settings.call_on_path        (this, data, path_mark,         p-path_mark);
    settings.call_on_url         (this, data, url_mark,          p-url_mark);
	
}

/* If http_should_keep_alive() in the on_headers_complete or
 * on_message_complete callback returns true, then this will be should be
 * the last message on the connection.
 * If you are the server, respond with the "Connection: close" header.
 * If you are the client, close the connection.
 */
	boolean http_should_keep_alive() {
		if (http_major > 0 && http_minor > 0) {
			/* HTTP/1.1 */
			if ( 0 != (flags & F_CONNECTION_CLOSE) ) {
				return false;
			} else {
				return true;
			}
		} else {
			/* HTTP/1.0 or earlier */
			if ( 0 != (flags & F_CONNECTION_KEEP_ALIVE) ) {
				return true;
			} else {
				return false;
			}
		}
	}
	boolean isDigit(byte b) {
		if (b >= 0x30 && b <=0x39) {
			return true;
		}
		return false;
	}

	boolean isAtoZ(byte b) {
		byte c = lower(b);
		return (c>= 0x61 /*a*/ && c <=  0x7a /*z*/);
	}

	boolean usual(byte b) {
		// TODO check wtf this does...

		//static const uint32_t  usual[] = {
		//    0xffffdbfe, /* 1111 1111 1111 1111  1101 1011 1111 1110 */
		//
		//                /* ?>=< ;:98 7654 3210  /.-, +*)( '&%$ #"!  */
		//    0x7ffffff6, /* 0111 1111 1111 1111  1111 1111 1111 0110 */
		//
		//                /* _^]\ [ZYX WVUT SRQP  ONML KJIH GFED CBA@ */
		//    0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
		//
		//                /*  ~}| {zyx wvut srqp  onml kjih gfed cba` */
		//    0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
		//
		//    0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
		//    0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
		//    0xffffffff, /* 1111 1111 1111 1111  1111 1111 1111 1111 */
		//    0xffffffff  /* 1111 1111 1111 1111  1111 1111 1111 1111 */
		//};
		//
		//#define USUAL(c) (usual[c >> 5] & (1 << (c & 0x1f)))
		
		switch (b) {
			case NULL:
			case CR:
			case LF:
			case SPACE:
			case QMARK:
			case HASH:
				return false;
		}
		return true;
	
	}

	byte lower (byte b) {
		return (byte)(b|0x20);
	}

	byte upper(byte b) {
		char c = (char)(b);
		return (byte)Character.toUpperCase(c);
	}
	

	void start_req_method_assign(byte c){
		switch (c) {
			case C: method = HTTPMethod.HTTP_CONNECT; break; /* or COPY */
			case D: method = HTTPMethod.HTTP_DELETE;  break;
			case G: method = HTTPMethod.HTTP_GET;     break;
			case H: method = HTTPMethod.HTTP_HEAD;    break;
			case L: method = HTTPMethod.HTTP_LOCK;    break;
			case M: method = HTTPMethod.HTTP_MKCOL;   break; /* or MOVE */
			case O: method = HTTPMethod.HTTP_OPTIONS; break;
			case P: method = HTTPMethod.HTTP_POST;    break; /* or PROPFIND, PROPPATH, PUT */
			case T: method = HTTPMethod.HTTP_TRACE;   break;
			case U: method = HTTPMethod.HTTP_UNLOCK;  break;
			default:
				throw new RuntimeException("Invalid Method: "+c);
		}
	}

      void header_almost_done(byte ch) {
				if (strict && LF != ch) {
					throw new RuntimeException("incorrect header ending, expection LF");
				}

        state = State.header_field_start;
        // TODO java enums support some sort of bitflag mechanism !?
        switch (header_state) {
          case connection_keep_alive:
            flags |= F_CONNECTION_KEEP_ALIVE;
            break;
          case connection_close:
            flags |= F_CONNECTION_CLOSE;
            break;
          case transfer_encoding_chunked:
            flags |= F_CHUNKED;
            break;
          default:
            break;
        }
      }

      void headers_almost_done (byte ch, ParserSettings settings) {

				if (LF != ch) {
					throw new RuntimeException("header not properly completed");
				}

        if (0 != (flags & F_TRAILING)) {
          /* End of a chunked request */

          settings.call_on_headers_complete(this);
					settings.call_on_message_complete(this);

          state = new_message(); 

					return;
        }

        nread = 0;

        if (0 != (flags & F_UPGRADE)) upgrade = true;

        /* Here we call the headers_complete callback. This is somewhat
         * different than other callbacks because if the user returns 1, we
         * will interpret that as saying that this message has no body. This
         * is needed for the annoying case of recieving a response to a HEAD
         * request.
         */

        /* (responses to HEAD request contain a CONTENT-LENGTH header
         * but no content)
         */

        /*TODO TODO TODO TODO TODO TODO TODO TODO TODO TODO */ 
        if (null != settings.on_headers_complete) {
          settings.call_on_headers_complete(this);
          //return;
        }
//        if (null != settings.on_headers_complete) {
//          switch (settings.on_headers_complete.cb(parser)) {
//            case 0:
//              break;
//
//            case 1:
//              flags |= F_SKIPBODY;
//              break;
//
//            default:
//              return p - data; /* Error */ // TODO // RuntimeException ?
//          }
//        }


        // Exit, the rest of the connect is in a different protocol.
        if (0 != (flags & F_UPGRADE)) {
					settings.call_on_message_complete(this);
          // TODO return (p - data); 
          // TODO remember initial buf.position as data?
					return;
        }

        if (0 != (flags & F_SKIPBODY)) {
					settings.call_on_message_complete(this);
          state = new_message(); 
        } else if (0 != (flags & F_CHUNKED)) {
          /* chunked encoding - ignore Content-Length header */
          state = State.chunk_size_start;
        } else {
          if (content_length == 0) {
            /* Content-Length header given but zero: Content-Length: 0\r\n */
						settings.call_on_message_complete(this);
            state = new_message(); 
          } else if (content_length > 0) {
            /* Content-Length header given and non-zero */
            state = State.body_identity;
          } else {
            if (type == ParserType.HTTP_REQUEST || http_should_keep_alive()) {
              /* Assume content-length 0 - read the next */
							settings.call_on_message_complete(this);
              state = new_message(); 
            } else {
              /* Read body until EOF */
              state = State.body_identity_eof;
            }
          }
        }

      } // headers_almost_fone


      final int min (int a, int b) {
        return a < b ? a : b;
      }

	public boolean HTTP_PARSER_STRICT = true;
	State new_message() {
		//try {throw new Exception();}catch (Throwable t) {t.printStackTrace();}
		if (HTTP_PARSER_STRICT){
			return http_should_keep_alive() ? start_state() : State.dead;
		} else {
			return start_state();
		}
		
	}
	
	State start_state() {
		return type == ParserType.HTTP_REQUEST ? State.start_req : State.start_res;
	}

	byte peak(ByteBuffer buf) {
		buf.mark();
		byte b = buf.get();
		buf.rewind();
		return b;
	}
	boolean parsing_header(State state) {

		switch (state) {
			case chunk_size_start :
			case chunk_size :
			case chunk_size_almost_done :
			case chunk_parameters :
			case chunk_data :
			case chunk_data_almost_done :
			case chunk_data_done :
			case body_identity :
			case body_identity_eof :
				return false;

		}
		return (0==(flags & F_TRAILING));
	}

	/* "Dial C for Constants" */
	static class C {
		static final int HTTP_MAX_HEADER_SIZE = 80 * 1024;

  	static final int F_CHUNKED               = 1 << 0;
   	static final int F_CONNECTION_KEEP_ALIVE = 1 << 1;
   	static final int F_CONNECTION_CLOSE      = 1 << 2;
   	static final int F_TRAILING              = 1 << 3;
   	static final int F_UPGRADE               = 1 << 4;
   	static final int F_SKIPBODY              = 1 << 5;

		static final byte [] UPCASE = {
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x20,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x2d,0x00,0x2f,
			0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x37,  0x38,0x39,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x41,0x42,0x43,0x44,0x45,0x46,0x47,  0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4f,
			0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,  0x58,0x59,0x5a,0x00,0x00,0x00,0x00,0x5f,
			0x00,0x41,0x42,0x43,0x44,0x45,0x46,0x47,  0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e,0x4f,
			0x50,0x51,0x52,0x53,0x54,0x55,0x56,0x57,  0x58,0x59,0x5a,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
			0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
		};
		static final byte [] CONNECTION = {
    	0x43, 0x4f, 0x4e, 0x4e, 0x45, 0x43, 0x54, 0x49, 0x4f, 0x4e, 
		};
		static final byte [] PROXY_CONNECTION = {
    	0x50, 0x52, 0x4f, 0x58, 0x59, 0x2d, 0x43, 0x4f, 0x4e, 0x4e, 0x45, 0x43, 0x54, 0x49, 0x4f, 0x4e, 
		};
		static final byte [] CONTENT_LENGTH = {
    	0x43, 0x4f, 0x4e, 0x54, 0x45, 0x4e, 0x54, 0x2d, 0x4c, 0x45, 0x4e, 0x47, 0x54, 0x48, 
		};
		static final byte [] TRANSFER_ENCODING = {
    	0x54, 0x52, 0x41, 0x4e, 0x53, 0x46, 0x45, 0x52, 0x2d, 0x45, 0x4e, 0x43, 0x4f, 0x44, 0x49, 0x4e, 0x47, 
		};
		static final byte [] UPGRADE = {
			0x55, 0x50, 0x47, 0x52, 0x41, 0x44, 0x45, 
		};
		static final byte [] CHUNKED = {
			0x43, 0x48, 0x55, 0x4e, 0x4b, 0x45, 0x44, 
		};
		static final byte [] KEEP_ALIVE = {
			0x4b, 0x45, 0x45, 0x50, 0x2d, 0x41, 0x4c, 0x49, 0x56, 0x45, 
		};
		static final byte [] CLOSE = {
			0x43, 0x4c, 0x4f, 0x53, 0x45, 
		};
		
 static final byte [] UNHEX =
  {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
  ,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
  ,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
  , 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,-1,-1,-1,-1,-1,-1
  ,-1,10,11,12,13,14,15,-1,-1,-1,-1,-1,-1,-1,-1,-1
  ,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
  ,-1,10,11,12,13,14,15,-1,-1,-1,-1,-1,-1,-1,-1,-1
  ,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
  };
public static final byte A = 0x41;
public static final byte B = 0x42;
public static final byte C = 0x43;
public static final byte D = 0x44;
public static final byte E = 0x45;
public static final byte F = 0x46;
public static final byte G = 0x47;
public static final byte H = 0x48;
public static final byte I = 0x49;
public static final byte J = 0x4a;
public static final byte K = 0x4b;
public static final byte L = 0x4c;
public static final byte M = 0x4d;
public static final byte N = 0x4e;
public static final byte O = 0x4f;
public static final byte P = 0x50;
public static final byte Q = 0x51;
public static final byte R = 0x52;
public static final byte S = 0x53;
public static final byte T = 0x54;
public static final byte U = 0x55;
public static final byte V = 0x56;
public static final byte W = 0x57;
public static final byte X = 0x58;
public static final byte Y = 0x59;
public static final byte Z = 0x5a;
public static final byte CR = 0x0d;
public static final byte LF = 0x0a;
public static final byte DOT = 0x2e;
public static final byte SPACE = 0x20;
public static final byte SEMI = 0x3b;
public static final byte COLON = 0x3a;
public static final byte HASH = 0x23;
public static final byte QMARK = 0x3f;
public static final byte SLASH = 0x2f;
public static final byte DASH = 0x2d;
public static final byte NULL = 0x00;

 
		

	
	}
enum State {

    dead                // why important?= 1 /* important that this is > 0 */

  , start_res_or_resp
  , res_or_resp_H
  , start_res
  , res_H
  , res_HT
  , res_HTT
  , res_HTTP
  , res_first_http_major
  , res_http_major
  , res_first_http_minor
  , res_http_minor
  , res_first_status_code
  , res_status_code
  , res_status
  , res_line_almost_done

  , start_req

  , req_method
  , req_spaces_before_url
  , req_schema
  , req_schema_slash
  , req_schema_slash_slash
  , req_host
  , req_port
  , req_path
  , req_query_string_start
  , req_query_string
  , req_fragment_start
  , req_fragment
  , req_http_start
  , req_http_H
  , req_http_HT
  , req_http_HTT
  , req_http_HTTP
  , req_first_http_major
  , req_http_major
  , req_first_http_minor
  , req_http_minor
  , req_line_almost_done

  , header_field_start
  , header_field
  , header_value_start
  , header_value

  , header_almost_done

  , headers_almost_done
  /* Important: 'headers_almost_done' must be the last 'header' state. All
   * states beyond this must be 'body' states. It is used for overflow
   * checking. See the PARSING_HEADER() macro.
   */
  , chunk_size_start
  , chunk_size
  , chunk_size_almost_done
  , chunk_parameters
  , chunk_data
  , chunk_data_almost_done
  , chunk_data_done

  , body_identity
  , body_identity_eof;


}
enum HState {
    general
  , C
  , CO
  , CON

  , matching_connection
  , matching_proxy_connection
  , matching_content_length
  , matching_transfer_encoding
  , matching_upgrade

  , connection
  , content_length
  , transfer_encoding
  , upgrade

  , matching_transfer_encoding_chunked
  , matching_connection_keep_alive
  , matching_connection_close

  , transfer_encoding_chunked
  , connection_keep_alive
  , connection_close
}
}
