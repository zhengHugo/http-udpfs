package protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MySocket {
  private InputStream userInputStream;
  private OutputStream userOutputStream;

  private byte[] writeBuffer = new byte[1013];
  private int writeCounter = 0;
  private byte[] readBuffer = new byte[1013];
  private int readCounter = 0;

  private MyTcpHost myTcpHost;

  public MySocket(MyTcpHost myTcpHost) {
    this.myTcpHost = myTcpHost;
    this.initStreams();
  }

  public MySocket(String host, int port) throws IOException {

    myTcpHost = new MyTcpHost(host, port);
    this.initStreams();
    myTcpHost.connect();
  }

  public OutputStream getOutputStream() {
    return this.userOutputStream;
  }

  public InputStream getInputStream() {
    return this.userInputStream;
  }

  public void close() throws IOException {
    myTcpHost.close();
  }

  // throws something if exceeding buffer size
  private void ensureCapacity(int minCapacity) throws MessageTooLongException {
    if (writeBuffer.length < minCapacity) {
      throw new MessageTooLongException("Message is too long to send");
    }
  }

  private void initStreams() {
    this.userOutputStream =
        new OutputStream() {

          @Override
          public void write(int b) throws IOException {
            ensureCapacity(writeCounter + 1);
            writeBuffer[writeCounter] = (byte) b;
            writeCounter += 1;
          }

          @Override
          public void flush() throws IOException {
            super.flush();

            try {
              myTcpHost.send(writeBuffer);
            } catch (MessageTooLongException e) {
              e.printStackTrace();
            }
          }
        };

    this.userInputStream =
        new InputStream() {
          private boolean finished = false;

          @Override
          public int read() throws IOException {
            while (readCounter == 0 && readBuffer[0] == 0) {
              readBuffer = myTcpHost.receive();
            }
            if (readBuffer[readCounter] == 0) {
              // finished reading
              readCounter = 0;
              finished = true;
              return -1;
            } else if (finished) {
              return -1;
            } else {
              return readBuffer[readCounter++];
            }
          }
        };
  }
}
