package ElFabrica.Wallet_pay.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    static void write(
            HttpServletResponse response,
            JsonMapper jsonMapper,
            ErrorCode code,
            String message
    ) throws IOException {
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message, Map.of()));
    }
}
