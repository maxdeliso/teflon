package name.maxdeliso.teflon.config;

import java.util.Optional;

public interface ConfigLoader {
    Optional<Config> loadFromFile(String path);
}
