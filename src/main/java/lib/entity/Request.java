package lib.entity;

public class Request {
  private HttpMethod method;
  private String file;
  private final Header header = new Header();
  private String body;

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public Header getHeader() {
    return header;
  }

  public static Request fromString(String rawString) {
    Request request = new Request();
    String[] lines = rawString.split("\n");

    // read method and file
    String[] firstLineTokens = lines[0].split(" ");
    String requestMethod = firstLineTokens[0].replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    String file = firstLineTokens[1];
    request.method = HttpMethod.valueOf(requestMethod);
    request.file = file;

    // read headers
    int i = 1;
    while (i < lines.length && !lines[i].isEmpty()) {
      String[] headerLineString = lines[i].split(": ");
      request.header.addEntry(headerLineString[0], headerLineString[1]);
      i += 1;
    }

    // skip the empty line after headers
    i += 1;
    StringBuilder bodySB = new StringBuilder();
    while (i < lines.length) {
      bodySB.append(lines[i]);
      bodySB.append("\n");
      i += 1;
    }
    request.body = bodySB.toString();
    return request;
  }

  public HttpMethod getMethod() {
    return this.method;
  }

  public String getFile() {
    return this.file;
  }
}
