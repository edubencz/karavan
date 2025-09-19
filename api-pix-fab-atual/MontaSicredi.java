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
                                Map<String, Object> dadosJuros,
                                Map<String, Object> dadosMulta,
                                Map<String, Object> dadosPagador,
                                Map<String, Object> dadosBeneficiario,
                                List<String> instrucoes,
                                List<String> mensagens,
                                Map<String, Object> dadosDescontos) throws Exception { 
        switch (tipo.toLowerCase()) {
            case "registrar":
                return montarEmissao(dadosBanco, dadosBoleto,  dadosJuros, dadosMulta, dadosPagador, dadosBeneficiario, instrucoes, mensagens, dadosDescontos);
            case "alterar":
                return montarAlteracao(dadosBanco, dadosBoleto, dadosJuros, dadosMulta, dadosPagador, dadosDescontos);
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
            pagador.put("cidade", enderecoPagador.get("cidade"));
            pagador.put("uf", enderecoPagador.get("uf"));
            pagador.put("cep", enderecoPagador.get("cep"));
            if (enderecoPagador.get("telefone") != null) {
                pagador.put("telefone", enderecoPagador.get("telefone"));
            }
            if (enderecoPagador.get("email") != null) {
                pagador.put("email", enderecoPagador.get("email"));
            }
            payload.put("pagador", pagador);

            /*
            DUPLICATA_MERCANTIL_INDICACAO, DUPLICATA_RURAL, NOTA_PROMISSORIA, NOTA_PROMISSORIA_RURAL,
            NOTA_SEGUROS, RECIBO, LETRA_CAMBIO, NOTA_DEBITO, DUPLICATA_SERVICO_INDICACAO, OUTROS,
            BOLETO_PROPOSTA, CARTAO_CREDITO, BOLETO_DEPOSITO
            */
            switch ((String) dadosBoleto.get("especieDocumento")) {
                case "CH":
                    payload.put("especieDocumento", "OUTROS");
                    break;
                case "DM":
                    payload.put("especieDocumento", "DUPLICATA_MERCANTIL_INDICACAO");
                    break;
                case "NP":
                    payload.put("especieDocumento", "NOTA_PROMISSORIA");
                    break;
                case "DS":
                    payload.put("especieDocumento", "DUPLICATA_SERVICO_INDICACAO");
                    break;
                case "ND":
                    payload.put("especieDocumento", "NOTA_DEBITO");
                    break;  
                default:
                    payload.put("especieDocumento", "OUTROS");
            }

            payload.put("especieDocumento", "DUPLICATA_MERCANTIL_INDICACAO");

            String nossoNumero = (String) dadosBoleto.get("numeroDocumento");
            payload.put("nossoNumero", String.format("%09d", Long.parseLong(nossoNumero)));
            payload.put("seuNumero", dadosBoleto.get("numeroDocumento"));
            payload.put("dataVencimento", dadosBoleto.get("dataVencimento"));

            //Protesto
            if (dadosBoleto.get("protestar") == "S") {
                payload.put("diasProtestoAuto", dadosBoleto.get("quantidadeDiasProtesto"));
            }

            //Negativação
            if (dadosBoleto.get("negativar") == "S") {
                payload.put("diasNegativacaoAuto", dadosBoleto.get("quantidadeDiasNegativacao"));
            }

            //Baixa automática
            Number validadeAposVencimento = (Number) dadosBoleto.get("numeroDiasLimiteRecebimento");
            if (validadeAposVencimento != null && validadeAposVencimento.doubleValue() > 0) {
                payload.put("validadeAposVencimento", validadeAposVencimento);
            }

            //Valor boleto
            payload.put("valor", dadosBoleto.get("valorTitulo"));

            //Descontos
            Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
            Number percentualDesconto = (Number) dadosDescontos.get("percentualDesconto");
            if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
                payload.put("tipoDesconto", "VALOR");
                payload.put("valorDesconto1", valorDesconto);
                payload.put("dataDesconto1", dadosBoleto.get("dataVencimento"));
            } 
            else if (percentualDesconto != null && percentualDesconto.doubleValue() > 0) {
                payload.put("tipoDesconto", "PERCENTUAL");
                payload.put("valorDesconto1", percentualDesconto);
                payload.put("dataDesconto1", dadosBoleto.get("dataVencimento"));
            }

            //Juros
            Number valorJuros = (Number) dadosJuros.get("valorJuros");
            Number percentualJuros = (Number) dadosJuros.get("percentualJuros");
            if (valorJuros != null && valorJuros.doubleValue() > 0) {
                payload.put("tipoJuros", "VALOR");
                payload.put("juros", valorJuros);
            } else {
                payload.put("tipoJuros", "PERCENTUAL");
                payload.put("juros", percentualJuros);
            }

            //Multa
            Number percentualMulta = (Number) dadosMulta.get("percentualMulta");
            Number valorMulta = (Number) dadosMulta.get("valorMulta");
            if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
                payload.put("tipoMulta", "PERCENTUAL");
                payload.put("multa", percentualMulta);
            } else {
                payload.put("tipoMulta", "VALOR");
                payload.put("multa", valorMulta);
            }

            //instrucoes
            if (instrucoes != null && !instrucoes.isEmpty()) {
                payload.put("informativo", instrucoes);
            }
            if (mensagens != null && !mensagens.isEmpty()) {
                payload.put("mensagens", mensagens);
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
                                    Map<String, Object> dadosBoleto,
                                    Map<String, Object> dadosJuros,
                                    Map<String, Object> dadosMulta,
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
