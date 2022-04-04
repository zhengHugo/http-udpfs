package protocol;

import java.io.IOException;
import java.net.SocketException;

public class MyServerSocket {
  private MyTcpHost myTcpHost;

  public MyServerSocket(int port) throws SocketException {
    myTcpHost = new MyTcpHost(port);
  }

  public MySocket accept() throws IOException {
    myTcpHost.accept();
    return new MySocket(myTcpHost);
  }
}
