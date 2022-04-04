package lib.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Header {

  Map<String, String> hashMap = new HashMap<>();

  public void addEntry(String key, String value) {
    this.hashMap.put(key, value);
  }

  public Optional<String> getValue(String headerKey) {
    String value = hashMap.get(headerKey);
    if (value == null) {
      return Optional.empty();
    } else {
      return Optional.of(value);
    }
  }

  @Override
  public String toString() {
    return hashMap.keySet().stream()
        .map(key -> key + ": " + hashMap.get(key))
        .collect(Collectors.joining("\r\n", "", "\r\n"));
  }
}
