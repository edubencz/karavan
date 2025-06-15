import org.apache.camel.BindToRegistry;
import org.apache.camel.Configuration;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Handler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

@Configuration
@BindToRegistry("CredentialDecoder")
public class CredentialDecoder implements Processor {
    public void process(Exchange exchange) throws Exception {
        Object rotaObj = exchange.getProperty("rota");
        if (rotaObj == null) {
            throw new IllegalArgumentException("Propriedade 'rota' não encontrada");
        }

        String rotaJson;
        if (rotaObj instanceof String) {
            rotaJson = (String) rotaObj;
        } else {
            rotaJson = new ObjectMapper().writeValueAsString(rotaObj);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode json;
        try {
            json = mapper.readTree(rotaJson);
        } catch (Exception e) {
            throw e;
        }

        JsonNode credencialNode = json.get("credencial");
        if (credencialNode == null || credencialNode.isNull()) {
            throw new IllegalArgumentException("Campo 'credencial' não encontrado em 'rota'");
        }

        String encoded = credencialNode.asText();
        String decoded;

        try {
            decoded = new String(Base64.getDecoder().decode(encoded));
        } catch (Exception e) {
            throw e;
        }

        String[] parts = decoded.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Credencial em formato inválido. Esperado 'client_id:client_secret'");
        }

        exchange.setProperty("client_id", parts[0]);
        exchange.setProperty("client_secret", parts[1]);
    }
}
