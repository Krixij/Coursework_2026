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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.opencsv.CSVWriter;


public class Parser {
    private static final int interval = 1;
    private static String lastUsdInvesting = null;
    private static String lastEurInvesting = null;
    private static String lastUsdCbr = null;
    private static String lastEurCbr = null;
    private static boolean firstSendMessage = true;
    private static float differentUsd = 0;
    private static float differentEur = 0;

    public static void main(String[] args) {
        String todayDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        ExcelExporter.excelDirectory();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ExcelExporter.exportAllToExcel();
            sendFinalMessage(todayDate);
            System.out.println("Парсер остановлен\n");
        }));
        System.out.println("Парсер запущен. Интервал: " + interval + " минута");
        while (true) {
            String updateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
            parseAllCurrency(client, updateTime, todayDate);
            try {
                Thread.sleep(interval * 60000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    private static void sendFinalMessage(String todayDate) {
        String currencyMessage = EmailNotifier.formatCurrencyMessage(lastUsdInvesting, lastEurInvesting, lastUsdCbr, lastEurCbr);
        String textMessage = "Парсер остановлен.\nСводка текущих курсов на сегодня:\n\n " + currencyMessage + "\n\nНа пк сформирован Excel отчет";
        EmailNotifier.sendCurrencyMail("Сводка данных на " + todayDate, textMessage);
    }
    private static void parseAllCurrency(HttpClient client, String updateTime, String todayDate) {
        System.out.println("Обновление данных: " + updateTime);
        try {
            investingCodeCurrency(client, updateTime);
        } catch (Exception e) {
            System.err.println("  Критическая ошибка на сайте Investing.com: " + e.getMessage());
        }
        try {
            parseCbrCurrency(client, updateTime);
        } catch (Exception e) {
            System.err.println("  Критическая ошибка на сайте ЦБ РФ: " + e.getMessage());
        }
        if (firstSendMessage) {
            String currencyMessage = EmailNotifier.formatCurrencyMessage(lastUsdInvesting, lastEurInvesting, lastUsdCbr, lastEurCbr);
            String textMessage = "Парсер запущен.\nСводка курсов на сегодня:\n\n " + currencyMessage;
            EmailNotifier.sendCurrencyMail("Сводка данных на " + todayDate, textMessage);
            firstSendMessage = false;
        } else {
            if (differentEur != 0 || differentUsd != 0) {
                String currencyMessage = EmailNotifier.formatDifferenceMessage(differentUsd, differentEur);
                String textMessage = "Изменение курса валюты:\n\n " + currencyMessage;
                EmailNotifier.sendCurrencyMail("Сводка изменения данных на " + todayDate, textMessage);
                differentUsd = 0;
                differentEur = 0;
            }
        }
    }
    private static void investingCodeCurrency(HttpClient client, String updateTime) {
        parseInvestingCurrency(client, "usd-rub", "USD/RUB", "USD(investing).csv", updateTime);
        parseInvestingCurrency(client, "eur-rub", "EUR/RUB", "EUR(investing).csv", updateTime);
    }
    private static void parseInvestingCurrency(HttpClient client, String symbol, String name, String filename, String updateTime) {
        String url = "https://ru.investing.com/currencies/" + symbol;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 YaBrowser/26.3.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("  HTTP ошибка на сайте Investing.com: " + response.statusCode());
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
            String currentPrice = "No value";
            if (priceElement != null) {
                currentPrice = priceElement.text();
            }
            System.out.println("  Investing.com (" + name + "): " + currentPrice);
            if (name.equals("USD/RUB")) {
                if (lastUsdInvesting == null){
                    lastUsdInvesting = currentPrice;
                } else {
                    if (!lastUsdInvesting.equals(currentPrice)){
                        float price = Float.parseFloat(currentPrice.replace(',', '.'));
                        float lastPrice = Float.parseFloat(lastUsdInvesting.replace(',', '.'));
                        differentUsd = (price / lastPrice - 1) * 100;
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
                        differentEur = (price / lastPrice - 1) * 100;
                        lastEurInvesting = currentPrice;
                    }
                }
            }
            try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filename, true))) {
                if (new java.io.File(filename).length() == 0) {
                    csvWriter.writeNext(new String[]{"Time", "Name currency", "Price currency"});
                }
                csvWriter.writeNext(new String[]{updateTime, currencyTitle, currentPrice});
            }
        } catch (Exception e) {
            System.err.println("  Ошибка при парсинге на сайте Investing.com (" + name + "): " + e.getMessage());
        }
    }
    private static void parseCbrCurrency(HttpClient client, String updateTime) {
        String url = "https://www.cbr.ru/scripts/XML_daily.asp";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 YaBrowser/26.3.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("  HTTP ошибка на сайте ЦБ РФ: " + response.statusCode());
                return;
            }
            String xmlContent = response.body();
            Document doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser());
            Element usdElement = doc.select("Valute:has(CharCode:contains(USD))").first();
            if (usdElement != null) {
                String usdValue = usdElement.select("Value").text();
                System.out.println("  ЦБ РФ (USD/RUB): " + usdValue);
                lastUsdCbr = usdValue;
                saveCbrData("USD(cbr).csv", updateTime, "USD/RUB", usdValue);
            }
            Element eurElement = doc.select("Valute:has(CharCode:contains(EUR))").first();
            if (eurElement != null) {
                String eurValue = eurElement.select("Value").text();
                System.out.println("  ЦБ РФ (EUR/RUB): " + eurValue);
                lastEurCbr = eurValue;
                saveCbrData("EUR(cbr).csv", updateTime, "EUR/RUB", eurValue);
            }
        } catch (Exception e) {
            System.err.println("  Ошибка при парсинге на сайте ЦБ РФ: " + e.getMessage());
        }
    }
    private static void saveCbrData(String filename, String updateTime, String name, String value) {
        try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filename, true))) {
            if (new java.io.File(filename).length() == 0) {
                csvWriter.writeNext(new String[]{"Time", "Name currency", "Price currency"});
            }
            csvWriter.writeNext(new String[]{updateTime, name, value});
        } catch (IOException e) {
            System.err.println("  Ошибка при записи CSV: " + e.getMessage());
        }
    }
}