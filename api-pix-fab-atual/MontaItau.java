package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("MontaItau")
public class MontaItau {

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

    public String montaItau(String tipo, 
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
        
        //**********************
        // Manual diferente do JSON TESTE, seguido JSON TESTE
        //
        // https://devportal.itau.com.br/nossas-apis/itau-ep9-gtw-pix-recebimentos-conciliacoes-v2-ext?tab=informacoesAdicionais
        //********************************************************************/

        Number valorNominal = (Number) dadosBoleto.get("valorNominal");
        long valorNominalMult = Math.round(valorNominal.doubleValue() * 100);
        String valorNominalFormatado = String.format("%017d", valorNominalMult);

        Map<String, Object> payload = new LinkedHashMap<>();
        
        // Campos obrigatórios conforme documentação Itau
        payload.put("etapa_processo_boleto", "efetivacao");

        //Beneficiario
        Map<String, Object> beneficiario = new LinkedHashMap<>();
        
        String agencia = (String) dadosBanco.get("agencia");
        String conta = (String) dadosBanco.get("conta");
        String digitoConta = (String) dadosBanco.get("digitoConta");
        agencia = String.format("%04d", Integer.parseInt(agencia));
        conta = String.format("%07d", Integer.parseInt(conta));

        String idBeneficiario = agencia + conta + digitoConta;
        beneficiario.put("id_beneficiario", idBeneficiario);
        //beneficiario.put("nome_cobranca", dadosBeneficiario.get("nome"));
        payload.put("beneficiario", beneficiario);
        /*
        //Beneficiario -> Tipo Pessoa
        Map<String, Object> tipo_pessoa_beneficiario = new LinkedHashMap<>();
        tipo_pessoa_beneficiario.put("codigo_tipo_pessoa", dadosBeneficiario.get("tipoPessoa"));
        payload.put("beneficiario", beneficiario);
        */

        //Dado do boleto
        Map<String, Object> dado_boleto = new LinkedHashMap<>();
        dado_boleto.put("tipo_boleto", "a vista"); //CF SGU
        dado_boleto.put("descricao_instrumento_cobranca", "boleto_pix");
        dado_boleto.put("texto_seu_numero", dadosBoleto.get("numeroDocumento"));
        dado_boleto.put("codigo_carteira", "109"); //CF SGU
        dado_boleto.put("valor_total_titulo", valorNominalFormatado);
        dado_boleto.put("codigo_especie", "01"); //CF SGU
        dado_boleto.put("data_emissao", dadosBoleto.get("dataEmissao"));

        //Dado Boleto -> Negativação
        Number diasNegativacao = (Number) dadosBoleto.get("quantidadeDiasNegativacao");
        if (diasNegativacao != null && diasNegativacao.intValue() > 0) {
            Map<String, Object> negativacao = new LinkedHashMap<>();
            negativacao.put("negativacao", true);
            negativacao.put("quantidade_dias_negativacao", diasNegativacao);
            dado_boleto.put("negativacao", negativacao);
        }

        //Dado Boleto -> Pagador -> Pessoa
        Map<String, Object> pagador = new LinkedHashMap<>();

        Map<String, Object> pessoa = new LinkedHashMap<>();
        pessoa.put("nome_pessoa", dadosPagador.get("nome"));
        //Dado Boleto -> Pagador -> Pessoa -> Tipo Pessoa
        Map<String, Object> tipo_pessoa = new LinkedHashMap<>();
        tipo_pessoa.put("codigo_tipo_pessoa", dadosPagador.get("tipoPessoa"));
        if (dadosPagador.get("tipoPessoa").equals("F")) {
            tipo_pessoa.put("numero_cadastro_pessoa_fisica", dadosPagador.get("documento"));
        } else {
            tipo_pessoa.put("numero_cadastro_pessoa_juridica", dadosPagador.get("documento"));
        }
        pessoa.put("tipo_pessoa", tipo_pessoa);
        pagador.put("pessoa", pessoa);

        //Dado Boleto -> Pagador -> Endereco é opcional
        Map<String, Object> endereco = new LinkedHashMap<>();
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        endereco.put("nome_logradouro", enderecoPagador.get("logradouro"));
        endereco.put("nome_bairro", enderecoPagador.get("bairro"));
        endereco.put("nome_cidade", enderecoPagador.get("cidade"));
        endereco.put("sigla_UF", enderecoPagador.get("uf"));
        endereco.put("numero_CEP", enderecoPagador.get("cep"));
        dado_boleto.put("endereco", endereco);
        pagador.put("endereco", endereco);

        dado_boleto.put("pagador", pagador);

        //Dado Boleto -> Dados individuais []
        Map<String, Object> dados_individuais = new LinkedHashMap<>();
        dados_individuais.put("numero_nosso_numero", dadosBoleto.get("numeroDocumento"));
        dados_individuais.put("data_vencimento", dadosBoleto.get("dataVencimento"));
        dados_individuais.put("texto_uso_beneficiario", dadosBoleto.get("numeroDocumento"));
        dados_individuais.put("valor_titulo", valorNominalFormatado);
        Number diasLimitePagamento = (Number) dadosBoleto.get("numeroDiasLimiteRecebimento");
        if (diasLimitePagamento != null && diasLimitePagamento.intValue() > 0) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, diasLimitePagamento.intValue());
            Date dataLimite = cal.getTime();
            String dataLimiteFormatada = new SimpleDateFormat("yyyy-MM-dd").format(dataLimite);
            dados_individuais.put("data_limite_pagamento", dataLimiteFormatada);
        }
        List<Map<String, Object>> dadosIndividuaisList = new ArrayList<>();
        dadosIndividuaisList.add(dados_individuais);
        dado_boleto.put("dados_individuais_boleto", dadosIndividuaisList);

        //Dado Boleto -> Protesto (tem no manual, não tem no JSON teste)
        Number diasProtesto = (Number) dadosBoleto.get("quantidadeDiasProtesto");
        if (diasProtesto != null && diasProtesto.intValue() > 0) {
            Map<String, Object> protesto = new LinkedHashMap<>();
            protesto.put("protesto", true);
            protesto.put("quantidade_dias_protesto", diasProtesto);
            payload.put("protesto", protesto);
            dado_boleto.put("protesto", protesto);
        }

        //Dado Boleto -> Juros
        Map<String, Object> juros = new LinkedHashMap<>();
        juros.put("data_juros", dadosBoleto.get("dataMulta"));
        Number percentualJuros = (Number) dadosBoleto.get("percentualJuros");
        if (percentualJuros != null && percentualJuros.doubleValue() > 0) {
            long percentualEmCentavos = Math.round(percentualJuros.doubleValue() * 100);
            String percentualFormatado = String.format("%012d", percentualEmCentavos);
            juros.put("codigo_tipo_juros", "91"); //CF do SGU
            juros.put("percentual_juros", percentualFormatado);
        } else {
            long valorJurosEmCentavos = Math.round(((Number) dadosBoleto.get("valorJuros")).doubleValue() * 100);
            String valorJuros = String.format("%017d", valorJurosEmCentavos);
            juros.put("codigo_tipo_juros", "93"); //CF do SGU
            juros.put("valor_juros", valorJuros);
        }
        dado_boleto.put("juros", juros);

        //Dado Boleto -> Multa
        Map<String, Object> multa = new LinkedHashMap<>();
        multa.put("data_multa", dadosBoleto.get("dataMulta"));
        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
            multa.put("codigo_tipo_multa", "02"); //CF do SGU
            long valorPercCentavos = Math.round(percentualMulta.doubleValue() * 100);
            String percentualFormatado = String.format("%012d", valorPercCentavos);
            multa.put("percentual_multa", percentualFormatado);
        } else {
            multa.put("codigo_tipo_multa", "01"); //CF do SGU
            long valorMultaEmCentavos = Math.round(((Number) dadosBoleto.get("valorMulta")).doubleValue() * 100);
            String valorMulta = String.format("%017d", valorMultaEmCentavos);
            multa.put("valor_multa", valorMulta);
        }
        dado_boleto.put("multa", multa);

        //Dado Boleto -> Descontos
        Map<String, Object> desconto = new LinkedHashMap<>();
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        Number percentualDesconto = (Number) dadosDescontos.get("percentualDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            desconto.put("codigo_tipo_desconto", "01"); //CF do SGU
            long valorDescontoEmCentavos = Math.round(valorDesconto.doubleValue() * 100);
            String valorFormatado = String.format("%017d", valorDescontoEmCentavos);
            desconto.put("valor_desconto", valorFormatado);
        } else if (percentualDesconto != null && percentualDesconto.doubleValue() > 0) {
            desconto.put("codigo_tipo_desconto", "02"); //CF do SGU
            long percentualDescontoEmCentavos = Math.round(percentualDesconto.doubleValue() * 100);
            String valorFormatado = String.format("%012d", percentualDescontoEmCentavos);
            desconto.put("percentual_desconto", valorFormatado);
        } else {
            desconto.put("codigo_tipo_desconto", "00"); //CF do SGU
        }
        dado_boleto.put("desconto", desconto);


        // Mensagens
        List<Map<String, String>> listaMensagemCobranca = new ArrayList<>();
        if (mensagens != null) {
            for (String msg : mensagens) {
            Map<String, String> mensagemObj = new HashMap<>();
            mensagemObj.put("mensagem", msg);
            listaMensagemCobranca.add(mensagemObj);
            }
        }
        dado_boleto.put("lista_mensagem_cobranca", listaMensagemCobranca);
        
        
        payload.put("dado_boleto", dado_boleto);
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
