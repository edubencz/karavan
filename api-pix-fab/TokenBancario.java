import org.apache.camel.BindToRegistry;
import org.apache.camel.Configuration;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.*;

@Configuration
@BindToRegistry("tokenBancario")
public class TokenBancario {

    private String bbToken;
    private long bbTokenExp;

    public synchronized String getBbTokenClientCredentials(String clientId, String clientSecret) throws Exception {
        if (bbToken == null || System.currentTimeMillis() >= bbTokenExp) {
            String basicAuth = gerarBasicAuth(clientId, clientSecret);
            String body = "grant_type=client_credentials&scopes=cobv.write cobv.read pix.write pix.read";

            bbToken = buscarToken("https://oauth.hm.bb.com.br/oauth/token", basicAuth, body);
            bbTokenExp = System.currentTimeMillis() + 570_000;
        }
        return bbToken;
    }

    private String buscarToken(String url, String authorization, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(url))
            .header("Authorization", authorization)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Erro ao obter token: " + response.body());
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json = mapper.readTree(response.body());
        return json.get("access_token").asText();
    }

    private String gerarBasicAuth(String clientId, String clientSecret) {
        String credenciais = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));
    }
}
