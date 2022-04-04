package lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import lib.entity.Request;
import lib.entity.Response;
import protocol.MyServerSocket;
import protocol.MySocket;

public class RunServer {

  public static void listenAndServe(int port, RequestHandler requestHandler, boolean verbose)
      throws IOException {
    MyServerSocket serverSocket = new MyServerSocket(port);
    while (true) {
      MySocket socket = serverSocket.accept();
      System.out.println("Connection built");
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

      String line = reader.readLine();
      StringBuilder stringBuilder = new StringBuilder();
      boolean readLineEnd = false;
      while (line != null) {
        if (line.isEmpty()) {
          stringBuilder.append("\n");
          break;
        }
        stringBuilder.append(line);
        stringBuilder.append("\n");
        line = reader.readLine();
      }
      if (verbose) {
        System.out.println("Request:");
        System.out.println(stringBuilder);
      }

      Request request = Request.fromString(stringBuilder.toString());
      StringBuilder bodyBuilder = new StringBuilder();
      if (request.getHeader().getValue("Content-Length").isPresent()) {
        // read body
        int sizeLeft = Integer.parseInt(request.getHeader().getValue("Content-Length").get());
        while (sizeLeft > 0) {
          String nextChar = String.valueOf((char) reader.read());
          sizeLeft -= nextChar.getBytes(StandardCharsets.UTF_8).length;
          bodyBuilder.append(nextChar);
        }
        request.setBody(bodyBuilder.toString());
      }

      Response response = requestHandler.handleRequest(request);
      if (verbose) {
        System.out.println("Response: ");
        System.out.println(response);
      }
      PrintWriter writer =
          new PrintWriter(
              new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false);
      writer.println(response.toString());
      writer.close();
    }
  }
}
