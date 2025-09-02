package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaSicoob")
public class MontaSicoob {

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

    public String montaSicoob(String tipo,
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
        Integer carteira = parseInteger(dadosBanco.get("carteira"));
        Integer conta = parseInteger(dadosBanco.get("conta"));
        Integer nossoNumero = parseInteger(dadosBoleto.get("numeroDocumento"));
        Integer identificacaoEmissaoBoleto = 1;
        payload.put("numeroCliente", carteira);
        payload.put("codigoModalidade", 1); // Fixo 1 SIMPLES COM REGISTRO
        //payload.put("numeroContaCorrente", conta); //Para testes fixado 0
        payload.put("numeroContaCorrente", 0);
        payload.put("codigoEspecieDocumento", "DM");
        payload.put("dataEmissao", dadosBoleto.get("dataEmissao"));
        payload.put("nossoNumero", nossoNumero);
        payload.put("seuNumero", dadosBoleto.get("numeroDocumento"));
        payload.put("identificacaoEmissaoBoleto", identificacaoEmissaoBoleto);
        payload.put("identificacaoDistribuicaoBoleto", identificacaoEmissaoBoleto);
        payload.put("valor", dadosBoleto.get("valorNominal"));
        payload.put("dataVencimento", dadosBoleto.get("dataVencimento"));

        //Descontos
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            payload.put("tipoDesconto", 1); //Fixo até a data informada
            payload.put("dataPrimeiroDesconto", dadosBoleto.get("dataVencimento"));
            payload.put("valorPrimeiroDesconto", dadosDescontos.get("valorDesconto"));
        }

        //Multa
        Number valorMulta = (Number) dadosBoleto.get("valorMulta");
        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        if (valorMulta != null && valorMulta.doubleValue() > 0) {
            payload.put("tipoMulta", 1); //Valor fixo
            payload.put("dataMulta", dadosBoleto.get("dataMulta"));
            payload.put("valorMulta", valorMulta);
        }
        else if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
            payload.put("tipoMulta", 2); //Percentual
            payload.put("dataMulta", dadosBoleto.get("dataMulta"));
            payload.put("valorMulta", percentualMulta);
        }

        //Juros
        Number valorJuros = (Number) dadosBoleto.get("valorJuros");
        Number percentualJuros = (Number) dadosBoleto.get("percentualJuros");
        if (valorJuros != null && valorJuros.doubleValue() > 0) {
            payload.put("tipoJurosMora", 1); //Valor diario
            payload.put("dataJurosMora", dadosBoleto.get("dataMulta"));
            payload.put("valorJurosMora", valorJuros);
        }
        else if (percentualJuros != null && percentualJuros.doubleValue() > 0) {
            payload.put("tipoJurosMora", 2); //Percentual ao mês
            payload.put("dataJurosMora", dadosBoleto.get("dataMulta"));
            payload.put("valorJurosMora", percentualJuros);
        }

        payload.put("numeroParcela", 1); //Numero da parcela, fixado 1

        //Pagador
        Map<String, Object> pagador = new LinkedHashMap<>();
        Map<String, Object> endereco = getNestedMap(dadosPagador, "endereco");
        pagador.put("numeroCpfCnpj", dadosPagador.get("documento"));
        pagador.put("nome", dadosPagador.get("nome"));
        pagador.put("endereco", endereco.get("logradouro"));
        pagador.put("bairro", endereco.get("bairro"));
        pagador.put("cidade", endereco.get("cidade"));
        pagador.put("uf", endereco.get("uf"));
        pagador.put("cep", endereco.get("cep"));
        payload.put("pagador", pagador);

        // Mensagens
        if (mensagens != null) {
            payload.put("mensagensInstrucao", mensagens);
        } else {
            payload.put("mensagensInstrucao", new ArrayList<String>());
        }

        payload.put("codigoCadastrarPIX", 1); //Com pix

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
