package api.pix.safra;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.apache.camel.BindToRegistry;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@BindToRegistry("safraQrCode")
public class SafraQrCode {

    private ObjectMapper objectMapper = new ObjectMapper();

    // Função principal
    public String processarBody(String jsonString) throws Exception {
        if (jsonString == null || jsonString.isEmpty()) {
            throw new IllegalArgumentException("Input JSON string cannot be null or empty");
        }

        /*
        {
            "codigoBarras": "42291122000000509847124000085544400002868292",
            "agencia": "12400",
            "conta": "8554440",
            "dataVencimento": "2025-09-30",
            "valorTitulo": "509.84",
            "numeroNossoNumero": "286815",
            "nome": "UNIMED DO ESTADO DE STA CATARINA FEDERACAO ESTADUAL DAS COOP MEDICAS",
            "cidade": "JOINVILLE",
            "type": "HML"
        }
        */
        // Parse do JSON recebido
        Map<String, Object> body = objectMapper.readValue(jsonString, Map.class);

        // Extração dos campos necessários com logs
        String tipoSafra = (String) body.get("type");
        String agencia = (String) body.get("agencia");
        String conta = (String) body.get("conta");
        String nomeBeneficiario = (String) body.get("nome");
        String cidadeBeneficiario = (String) body.get("cidade");
        String dataVencimento = (String) body.get("dataVencimento");
        String valor = (String) body.get("valorTitulo");
        String nossoNumero = (String) body.get("numeroNossoNumero");

        // Gerar linha digitável a partir do código de barras
        String codigoBarras = (String) body.get("codigoBarras");
        if (codigoBarras == null || codigoBarras.length() != 44) {
            throw new IllegalArgumentException("Código de barras inválido ou ausente.");
        }
        String linhaDigitavel = gerarLinhaDigitavel(codigoBarras);

        // Validação dos campos obrigatórios
        if (tipoSafra == null || agencia == null || conta == null || nomeBeneficiario == null || cidadeBeneficiario == null || dataVencimento == null || valor == null || nossoNumero == null) {
            throw new IllegalArgumentException("Um ou mais campos obrigatórios estão ausentes ou nulos.");
        }

        // Geração do QR Code
        String qrCode = gerarPixQrCode(tipoSafra, agencia, conta, linhaDigitavel, nomeBeneficiario, cidadeBeneficiario);

        // Montagem do novo body
        Map<String, String> novoBody = new HashMap<>();
        novoBody.put("linhaDigitavel", linhaDigitavel);
        novoBody.put("codigoBarras", codigoBarras);
        novoBody.put("qrCode", qrCode);

        return objectMapper.writeValueAsString(novoBody);
    }

    private String gerarPixQrCode(String tipoSafra, String agencia, String conta, String linhaDigitavel, String nomeBeneficiario, String cidadeBeneficiario) {
        // Campo 1: Texto fixo
        String campo1 = "000201010212";

        // Campo 2: Merchant Account Information
        String gui = "br.gov.bcb.pix";
        String url = tipoSafra != null && tipoSafra.equalsIgnoreCase("HML") ? "pix-h.safra.com.br/qr/c/cobv/" : "pix.safra.com.br/qr/c/cobv/";

        // Formatar TXID conforme o manual
        String formattedAgency = String.format("%5s", agencia).replace(' ', '0');
        String formattedAccount = String.format("%9s", conta).replace(' ', '0');
        String formattedNossoNumero = String.format("%9s", linhaDigitavel).replace(' ', '0');
        String txid = "0" + "7" + formattedAgency + formattedAccount + formattedNossoNumero + "2";

        // Calcular tamanho do campo URL
        int urlLength = url.length() + txid.length();
        String campo2 = "26" + (tipoSafra != null && tipoSafra.equalsIgnoreCase("hml") ? "77" : "75") + "0014" + gui + "25" + String.format("%02d", urlLength) + url + txid;

        // Campo 3: Categoria do Merchant
        String campo3 = "52040000";

        // Campo 4: Moeda
        String campo4 = "5303986";

        // Campo 5: País
        String campo5 = "5802BR";

        // Campo 6: Nome do Merchant
        String truncatedMerchantName = nomeBeneficiario.length() > 25 ? nomeBeneficiario.substring(0, 25) : nomeBeneficiario;
        String campo6 = "59" + String.format("%02d", truncatedMerchantName.length()) + truncatedMerchantName;

        // Campo 7: Cidade do Merchant
        String truncatedMerchantCity = cidadeBeneficiario.length() > 15 ? cidadeBeneficiario.substring(0, 15) : cidadeBeneficiario;
        String campo7 = "60" + String.format("%02d", truncatedMerchantCity.length()) + truncatedMerchantCity;

        // Campo 8: Additional Data Field
        String campo8 = "62070503***";

        // Montagem do QR Code sem o CRC16
        String qrCode = campo1 + campo2 + campo3 + campo4 + campo5 + campo6 + campo7 + campo8;

        // Campo 9: CRC16
        String crc16 = calculateCRC16(qrCode);
        String campo9 = "6304" + crc16;

        // QR Code completo
        return qrCode + campo9;
    }

