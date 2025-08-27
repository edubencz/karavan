package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaSicredi")
public class MontaSicredi {

    private ObjectMapper objectMapper = new ObjectMapper();

    public String tratarData(String data, String formato) throws Exception {
        if (data == null || data.isEmpty()) {
            return null;
        }
        SimpleDateFormat sdfEntrada = new SimpleDateFormat("yyyy-MM-dd");
        Date d = sdfEntrada.parse(data);
        return new SimpleDateFormat(formato).format(d);
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

        // Pagador
        Map<String, Object> pagador = new LinkedHashMap<>();
        if (dadosPagador.get("tipoPessoa").equals("F")) {
            pagador.put("tipoPessoa", "PESSOA_FISICA");
        } else {
            pagador.put("tipoPessoa", "PESSOA_JURIDICA");
        }
        pagador.put("documento", dadosPagador.get("documento"));
        pagador.put("nome", dadosPagador.get("nome"));
        pagador.put("endereco", enderecoPagador.get("logradouro"));
        //pagador.put("bairro", enderecoPagador.get("bairro"));
        pagador.put("cidade", enderecoPagador.get("cidade"));
        pagador.put("uf", enderecoPagador.get("uf"));
        pagador.put("cep", enderecoPagador.get("cep"));
        payload.put("pagador", pagador);

        payload.put("especieDocumento", "DUPLICATA_MERCANTIL_INDICACAO");
        payload.put("nossoNumero", dadosBoleto.get("numeroNossoNumero"));
        payload.put("seuNumero", dadosBoleto.get("numeroDocumento"));
        payload.put("dataVencimento", dadosBoleto.get("dataVencimento"));

        if (dadosBoleto.get("quantidadeDiasProtesto").val() > 0) {
            payload.put("diasProtestoAuto", (Number) dadosBoleto.get("quantidadeDiasProtesto"));
        }

        if (dadosBoleto.get("numeroDiasLimiteRecebimento").val() > 0)
        {
            payload.put("validadeAposVencimento", (Number) dadosBoleto.get("numeroDiasLimiteRecebimento"));
        }

        payload.put("valor", dadosBoleto.get("valorNominal"));
        if (dadosDescontos.get("valorDesconto").val() > 0) {
            payload.put("valorDesconto1", (Number) dadosDescontos.get("valorDesconto"));
            payload.put("dataDesconto1", dadosDescontos.get("dataVencimento"));
        }

        if (dadosBoleto.get("valorJuros").val() > 0) {
            payload.put("tipoJuros", "VALOR");
        } else {
            payload.put("tipoJuros", "PERCENTUAL");
        }
        payload.put("juros", (Number) dadosBoleto.get("valorJuros"));

        if (dadosBoleto.get("percentualMulta").val() > 0) {
            payload.put("multa", (Number) dadosBoleto.get("percentualMulta"));
        } else {
            payload.put("multa", (Number) dadosBoleto.get("valorMulta"));
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
        
        return objectMapper.writeValueAsString(payload);
    }

    public String montarCancelamento(Map<String, Object> dadosBanco,
                                   Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        return objectMapper.writeValueAsString(payload);
    }
}
