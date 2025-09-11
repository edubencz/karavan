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

    private Integer parseInteger(Object value, String fieldName) {
        if (value instanceof Number) {
            System.out.println("Field: " + fieldName + " - Type: Number - Value: " + value);
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            System.out.println("Field: " + fieldName + " - Type: String - Value: " + value);
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Falha ao converter valor para Integer no campo: " + fieldName + " - Valor: " + value, e);
            }
        } else {
            System.out.println("Field: " + fieldName + " - Unexpected Type: " + value.getClass().getName() + " - Value: " + value);
            throw new RuntimeException("Tipo de dado inesperado para conversão no campo: " + fieldName + " - Valor: " + value);
        }
    }

    public String montaSicoob(String tipo,
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
        
        // Campos obrigatórios conforme documentação BB
        Integer carteira = parseInteger(dadosBanco.get("carteira"), "carteira");
        Integer conta = parseInteger(dadosBanco.get("conta"), "conta");
        Integer identificacaoEmissaoBoleto = 1;
        payload.put("numeroCliente", carteira);
        payload.put("codigoModalidade", 1); // Fixo 1 SIMPLES COM REGISTRO
        //payload.put("numeroContaCorrente", conta); //Para testes fixado 0
        payload.put("numeroContaCorrente", 0);

        /*
            CH - Cheque
            DM - Duplicata Mercantil
            DMI - Duplicata Mercantil Indicação
            DS - Duplicata de Serviço
            DSI - Duplicata Serviço Indicação
            DR - Duplicata Rural
            LC - Letra de Câmbio
            NCC - Nota de Crédito Comercial
            NCE - Nota de Crédito Exportação
            NCI - Nota de Crédito Industrial
            NCR - Nota de Crédito Rural
            NP - Nota Promissória
            NPR - Nota Promissória Rural
            TM - Triplicata Mercantil
            TS - Triplicata de Serviço
            NS - Nota de Seguro
            RC - Recibo
            FAT - Fatura
            ND - Nota de Débito
            AP - Apólice de Seguro
            ME - Mensalidade Escolar
            PC - Pagamento de Consórcio
            NF - Nota Fiscal
            DD - Documento de Dívida
            CC - Cartão de Crédito
            BDP - Boleto Proposta
            OU - Outros
        */
        payload.put("codigoEspecieDocumento", dadosBoleto.get("especieDocumento"));
        payload.put("dataEmissao", dadosBoleto.get("dataEmissao"));

        Integer nossoNumero = parseInteger(dadosBoleto.get("numeroNossoNumero"), "numeroNossoNumero");
        payload.put("nossoNumero", nossoNumero);
        payload.put("seuNumero", dadosBoleto.get("numeroDocumento"));

        payload.put("identificacaoEmissaoBoleto", 2); //Fixo cliente emite boleto
        payload.put("identificacaoDistribuicaoBoleto", 2); //Fixo cliente emite boleto
        payload.put("valor", dadosBoleto.get("valorTitulo"));
        payload.put("dataVencimento", dadosBoleto.get("dataVencimento"));
        //Descontos
        Number valorDesconto = (Number) dadosDescontos.get("valorDesconto");
        if (valorDesconto != null && valorDesconto.doubleValue() > 0) {
            payload.put("tipoDesconto", 1); //Fixo até a data informada
            payload.put("dataPrimeiroDesconto", dadosBoleto.get("dataVencimento"));
            payload.put("valorPrimeiroDesconto", dadosDescontos.get("valorDesconto"));
        }
        else {
            payload.put("tipoDesconto", 0); //Sem desconto
        }

        //Multa
        Number valorMulta = (Number) dadosMulta.get("valorMulta");
        Number percentualMulta = (Number) dadosMulta.get("percentualMulta");
        if (valorMulta != null && valorMulta.doubleValue() > 0) {
            payload.put("tipoMulta", 1); //Valor fixo
            payload.put("dataMulta", dadosMulta.get("dataMulta"));
            payload.put("valorMulta", valorMulta);
        } else if (percentualMulta != null && percentualMulta.doubleValue() > 0) {
            payload.put("tipoMulta", 2); //Percentual
            payload.put("dataMulta", dadosMulta.get("dataMulta"));
            payload.put("valorMulta", percentualMulta);
        }

        //Juros
        Number valorJuros = (Number) dadosJuros.get("valorJuros");
        Number percentualJuros = (Number) dadosJuros.get("percentualJuros");
        if (valorJuros != null && valorJuros.doubleValue() > 0) {
            payload.put("tipoJurosMora", 1); //Valor diario
            payload.put("dataJurosMora", dadosJuros.get("dataJuros"));
            payload.put("valorJurosMora", valorJuros);
        }
        else if (percentualJuros != null && percentualJuros.doubleValue() > 0) {
            payload.put("tipoJurosMora", 2); //Percentual ao mês
            payload.put("dataJurosMora", dadosJuros.get("dataJuros"));
            payload.put("valorJurosMora", percentualJuros);
        }
        payload.put("numeroParcela", 1); //Numero da parcela, fixado 1

        //Negativação
        if (dadosBoleto.get("negativar") != null && dadosBoleto.get("negativar").equals("S")) {
            payload.put("codigoNegativacao", 2); //2 - Negativar
            payload.put("numeroDiasNegativacao", dadosBoleto.get("quantidadeDiasNegativacao")); //Dias para negativação
        } else {
            payload.put("codigoNegativacao", 3); //3 - Não negativar
        }

        //Protestar
        if (dadosBoleto.get("protestar") != null && dadosBoleto.get("protestar").equals("S")) {
            payload.put("codigoProtesto", 1); //1 - Protestar dias corridos
            payload.put("numeroDiasProtesto", dadosBoleto.get("quantidadeDiasProtesto")); //Dias para protesto
        } else {
            payload.put("codigoProtesto", 3); //3 - Não protestar
        }

        //Pagador
        Map<String, Object> pagador = new LinkedHashMap<>();
        pagador.put("numeroCpfCnpj", dadosPagador.get("documento"));
        pagador.put("nome", dadosPagador.get("nome"));
        pagador.put("endereco", getNestedMap(dadosPagador, "endereco").get("logradouro"));
        pagador.put("bairro", getNestedMap(dadosPagador, "endereco").get("bairro"));
        pagador.put("cidade", getNestedMap(dadosPagador, "endereco").get("cidade"));
        pagador.put("uf", getNestedMap(dadosPagador, "endereco").get("uf"));
        pagador.put("cep", getNestedMap(dadosPagador, "endereco").get("cep"));
        if (dadosPagador.get("email") != null) {
            pagador.put("email", dadosPagador.get("email"));
        }
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
