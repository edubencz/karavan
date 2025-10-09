package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaUnicred")
public class MontaUnicred {

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

    public String montaUnicred(String tipo,
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
            
            // Campos obrigatórios conforme documentação BB
            payload.put("beneficiarioVariacaoCarteira", dadosBanco.get("carteira"));
            payload.put("seuNumero", dadosBoleto.get("numeroDocumento"));
            payload.put("valor", dadosBoleto.get("valorTitulo"));
            payload.put("vencimento", dadosBoleto.get("dataVencimento"));
            payload.put("nossoNumero", dadosBoleto.get("numeroNossoNumero"));
            
            //Protesto
            Number quantidadeDiasProtesto = (Number) dadosBoleto.get("diasProtesto");
            if (dadosBoleto.get("protestar") != null && dadosBoleto.get("protestar").equals("S")) {
                payload.put("codigoParaProtesto", "DIAS_CORRIDOS");
                payload.put("quantidadeDiasProtesto", quantidadeDiasProtesto.intValue());
            }
            else {
                payload.put("codigoParaProtesto", "NAO_PROTESTAR");
            }

            //Negativação
            Number quantidadeDiasNegativacao = (Number) dadosBoleto.get("diasNegativacao");
            if (dadosBoleto.get("negativar") != null && dadosBoleto.get("negativar").equals("S")) {
                payload.put("codigoParaNegativacao", "DIAS_CORRIDOS");
                payload.put("quantidadeDiasNegativacao", quantidadeDiasNegativacao.intValue());
            }
            else {
                payload.put("codigoParaNegativacao", "NAO_NEGATIVAR");
            }

            //Descontos
            Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
            if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
                Map<String, Object> desconto = new LinkedHashMap<>();
                desconto.put("indicador", 1); //Pois somente valor fixo
                desconto.put("dataLimite", dadosBoleto.get("dataVencimento"));
                desconto.put("valor", dadosDescontos.get("valorDesconto"));
                payload.put("desconto", desconto);
            }

            //Juros
            /*
            '1' = Valor Diário (R$); '2' = Taxa diária (%); '3' = Taxa Mensal (%); '5' = Isento.
            */
            Number valorJuros = (Number) dadosJuros.get("valorJuros");
            Number percentualJuros = (Number) dadosJuros.get("percentualJuros");
            if (valorJuros != null && valorJuros.doubleValue() > 0) {
                Map<String, Object> juros = new LinkedHashMap<>();
                juros.put("codigo", 1); //Valor diario
                juros.put("dataInicio", dadosJuros.get("dataJuros"));
                payload.put("juros", juros);
            }
            else if (percentualJuros != null && percentualJuros.doubleValue() > 0) {
                Map<String, Object> juros = new LinkedHashMap<>();
                juros.put("codigo", 2); //Percentual ao mês
                juros.put("dataInicio", dadosJuros.get("dataJuros"));
                juros.put("valor", dadosJuros.get("percentualJuros"));
                payload.put("juros", juros);
            }

            //Multa
            /*
            '1' = Valor Fixo (R$); '2' = Taxa (%); '3' = Isento. 
            */
            Number valorMulta = (Number) dadosMulta.get("valorMulta");
            Number percentualMulta = (Number) dadosMulta.get("percentualMulta");
            if (valorMulta != null && valorMulta.doubleValue() > 0) {
                Map<String, Object> multa = new LinkedHashMap<>();
                multa.put("codigo", 1); //Valor diario
                multa.put("dataInicio", dadosMulta.get("dataMulta"));
                multa.put("valor", dadosMulta.get("valorMulta"));
                payload.put("multa", multa);
            }
            else if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
                Map<String, Object> multa = new LinkedHashMap<>();
                multa.put("codigo", 2); //Percentual ao mês
                multa.put("dataInicio", dadosMulta.get("dataMulta"));
                multa.put("valor", dadosMulta.get("percentualMulta"));
                payload.put("multa", multa);
            }

            //Mensagens
            if (mensagens != null && !mensagens.isEmpty()) {
                payload.put("mensagensFichaCompensacao", mensagens);
            }

            //Pagador
            Map<String, Object> pagador = new LinkedHashMap<>();
            pagador.put("nomeRazaoSocial", dadosPagador.get("nome"));
            pagador.put("tipoPessoa", dadosPagador.get("tipoPessoa"));
            if (dadosPagador.get("tipoPessoa").equals("F")) {
                pagador.put("tipoDocumento", "CPF");
            } else {
                pagador.put("tipoDocumento", "CNPJ");
            }
            pagador.put("numeroDocumento", dadosPagador.get("documento"));
            pagador.put("nomeFantasia", dadosPagador.get("nome"));

            if (dadosPagador.get("email") != null && !dadosPagador.get("email").toString().isEmpty()) {
                pagador.put("email", dadosPagador.get("email"));
            }

            Map<String, Object> enderecoPagador = new LinkedHashMap<>();
            Map<String, Object> endereco = getNestedMap(dadosPagador, "endereco");
            enderecoPagador.put("logradouro", endereco.get("logradouro"));
            enderecoPagador.put("bairro", endereco.get("bairro"));
            enderecoPagador.put("cidade", endereco.get("cidade"));
            enderecoPagador.put("uf", endereco.get("uf"));
            enderecoPagador.put("cep", endereco.get("cep"));
            if (endereco.get("complemento") != null && !endereco.get("complemento").toString().isEmpty()) {
                enderecoPagador.put("complemento", endereco.get("complemento"));
            }
            pagador.put("endereco", enderecoPagador);
            payload.put("pagador", pagador);

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
