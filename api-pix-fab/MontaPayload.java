package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import java.util.*;

@BindToRegistry("montaPayload")
public class MontaPayload {

    private MontaBB montaBB = new MontaBB();

    @SuppressWarnings("unchecked")
    public Map<String, Object> montarPayload(Map<String, Object> jsonCompleto) throws Exception {
        
        // Extrair objetos do JSON principal
        Map<String, Object> rota = getNestedMap(jsonCompleto, "rota");
        Map<String, Object> dadosBoleto = getNestedMap(jsonCompleto, "dadosBoleto");
        Map<String, Object> dadosBanco = getNestedMap(jsonCompleto, "dadosBanco");
        Map<String, Object> pagador = getNestedMap(jsonCompleto, "pagador");
        Map<String, Object> beneficiario = getNestedMap(jsonCompleto, "beneficiario");
        Map<String, Object> descontos = getNestedMap(jsonCompleto, "descontos");
        
        // Extrair arrays
        List<String> instrucoes = getStringList(jsonCompleto, "instrucoes");
        List<String> mensagens = getStringList(jsonCompleto, "mensagens");

        // Determinar banco baseado no código do banco
        Number codigoBanco = (Number) rota.get("codigoBanco");
        String tipo = (String) rota.get("tipo");
        
        if (codigoBanco == null) {
            throw new IllegalArgumentException("{ \"erro\": \"Código do banco não informado\" }");
        }
        
        if (tipo == null || tipo.isEmpty()) {
            throw new IllegalArgumentException("{ \"erro\": \"Tipo de operação não informado\" }");
        }

        // Por enquanto só temos BB implementado
        if (codigoBanco.intValue() == 1) { // Banco do Brasil
            return processarBB(tipo, dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
        } else {
            throw new IllegalArgumentException("{ \"erro\": \"Banco não suportado: " + codigoBanco + "\" }");
        }
    }

    private Map<String, Object> processarBB(String tipo, 
                                          Map<String, Object> dadosBanco,
                                          Map<String, Object> dadosBoleto,
                                          Map<String, Object> pagador,
                                          Map<String, Object> beneficiario,
                                          List<String> instrucoes,
                                          List<String> mensagens,
                                          Map<String, Object> descontos) throws Exception {
        
        switch (tipo.toLowerCase()) {
            case "inclusao":
                return montaBB.montarEmissao(dadosBanco, dadosBoleto, pagador, beneficiario, instrucoes, mensagens, descontos);
            case "alteracao":
                return montaBB.montarAlteracao(dadosBanco, dadosBoleto);
            case "cancelamento":
            case "cancelar":
                return montaBB.montarCancelamento(dadosBanco, dadosBoleto);
            default:
                throw new IllegalArgumentException("{ \"erro\": \"Tipo de operação não suportado: " + tipo + "\" }");
        }
    }

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
}
