package protocol;

/*
class for My TCP Packet
A MyTcpPacket can be build from its Builder class and used as payload in a UDP request
 */
public class MyTcpPacket {

  private PacketType packetType;
  private int sequenceNum;
  private String peerAddress;
  private int peerPort;
  private byte[] payload;

  public String getPeerAddress() {
    return peerAddress;
  }

  public int getPeerPort() {
    return peerPort;
  }

  public byte[] getPayload() {
    return payload;
  }


  private MyTcpPacket() {
  }

  private MyTcpPacket(PacketType packetType, int sequenceNum, String peerAddress, int peerPort,
      byte[] payload) {
    this.packetType = packetType;
    this.sequenceNum = sequenceNum;
    this.peerAddress = peerAddress;
    this.peerPort = peerPort;
    this.payload = payload;
  }

  public byte[] toBytes() {
    byte[] packetAsBytes = new byte[1024];

    // packet type
    packetAsBytes[0] = this.packetType.toByte();

    // sequence number
    packetAsBytes[1] = (byte) (sequenceNum >> 24);
    packetAsBytes[2] = (byte) (sequenceNum >> 16);
    packetAsBytes[3] = (byte) (sequenceNum >> 8);
    packetAsBytes[4] = (byte) sequenceNum;

    // peer address
    String[] peerAddressSplit = peerAddress.split("\\.");
    packetAsBytes[5] = (byte) Integer.parseInt(peerAddressSplit[0]);
    packetAsBytes[6] = (byte) Integer.parseInt(peerAddressSplit[1]);
    packetAsBytes[7] = (byte) Integer.parseInt(peerAddressSplit[2]);
    packetAsBytes[8] = (byte) Integer.parseInt(peerAddressSplit[3]);

    // peer port
    packetAsBytes[9] = (byte) (peerPort >> 8);
    packetAsBytes[10] = (byte) peerPort;

    // payload
    System.arraycopy(payload, 0, packetAsBytes, 11, payload.length);

    return packetAsBytes;
  }

  public static MyTcpPacket fromByte(byte[] data) {
    MyTcpPacket myTcpPacket = new MyTcpPacket();

    // packet type
    myTcpPacket.packetType = PacketType.fromByte(data[0]);

    // sequence number
    myTcpPacket.sequenceNum = 0;
    myTcpPacket.sequenceNum += (data[1] & 0xff) << 24 ;
    myTcpPacket.sequenceNum += (data[2] & 0xff) << 16;
    myTcpPacket.sequenceNum += (data[3] & 0xff) << 8;
    myTcpPacket.sequenceNum += data[4] & 0xff;

    // peer address
    myTcpPacket.peerAddress = (data[5] & 0xff)
        + "."
        + (data[6] & 0xff)
        + "."
        + (data[7] & 0xff)
        + "."
        + (data[8] & 0xff);

    // port
    myTcpPacket.peerPort = 0;
    myTcpPacket.peerPort += (data[9] & 0xff) << 8;
    myTcpPacket.peerPort += data[10] & 0xff;

    myTcpPacket.payload = new byte[1013];
    System.arraycopy(data, 11, myTcpPacket.payload, 0, 1013);

    return myTcpPacket;
  }

  public int getSequenceNum() {
    return sequenceNum;
  }

  public PacketType getPacketType(){
    return packetType;
  }

  static class Builder {
    private PacketType packetType;
    private int sequenceNum;
    private String peerAddress;
    private int peerPort;
    private byte[] payload;

    Builder withPacketType(PacketType packetType) {
      this.packetType = packetType;
      return this;
    }

    Builder withSequenceNum(int sequenceNum){
      this.sequenceNum = sequenceNum;
      return this;
    }

    Builder withPeerAddress(String peerAddress) {
      this.peerAddress = peerAddress;
      return this;
    }

    Builder withPeerPort(int peerPort) {
      this.peerPort = peerPort;
      return this;
    }

    Builder withPayload(byte[] payload) throws MessageTooLongException {
      if (payload.length > 1013) {
        throw new MessageTooLongException("Payload in a single MyTCP message exceeds 1013 bytes");
      }
      this.payload = payload;
      return this;
    }

    MyTcpPacket build() {
      return new MyTcpPacket(packetType, sequenceNum, peerAddress, peerPort, payload);
    }
  }
}

enum PacketType {
  DATA,
  ACK,
  SYN,
  SYN_ACK,
  DATA_ACK;

  byte toByte() {
    return (byte)switch (this) {
      case DATA -> 0;
      case ACK -> 1;
      case SYN -> 2;
      case SYN_ACK -> 3;
      case DATA_ACK -> 4;
    };
  }

  static PacketType fromByte(byte packetTypeAsByte) {
    return switch (packetTypeAsByte) {
      case 0 -> DATA;
      case 1 -> ACK;
      case 2 -> SYN;
      case 3 -> SYN_ACK;
      case 4 -> DATA_ACK;
      default -> throw new IllegalArgumentException("Invalid byte for a packet type");
    };
  }

}
