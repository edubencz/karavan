package org.camel.karavan.demo.apipixfab;

import org.apache.camel.BindToRegistry;
import java.text.SimpleDateFormat;
import java.util.*;

@BindToRegistry("montaBB")
public class MontaBB {

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

    public Map<String, Object> montarEmissao(Map<String, Object> dadosBanco,
                                           Map<String, Object> dadosBoleto,
                                           Map<String, Object> dadosPagador,
                                           Map<String, Object> dadosBeneficiario,
                                           List<String> instrucoes,
                                           List<String> mensagens,
                                           Map<String, Object> dadosDescontos) throws Exception {
        
        Map<String, Object> payload = new LinkedHashMap<>();
        
        // Dados básicos do boleto
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
        payload.put("numeroTituloCliente", dadosBoleto.get("numeroNossoNumero"));
        payload.put("indicadorPix", "S");

        // Montagem do pagador
        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("tipoInscricao", dadosPagador.get("tipoPessoaNumero"));
        pagador.put("numeroInscricao", dadosPagador.get("documento"));
        if (dadosPagador.get("nome") != null) {
            pagador.put("nome", dadosPagador.get("nome"));
        }
        if (dadosPagador.get("telefone") != null) {
            pagador.put("telefone", dadosPagador.get("telefone"));
        }
        
        // Adicionar endereço do pagador se existir
        Map<String, Object> enderecoPagador = getNestedMap(dadosPagador, "endereco");
        if (!enderecoPagador.isEmpty()) {
            Map<String, Object> endereco = new LinkedHashMap<>();
            if (enderecoPagador.get("logradouro") != null) {
                endereco.put("logradouro", enderecoPagador.get("logradouro"));
            }
            if (enderecoPagador.get("bairro") != null) {
                endereco.put("bairro", enderecoPagador.get("bairro"));
            }
            if (enderecoPagador.get("cidade") != null) {
                endereco.put("cidade", enderecoPagador.get("cidade"));
            }
            if (enderecoPagador.get("uf") != null) {
                endereco.put("uf", enderecoPagador.get("uf"));
            }
            if (enderecoPagador.get("cep") != null) {
                endereco.put("cep", enderecoPagador.get("cep"));
            }
            if (enderecoPagador.get("complemento") != null) {
                endereco.put("complemento", enderecoPagador.get("complemento"));
            }
            if (!endereco.isEmpty()) {
                pagador.put("endereco", endereco);
            }
        }
        payload.put("pagador", pagador);

        // Beneficiário se existir
        if (dadosBeneficiario != null && !dadosBeneficiario.isEmpty()) {
            Map<String, Object> beneficiario = new LinkedHashMap<>();
            if (dadosBeneficiario.get("nome") != null) {
                beneficiario.put("nome", dadosBeneficiario.get("nome"));
            }
            if (dadosBeneficiario.get("documento") != null) {
                beneficiario.put("documento", dadosBeneficiario.get("documento"));
            }
            if (dadosBeneficiario.get("tipoPessoaNumero") != null) {
                beneficiario.put("tipoInscricao", dadosBeneficiario.get("tipoPessoaNumero"));
            }
            
            // Endereço do beneficiário
            Map<String, Object> enderecoBeneficiario = getNestedMap(dadosBeneficiario, "endereco");
            if (!enderecoBeneficiario.isEmpty()) {
                Map<String, Object> endereco = new LinkedHashMap<>();
                if (enderecoBeneficiario.get("logradouro") != null) {
                    endereco.put("logradouro", enderecoBeneficiario.get("logradouro"));
                }
                if (enderecoBeneficiario.get("bairro") != null) {
                    endereco.put("bairro", enderecoBeneficiario.get("bairro"));
                }
                if (enderecoBeneficiario.get("cidade") != null) {
                    endereco.put("cidade", enderecoBeneficiario.get("cidade"));
                }
                if (enderecoBeneficiario.get("uf") != null) {
                    endereco.put("uf", enderecoBeneficiario.get("uf"));
                }
                if (enderecoBeneficiario.get("cep") != null) {
                    endereco.put("cep", enderecoBeneficiario.get("cep"));
                }
                if (!endereco.isEmpty()) {
                    beneficiario.put("endereco", endereco);
                }
            }
            
            if (!beneficiario.isEmpty()) {
                payload.put("beneficiario", beneficiario);
            }
        }

        // Desconto
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            payload.put("valorAbatimento", valorDesconto);
            
            Map<String, Object> desc = new HashMap<>();
            desc.put("tipo", 1);
            desc.put("dataExpiracao", tratarData((String) dadosBoleto.get("dataEmissao"), "dd.MM.yyyy"));
            desc.put("porcentagem", 5.00);
            desc.put("valor", valorDesconto);
            payload.put("desconto", desc);
        }

