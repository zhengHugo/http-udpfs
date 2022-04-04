package application;

import java.io.IOException;
import lib.RunServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "httpfs")
public class Httpfs implements Runnable {
  @Option(names = "-v")
  boolean verbose;

  @Option(names = "-p", defaultValue = "8080")
  int port;

  @Option(names = "-d")
  String rootDir;

  @Override
  public void run() {
    try {
      RequestHandlerImpl handler = new RequestHandlerImpl();
      if (rootDir != null) {
        handler.setRootDir(rootDir);
      }

      RunServer.listenAndServe(port, handler, verbose);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    int rc = new CommandLine(new Httpfs()).execute(args);
    System.exit(rc);
  }
}
