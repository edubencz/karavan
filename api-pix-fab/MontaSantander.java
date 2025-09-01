package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("MontaSantander")
public class MontaSantander {

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

    public String montaSantander(String tipo,
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
                return montarAlteracao(dadosBanco, dadosBoleto);
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
        
        // Campos obrigatórios conforme documentação Santander
        payload.put("environment", "SANDBOX");
        //payload.put("nsuCode", UUID.randomUUID().toString());
        payload.put("nsuCode", dadosBoleto.get("numeroDocumento"));
        payload.put("nsuDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        payload.put("covenantCode", dadosBanco.get("convenio"));

        //Dados Pagador        
        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("documentType", dadosPagador.get("tipoPessoa"));
        payer.put("documentNumber", dadosPagador.get("documento"));
        payer.put("name", dadosPagador.get("nome"));
        
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        payer.put("address", enderecoPagador.get("logradouro"));
        payer.put("neighborhood", enderecoPagador.get("bairro"));
        payer.put("city", enderecoPagador.get("cidade"));
        payer.put("state", enderecoPagador.get("uf"));
        payer.put("zipCode", enderecoPagador.get("cep"));
        payload.put("payer", payer);

        payload.put("bankNumber", dadosBoleto.get("numeroNossoNumero"));
        payload.put("dueDate", dadosBoleto.get("dataVencimento"));
        payload.put("issueDate", dadosBoleto.get("dataEmissao"));
        payload.put("nominalValue", dadosBoleto.get("valorNominal"));
        payload.put("documentKind", dadosBoleto.get("tipoDoc"));

        //Campo opicional, mas enviado sempre para seguir o CADASTRO_CONVENIO entre Unimed e Banco
        payload.put("protestType", "CADASTRO_CONVENIO");
        payload.put("paymentType", "REGISTRO");

        // Campos opcionais
        //Descontos
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            Map<String, Object> discount = new LinkedHashMap<>();
            discount.put("type", "VALOR_DATA_FIXA");

            //Primeiro desconto
            Map<String, Object> discountOne = new LinkedHashMap<>();
            discountOne.put("value", valorDesconto);
            discount.put("limitDate", dadosBoleto.get("dataVencimento"));

            discount.put("discountOne", discountOne);
            payload.put("discount", discount);
        }

        //Juros
        Number valorJuros = (Number) dadosDescontos.get("valorJuros");
        if (valorJuros != null && valorJuros.doubleValue() > 0) {
            payload.put("finePercentage", dadosBoleto.get("percentualJuros"));
            payload.put("fineQuantityDays", (Number) 1);
        }

        // Mensagens
        if (mensagens != null) {
            payload.put("messages", mensagens);
        } else {
            payload.put("messages", new ArrayList<String>());
        }
        return objectMapper.writeValueAsString(payload);
    }

    public String montarAlteracao(Map<String, Object> dadosBanco,
                                 Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        payload.put("numeroDocumento", dadosBoleto.get("numeroDocumento"));
        return objectMapper.writeValueAsString(payload);
    }

    public String montarCancelamento(Map<String, Object> dadosBanco,
                                   Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        /*
        "covenantCode": "3567206",
        "bankNumber": "123",
        "operation": "BAIXAR"
        */
        payload.put("covenantCode", dadosBanco.get("convenio"));
        payload.put("bankNumber", dadosBanco.get("codigoBanco"));
        payload.put("operation", "BAIXAR");
        return objectMapper.writeValueAsString(payload);
    }
}
