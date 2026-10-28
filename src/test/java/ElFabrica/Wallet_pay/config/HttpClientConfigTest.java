package ElFabrica.Wallet_pay.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ElFabrica.Wallet_pay.user.infra.CnpjWsValidatorGateway;
import ElFabrica.Wallet_pay.user.service.CnpjValidationUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpClientConfigTest {

    private HttpServer slowCnpjService;
    private ExecutorService handlerExecutor;
    private CountDownLatch releaseHandlers;

    @BeforeEach
    void startSlowCnpjService() throws IOException {
        releaseHandlers = new CountDownLatch(1);
        handlerExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "slow-cnpj-handler");
            thread.setDaemon(true);
            return thread;
        });
        slowCnpjService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowCnpjService.createContext("/cnpj/", exchange -> {
            try {
                // Holds the response until teardown releases it (bounded, never leaks past 6s).
                releaseHandlers.await(6, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(200, -1);
            } catch (IOException ignored) {
                // client already gave up
            } finally {
                exchange.close();
            }
        });
        slowCnpjService.setExecutor(handlerExecutor);
        slowCnpjService.start();
    }

    @AfterEach
    void stopSlowCnpjService() {
        releaseHandlers.countDown();
        slowCnpjService.stop(0);
        handlerExecutor.shutdownNow();
    }

    @Test
    void shouldGiveUpOnSlowCnpjServiceAfterTheReadTimeout() {
        CnpjWsValidatorGateway gateway = new CnpjWsValidatorGateway(
                new HttpClientConfig().restClientBuilder(),
                "http://127.0.0.1:" + slowCnpjService.getAddress().getPort()
        );
        long start = System.nanoTime();

        assertThatThrownBy(() -> gateway.exists("11222333000181"))
                .isInstanceOf(CnpjValidationUnavailableException.class)
                .hasMessage("Tempo esgotado ao validar o CNPJ");

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isBetween(Duration.ofMillis(2_500), Duration.ofMillis(5_000));
    }
}
