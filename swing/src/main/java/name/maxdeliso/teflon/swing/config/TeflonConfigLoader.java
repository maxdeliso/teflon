package name.maxdeliso.teflon.swing.config;

import java.util.Optional;

public interface TeflonConfigLoader {
    Optional<TeflonConfig> loadFromFile(String path);
}
