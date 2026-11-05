package ElFabrica.Wallet_pay.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Minimal client for the Mailpit HTTP API (GET/DELETE /api/v1/messages). */
public class MailpitClient {

    public record MailpitMessage(String to, String subject, String body) {
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();

    public MailpitClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<MailpitMessage> messages() {
        JsonNode list = get("/api/v1/messages");
        List<MailpitMessage> result = new ArrayList<>();
        for (JsonNode summary : list.path("messages")) {
            JsonNode full = get("/api/v1/message/" + summary.path("ID").asString());
            String to = full.path("To").isEmpty() ? "" : full.path("To").get(0).path("Address").asString();
            result.add(new MailpitMessage(to, full.path("Subject").asString(), full.path("Text").asString()));
        }
        return result;
    }

    public void clear() {
        send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/messages")).DELETE().build());
    }

    private JsonNode get(String path) {
        String body = send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
        return mapper.readTree(body);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Mailpit answered " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
