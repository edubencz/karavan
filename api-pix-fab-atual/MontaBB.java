package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaBB")
public class MontaBB {

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

    public String montaBB(String tipo,
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
                               Map<String, Object> dadosJuros,
                               Map<String, Object> dadosMulta,
                               Map<String, Object> dadosPagador,
                               Map<String, Object> dadosBeneficiario,
                               List<String> instrucoes,
                               List<String> mensagens,
                               Map<String, Object> dadosDescontos) throws Exception {
        
        Map<String, Object> payload = new LinkedHashMap<>();
        Object convenioObj = dadosBanco.get("convenio");
        Object carteiraObj = dadosBanco.get("carteira");

        Integer convenio = convenioObj instanceof Number 
            ? ((Number) convenioObj).intValue() 
            : Integer.valueOf(convenioObj.toString());

        Integer carteira = carteiraObj instanceof Number 
            ? ((Number) carteiraObj).intValue() 
            : Integer.valueOf(carteiraObj.toString());

        payload.put("numeroConvenio", convenio);
        payload.put("numeroCarteira", carteira);
        payload.put("numeroVariacaoCarteira", dadosBanco.get("variacao"));
        payload.put("numeroTituloCliente", dadosBoleto.get("numeroNossoNumero"));
        payload.put("codigoModalidade", 1); //Valor fixo 01 - SIMPLES
        payload.put("dataEmissao", tratarData((String) dadosBoleto.get("dataEmissao"), "dd.MM.yyyy"));
        payload.put("dataVencimento", tratarData((String) dadosBoleto.get("dataVencimento"), "dd.MM.yyyy"));
        payload.put("valorOriginal", dadosBoleto.get("valorTitulo"));

        //Protesto
        if (dadosBoleto.get("protestar") == "S") {
            payload.put("quantidadeDiasProtesto", dadosBoleto.get("quantidadeDiasProtesto"));
        }

        //Negativação
        if (dadosBoleto.get("negativar") == "S") {
            payload.put("quantidadeDiasNegativacao", dadosBoleto.get("quantidadeDiasNegativacao"));
            payload.put("orgaoNegativador", dadosBoleto.getOrDefault("orgaoNegativador", 10)); //10 SERASA
        }

        payload.put("indicadorAceiteTituloVencido", dadosBoleto.getOrDefault("aceiteTituloVencido", "N"));

        Number diasLimiteRecebimento = (Number) dadosBoleto.get("numeroDiasLimiteRecebimento");
        if (diasLimiteRecebimento != null && diasLimiteRecebimento.intValue() > 0) {
            payload.put("numeroDiasLimiteRecebimento", diasLimiteRecebimento);
        }
        /*
         1- CHEQUE, 2- DUPLICATA MERCANTIL, 3- DUPLICATA MTIL POR INDICACAO, 4- DUPLICATA DE SERVICO, 5- DUPLICATA DE SRVC P/INDICACAO, 
         6- DUPLICATA RURAL, 7- LETRA DE CAMBIO, 8- NOTA DE CREDITO COMERCIAL, 9- NOTA DE CREDITO A EXPORTACAO, 10- NOTA DE CREDITO INDULTRIAL, 
         11- NOTA DE CREDITO RURAL, 12- NOTA PROMISSORIA, 13- NOTA PROMISSORIA RURAL, 14- TRIPLICATA MERCANTIL, 15- TRIPLICATA DE SERVICO, 
         16- NOTA DE SEGURO, 17- RECIBO, 18- FATURA, 19- NOTA DE DEBITO, 20- APOLICE DE SEGURO, 21- MENSALIDADE ESCOLAR, 22- PARCELA DE CONSORCIO, 
         23- DIVIDA ATIVA DA UNIAO, 24- DIVIDA ATIVA DE ESTADO, 25- DIVIDA ATIVA DE MUNICIPIO, 31- CARTAO DE CREDITO, 32- BOLETO PROPOSTA, 
         33- BOLETO APORTE, 99- OUTROS.
        */
       switch ((String) dadosBoleto.get("especieDocumento")) {
            case "CH":
                payload.put("codigoTipoTitulo", 1);
                payload.put("descricaoTipoTitulo", "CHEQUE");
                break;
            case "DM":
                payload.put("codigoTipoTitulo", 2);
                payload.put("descricaoTipoTitulo", "DUPLICATA MERCANTIL");
                break;
            case "DS":
                payload.put("codigoTipoTitulo", 3);
                payload.put("descricaoTipoTitulo", "DUPLICATA MTIL POR INDICACAO");
                break;
            case "NF":
                payload.put("codigoTipoTitulo", 18);
                payload.put("descricaoTipoTitulo", "NOTA FISCAL");
                break;
            case "ND":
                payload.put("codigoTipoTitulo", 19);
                payload.put("descricaoTipoTitulo", "NOTA DE DEBITO");
                break;
            default:
                payload.put("codigoTipoTitulo", 99); //OUTROS
                payload.put("descricaoTipoTitulo", "OUTROS");
        }

        //Pagamento Parcial
        if (dadosBoleto.get("pagamentoParcial") == "S") {
            payload.put("indicadorPermissaoRecebimentoParcial", dadosBoleto.getOrDefault("pagamentoParcial", "N"));
        }

        payload.put("numeroTituloBeneficiario", dadosBoleto.get("numeroDocumento"));
        payload.put("campoUtilizacaoBeneficiario", dadosBoleto.get("numeroDocumento"));
        payload.put("numeroTituloCliente", dadosBoleto.get("numeroNossoNumero"));
        payload.put("indicadorPix", "S");
        
        //Desconto
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        Number percentualDesconto = (Number) dadosDescontos.get("percentualDesconto");
        Map<String, Object> desconto = new LinkedHashMap<>();
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            desconto.put("tipo", 1); //Valor fixo até data informada
            desconto.put("dataExpiracao", tratarData((String) dadosBoleto.get("dataVencimento"), "dd.MM.yyyy"));
            desconto.put("valor", valorDesconto);
            payload.put("desconto", desconto);
        }
        else if (percentualDesconto != null && percentualDesconto.doubleValue() > 0) {
            desconto.put("tipo", 2); //Percentual até data informada
            desconto.put("dataExpiracao", tratarData((String) dadosBoleto.get("dataVencimento"), "dd.MM.yyyy"));
            desconto.put("porcentagem", percentualDesconto);
            payload.put("desconto", desconto);
        }

