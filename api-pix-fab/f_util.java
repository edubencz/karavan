package f_util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;

public class f_util {

    public static String tratar_data(String dataOriginal, String formatoDestino) {
        try {
            // Detectar o formato da entrada (assume yyyy-MM-dd por padrão)
            LocalDate data = LocalDate.parse(dataOriginal, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return data.format(DateTimeFormatter.ofPattern(formatoDestino));
        } catch (DateTimeParseException e) {
            return "formato inválido";
        }
    }

    public static String tratar_valor(String valor, String formatoDestino) {
        try {
            BigDecimal decimal = new BigDecimal(valor.replace(",", "."));
            if (formatoDestino.equalsIgnoreCase("pt-BR")) {
                return String.format("%,.2f", decimal).replace(",", "#").replace(".", ",").replace("#", ".");
            } else {
                return decimal.toPlainString();
            }
        } catch (NumberFormatException e) {
            return "erro";
        }
    }

    public static int tratar_inteiro(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
