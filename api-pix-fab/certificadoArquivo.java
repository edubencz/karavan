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

/**
 * Utilitário para criar arquivo de certificado a partir de uma string Base64
 * e configurar um SSLContextParameters para uso com o Camel.
 */
@BindToRegistry("certificadoArquivo")
public class certificadoArquivo {

    /**
     * Cria um arquivo de certificado a partir de uma string Base64 e configura um SSLContextParameters.
     * 
     * @param exchange O Exchange Camel contendo as propriedades necessárias
     * @return O ID do SSLContextParameters registrado no contexto Camel
     * @throws Exception Se ocorrer algum erro durante o processamento
     */
    public String criarArquivoCertificado(Exchange exchange) throws Exception {
        // Obtém a string Base64 do certificado e a senha da propriedade do exchange
        String base64 = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificate");
        String senha = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificatePrivateKeyPassword");

        System.out.println("[DEBUG] Iniciando processamento de certificado");
        System.out.println("[DEBUG] Tamanho do certificado Base64: " + (base64 != null ? base64.length() : "null"));

        // Verifica se o certificado e senha estão presentes
        if (base64 == null || senha == null) {
            System.out.println("[ERROR] Certificado (base64) ou Senha ausentes");
            throw new RuntimeException("Certificado (base64) ou Senha ausentes");
        }

        // Decodifica a string Base64 para bytes, removendo espaços em branco
        byte[] pfxBytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        System.out.println("[DEBUG] Certificado decodificado, tamanho: " + pfxBytes.length + " bytes");

        // Gera um ID único para o certificado
        String id = "camel-ssl-" + exchange.getExchangeId();
        String tempDir = System.getProperty("java.io.tmpdir");
        System.out.println("[DEBUG] Diretório temporário: " + tempDir);

        // Cria o caminho do arquivo
        String path = tempDir + File.separator + id + ".p12";
        System.out.println("[DEBUG] Caminho do arquivo temporário: " + path);

        // Escreve os bytes no arquivo
        try (FileOutputStream stream = new FileOutputStream(path)) {
            stream.write(pfxBytes);
        }

        System.out.println("[DEBUG] Cria exchangeProperty.bancoArquivoCertificado: " + path);
        exchange.setProperty("bancoArquivoCertificado", path);

        System.out.println("[DEBUG] Certificado salvo em: " + path);
        File certFile = new File(path);
        
        System.out.println("[DEBUG] Arquivo existe? " + certFile.exists());
        System.out.println("[DEBUG] Tamanho do arquivo: " + certFile.length() + " bytes");
        System.out.println("[DEBUG] Permissões do arquivo: " + certFile.canRead() + ", " + certFile.canWrite());

        // Lê os primeiros bytes para verificar integridade
        byte[] headBytes = Files.readAllBytes(Paths.get(path));
        byte[] firstBytes = new byte[Math.min(16, headBytes.length)];
        System.arraycopy(headBytes, 0, firstBytes, 0, firstBytes.length);
        
        String hexBytes = IntStream.range(0, firstBytes.length)
            .mapToObj(i -> String.format("%02X", firstBytes[i]))
            .collect(Collectors.joining(" "));
        System.out.println("[DEBUG] Primeiros bytes do arquivo: " + hexBytes);

        // Obtém o tipo de certificado da propriedade do exchange
        String certificateType = (String) exchange.getProperty("bancoPayloadDados", java.util.Map.class).get("certificateType");
        System.out.println("[DEBUG] Tipo de certificado recebido: " + certificateType);

        // Mapeia o tipo de certificado para o tipo de KeyStore
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
        kp.setResource("file:" + path);
        kp.setType(keyStoreType);
        kp.setPassword(senha);
        System.out.println("[DEBUG] KeyStoreParameters configurado: resource=file:" + path + ", type=" + keyStoreType + ", password=***");

        // Configura os parâmetros do KeyManager
        KeyManagersParameters km = new KeyManagersParameters();
        km.setKeyStore(kp);
        km.setKeyPassword(senha);
        System.out.println("[DEBUG] KeyManagersParameters configurado");

        // Cria o SSLContextParameters
        SSLContextParameters ssl = new SSLContextParameters();
        ssl.setKeyManagers(km);
        System.out.println("[DEBUG] SSLContextParameters criado");

        // Registra o SSLContextParameters no registro do Camel
        exchange.getContext().getRegistry().bind(id, ssl);
        System.out.println("[DEBUG] SSL Context configurado e registrado com ID: " + id);

        return id;
    }

