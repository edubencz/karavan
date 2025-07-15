import org.apache.camel.BindToRegistry;
import org.apache.camel.Configuration;

import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
@BindToRegistry("formataData")
public class FormataData {
    public String tratarData(String data, String formato) throws Exception {
        SimpleDateFormat sdfEntrada = new SimpleDateFormat("yyyy-MM-dd");
        Date dataConvertida = sdfEntrada.parse(data);

        SimpleDateFormat sdfSaida = new SimpleDateFormat(formato);
        return sdfSaida.format(dataConvertida);
    }
}
