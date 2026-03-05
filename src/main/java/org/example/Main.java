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
import org.jsoup.select.Elements;
import com.opencsv.CSVWriter;

public class Main {
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();

        // создание HttpRequest
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.scrapingcourse.com/ecommerce/"))
                .build();

        // отправка асинхронного запроса GET и обработка ответа
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                // извлечение тело в виде строки
                .thenApply(HttpResponse::body)
                // извлечение извлеченного тела
                .thenAccept(htmlContent -> {
                    // Анализ HTML-содержимого с помощью Jsoup
                    Document doc = Jsoup.parse(htmlContent);

                    // выделение первго элемента h2 с помощью CSS selector и извлечение его текстового содержимого
                    //Element titleElement = doc.select("h2").first();
                    //String productTitle = titleElement.text();
                    //System.out.println("Product Title: " + productTitle);

                    // выбор всех элементов продуктов
                    Elements productElements = doc.select(".product");

                    // инициализирование программы записей CSV-файлов
                    try (CSVWriter csvWriter = new CSVWriter(new FileWriter("products.csv"))) {
                        // Запись заголовка столбцов
                        csvWriter.writeNext(new String[]{"Product Title", "Image URL", "Link"});

                        // проход итерации по каждому продукту
                        for (Element productElement : productElements) {
                            // Извлечение названия продукта
                            String productTitle = productElement.select("h2").text();
                            // Извлечение URL-адрес изображения
                            String imageUrl = productElement.select("img[src]").attr("src");
                            // Извлечение ссылку
                            String link = productElement.select("a[href]").attr("href");

                            //System.out.println("Product Title: " + productTitle + "\nImage URL: " + imageUrl + "\nLink: " + link + "\n");

                            // запись данных в формат CSV
                            csvWriter.writeNext(new String[]{productTitle, imageUrl, link});
                        }

                        System.out.println("Data successfully exported to CSV");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                })

                .join();
    }
}
