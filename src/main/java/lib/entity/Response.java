package lib.entity;

public class Response {
  String status;
  Header headers = new Header();
  String body = "";

  public Response(String status, Header headers, String body) {
    this.status = status;
    this.headers = headers;
    this.body = body;
    this.headers.addEntry("server", "Concordia/COMP445A2");
  }

  public Response() {}

  public String getStatus() {
    return this.status;
  }

  public Header getHeaders() {
    return this.headers;
  }

  public String getBody() {
    return this.body;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setHeaders(Header headers) {
    this.headers = headers;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public void addHeader(String key, String value) {
    this.headers.addEntry(key, value);
  }

  @Override
  public String toString() {
    String statusLine = "HTTP/1.0 " + status + "\r\n";
    String headerLines = headers.toString();
    String body = this.getBody();
    String result;

    result = statusLine.concat(headerLines).concat("\r\n").concat(body);
    return result;
  }
}
