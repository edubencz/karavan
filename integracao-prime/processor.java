import org.apache.camel.BindToRegistry;
import org.apache.camel.Configuration;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import org.apache.camel.Handler;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@BindToRegistry("processor")
public class processor implements Processor {

    public void process(Exchange exchange) throws Exception {
        String emailSenha = exchange.getIn().getHeader("emailSenha", String.class);
        String chaveBase64 = exchange.getIn().getHeader("chaveCript", String.class);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(chaveBase64)));

        byte[] encodedData = emailSenha.getBytes(StandardCharsets.UTF_8);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encodedData.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(encodedData[i] & 0xFF);
        }

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT
        );

        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);
        String encryptedBase64 = Base64.getEncoder().encodeToString(cipher.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8)));

        exchange.setProperty("tokenSolicitante", encryptedBase64);
    }
}