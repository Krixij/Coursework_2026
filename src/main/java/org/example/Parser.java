package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.opencsv.CSVWriter;


public class Parser {
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();

        String[][] currencies = {
                {"usd-rub", "USD/RUB", "usd_rub(investing).csv"},
                {"eur-rub", "EUR/RUB", "eur_rub(investing).csv"}
        };

        for (String[] currency : currencies) {
            parseCurrency(client, currency[0], currency[1], currency[2]);
        }
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void parseCurrency(HttpClient client, String symbol, String name, String filename) {
        String url = "https://ru.investing.com/currencies/" + symbol;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 YaBrowser/26.3.0.0 Safari/537.36")
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(htmlContent -> {
                    Document doc = Jsoup.parse(htmlContent);

                    Element titleElement = doc.select("h1:has(div.inline.text-left)").first();
                    if (titleElement == null) {
                        titleElement = doc.select("h1").first();
                    }
                    String fullTitle = titleElement != null ? titleElement.text() : name;
                    String currencyTitle = fullTitle.split("-")[0].trim();

                    Element priceElement = doc.select("[data-test='instrument-price-last']").first();
                    String currentPrice = priceElement != null ? priceElement.text() : "N/A";

                    System.out.println("Курс " + name + ": " + currentPrice);

                    try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filename))) {
                        csvWriter.writeNext(new String[]{"Name currency", "Price currency"});
                        csvWriter.writeNext(new String[]{currencyTitle, currentPrice});
                        System.out.println("Данные для " + name + " экспортированы в " + filename);
                    } catch (IOException e) {
                        System.err.println("Ошибка при записи CSV для " + name + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("Ошибка при парсинге " + name + ": " + ex.getMessage());
                    return null;
                });
    }
}
