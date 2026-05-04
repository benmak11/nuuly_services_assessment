package app.inventory;

import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

final class JavalinHarness {

    private JavalinHarness() {
    }

    @FunctionalInterface
    interface ThrowingTestBody {
        void accept(Javalin server, Client client) throws Exception;
    }

    static void run(Javalin app, ThrowingTestBody body) throws Exception {
        app.start(0);
        try {
            body.accept(app, new Client(app.port()));
        } finally {
            app.stop();
        }
    }

    static final class Client {

        private final HttpClient http = HttpClient.newHttpClient();
        private final String base;

        Client(int port) {
            this.base = "http://localhost:" + port;
        }

        HttpResponse<String> get(String path) throws Exception {
            return http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(base + path))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            return http.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(base + path))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
