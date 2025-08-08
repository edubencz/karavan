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

    public String montarEmissao(Map<String, Object> dadosBanco,
                               Map<String, Object> dadosBoleto,
                               Map<String, Object> dadosPagador,
                               Map<String, Object> dadosBeneficiario,
                               List<String> instrucoes,
                               List<String> mensagens,
                               Map<String, Object> dadosDescontos) throws Exception {
        
        Map<String, Object> payload = new LinkedHashMap<>();
        
        // Campos obrigatórios conforme documentação BB
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        payload.put("numeroCarteira", dadosBanco.get("carteira"));
        payload.put("numeroVariacaoCarteira", dadosBanco.get("variacao"));
        payload.put("codigoModalidade", dadosBanco.get("codigoModalidade"));
        payload.put("dataEmissao", tratarData((String) dadosBoleto.get("dataEmissao"), "dd.MM.yyyy"));
        payload.put("dataVencimento", tratarData((String) dadosBoleto.get("dataVencimento"), "dd.MM.yyyy"));
        payload.put("valorOriginal", dadosBoleto.get("valorNominal"));
        payload.put("codigoAceite", dadosBoleto.get("aceite"));
        payload.put("codigoTipoTitulo", dadosBoleto.get("codigoTipoTitulo"));
        payload.put("indicadorPermissaoRecebimentoParcial", "N");
        payload.put("numeroTituloBeneficiario", dadosBoleto.get("numeroDocumento"));
        payload.put("campoUtilizacaoBeneficiario", dadosBoleto.get("numeroDocumento"));
        payload.put("numeroTituloCliente", dadosBoleto.get("numeroNossoNumero"));
        payload.put("indicadorPix", "S");
        
        // Campos opcionais
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            payload.put("valorAbatimento", valorDesconto);
        }
        
        Number diasProtesto = (Number) dadosBoleto.get("quantidadeDiasProtesto");
        if (diasProtesto != null && diasProtesto.intValue() > 0) {
            payload.put("quantidadeDiasProtesto", diasProtesto);
        }
        
        Number diasNegativacao = (Number) dadosBoleto.get("quantidadeDiasNegativacao");
        if (diasNegativacao != null && diasNegativacao.intValue() > 0) {
            payload.put("quantidadeDiasNegativacao", diasNegativacao);
            payload.put("orgaoNegativador", dadosBoleto.getOrDefault("orgaoNegativador", 0));
        }
        
        payload.put("indicadorAceiteTituloVencido", dadosBoleto.getOrDefault("aceiteTituloVencido", "S"));
        payload.put("numeroDiasLimiteRecebimento", dadosBoleto.getOrDefault("numeroDiasLimiteRecebimento", 0));
        
        if (dadosBoleto.get("mensagemBloquetoOcorrencia") != null) {
            payload.put("mensagemBloquetoOcorrencia", dadosBoleto.get("mensagemBloquetoOcorrencia"));
        }
        
        // Descontos
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            Map<String, Object> desconto = new LinkedHashMap<>();
            desconto.put("tipo", 1);
            desconto.put("dataExpiracao", tratarData((String) dadosBoleto.get("dataEmissao"), "dd.MM.yyyy"));
            desconto.put("porcentagem", dadosDescontos.getOrDefault("percentualDesconto", 0));
            desconto.put("valor", valorDesconto);
            payload.put("desconto", desconto);
        }
        
        // Juros
        Number tipoJuros = (Number) dadosBoleto.get("tipoJuros");
        Number valorJuros = (Number) dadosBoleto.get("valorJuros");
        Number percentualJuros = (Number) dadosBoleto.get("percentualJuros");
        
        if (tipoJuros != null) {
            Map<String, Object> jurosMora = new LinkedHashMap<>();
            jurosMora.put("tipo", tipoJuros);
            
            if (tipoJuros.intValue() == 1 && valorJuros != null && valorJuros.doubleValue() > 0) {
                jurosMora.put("valor", valorJuros);
            } else if (tipoJuros.intValue() == 2 && percentualJuros != null && percentualJuros.doubleValue() > 0) {
                jurosMora.put("porcentagem", percentualJuros);
            }
            
            payload.put("jurosMora", jurosMora);
        }
        
        // Multa
        Number tipoMulta = (Number) dadosBoleto.get("tipoMulta");
        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        Number valorMulta = (Number) dadosBoleto.get("valorMulta");
        
        if (tipoMulta != null) {
            Map<String, Object> multa = new LinkedHashMap<>();
            multa.put("tipo", tipoMulta);
            
            if (dadosBoleto.get("dataMulta") != null) {
                multa.put("data", tratarData((String) dadosBoleto.get("dataMulta"), "dd.MM.yyyy"));
            }
            
            if (tipoMulta.intValue() == 1 && valorMulta != null) {
                multa.put("valor", valorMulta);
            } else if (tipoMulta.intValue() == 2 && percentualMulta != null) {
                multa.put("porcentagem", percentualMulta);
            }
            
            payload.put("multa", multa);
        }
        
        // Pagador
        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("tipoInscricao", dadosPagador.get("tipoPessoaNumero"));
        pagador.put("numeroInscricao", dadosPagador.get("documento"));
        
        if (dadosPagador.get("nome") != null) {
            pagador.put("nome", dadosPagador.get("nome"));
        }
        
        // Endereço do pagador
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        if (!enderecoPagador.isEmpty()) {
            if (enderecoPagador.get("logradouro") != null) {
                pagador.put("endereco", enderecoPagador.get("logradouro"));
            }
            
            if (enderecoPagador.get("bairro") != null) {
                pagador.put("bairro", enderecoPagador.get("bairro"));
            }
            
            if (enderecoPagador.get("cidade") != null) {
                pagador.put("cidade", enderecoPagador.get("cidade"));
            }
            
            if (enderecoPagador.get("uf") != null) {
                pagador.put("uf", enderecoPagador.get("uf"));
            }
            
            if (enderecoPagador.get("cep") != null) {
                String cepStr = enderecoPagador.get("cep").toString();
                cepStr = cepStr.replaceAll("[^0-9]", "");
                try {
                    pagador.put("cep", Long.valueOf(cepStr));
                } catch (NumberFormatException e) {
                    pagador.put("cep", cepStr);
                }
            }
        }
        
        if (dadosPagador.get("telefone") != null) {
            pagador.put("telefone", dadosPagador.get("telefone"));
        }
        
        payload.put("pagador", pagador);
        
        // Beneficiário Final
        if (dadosBeneficiario != null && !dadosBeneficiario.isEmpty()) {
            Map<String, Object> beneficiarioFinal = new LinkedHashMap<>();
            
            if (dadosBeneficiario.get("tipoPessoaNumero") != null) {
                beneficiarioFinal.put("tipoInscricao", dadosBeneficiario.get("tipoPessoaNumero"));
            }
            
            if (dadosBeneficiario.get("documento") != null) {
                beneficiarioFinal.put("numeroInscricao", dadosBeneficiario.get("documento"));
            }
            
            if (dadosBeneficiario.get("nome") != null) {
                beneficiarioFinal.put("nome", dadosBeneficiario.get("nome"));
            }
            
            if (!beneficiarioFinal.isEmpty()) {
                payload.put("beneficiarioFinal", beneficiarioFinal);
            }
        }

        return objectMapper.writeValueAsString(payload);
    }

    public String montarAlteracao(Map<String, Object> dadosBanco,
                                 Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        payload.put("motivoAlteracao", "alteracao via API");
        return objectMapper.writeValueAsString(payload);
    }

    public String montarCancelamento(Map<String, Object> dadosBanco,
                                   Map<String, Object> dadosBoleto) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        payload.put("motivoCancelamento", "Solicitado pelo cliente");
        return objectMapper.writeValueAsString(payload);
    }
}
