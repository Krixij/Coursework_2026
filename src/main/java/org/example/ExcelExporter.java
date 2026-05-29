package org.example;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExcelExporter {
    private static final String DECIMAL_FORMAT = "0.0000";

    public static void exportAllToExcel() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm"));
        String fullPath = "excel_exports" + File.separator + "currency_rates_" + timestamp + ".xlsx";

        ensureExcelDirectory();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            CellStyle decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat(DECIMAL_FORMAT));

            createCurrencySheet(workbook, "USD_RUB",
                    "usd_rub_investing.csv", "usd_rub_cbr.csv", decimalStyle);

            createCurrencySheet(workbook, "EUR_RUB",
                    "eur_rub_investing.csv", "eur_rub_cbr.csv", decimalStyle);

            try (FileOutputStream fileOut = new FileOutputStream(fullPath)) {
                workbook.write(fileOut);
            }

            System.out.println("Excel файл создан: " + fullPath);

        } catch (IOException e) {
            System.err.println("Ошибка создания Excel: " + e.getMessage());
        }
    }
    private static void createCurrencySheet(XSSFWorkbook workbook, String sheetName,
                                            String investingFile, String cbrFile,
                                            CellStyle decimalStyle) {
        Sheet sheet = workbook.createSheet(sheetName);

        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 4500);
        sheet.setColumnWidth(2, 4500);
        sheet.setColumnWidth(3, 4500);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(sheetName + " — сравнение курсов");
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        Row dateRow = sheet.createRow(1);
        dateRow.createCell(0).setCellValue("Создано: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        Row headerRow = sheet.createRow(3);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] headers = {"Дата и время", "Investing.com", "ЦБ РФ", "Разница"};
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

        int rowNum = 4;
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
            double diff = inv - cbr;
            cell3.setCellValue(diff);
            cell3.setCellStyle(decimalStyle);

            rowNum++;
        }

        int lastDataRow = rowNum - 1;

        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
        createComparisonChart(drawing, sheet,
                5, 3,
                25, 32,
                sheetName + " — Сравнительный график",
                0, 1, 2, lastDataRow);
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
    private static void createComparisonChart(XSSFDrawing drawing, Sheet sheet,
                                              int col1, int row1, int col2, int row2,
                                              String title, int catCol, int valCol1,
                                              int valCol2, int lastRow) {
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2);
        XSSFChart chart = drawing.createChart(anchor);

        chart.setTitleText(title);
        chart.setTitleOverlay(false);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("Время");

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Курс (руб)");
        leftAxis.setNumberFormat(DECIMAL_FORMAT);

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                (XSSFSheet) sheet, new CellRangeAddress(4, lastRow, catCol, catCol));

        XDDFLineChartData data = (XDDFLineChartData) chart.createData(
                ChartTypes.LINE, bottomAxis, leftAxis);

        XDDFNumericalDataSource<Double> values1 = XDDFDataSourcesFactory.fromNumericCellRange(
                (XSSFSheet) sheet, new CellRangeAddress(4, lastRow, valCol1, valCol1));
        XDDFLineChartData.Series series1 = (XDDFLineChartData.Series) data.addSeries(categories, values1);
        series1.setTitle("Investing.com", null);
        series1.setSmooth(false);
        series1.setMarkerStyle(MarkerStyle.NONE);

        XDDFNumericalDataSource<Double> values2 = XDDFDataSourcesFactory.fromNumericCellRange(
                (XSSFSheet) sheet, new CellRangeAddress(4, lastRow, valCol2, valCol2));
        XDDFLineChartData.Series series2 = (XDDFLineChartData.Series) data.addSeries(categories, values2);
        series2.setTitle("ЦБ РФ", null);
        series2.setSmooth(false);
        series2.setMarkerStyle(MarkerStyle.NONE);

        chart.plot(data);
    }
    public static void ensureExcelDirectory() {
        File dir = new File("excel_exports");
        if (!dir.exists()) {
            dir.mkdir();
        }
    }
}