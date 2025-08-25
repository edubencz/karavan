package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("MontaSantander")
public class MontaSantander {

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
        payer.put("state", enderecoPagador.get("estado"));
        payer.put("zipCode", enderecoPagador.get("cep"));
        payload.put("payer", payer);

        payer.put("bankNumber", dadosBoleto.get("numeroNossoNumero"));
        payer.put("dueDate", dadosBoleto.get("dataVencimento"));
        payer.put("issueDate", dadosBoleto.get("dataEmissao"));
        payer.put("nominalValue", dadosBoleto.get("valorNominal"));
        payer.put("documentKind", dadosBoleto.get("tipoDoc"));

        //Campo opicional, mas enviado sempre para seguir o CADASTRO_CONVENIO entre Unimed e Banco
        payer.put("protestType", "CADASTRO_CONVENIO");
        payer.put("paymentType", "REGISTRO");

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
            payer.put("discount", discount);
        }

        //Juros
        Number valorJuros = (Number) dadosDescontos.get("valorJuros");
        if (valorJuros != null && valorJuros.doubleValue() > 0) {
            payer.put("finePercentage", dadosBoleto.get("percentualJuros"));
            payer.put("fineQuantityDays", (Number) 1);
        }

        
        return objectMapper.writeValueAsString(payer);
    }

    public String montarAlteracao(Map<String, Object> dadosBanco,
                                 Map<String, Object> dadosBoleto,
                                 Map<String, Object> dadosPagador,
                                 Map<String, Object> dadosDescontos) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        
        // Campo obrigatório - número do convênio
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        
        // Alteração de data de vencimento
        payload.put("indicadorNovaDataVencimento", "S");
        Map<String, Object> alteracaoData = new LinkedHashMap<>();
        alteracaoData.put("novaDataVencimento", tratarData((String) dadosBoleto.get("dataVencimento"), "dd.MM.yyyy"));
        payload.put("alteracaoData", alteracaoData);
        
        // Alteração de valor nominal
        payload.put("indicadorNovoValorNominal", "S");
        Map<String, Object> alteracaoValor = new LinkedHashMap<>();
        alteracaoValor.put("novoValorNominal", dadosBoleto.get("valorNominal"));
        payload.put("alteracaoValor", alteracaoValor);
        
        // Protesto - se houver dias de protesto configurados
        Number diasProtesto = (Number) dadosBoleto.get("quantidadeDiasProtesto");
        if (diasProtesto != null && diasProtesto.intValue() > 0) {
            payload.put("indicadorProtestar", "S");
            Map<String, Object> protesto = new LinkedHashMap<>();
            protesto.put("quantidadeDiasProtesto", diasProtesto);
            payload.put("protesto", protesto);
        }
        
        // Juros
        Number tipoJuros = (Number) dadosBoleto.get("tipoJuros");
        Number valorJuros = (Number) dadosBoleto.get("valorJuros");
        Number percentualJuros = (Number) dadosBoleto.get("percentualJuros");
        
        if (tipoJuros != null) {
            payload.put("indicadorCobrarJuros", "S");
            Map<String, Object> juros = new LinkedHashMap<>();
            juros.put("tipoJuros", tipoJuros);
            
            if (tipoJuros.intValue() == 1 && valorJuros != null && valorJuros.doubleValue() > 0) {
                juros.put("valorJuros", valorJuros);
            } else if (tipoJuros.intValue() == 2 && percentualJuros != null && percentualJuros.doubleValue() > 0) {
                juros.put("taxaJuros", percentualJuros);
            }
            
            payload.put("juros", juros);
        }
        
        // Multa
        Number tipoMulta = (Number) dadosBoleto.get("tipoMulta");
        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        Number valorMulta = (Number) dadosBoleto.get("valorMulta");
        
        if (tipoMulta != null) {
            payload.put("indicadorCobrarMulta", "S");
            Map<String, Object> multa = new LinkedHashMap<>();
            multa.put("tipoMulta", tipoMulta);
            
            if (dadosBoleto.get("dataMulta") != null) {
                multa.put("dataInicioMulta", tratarData((String) dadosBoleto.get("dataMulta"), "dd.MM.yyyy"));
            }
            
            if (tipoMulta.intValue() == 1 && valorMulta != null) {
                multa.put("valorMulta", valorMulta);
            } else if (tipoMulta.intValue() == 2 && percentualMulta != null) {
                multa.put("taxaMulta", percentualMulta);
            }
            
            payload.put("multa", multa);
        }
        
        // Negativação se existir configuração
        Number diasNegativacao = (Number) dadosBoleto.get("quantidadeDiasNegativacao");
        if (diasNegativacao != null && diasNegativacao.intValue() > 0) {
            payload.put("indicadorNegativar", "S");
            Map<String, Object> negativacao = new LinkedHashMap<>();
            negativacao.put("quantidadeDiasNegativacao", diasNegativacao);
            negativacao.put("tipoNegativacao", 2); // Valor padrão conforme documentação Santander

            if (dadosBoleto.get("orgaoNegativador") != null) {
                negativacao.put("orgaoNegativador", dadosBoleto.get("orgaoNegativador"));
            }
            
            payload.put("negativacao", negativacao);
        }
        
        // Alteração de prazo para aceite de boleto vencido
        if (dadosBoleto.get("aceiteTituloVencido") != null && 
            "S".equals(dadosBoleto.get("aceiteTituloVencido"))) {
            
            payload.put("indicadorAlterarPrazoBoletoVencido", "S");
            Map<String, Object> alteracaoPrazo = new LinkedHashMap<>();
            alteracaoPrazo.put("quantidadeDiasAceite", dadosBoleto.get("numeroDiasLimiteRecebimento"));
            payload.put("alteracaoPrazo", alteracaoPrazo);
        }
        
        // Desconto
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        Number percentualDesconto = (Number) dadosDescontos.get("percentualDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            payload.put("indicadorAtribuirDesconto", "S");
            Map<String, Object> desconto = new LinkedHashMap<>();
            desconto.put("tipoPrimeiroDesconto", 1);
            desconto.put("valorPrimeiroDesconto", valorDesconto);
            
            if (percentualDesconto != null && percentualDesconto.doubleValue() > 0) {
                desconto.put("percentualPrimeiroDesconto", percentualDesconto);
            }
            
            desconto.put("dataPrimeiroDesconto", tratarData((String) dadosBoleto.get("dataVencimento"), "dd.MM.yyyy"));
            payload.put("desconto", desconto);
        }
        
        // Alteração de endereço do pagador
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        if (!enderecoPagador.isEmpty()) {
            payload.put("indicadorAlterarEnderecoPagador", "S");
            Map<String, Object> alteracaoEndereco = new LinkedHashMap<>();
            
            if (enderecoPagador.get("logradouro") != null) {
                alteracaoEndereco.put("enderecoPagador", enderecoPagador.get("logradouro"));
            }
            
            if (enderecoPagador.get("bairro") != null) {
                alteracaoEndereco.put("bairroPagador", enderecoPagador.get("bairro"));
            }
            
            if (enderecoPagador.get("cidade") != null) {
                alteracaoEndereco.put("cidadePagador", enderecoPagador.get("cidade"));
            }
            
            if (enderecoPagador.get("uf") != null) {
                alteracaoEndereco.put("UFPagador", enderecoPagador.get("uf"));
            }
            
            if (enderecoPagador.get("cep") != null) {
                String cepStr = enderecoPagador.get("cep").toString();
                cepStr = cepStr.replaceAll("[^0-9]", "");
                try {
                    alteracaoEndereco.put("CEPPagador", Long.valueOf(cepStr));
                } catch (NumberFormatException e) {
                    alteracaoEndereco.put("CEPPagador", cepStr);
                }
            }
            
            payload.put("alteracaoEndereco", alteracaoEndereco);
        }
        
        return objectMapper.writeValueAsString(payload);
    }

    public String montarCancelamento(Map<String, Object> dadosBanco,
                                   Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        return objectMapper.writeValueAsString(payload);
    }
}