        // Protesto
        Number diasProtesto = (Number) dadosBoleto.get("quantidadeDiasProtesto");
        if (diasProtesto != null && diasProtesto.intValue() > 0) {
            payload.put("quantidadeDiasProtesto", diasProtesto);
        }

        // Configurações padrão
        payload.put("indicadorAceiteTituloVencido", "S");
        payload.put("numeroDiasLimiteRecebimento",
                dadosBoleto.getOrDefault("numeroDiasLimiteRecebimento", 0));
        
        if (dadosBoleto.get("mensagemBloquetoOcorrencia") != null) {
            payload.put("mensagemBloquetoOcorrencia", dadosBoleto.get("mensagemBloquetoOcorrencia"));
        }

        // Juros
        Number tipoJuros = (Number) dadosBoleto.get("tipoJuros");
        Number valorJuros = (Number) dadosBoleto.get("valorJuros");
        Number percentualJuros = (Number) dadosBoleto.get("percentualJuros");
        if (tipoJuros != null && tipoJuros.intValue() == 1 && valorJuros != null && valorJuros.doubleValue() > 0) {
            payload.put("jurosMora", Map.of("tipo", tipoJuros, "valor", valorJuros));
        } else if (tipoJuros != null && tipoJuros.intValue() == 2 && percentualJuros != null && percentualJuros.doubleValue() > 0) {
            payload.put("jurosMora", Map.of("tipo", tipoJuros, "porcentagem", percentualJuros));
        }

        // Multa
        Number tipoMulta = (Number) dadosBoleto.get("tipoMulta");
        Number percentualMulta = (Number) dadosBoleto.get("percentualMulta");
        Number valorMulta = (Number) dadosBoleto.get("valorMulta");
        if (tipoMulta != null && tipoMulta.intValue() == 2 && percentualMulta != null && percentualMulta.doubleValue() > 0) {
            payload.put("multa", Map.of("tipo", tipoMulta,
                    "data", tratarData((String) dadosBoleto.get("dataMulta"), "dd.MM.yyyy"),
                    "porcentagem", percentualMulta));
        } else if (tipoMulta != null && tipoMulta.intValue() == 1 && valorMulta != null && valorMulta.doubleValue() > 0) {
            payload.put("multa", Map.of("tipo", tipoMulta,
                    "data", tratarData((String) dadosBoleto.get("dataMulta"), "dd.MM.yyyy"),
                    "valor", valorMulta));
        }

        // Instruções
        if (instrucoes != null && !instrucoes.isEmpty()) {
            payload.put("instrucoes", instrucoes);
        }

        // Mensagens
        if (mensagens != null && !mensagens.isEmpty()) {
            payload.put("mensagens", mensagens);
        }

        return payload;
    }

    public Map<String, Object> montarAlteracao(Map<String, Object> dadosBanco,
                                             Map<String, Object> dadosBoleto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        payload.put("motivoAlteracao", "alteracao via API");
        return payload;
    }

    public Map<String, Object> montarCancelamento(Map<String, Object> dadosBanco,
                                                 Map<String, Object> dadosBoleto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("numeroConvenio", dadosBanco.get("convenio"));
        payload.put("motivoCancelamento", "Solicitado pelo cliente");
        return payload;
    }
}
