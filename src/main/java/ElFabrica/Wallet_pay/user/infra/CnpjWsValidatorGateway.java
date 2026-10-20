package ElFabrica.Wallet_pay.user.infra;

import ElFabrica.Wallet_pay.user.service.CnpjValidationUnavailableException;
import ElFabrica.Wallet_pay.user.service.CnpjValidatorGateway;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CnpjWsValidatorGateway implements CnpjValidatorGateway {

    private final RestClient restClient;
    private final String baseUrl;

    public CnpjWsValidatorGateway(
            RestClient.Builder restClientBuilder,
            @Value("${wallet-pay.integrations.cnpj-ws.base-url:https://publica.cnpj.ws}") String baseUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean exists(String cnpj) {
        String uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/cnpj/{cnpj}")
                .build(cnpj)
                .toString();

        try {
            restClient.get()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }

            throw new CnpjValidationUnavailableException("Nao foi possivel validar o CNPJ", exception);
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                throw new CnpjValidationUnavailableException("Tempo esgotado ao validar o CNPJ", exception);
            }
            throw new CnpjValidationUnavailableException("Nao foi possivel validar o CNPJ", exception);
        }
    }

    private static boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
