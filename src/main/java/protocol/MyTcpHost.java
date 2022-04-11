package protocol;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Timer;
import java.util.TimerTask;

public class MyTcpHost {

  private String destAddress;
  private int destPort;

  private int listenPort;

  private final String routerAddress = "127.0.0.1";
  private final int routerPort = 3000;

  private DatagramSocket datagramSocket;
  private DatagramPacket response;

  private final long timeout = 1000;
  private final int INIT_SEQ_NUM = 0;
  private final int DEFAULT_LISTEN_PORT = 5050;
  private final int windowSize = 5;

  private int sequenceNum = INIT_SEQ_NUM;
  private Timer[] timers;
  private int unAckedPacketNum;

  private HostState state;

  private boolean isClient;

  private MyTcpPacket lastSentPacket;

  public MyTcpHost(int port) throws SocketException {
    serverInit(port);
  }

  public void serverInit(int port) throws SocketException {
    this.listenPort = port;
    datagramSocket = new DatagramSocket(listenPort);
    this.state = HostState.LISTEN;
    this.sequenceNum = INIT_SEQ_NUM;
    timers = new Timer[windowSize];
    for (int i = 0; i < windowSize; i++) {
      timers[i] = new Timer();
    }
  }

  public MyTcpHost(String host, int port) throws SocketException {
    datagramSocket = new DatagramSocket(listenPort);

    this.destAddress = host;
    this.destPort = port;
    this.listenPort = DEFAULT_LISTEN_PORT;
    this.state = HostState.LISTEN;
    timers = new Timer[windowSize];
    for (int i = 0; i < windowSize; i++) {
      timers[i] = new Timer();
    }
  }

