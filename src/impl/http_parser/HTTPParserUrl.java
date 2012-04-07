package http_parser;

/**
 */
public class HTTPParserUrl {
  public int field_set;
  public int port;
  
  public class FieldData {
    public int off;
    public int len;
  }

  public FieldData[] field_data = new FieldData[6]; //UF_MAX
  
}
