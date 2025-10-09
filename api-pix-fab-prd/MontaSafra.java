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
            
            // Campos obrigatórios
            Integer agencia = parseInteger(dadosBanco.get("agencia"));
            Integer conta = parseInteger(dadosBanco.get("conta"));
            payload.put("conta", conta);
            payload.put("agencia", agencia);

            Map<String, Object> documento = new LinkedHashMap<>();
            documento.put("numero", dadosBoleto.get("numeroNossoNumero"));
            documento.put("numeroCliente", dadosBoleto.get("numeroDocumento"));

            /*
            01–Duplicata mercantil, 02–Nota promissória, 03–Nota de seguro, 05–Recibo, 09–Duplicata de serviço
            */
            switch ((String) dadosBoleto.get("especieDocumento")) {
                case "DM":
                    documento.put("especie", "01");
                    break;
                case "DS":
                    documento.put("especie", "09");
                    break;
                case "NS":
                    documento.put("especie", "03");
                    break;
                case "NP":
                    documento.put("especie", "02");
                    break;
                default:
                    throw new RuntimeException("EspecieDocumento não suportada: " + dadosBoleto.get("especieDocumento"));
            }
            documento.put("dataVencimento", dadosBoleto.get("dataVencimento"));
            documento.put("valor", dadosBoleto.get("valorTitulo"));
            documento.put("codigoMoeda", 0); //0 Fixo Layout

            //Protestar
            if (dadosBoleto.get("protestar") != null && dadosBoleto.get("protestar").equals("S")) {
                documento.put("quantidadeDiasProtesto", dadosBoleto.get("quantidadeDiasProtesto")); //Dias para protesto
            }

            //PagamentoParcial
            if (dadosBoleto.get("pagamentoParcial") != null && dadosBoleto.get("pagamentoParcial").equals("S")) {
                documento.put("valorMinimo", dadosBoleto.get("pagamentoParcialMin"));            
                documento.put("valorMaximo", dadosBoleto.get("pagamentoParcialMax"));
                documento.put("quantidadeParcelas", dadosBoleto.get("pagamentoParcialQtd"));
                documento.put("tipoPagamento", 1); //Aceita qualquer valor
                documento.put("tipoValor", 2); //Valor
            }

            //Multa / Juros
            Map<String, Object> multa = new LinkedHashMap<>();
            Number percentualMulta = (Number) dadosMulta.get("percentualMulta");
            Number valorJuros = (Number) dadosJuros.get("valorJuros");
            Number percentualJuros = (Number) dadosJuros.get("percentualJuros");
            if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
                multa.put("dataMulta", dadosMulta.get("dataMulta"));
                multa.put("taxaMulta", percentualMulta);
            }
            if (percentualJuros != null && percentualJuros.doubleValue() > 0) {
                multa.put("dataJuros", dadosJuros.get("dataJuros"));
                multa.put("taxaJuros", percentualJuros);
            }
            if (!multa.isEmpty()) {
                documento.put("multa", multa);
            }

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

            payload.put("documento", documento);

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
