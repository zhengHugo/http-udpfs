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

  private String routerAddress = "127.0.0.1";
  private int routerPort = 3000;

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

  public MyTcpHost(int port) throws SocketException {
    this.listenPort = port;
    datagramSocket = new DatagramSocket(listenPort);
    this.state = HostState.LISTEN;
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
      sendPacket(firstConnectionResponse);
      this.state = HostState.SYN_RCVD;
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
      MyTcpPacket requestPacket = null;
      if (this.state.equals(HostState.SYNSENT)) {
        // sender third handshake
        requestPacket = packetBuilder.withPacketType(PacketType.ACK).build();
        MyTcpPacket finalRequestPacket = requestPacket;
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
        requestPacket = packetBuilder.withPacketType(PacketType.DATA).build();
        MyTcpPacket finalRequestPacket = requestPacket;
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
        requestPacket = packetBuilder.withPacketType(PacketType.ACK).build();
      } else if (this.state.equals(HostState.ESTAB) && !this.isClient) {
        requestPacket = packetBuilder.withPacketType(PacketType.ACK).build();
      }
      // TODO: How to differentiate server sending or client sending?
      System.out.println("This is client: " + this.isClient);
      System.out.println("This state: " + this.state);
      sendPacket(requestPacket);
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
      timers[sequenceNum - 1].cancel();
      this.unAckedPacketNum--;
      return incomingPacket.getPayload();
    } else if ((this.state.equals(HostState.SYN_RCVD)
            && incomingPacket.getPacketType().equals(PacketType.ACK))
        || (this.state.equals(HostState.ESTAB)
                && incomingPacket.getPacketType().equals(PacketType.DATA))
            && (incomingPacket.getSequenceNum() == sequenceNum)) {
      // receiver receive or third handshake
      timers[sequenceNum].cancel();
      this.unAckedPacketNum--;
      this.destAddress = incomingPacket.getPeerAddress();
      this.destPort = incomingPacket.getPeerPort();
      return incomingPacket.getPayload();
    } else {
      return receive();
    }
  }

  void close() {}

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
}

enum HostState {
  LISTEN,
  SYNSENT,
  SYN_RCVD,
  ESTAB
}