        // Juros
        Number valorJuros = (Number) dadosJuros.get("valorJuros");
        Number percentualJuros = (Number) dadosJuros.get("percentualJuros");
        Map<String, Object> jurosMora = new LinkedHashMap<>();
        if (valorJuros != null && valorJuros.doubleValue() > 0) {
            jurosMora.put("tipo", 1); //DIAS DE ATRASO
            jurosMora.put("data", tratarData((String) dadosJuros.get("dataJuros"), "dd.MM.yyyy"));
            jurosMora.put("valor", valorJuros);
            payload.put("jurosMora", jurosMora);
        }
        else if (percentualJuros != null && percentualJuros.doubleValue() > 0) {
            jurosMora.put("tipo", 2); //TAXA MENSAL
            jurosMora.put("data", tratarData((String) dadosJuros.get("dataJuros"), "dd.MM.yyyy"));
            jurosMora.put("porcentagem", percentualJuros);
            payload.put("jurosMora", jurosMora);
        }

        // Multa
        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        Map<String, Object> multa = new LinkedHashMap<>();
        if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
            multa.put("tipo", 2); //TAXA MENSAL
            multa.put("data", tratarData((String) dadosMulta.get("dataMulta"), "dd.MM.yyyy"));
            multa.put("porcentagem", percentualMulta);
            payload.put("multa", multa);
        }

        // Pagador
        Number tipoInscricao;
        switch ((String) dadosPagador.get("tipoPessoa")) {
            case "F":
                tipoInscricao = 1;
                break;
            case "J":
                tipoInscricao = 2;
                break;
            default:
                tipoInscricao = 9; //Não informado
        }
        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("tipoInscricao", tipoInscricao);
        pagador.put("numeroInscricao", dadosPagador.get("documento"));
        
        if (dadosPagador.get("nome") != null) {
            pagador.put("nome", dadosPagador.get("nome"));
        }
        
        pagador.put("endereco", getNestedMap(dadosPagador, "endereco").get("logradouro"));
        pagador.put("bairro", getNestedMap(dadosPagador, "endereco").get("bairro"));
        pagador.put("cidade", getNestedMap(dadosPagador, "endereco").get("cidade"));
        pagador.put("uf", getNestedMap(dadosPagador, "endereco").get("uf"));
        pagador.put("cep", getNestedMap(dadosPagador, "endereco").get("cep"));
        if (dadosPagador.get("telefone") != null) {
            pagador.put("telefone", dadosPagador.get("telefone"));
        }
        if (dadosPagador.get("email") != null) {
            pagador.put("email", dadosPagador.get("email"));
        }
        payload.put("pagador", pagador);
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

        return objectMapper.writeValueAsString(payload);
    }
}