    private int calcularFatorVencimento(String dataVencimento) {
        // Data base para cálculo do fator de vencimento (07/10/1997)
        LocalDate dataBase = LocalDate.of(1997, 10, 7);
        LocalDate dataTitulo = LocalDate.parse(dataVencimento);
        return (int) ChronoUnit.DAYS.between(dataBase, dataTitulo);
    }

    private static String calculateCRC16(String str) {
        int crc = 0xFFFF;
        for (int i = 0; i < str.length(); i++) {
            crc ^= str.charAt(i) << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
            }
        }
        crc &= 0xFFFF;
        return String.format(Locale.getDefault(), "%04X", crc).toUpperCase();
    }

    private static char calculateDAC(String barcode) {
        int sum = 0;
        int weight = 2;

        // Percorrer os caracteres do código de barras da direita para a esquerda
        for (int i = barcode.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(barcode.charAt(i));
            sum += digit * weight;
            weight = (weight == 9) ? 2 : weight + 1; // Ciclo de pesos de 2 a 9
        }

        int remainder = sum % 11;
        if (remainder == 0 || remainder == 1 || remainder == 10) {
            return '1'; // DAC é 1 nesses casos
        }
        return (char) ('0' + (11 - remainder));
    }

    private String gerarLinhaDigitavel(String codigoBarras) {
        if (codigoBarras == null || codigoBarras.length() != 44) {
            throw new IllegalArgumentException("Código de barras inválido. Deve conter exatamente 44 dígitos.");
        }

        // 1º Campo: Código do banco, moeda, 5 primeiras posições do campo livre e DV
        String campo1 = codigoBarras.substring(0, 4) + codigoBarras.substring(19, 24);
        campo1 += calcularDVM10(campo1);
        campo1 = campo1.substring(0, 5) + "." + campo1.substring(5);

        // 2º Campo: Posições 6 a 15 do campo livre e DV
        String campo2 = codigoBarras.substring(24, 34);
        campo2 += calcularDVM10(campo2);
        campo2 = campo2.substring(0, 5) + "." + campo2.substring(5);

        // 3º Campo: Posições 16 a 25 do campo livre e DV
        String campo3 = codigoBarras.substring(34, 44);
        campo3 += calcularDVM10(campo3);
        campo3 = campo3.substring(0, 5) + "." + campo3.substring(5);

        // 4º Campo: DAC do código de barras
        String campo4 = codigoBarras.substring(4, 5);

        // 5º Campo: Fator de vencimento e valor nominal
        String campo5 = codigoBarras.substring(5, 19);

        // Montar linha digitável
        return campo1 + " " + campo2 + " " + campo3 + " " + campo4 + " " + campo5;
    }

    private int calcularDVM10(String campo) {
        int soma = 0;
        int peso = 2;

        // Percorrer os dígitos da direita para a esquerda
        for (int i = campo.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(campo.charAt(i));
            int resultado = digito * peso;
            soma += (resultado > 9) ? resultado - 9 : resultado;
            peso = (peso == 2) ? 1 : 2;
        }

        int resto = soma % 10;
        return (resto == 0) ? 0 : 10 - resto;
    }
}