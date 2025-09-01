package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaSicredi")
public class MontaSicredi {

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

    public String montaSicredi(String tipo,
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
        
        // Campos obrigatórios conforme documentação BB
        payload.put("tipoCobranca", "HIBRIDO");
        payload.put("codigoBeneficiario", dadosBanco.get("convenio"));

        // Pagador
        Map<String, Object> pagador = new LinkedHashMap<>();
        if (dadosPagador.get("tipoPessoa").equals("F")) {
            pagador.put("tipoPessoa", "PESSOA_FISICA");
        } else {
            pagador.put("tipoPessoa", "PESSOA_JURIDICA");
        }
        pagador.put("documento", dadosPagador.get("documento"));
        pagador.put("nome", dadosPagador.get("nome"));
        
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        pagador.put("endereco", enderecoPagador.get("logradouro"));
        //pagador.put("bairro", enderecoPagador.get("bairro"));
        pagador.put("cidade", enderecoPagador.get("cidade"));
        pagador.put("uf", enderecoPagador.get("uf"));
        pagador.put("cep", enderecoPagador.get("cep"));
        payload.put("pagador", pagador);

        payload.put("especieDocumento", "DUPLICATA_MERCANTIL_INDICACAO");
        String nossoNumero = (String) dadosBoleto.get("numeroDocumento");
        payload.put("nossoNumero", String.format("%09d", Long.parseLong(nossoNumero)));
        payload.put("seuNumero", dadosBoleto.get("numeroDocumento"));
        payload.put("dataVencimento", dadosBoleto.get("dataVencimento"));

        Number quantidadeDiasProtesto = (Number) dadosBoleto.get("quantidadeDiasProtesto");
        if (quantidadeDiasProtesto != null && quantidadeDiasProtesto.doubleValue() > 0) {
            payload.put("diasProtestoAuto", quantidadeDiasProtesto);
        }

        Number numeroDiasLimiteRecebimento = (Number) dadosBoleto.get("numeroDiasLimiteRecebimento");
        if (numeroDiasLimiteRecebimento != null && numeroDiasLimiteRecebimento.doubleValue() > 0) {
            payload.put("validadeAposVencimento", numeroDiasLimiteRecebimento);
        }

        payload.put("valor", dadosBoleto.get("valorNominal"));
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            payload.put("valorDesconto1", valorDesconto);
            payload.put("dataDesconto1", dadosBoleto.get("dataVencimento"));
        }

        Number valorJuros = (Number) dadosBoleto.get("valorJuros");
        Number percentualJuros = (Number) dadosBoleto.get("percentualJuros");
        if (valorJuros != null && valorJuros.doubleValue() > 0) {
            payload.put("tipoJuros", "VALOR");
            payload.put("juros", valorJuros);
        } else {
            payload.put("tipoJuros", "PERCENTUAL");
            payload.put("juros", percentualJuros);
        }

        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        Number valorMulta = (Number) dadosBoleto.get("valorMulta");
        if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
            payload.put("multa", percentualMulta);
        } else {
            payload.put("multa", valorMulta);
        }

        //instrucoes
        if (instrucoes != null && !instrucoes.isEmpty()) {
            payload.put("instrucoes", instrucoes);
        }
        if (mensagens != null && !mensagens.isEmpty()) {
            payload.put("mensagens", mensagens);
        }
        

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
