package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
public class Main {
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();

        // build a HttpRequest
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ru.investing.com/currencies/usd-rub"))
                .build();

        // send asynchronous GET request and handle response.
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                // extract body as string
                .thenApply(HttpResponse::body)
                // retrieve extracted body
                .thenAccept(htmlContent -> {
                    System.out.println(htmlContent);
                })

                .join();
    }
}