  public void connect() throws IOException {
    this.isClient = true;
    if (!state.equals(HostState.LISTEN)) {
      throw new MySocketException("Connection has already opened");
    }
    sequenceNum = INIT_SEQ_NUM;
    var firstConnectionRequest =
        new MyTcpPacket.Builder()
            .withPacketType(PacketType.SYN)
            .withSequenceNum(sequenceNum)
            .withPeerAddress(destAddress)
            .withPeerPort(destPort)
            .withPayload(new byte[1013])
            .build();

    sendPacket(firstConnectionRequest);
    timers[0].schedule(
        new TimerTask() {
          @Override
          public void run() {
            try {
              sendPacket(firstConnectionRequest);
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        },
        timeout);
    System.out.println("Sent first connection packet");
    var firstConnectionResponse = receivePacket();
    timers[0].cancel();
    System.out.println("received first connection packet");
    if (!firstConnectionResponse.getPacketType().equals(PacketType.SYN_ACK)
        || firstConnectionResponse.getSequenceNum() != sequenceNum - 1) {
      throw new IOException("Connection fail");
    }
    this.state = HostState.SYNSENT;
  }

  public void accept() throws IOException {
    this.isClient = false;
    var incomingPacket = receivePacket();
    if (this.state.equals(HostState.LISTEN)
        && incomingPacket.getPacketType().equals(PacketType.SYN)
        && incomingPacket.getSequenceNum() == INIT_SEQ_NUM) {
      var firstConnectionResponse =
          new MyTcpPacket.Builder()
              .withPacketType(PacketType.SYN_ACK)
              .withPeerAddress(incomingPacket.getPeerAddress())
              .withPeerPort(incomingPacket.getPeerPort())
              .withSequenceNum(incomingPacket.getSequenceNum())
              .withPayload(new byte[1])
              .build();
      this.lastSentPacket = firstConnectionResponse;
      sendPacket(firstConnectionResponse);
      this.state = HostState.SYN_RCVD;
      System.out.println("This state: " + this.state);
    }
  }

  public void send(byte[] data) throws MessageTooLongException, IOException {
    if (unAckedPacketNum < windowSize) {
      var packetBuilder =
          new MyTcpPacket.Builder()
              .withSequenceNum(sequenceNum)
              .withPeerAddress(destAddress)
              .withPeerPort(destPort)
              .withPayload(data);
      MyTcpPacket packetToSend = null;
      if (this.state.equals(HostState.SYNSENT)) {
        // sender third handshake
        packetToSend = packetBuilder.withPacketType(PacketType.ACK).build();
        MyTcpPacket finalRequestPacket = packetToSend;
        timers[1].schedule(
            new TimerTask() {
              @Override
              public void run() {
                try {
                  sendPacket(finalRequestPacket);
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            },
            timeout);
      } else if (this.state.equals(HostState.ESTAB) && this.isClient) {
        // sender send data
        packetToSend = packetBuilder.withPacketType(PacketType.DATA).build();
        MyTcpPacket finalRequestPacket = packetToSend;
        timers[sequenceNum % windowSize].schedule(
            new TimerTask() {
              @Override
              public void run() {
                try {
                  sendPacket(finalRequestPacket);
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            },
            timeout);
      } else if (this.state.equals(HostState.SYN_RCVD)) {
        // receiver response to third handshake
        packetToSend = packetBuilder.withPacketType(PacketType.ACK).build();
        this.lastSentPacket = packetToSend;
//      } else if (this.state.equals(HostState.ESTAB) && !this.isClient) {
//        packetToSend = packetBuilder.withPacketType(ACK).build();
      }
      System.out.println("This is client: " + this.isClient);
      System.out.println("This state: " + this.state);
      assert packetToSend != null;
      sendPacket(packetToSend);
      this.state = HostState.ESTAB;
      this.unAckedPacketNum++;
    }
  }

  public byte[] receive() throws IOException {
    var incomingPacket = receivePacket();
    if (this.state.equals(HostState.ESTAB)
        && incomingPacket.getPacketType().equals(PacketType.ACK)
        && incomingPacket.getSequenceNum() == sequenceNum - 1) {
      // sender received response
      timers[incomingPacket.getSequenceNum() % windowSize].cancel();
      this.unAckedPacketNum--;
      return incomingPacket.getPayload();
    } else if (incomingPacket.getPacketType().equals(PacketType.FIN)
        && incomingPacket.getSequenceNum() == sequenceNum - 1) {
      // receiver receives close request
      sendCloseResponsePacket();
      serverSendLastClose();
      receiveLastClosePacket();
      return null;
    } else if ((this.state.equals(HostState.SYN_RCVD) && incomingPacket.getPacketType().equals(PacketType.DATA))
        || (this.state.equals(HostState.ESTAB)
            && incomingPacket.getPacketType().equals(PacketType.DATA))) {

      if (incomingPacket.getSequenceNum() == sequenceNum) {
        // receiver receive or third handshake
        this.unAckedPacketNum--;
        this.destAddress = incomingPacket.getPeerAddress();
        this.destPort = incomingPacket.getPeerPort();
        return incomingPacket.getPayload();
      } else if (incomingPacket.getSequenceNum() == sequenceNum - 1) {
        // receiver learned that last response packet lost: resend last packet
        sendPacket(lastSentPacket);
        return receive();
      }
    } else {
      return receive();
    }
    return null;
  }

  void close() throws IOException {
    clientSendClose();
    severSendClose();
    serverSendLastClose();
    clientSendLastClose();
  }

  private void sendPacket(MyTcpPacket packet) throws IOException {
    var udpPayload = packet.toBytes();
    // send packet
    var udpRequestPacket =
        new DatagramPacket(
            udpPayload, udpPayload.length, InetAddress.getByName(routerAddress), routerPort);
    sequenceNum++;
    datagramSocket.send(udpRequestPacket);
    System.out.println(
        "send packet seq num: " + packet.getSequenceNum() + " type: " + packet.getPacketType());
  }

  private MyTcpPacket receivePacket() throws IOException {
    byte[] buf = new byte[4095];
    response = new DatagramPacket(buf, buf.length);
    datagramSocket.receive(response);
    var tcpResponse = MyTcpPacket.fromByte(response.getData());
    System.out.println(
        "receive packet seq num: "
            + tcpResponse.getSequenceNum()
            + " type: "
            + tcpResponse.getPacketType());
    return tcpResponse;
  }

  public void clientSendClose() throws IOException {
    if (this.isClient) {
      var packetBuilder =
          new MyTcpPacket.Builder().withPeerAddress(destAddress).withPeerPort(destPort);
      MyTcpPacket requestPacket = null;
      packetBuilder.withPacketType(PacketType.FIN);
      requestPacket = packetBuilder.withSequenceNum(sequenceNum).build();
      MyTcpPacket closeRequestPacket = requestPacket;
      timers[sequenceNum % windowSize].schedule(
          new TimerTask() {
            @Override
            public void run() {
              try {
                sendPacket(closeRequestPacket);
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          },
          timeout);
      System.out.println("This is client: " + this.isClient);
      System.out.println("This state: " + this.state);
      sendPacket(closeRequestPacket);
    }
  }

  public void severSendClose() throws IOException {
    if (!this.isClient) {
      var incomingPacket = receivePacket();
      // check receive
      if (incomingPacket.getPacketType().equals(PacketType.FIN)
          && (incomingPacket.getSequenceNum() == sequenceNum)) {
        timers[sequenceNum].cancel();
        sendCloseResponsePacket();
      } else {
        clientSendClose();
      }
    }
  }

  public void serverSendLastClose() throws IOException {
    if (!this.isClient) {
      var lastClosePacket =
          new MyTcpPacket.Builder()
              .withPeerAddress(destAddress)
              .withPeerPort(destPort)
              .withPacketType(PacketType.FIN)
              .withSequenceNum(sequenceNum)
              .build();
      System.out.println("This is client: " + this.isClient);
      System.out.println("This state: " + this.state);
      sendPacket(lastClosePacket);
    }
  }

  public void clientSendLastClose() throws IOException {
    if (this.isClient) {
      var incomingPacket = receivePacket();
      // check receive
      if (incomingPacket.getPacketType().equals(PacketType.FIN)
          && (incomingPacket.getSequenceNum() == sequenceNum)) {
        sendCloseResponsePacket();
      }
    }
  }

  private void sendCloseResponsePacket() throws IOException {
    var closeResponsePacket =
        new MyTcpPacket.Builder()
            .withPeerAddress(destAddress)
            .withPeerPort(destPort)
            .withPacketType(PacketType.ACK)
            .withSequenceNum(sequenceNum)
            .build();
    System.out.println("This is client: " + this.isClient);
    System.out.println("This state: " + this.state);
    sendPacket(closeResponsePacket);
  }

  private void receiveLastClosePacket() throws IOException {
    if (!this.isClient) {
      var incomingPacket = receivePacket();
      serverInit(listenPort);
    }
  }
}

enum HostState {
  LISTEN,
  SYNSENT,
  SYN_RCVD,
  ESTAB,
}
