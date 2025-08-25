package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@BindToRegistry("montaPayload")
public class MontaPayload {

    private MontaBB montaBB = new MontaBB();
    private MontaSantander montaSantander = new MontaSantander();
    private MontaItau montaItau = new MontaItau();
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

    @SuppressWarnings("unchecked")
    public String montarPayload(String jsonString) throws Exception {
        Map<String, Object> jsonCompleto = objectMapper.readValue(jsonString, Map.class);
        return montarPayload(jsonCompleto);
    }

    @SuppressWarnings("unchecked")
    public String montarPayload(Map<String, Object> jsonCompleto) throws Exception {
        
        // Extrair objetos do JSON principal
        Map<String, Object> rota = getNestedMap(jsonCompleto, "rota");
        Map<String, Object> dadosBoleto = getNestedMap(jsonCompleto, "dadosBoleto");
        Map<String, Object> dadosBanco = getNestedMap(jsonCompleto, "dadosBanco");
        Map<String, Object> pagador = getNestedMap(jsonCompleto, "pagador");
        Map<String, Object> beneficiario = getNestedMap(jsonCompleto, "beneficiario");
        Map<String, Object> descontos = getNestedMap(jsonCompleto, "descontos");
        
        // Extrair arrays
        List<String> instrucoes = getStringList(jsonCompleto, "instrucoes");
        List<String> mensagensOriginais = getStringList(jsonCompleto, "mensagens");
        List<String> mensagens = new ArrayList<>();
        if (mensagensOriginais != null) {
            for (String msg : mensagensOriginais) {
                if (msg != null && !msg.trim().isEmpty() && !msg.matches("\\[.*@.*")) {
                    mensagens.add(msg);
                }
            }
        }

        // Determinar banco baseado no código do banco
        Number codigoBanco = (Number) rota.get("codigoBanco");
        String tipo = (String) rota.get("tipo");
        
        if (codigoBanco == null) {
            Map<String, Object> erro = new HashMap<>();
            erro.put("erro", "Código do banco não informado");
            return objectMapper.writeValueAsString(erro);
        }
        
        if (tipo == null || tipo.isEmpty()) {
            Map<String, Object> erro = new HashMap<>();
            erro.put("erro", "Tipo de operação não informado");
            return objectMapper.writeValueAsString(erro);
        }

        // Por enquanto só temos BB implementado
        if (codigoBanco.intValue() == 1) { // Banco do Brasil
            return processarBB(tipo, dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
        } 
        else if (codigoBanco.intValue() == 33) { // Santander
            return processarSantander(tipo, dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
        }
        else if (codigoBanco.intValue() == 341) { // Itau
            return processarItau(tipo, dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
        }
        else {
            Map<String, Object> erro = new HashMap<>();
            erro.put("erro", "Banco não suportado: " + codigoBanco);
            return objectMapper.writeValueAsString(erro);
        }
    }

    private String processarBB(String tipo, 
                              Map<String, Object> dadosBanco,
                              Map<String, Object> dadosBoleto,
                              Map<String, Object> pagador,
                              Map<String, Object> beneficiario,
                              List<String> instrucoes,
                              List<String> mensagens,
                              Map<String, Object> descontos) throws Exception {
        
        switch (tipo.toLowerCase()) {
            case "registrar":
                return montaBB.montarEmissao(dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
            case "alterar":
                return montaBB.montarAlteracao(dadosBanco, dadosBoleto);
            case "cancelamento":
            case "cancelar":
                return montaBB.montarCancelamento(dadosBanco, dadosBoleto);
            default:
                Map<String, Object> erro = new HashMap<>();
                erro.put("erro", "Tipo de operação não suportado: " + tipo);
                return objectMapper.writeValueAsString(erro);
        }
    }

    private String processarSantander(String tipo, 
                              Map<String, Object> dadosBanco,
                              Map<String, Object> dadosBoleto,
                              Map<String, Object> pagador,
                              Map<String, Object> beneficiario,
                              List<String> instrucoes,
                              List<String> mensagens,
                              Map<String, Object> descontos) throws Exception {
        
        switch (tipo.toLowerCase()) {
            case "registrar":
                return montaSantander.montarEmissao(dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
            case "alterar":
                return montaSantander.montarAlteracao(dadosBanco, dadosBoleto);
            case "cancelamento":
            case "cancelar":
                return montaSantander.montarCancelamento(dadosBanco, dadosBoleto);
            default:
                Map<String, Object> erro = new HashMap<>();
                erro.put("erro", "Tipo de operação não suportado: " + tipo);
                return objectMapper.writeValueAsString(erro);
        }
    }

    private String processarItau(String tipo, 
                              Map<String, Object> dadosBanco,
                              Map<String, Object> dadosBoleto,
                              Map<String, Object> pagador,
                              Map<String, Object> beneficiario,
                              List<String> instrucoes,
                              List<String> mensagens,
                              Map<String, Object> descontos) throws Exception {
        
        switch (tipo.toLowerCase()) {
            case "registrar":
                return montaItau.montarEmissao(dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
            case "alterar":
                return montaItau.montarAlteracao(dadosBanco, dadosBoleto);
            case "cancelamento":
            case "cancelar":
                return montaItau.montarCancelamento(dadosBanco, dadosBoleto);
            default:
                Map<String, Object> erro = new HashMap<>();
                erro.put("erro", "Tipo de operação não suportado: " + tipo);
                return objectMapper.writeValueAsString(erro);
        }
    }    

}
