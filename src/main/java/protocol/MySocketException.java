package protocol;

import java.io.IOException;

public class MySocketException extends IOException {
  public MySocketException(String msg) {
    super(msg);
  }
}
