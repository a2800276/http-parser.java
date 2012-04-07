package http_parser;

import java.util.Arrays;

/**
 */
public class HTTPParserUrl {

  public int field_set;
  public int port;

  public FieldData[] field_data = new FieldData[]{
    new FieldData(0,0),
    new FieldData(0,0),
    new FieldData(0,0),
    new FieldData(0,0),
    new FieldData(0,0),
    new FieldData(0,0)
  }; //UF_MAX

  public HTTPParserUrl(){}

  public HTTPParserUrl(int field_set, int port, FieldData[] field_data){
    this.field_set = field_set;
    this.port = port;
    this.field_data = field_data;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HTTPParserUrl that = (HTTPParserUrl) o;

    if (field_set != that.field_set) return false;
    if (port != that.port) return false;
    if (!Arrays.equals(field_data, that.field_data)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = field_set;
    result = 31 * result + port;
    result = 31 * result + Arrays.hashCode(field_data);
    return result;
  }

  @Override
  public String toString() {
    return "HTTPParserUrl{" +
        "field_set=" + field_set +
        ", port=" + port +
        ", field_data=" + (field_data == null ? null : Arrays.asList(field_data)) +
        '}';
  }
}
