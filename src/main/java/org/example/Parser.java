package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.opencsv.CSVWriter;


public class Parser {
    private static final int intervalMinutes = 1;

    private static volatile boolean running = true;

    public static void main(String[] args) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        parseAllCurrency(client);
        scheduler.scheduleAtFixedRate(
                () -> parseAllCurrency(client),
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void parseAllCurrency(HttpClient client) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] обновление данных:");
        CompletableFuture<Void> investingFuture = CompletableFuture.runAsync(() ->
                InvestingCodeCurrency(client));
        CompletableFuture<Void> cbrFuture = CompletableFuture.runAsync(() ->
                parseCbrCurrency(client));
        CompletableFuture.allOf(investingFuture, cbrFuture).join();

    }
    private static void InvestingCodeCurrency(HttpClient client) {
        String[][] currencies = {
                {"usd-rub", "USD/RUB", "usd_rub_investing.csv"},
                {"eur-rub", "EUR/RUB", "eur_rub_investing.csv"}
        };
        for (String[] currency : currencies) {
            parseInvestingCurrency(client, currency[0], currency[1], currency[2]);
        }
    }
    private static void parseInvestingCurrency(HttpClient client, String symbol, String name, String filename) {
        String url = "https://ru.investing.com/currencies/" + symbol;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 YaBrowser/26.3.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("  [Investing.com] HTTP ошибка: " + response.statusCode());
                return;
            }
            Document doc = Jsoup.parse(response.body());
            Element titleElement = doc.select("h1:has(div.inline.text-left)").first();
            if (titleElement == null) {
                titleElement = doc.select("h1").first();
            }
            String fullTitle = titleElement != null ? titleElement.text() : name;
            String currencyTitle = fullTitle.split("-")[0].trim();
            Element priceElement = doc.select("[data-test='instrument-price-last']").first();
            String currentPrice = priceElement != null ? priceElement.text() : "N/A";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println("  [Investing.com] " + name + ": " + currentPrice);
            try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filename, true))) {
                if (new java.io.File(filename).length() == 0) {
                    csvWriter.writeNext(new String[]{"Timestamp", "Name currency", "Price currency"});
                }
                csvWriter.writeNext(new String[]{timestamp, currencyTitle, currentPrice});
            }
        } catch (Exception e) {
            System.err.println("  [Investing.com] Ошибка при парсинге " + name + ": " + e.getMessage());
        }
    }

    private static String lastUsdValue = null;
    private static String lastEurValue = null;

    private static void parseCbrCurrency(HttpClient client) {
        String url = "https://www.cbr.ru/scripts/XML_daily.asp";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 YaBrowser/26.3.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("  [ЦБ РФ] HTTP ошибка: " + response.statusCode());
                return;
            }
            String xmlContent = response.body();
            Document doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Element usdElement = doc.select("Valute:has(CharCode:contains(USD))").first();
            if (usdElement != null) {
                String usdValue = usdElement.select("Value").text();
                if (!usdValue.equals(lastUsdValue)) {
                    System.out.println("  [ЦБ РФ] USD/RUB: " + usdValue);
                    saveCbrData("usd_rub_cbr.csv", timestamp, "USD/RUB", usdValue);
                    lastUsdValue = usdValue;
                }
            }
            Element eurElement = doc.select("Valute:has(CharCode:contains(EUR))").first();
            if (eurElement != null) {
                String eurValue = eurElement.select("Value").text();
                if (!eurValue.equals(lastEurValue)) {
                    System.out.println("  [ЦБ РФ] EUR/RUB: " + eurValue);
                    saveCbrData("eur_rub_cbr.csv", timestamp, "EUR/RUB", eurValue);
                    lastEurValue = eurValue;
                }
            }
        } catch (Exception e) {
            System.err.println("  [ЦБ РФ] Ошибка при парсинге: " + e.getMessage());
        }
    }
    private static void saveCbrData(String filename, String timestamp, String name, String value) {
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filename, true))) {
            if (new java.io.File(filename).length() == 0) {
                csvWriter.writeNext(new String[]{"Timestamp", "Name currency", "Price currency"});
            }
            csvWriter.writeNext(new String[]{timestamp, name, value});
        } catch (IOException e) {
            System.err.println("  [ЦБ РФ] Ошибка при записи CSV: " + e.getMessage());
        }
    }
}