    /**
     * Busca o arquivo de certificado, valida sua existência e configura o SSLContextParameters.
     * 
     * @param exchange O Exchange Camel contendo as propriedades necessárias
     * @return O ID do SSLContextParameters registrado no contexto Camel
     * @throws Exception Se o arquivo não for encontrado ou ocorrer algum erro
     */
    public String buscarArquivoCertificado(Exchange exchange) throws Exception {
        // Recebe o nome do arquivo do certificado já existente e a senha
        String arquivo = (String) exchange.getProperty("bancoArquivoCertificado", String.class);
        String senha = (String) exchange.getProperty("bancoSenhaCertificado", String.class);

        System.out.println("[DEBUG] Iniciando processamento de certificado por nome de arquivo");
        System.out.println("[DEBUG] Nome do arquivo recebido: " + arquivo);
        System.out.println("[DEBUG] Senha recebida: " + (senha != null ? "***" : "null"));

        if (arquivo == null || senha == null) {
            System.out.println("[ERROR] Nome do arquivo ou senha ausentes");
            throw new RuntimeException("Nome do arquivo ou senha ausentes");
        }

        File certFile = new File(arquivo);
        if (!certFile.exists()) {
            System.out.println("[ERROR] Arquivo de certificado não encontrado: " + arquivo);
            throw new RuntimeException("Arquivo de certificado não encontrado: " + arquivo);
        }

        System.out.println("[DEBUG] Arquivo existe? " + certFile.exists());
        System.out.println("[DEBUG] Tamanho do arquivo: " + certFile.length() + " bytes");
        System.out.println("[DEBUG] Permissões do arquivo: " + certFile.canRead() + ", " + certFile.canWrite());

        // Opcional: ler os primeiros bytes para garantir que não está corrompido
        byte[] headBytes = Files.readAllBytes(Paths.get(arquivo));
        byte[] firstBytes = new byte[Math.min(16, headBytes.length)];
        System.arraycopy(headBytes, 0, firstBytes, 0, firstBytes.length);
        String hexBytes = IntStream.range(0, firstBytes.length)
            .mapToObj(i -> String.format("%02X", firstBytes[i]))
            .collect(Collectors.joining(" "));
        System.out.println("[DEBUG] Primeiros bytes do arquivo: " + hexBytes);

        // Criar e configurar SSL Context Parameters para o Camel
        KeyStoreParameters kp = new KeyStoreParameters();
        kp.setResource("file:" + arquivo);
        kp.setType("PKCS12");
        kp.setPassword(senha);
        System.out.println("[DEBUG] KeyStoreParameters configurado: resource=file:" + arquivo + ", type=PKCS12, password=***");

        KeyManagersParameters km = new KeyManagersParameters();
        km.setKeyStore(kp);
        km.setKeyPassword(senha);
        System.out.println("[DEBUG] KeyManagersParameters configurado");

        SSLContextParameters ssl = new SSLContextParameters();
        ssl.setKeyManagers(km);
        System.out.println("[DEBUG] SSLContextParameters criado");

        String id = "camel-ssl-" + exchange.getExchangeId();
        exchange.getContext().getRegistry().bind(id, ssl);
        System.out.println("[DEBUG] SSL Context configurado e registrado com ID: " + id);

        return id;
    }
}
