package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExcelExporter {
    private static final String format = "0.0000";

    public static void exportAllToExcel() {
        String updateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm"));
        String path = "excel data/currency rates " + updateTime + ".xlsx";
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat(format));
            createCurrencySheet(workbook, "USD-RUB", "USD(investing).csv", "USD(cbr).csv", decimalStyle);
            createCurrencySheet(workbook, "EUR-RUB", "EUR(investing).csv", "EUR(cbr).csv", decimalStyle);
            try (FileOutputStream fileOut = new FileOutputStream(path)) {
                workbook.write(fileOut);
            }
            System.out.println("Excel файл создан: " + path);
        } catch (IOException e) {
            System.err.println("Ошибка создания Excel: " + e.getMessage());
        }
    }
    private static void createCurrencySheet(XSSFWorkbook workbook, String sheetName, String investingFile, String cbrFile, CellStyle decimalStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setColumnWidth(0, 4000);
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        String[] headers = {"Дата и время", "Investing", "ЦБ РФ", "Разница"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        Map<String, Double> investingData = readCSV(investingFile);
        Map<String, Double> cbrData = readCSV(cbrFile);
        Set<String> allTimes = new TreeSet<>();
        allTimes.addAll(investingData.keySet());
        allTimes.addAll(cbrData.keySet());
        int rowNum = 1;
        double lastInvesting = 0;
        double lastCbr = 0;
        boolean hasFirstInvesting = false;
        boolean hasFirstCbr = false;
        for (String time : allTimes) {
            Double investingVal = investingData.get(time);
            Double cbrVal = cbrData.get(time);
            if (investingVal != null) {
                lastInvesting = investingVal;
                hasFirstInvesting = true;
            }
            if (cbrVal != null) {
                lastCbr = cbrVal;
                hasFirstCbr = true;
            }
            if (!hasFirstInvesting || !hasFirstCbr) continue;
            double inv;
            if (investingVal != null) {
                inv = investingVal;
            } else {
                inv = lastInvesting;
            }
            double cbr;
            if (cbrVal != null) {
                cbr = cbrVal;
            } else {
                cbr = lastCbr;
            }
            Row row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue(time);
            Cell cell1 = row.createCell(1);
            cell1.setCellValue(inv);
            cell1.setCellStyle(decimalStyle);
            Cell cell2 = row.createCell(2);
            cell2.setCellValue(cbr);
            cell2.setCellStyle(decimalStyle);
            Cell cell3 = row.createCell(3);
            double diff = ((inv / cbr) - 1) * 100;
            cell3.setCellValue(String.format("%.2f", diff) + "%");
            cell3.setCellStyle(decimalStyle);
            rowNum++;
        }
    }
    private static Map<String, Double> readCSV(String fileName) {
        Map<String, Double> data = new TreeMap<>();
        File file = new File(fileName);
        if (!file.exists()) return data;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.replace("\"", "");
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String dateTime = parts[0].trim();
                    if (dateTime.length() > 16) {
                        dateTime = dateTime.substring(0, 16);
                    }
                    String priceStr = parts[2].trim();
                    if (parts.length > 3) {
                        priceStr = parts[2];
                        for (int i = 3; i < parts.length; i++) {
                            priceStr += "." + parts[i];
                        }
                    }
                    priceStr = priceStr.replace(",", ".").trim();
                    try {
                        double price = Double.parseDouble(priceStr);
                        data.put(dateTime, price);
                    } catch (NumberFormatException e) {
                        System.err.println("Ошибка числа в " + fileName + ": '" + priceStr + "'");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения " + fileName + ": " + e.getMessage());
        }
        return data;
    }
    public static void excelDirectory() {
        File dir = new File("excel data");
        if (!dir.exists()) {
            dir.mkdir();
        }
    }
}