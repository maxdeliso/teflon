package name.maxdeliso.teflon.ctx;

import java.util.UUID;

public class RunContext {
  private final String localHostUUID;

  public RunContext() {
    localHostUUID = UUID.randomUUID().toString();
  }

  public String getLocalHostUUID() {
    return localHostUUID;
  }
}
