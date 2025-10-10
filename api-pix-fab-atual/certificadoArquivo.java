import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.BindToRegistry;

@BindToRegistry("certificadoArquivo")
public class certificadoArquivo {

    public String criarArquivoCertificado(Exchange exchange) throws Exception {
        String base64 = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificate");
        String senha = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificatePrivateKeyPassword");

        if (base64 == null || senha == null) {
            throw new RuntimeException("Certificado (base64) ou Senha ausentes");
        }

        byte[] pfxBytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));

        String id = "camel-ssl-" + exchange.getExchangeId();
        String tempDir = System.getProperty("java.io.tmpdir");
        String path = tempDir + File.separator + id + ".p12";

        try (FileOutputStream stream = new FileOutputStream(path)) {
            stream.write(pfxBytes);
        }

        exchange.setProperty("bancoArquivoCertificado", path);
        File certFile = new File(path);

        byte[] headBytes = Files.readAllBytes(Paths.get(path));
        byte[] firstBytes = new byte[Math.min(16, headBytes.length)];
        System.arraycopy(headBytes, 0, firstBytes, 0, firstBytes.length);
        
        String hexBytes = IntStream.range(0, firstBytes.length)
            .mapToObj(i -> String.format("%02X", firstBytes[i]))
            .collect(Collectors.joining(" "));

        String certificateType = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificateType");

        String keyStoreType;
        switch (certificateType) {
            case "X509_PEM":
            case "PRIVATE_KEY_PEM":
                keyStoreType = "PEM"; // Exemplo: PEM pode ser usado para certificados X.509 em formato PEM
                break;
            case "PKCS12_PFX":
            case "PKCS12_P12":
                keyStoreType = "PKCS12";
                break;
            case "X509_DER":
                keyStoreType = "JKS"; // Exemplo: JKS pode ser usado para certificados DER
                break;
            default:
                throw new RuntimeException("Tipo de certificado não suportado: " + certificateType);
        }

        KeyStoreParameters kp = new KeyStoreParameters();
        kp.setResource("file:" + path);
        kp.setType(keyStoreType);
        kp.setPassword(senha);

        KeyManagersParameters km = new KeyManagersParameters();
        km.setKeyStore(kp);
        km.setKeyPassword(senha);

        // Cria o SSLContextParameters
        SSLContextParameters ssl = new SSLContextParameters();
        ssl.setKeyManagers(km);

        // Registra o SSLContextParameters no registro do Camel
        exchange.getContext().getRegistry().bind(id, ssl);

        return id;
    }

    public String buscarArquivoCertificado(Exchange exchange) throws Exception {
        String arquivo = (String) exchange.getProperty("bancoArquivoCertificado", String.class);
        String senha = (String) exchange.getProperty("bancoSenhaCertificado", String.class);

        if (arquivo == null || senha == null) {
            throw new RuntimeException("Nome do arquivo ou senha ausentes");
        }

        File certFile = new File(arquivo);
        if (!certFile.exists()) {
            throw new RuntimeException("Arquivo de certificado não encontrado: " + arquivo);
        }

        // Opcional: ler os primeiros bytes para garantir que não está corrompido
        byte[] headBytes = Files.readAllBytes(Paths.get(arquivo));
        byte[] firstBytes = new byte[Math.min(16, headBytes.length)];
        System.arraycopy(headBytes, 0, firstBytes, 0, firstBytes.length);
        String hexBytes = IntStream.range(0, firstBytes.length)
            .mapToObj(i -> String.format("%02X", firstBytes[i]))
            .collect(Collectors.joining(" "));

        String certificateType = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificateType");

        String keyStoreType;
        switch (certificateType) {
            case "X509_PEM":
            case "PRIVATE_KEY_PEM":
                keyStoreType = "PEM"; // Exemplo: PEM pode ser usado para certificados X.509 em formato PEM
                break;
            case "PKCS12_PFX":
            case "PKCS12_P12":
                keyStoreType = "PKCS12";
                break;
            case "X509_DER":
                keyStoreType = "JKS"; // Exemplo: JKS pode ser usado para certificados DER
                break;
            default:
                throw new RuntimeException("Tipo de certificado não suportado: " + certificateType);
        }

        // Configura os parâmetros do KeyStore
        KeyStoreParameters kp = new KeyStoreParameters();
        kp.setResource("file:" + arquivo);
        kp.setType(keyStoreType);
        kp.setPassword(senha);

        // Configura os parâmetros do KeyManager
        KeyManagersParameters km = new KeyManagersParameters();
        km.setKeyStore(kp);
        km.setKeyPassword(senha);

        // Cria o SSLContextParameters
        SSLContextParameters ssl = new SSLContextParameters();
        ssl.setKeyManagers(km);

        String id = "camel-ssl-" + exchange.getExchangeId();
        exchange.getContext().getRegistry().bind(id, ssl);

        return id;
    }
}
