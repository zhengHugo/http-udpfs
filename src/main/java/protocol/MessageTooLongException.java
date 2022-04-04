package protocol;

import java.io.IOException;

public class MessageTooLongException extends IOException {

  public MessageTooLongException(String message) {
    super(message);
  }
}
