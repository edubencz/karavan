package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@BindToRegistry("montaPayload")
public class MontaPayload {

    private MontaBB montaBB = new MontaBB();
    private MontaSantander montaSantander = new MontaSantander();
    private MontaItau montaItau = new MontaItau();
    private MontaSicredi montaSicredi = new MontaSicredi();
    private MontaUnicred montaUnicred = new MontaUnicred();
    private MontaSicoob montaSicoob = new MontaSicoob();
    private MontaSafra montaSafra = new MontaSafra();
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
        Map<String, Object> dadosMulta = getNestedMap(jsonCompleto, "dadosMulta");
        Map<String, Object> dadosJuros = getNestedMap(jsonCompleto, "dadosJuros");
        Map<String, Object> pagador = getNestedMap(jsonCompleto, "pagador");
        Map<String, Object> beneficiario = getNestedMap(jsonCompleto, "beneficiario");
        Map<String, Object> dadosDesconto = getNestedMap(jsonCompleto, "descontos");
        
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

        if (codigoBanco.intValue() == 1) { // Banco do Brasil
            return montaBB.montaBB(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        } 
        else if (codigoBanco.intValue() == 33) { // Santander
            return montaSantander.montaSantander(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        }
        else if (codigoBanco.intValue() == 748) { // Sicredi
            return montaSicredi.montaSicredi(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        }
        else if (codigoBanco.intValue() == 136) { // Unicred
            return montaUnicred.montaUnicred(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        }
        else if (codigoBanco.intValue() == 756) { // Sicoob
            return montaSicoob.montaSicoob(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        }
        /*
        else if (codigoBanco.intValue() == 341) { // Itau
            return montaItau.montaItau(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        }
        else if (codigoBanco.intValue() == 422) { // Safra
            return montaSafra.montaSafra(tipo, dadosBanco, dadosBoleto, dadosJuros, dadosMulta, pagador, beneficiario, instrucoes, mensagens, dadosDesconto);
        }
        */
        else {
            throw new UnsupportedOperationException("Banco não suportado: " + codigoBanco);
        }
    }
}
