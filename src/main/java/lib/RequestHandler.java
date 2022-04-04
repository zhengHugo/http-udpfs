package lib;


import java.io.IOException;
import lib.entity.Request;
import lib.entity.Response;

public interface RequestHandler {
  Response handleRequest(Request request) throws IOException;
}
