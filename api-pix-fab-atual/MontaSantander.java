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
                                Map<String, Object> dadosJuros,
                                Map<String, Object> dadosMulta,
                                Map<String, Object> dadosPagador,
                                Map<String, Object> dadosBeneficiario,
                                List<String> instrucoes,
                                List<String> mensagens,
                                Map<String, Object> dadosDescontos) throws Exception {
        switch (tipo.toLowerCase()) {
            case "registrar":
                return montarEmissao(dadosBanco, dadosBoleto, dadosJuros, dadosMulta, dadosPagador, dadosBeneficiario, instrucoes, mensagens, dadosDescontos);
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
                                Map<String, Object> dadosJuros,
                                Map<String, Object> dadosMulta,
                                Map<String, Object> dadosPagador,
                                Map<String, Object> dadosBeneficiario,
                                List<String> instrucoes,
                                List<String> mensagens,
                                Map<String, Object> dadosDescontos) throws Exception {
        
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            
            // Campos obrigatórios conforme documentação Santander
            payload.put("environment", "SANDBOX");
            //payload.put("nsuCode", UUID.randomUUID().toString());
            payload.put("nsuCode", dadosBoleto.get("numeroDocumento"));
            payload.put("nsuDate", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            payload.put("covenantCode", dadosBanco.get("convenio"));

            //Dados Pagador        
            Map<String, Object> payer = new LinkedHashMap<>();
            if (dadosPagador.get("tipoPessoa") != null && dadosPagador.get("tipoPessoa").equals("J")) {
                payer.put("payerType", "CNPJ");
            } else {
                payer.put("payerType", "CPF");
            }
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
            payload.put("nominalValue", dadosBoleto.get("valorTitulo"));

            /*
            DUPLICATA_MERCANTIL, DUPLICATA_SERVICO, DUPLICATA_MERCANTIL_POR_INDICACAO, NOTA_PROMISSORIA,
            NOTA_PROMISSORIA_RURAL, RECIBO, APOLICE_SEGURO, BOLETO_CARTAO_CREDITO, BOLETO_PROPOSTA,
            BOLETO_DEPOSITO_APORTE, CHEQUE, NOTA_PROMISSORIA_DIRETA, OUTROS
            */
            switch ((String) dadosBoleto.get("especieDocumento")) {
                case "CH":
                    payload.put("documentKind", "CHEQUE");
                    break;
                case "DM":
                    payload.put("documentKind", "DUPLICATA MERCANTIL");
                    break;
                case "DS":
                    payload.put("documentKind", "DUPLICATA MTIL POR INDICACAO");
                    break;
                case "NF":
                    payload.put("documentKind", "NOTA FISCAL");
                    break;
                case "ND":
                    payload.put("documentKind", "NOTA DE DEBITO");
                    break;
                default:
                    payload.put("documentKind", "OUTROS");
            }

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

            //Multa e Juros
            Number percentualJuros = (Number) dadosJuros.get("percentualJuros");
            Number percentualMulta = (Number) dadosMulta.get("percentualMulta");
            if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
                payload.put("finePercentage", percentualMulta);
                payload.put("fineQuantityDays", 1); //1 dia após o vencimento
                payload.put("interestPercentage", percentualJuros);
            }

            // Mensagens
            if (mensagens != null) {
                payload.put("messages", mensagens);
            } else {
                payload.put("messages", new ArrayList<String>());
            }
            return objectMapper.writeValueAsString(payload);
        }
        catch (Exception e) {
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("payload", true);
            erro.put("mensagem", e.getMessage());
            //erro.put("classeErro", e.getClass().getName());
            try {
                return objectMapper.writeValueAsString(erro);
            } catch (Exception ex) {
                erro.put("payload", true);
                erro.put("mensagem", "Erro desconhecido ao serializar exceção");
                return objectMapper.writeValueAsString(erro);
            }
        }
    }

    public String montarAlteracao(Map<String, Object> dadosBanco,
                                 Map<String, Object> dadosBoleto) throws Exception {
        
        try {
            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("covenantCode", dadosBanco.get("convenio"));
            payload.put("bankNumber", dadosBoleto.get("numeroNossoNumero"));


            if (dadosBoleto.get("alteraSaldo") == "S") {
                if (dadosBoleto.get("tipoMovimento") == "credito") {
                    Map<String, Object> erro = new LinkedHashMap<>();
                    erro.put("regra", true);
                    erro.put("mensagem", "Operação de crédito não é suportada para o Santander.");
                    return objectMapper.writeValueAsString(erro);
                }
                else {
                    payload.put("type", "VALOR_DATA_FIXA");
                    
                    Map<String, Object> discountOne = new LinkedHashMap<>();
                    Number valorMovimento = (Number) dadosBoleto.get("valorMovimento");
                    discountOne.put("value", valorMovimento);
                    discountOne.put("limitDate", dadosBoleto.get("dataVencimento"));
                    payload.put("discount", discountOne);
                }

            } else if (dadosBoleto.get("alteraVencimento") == "S") {
                payload.put("dueDate", dadosBoleto.get("dataVencimento"));
            }

            return objectMapper.writeValueAsString(payload);
        } 
        catch (Exception e) {
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("payload", true);
            erro.put("mensagem", e.getMessage());
            try {
                return objectMapper.writeValueAsString(erro);
            } catch (Exception ex) {
                erro.put("payload", true);
                erro.put("mensagem", "Erro montar alteração para o Santander");
                return objectMapper.writeValueAsString(erro);
            }
        }
    }

    public String montarCancelamento(Map<String, Object> dadosBanco,
                                   Map<String, Object> dadosBoleto) throws Exception {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("covenantCode", dadosBanco.get("convenio"));
            payload.put("bankNumber", dadosBoleto.get("numeroNossoNumero"));
            payload.put("operation", "BAIXAR");

            return objectMapper.writeValueAsString(payload);
        } 
        catch (Exception e) {
            Map<String, Object> erro = new LinkedHashMap<>();
            erro.put("payload", true);
            erro.put("mensagem", e.getMessage());
            try {
                return objectMapper.writeValueAsString(erro);
            } catch (Exception ex) {
                erro.put("payload", true);
                erro.put("mensagem", "Erro montar cancelamento para o Santander");
                return objectMapper.writeValueAsString(erro);
            }
        }
    }
}
