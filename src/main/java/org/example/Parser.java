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
    private static String lastUsdInvesting = null;
    private static String lastEurInvesting = null;
    private static String lastUsdCbr = null;
    private static String lastEurCbr = null;
    private static boolean firstSend = true;
    private static Float difUsdInvesting = null;
    private static Float difEurInvesting = null;

    public static void main(String[] args) {
        ExcelExporter.ensureExcelDirectory();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ExcelExporter.exportAllToExcel();
            sendFinalCurrencyMessage();
            System.out.println("Парсер остановлен\n");
        }));

        System.out.println("Парсер запущен. Интервал: " + intervalMinutes + " минута");

        parseAllCurrency(client);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
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
    private static void sendFinalCurrencyMessage() {
        String currencyMessage = EmailNotifier.formatCurrencyMessage(
                lastUsdInvesting, lastEurInvesting, lastUsdCbr, lastEurCbr
        );
        String fullMessage = "До свидания, парсер остановлен.\nСводка текущих курсов на сегодня:\n\n " + currencyMessage
                + "\n\nСформирован Excel отчет";
        EmailNotifier.sendCurrencyUpdate(
                "Сводка данных на " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                fullMessage
        );
    }
    private static void parseAllCurrency(HttpClient client) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
        System.out.println("[" + timestamp + "] обновление данных:");

        try {
            InvestingCodeCurrency(client);
        } catch (Exception e) {
            System.err.println("  [Investing.com] Критическая ошибка: " + e.getMessage());
        }

        try {
            parseCbrCurrency(client);
        } catch (Exception e) {
            System.err.println("  [ЦБ РФ] Критическая ошибка: " + e.getMessage());
        }

        if (firstSend) {
            String currencyMessage = EmailNotifier.formatCurrencyMessage(
                    lastUsdInvesting, lastEurInvesting, lastUsdCbr, lastEurCbr
            );
            String fullMessage = "Здравствуйте, парсер запущен.\nСводка курсов на сегодня:\n\n " + currencyMessage;
            EmailNotifier.sendCurrencyUpdate(
                    "Сводка данных на " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    fullMessage
            );
            firstSend = false;
        } else {
            if (difEurInvesting != null || difUsdInvesting != null) {
                String currencyMessage = EmailNotifier.formatDifferenceMessage(
                        difUsdInvesting, difEurInvesting);
                String fullMessage = "Изменение курса валюты:\n\n " + currencyMessage;
                EmailNotifier.sendCurrencyUpdate(
                        "Сводка изменения данных на " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        fullMessage
                );
                difUsdInvesting = null;
                difEurInvesting = null;
            }
        }
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
            String fullTitle = name;
            if (titleElement != null) {
                fullTitle = titleElement.text();
            }
            String currencyTitle = fullTitle.split("-")[0].trim();
            Element priceElement = doc.select("[data-test='instrument-price-last']").first();
            String currentPrice = "N/A";
            if (priceElement != null) {
                currentPrice = priceElement.text();
            }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
            System.out.println("  [Investing.com] " + name + ": " + currentPrice);

            if (name.equals("USD/RUB")) {
                if (lastUsdInvesting == null){
                    lastUsdInvesting = currentPrice;
                } else {
                    if (!lastUsdInvesting.equals(currentPrice)){
                        float price = Float.parseFloat(currentPrice.replace(',', '.'));
                        float lastPrice = Float.parseFloat(lastUsdInvesting.replace(',', '.'));
                        difUsdInvesting = (price / lastPrice - 1) * 100;
                        lastUsdInvesting = currentPrice;
                    }
                }
            } else if (name.equals("EUR/RUB")) {
                if (lastEurInvesting == null){
                    lastEurInvesting = currentPrice;
                } else {
                    if (!lastEurInvesting.equals(currentPrice)){
                        float price = Float.parseFloat(currentPrice.replace(',', '.'));
                        float lastPrice = Float.parseFloat(lastEurInvesting.replace(',', '.'));
                        difEurInvesting = (price / lastPrice - 1) * 100;
                        lastEurInvesting = currentPrice;
                    }
                }
            }

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
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
            Element usdElement = doc.select("Valute:has(CharCode:contains(USD))").first();
            if (usdElement != null) {
                String usdValue = usdElement.select("Value").text();
                System.out.println("  [ЦБ РФ] USD/RUB: " + usdValue);
                lastUsdCbr = usdValue;
                saveCbrData("usd_rub_cbr.csv", timestamp, "USD/RUB", usdValue);
            }
            Element eurElement = doc.select("Valute:has(CharCode:contains(EUR))").first();
            if (eurElement != null) {
                String eurValue = eurElement.select("Value").text();
                System.out.println("  [ЦБ РФ] EUR/RUB: " + eurValue);
                lastEurCbr = eurValue;
                saveCbrData("eur_rub_cbr.csv", timestamp, "EUR/RUB", eurValue);
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