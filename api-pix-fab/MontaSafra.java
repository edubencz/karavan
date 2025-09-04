package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaSafra")
public class MontaSafra {

    private ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> getNestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Falha ao converter valor para Integer: " + value, e);
            }
        } else {
            throw new RuntimeException("Tipo de dado inesperado para conversão: " + value);
        }
    }

    public String montaSafra(String tipo,
                                Map<String, Object> dadosBanco,
                                Map<String, Object> dadosBoleto,
                                Map<String, Object> dadosPagador,
                                Map<String, Object> dadosBeneficiario,
                                List<String> instrucoes,
                                List<String> mensagens,
                                Map<String, Object> dadosDescontos) throws Exception {
        switch (tipo.toLowerCase()) {
            case "registrar":
                return montarEmissao(dadosBanco, dadosBoleto, dadosPagador, dadosBeneficiario, instrucoes, mensagens, dadosDescontos);
            case "alterar":
                return montarAlteracao(dadosBanco, dadosBoleto, dadosPagador, dadosDescontos);
            case "cancelamento":
            case "cancelar":
                return montarCancelamento(dadosBanco, dadosBoleto);
            default:
                Map<String, Object> erro = new HashMap<>();
                erro.put("erro", "Tipo de operação não suportado: " + tipo);
                return objectMapper.writeValueAsString(erro);
        }

    }
    public String montarEmissao(Map<String, Object> dadosBanco,
                               Map<String, Object> dadosBoleto,
                               Map<String, Object> dadosPagador,
                               Map<String, Object> dadosBeneficiario,
                               List<String> instrucoes,
                               List<String> mensagens,
                               Map<String, Object> dadosDescontos) throws Exception {
        
        Map<String, Object> payload = new LinkedHashMap<>();
        
        // Campos obrigatórios
        Integer agencia = parseInteger(dadosBanco.get("agencia"));
        Integer conta = parseInteger(dadosBanco.get("conta"));
        payload.put("agencia", agencia);
        payload.put("conta", conta);

        Map<String, Object> documento = new LinkedHashMap<>();
        documento.put("numero", dadosBoleto.get("numeroDocumento"));
        documento.put("numeroCliente", dadosBoleto.get("numeroDocumento"));
        //documento.put("diasDevolucao", dadosBoleto.get("diasDevolucao"));
        documento.put("especie", 1); // 1 DM
        documento.put("dataVencimento", dadosBoleto.get("dataVencimento"));
        documento.put("valor", dadosBoleto.get("valorNominal"));
        documento.put("codigoMoeda", 0); //0 Fixo Layout

        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("tipoPessoa", dadosPagador.get("tipoPessoa"));
        pagador.put("numeroDocumento", dadosPagador.get("documento"));
        pagador.put("nome", dadosPagador.get("nome"));

        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        Map<String, Object> endereco = new LinkedHashMap<>();
        String logradouro = String.valueOf(enderecoPagador.get("logradouro"));
        if (logradouro.length() > 40) {
            logradouro = logradouro.substring(0, 40);
        }
        endereco.put("logradouro", logradouro);
        endereco.put("bairro", enderecoPagador.get("bairro"));
        endereco.put("cidade", enderecoPagador.get("cidade"));
        endereco.put("uf", enderecoPagador.get("uf"));
        endereco.put("cep", enderecoPagador.get("cep"));
        pagador.put("endereco", endereco);

        documento.put("pagador", pagador);
        /*
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        pagador.put("endereco", enderecoPagador.get("logradouro"));
        //pagador.put("bairro", enderecoPagador.get("bairro"));
        pagador.put("cidade", enderecoPagador.get("cidade"));
        pagador.put("uf", enderecoPagador.get("uf"));
        pagador.put("cep", enderecoPagador.get("cep"));
        payload.put("pagador", pagador);
        */

        payload.put("documento", documento);

        return objectMapper.writeValueAsString(payload);
    }

    public String montarAlteracao(Map<String, Object> dadosBanco,
                                 Map<String, Object> dadosBoleto,
                                 Map<String, Object> dadosPagador,
                                 Map<String, Object> dadosDescontos) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        
        return "";//objectMapper.writeValueAsString(payload);
    }

    public String montarCancelamento(Map<String, Object> dadosBanco,
                                   Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        return objectMapper.writeValueAsString(payload);
    }
}
