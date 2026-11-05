package ElFabrica.Wallet_pay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final String ALLOWED_PACKAGE_DIR = "common/event/rabbit";

    @Test
    void onlyTheRabbitAdapterMayImportAmqpClasses() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace((char) 92, '/').contains(ALLOWED_PACKAGE_DIR))
                    .filter(ArchitectureBoundaryTest::mentionsAmqp)
                    .map(Path::toString)
                    .toList();
        }

        assertThat(offenders).as("files outside %s that reference the AMQP client", ALLOWED_PACKAGE_DIR).isEmpty();
    }

    private static boolean mentionsAmqp(Path path) {
        try {
            String content = Files.readString(path);
            return content.contains("org.springframework.amqp") || content.contains("com.rabbitmq");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
