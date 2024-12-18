package expense_income_tracker;

import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

public class LineChartGenerator {
    // Multiple date formats to try parsing
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),  // Primary format
            DateTimeFormatter.ofPattern("dd-MM-yy"),    // Alternative format
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),  // Another alternative
            DateTimeFormatter.ofPattern("MM-dd-yyyy")   // US format
    );

    // Robust date parsing method
    private static LocalDate parseDate(String dateString) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateString, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        throw new IllegalArgumentException("Unable to parse date: " + dateString);
    }

    public static JFrame createOverallIncomeExpenseChart(List<ExpenseIncomeEntry> entries) {
        // Handle empty entries
        if (entries == null || entries.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "No entries available to create chart.",
                    "Chart Error",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        // Group entries by month and calculate cumulative balance
        Map<String, Double> monthlyBalances = calculateMonthlyBalances(entries);

        XYChart chart = new XYChartBuilder()
                .width(800)
                .height(600)
                .title("Overall Income vs Expenses")
                .xAxisTitle("Month")
                .yAxisTitle("Balance")
                .theme(Styler.ChartTheme.GGPlot2)
                .build();

        // Sort the months to ensure chronological order
        List<String> sortedMonths = new ArrayList<>(monthlyBalances.keySet());
        Collections.sort(sortedMonths);

        // Use Integer stream for x-axis
        int[] xData = IntStream.range(0, sortedMonths.size()).toArray();
        double[] yData = new double[sortedMonths.size()];

        for (int i = 0; i < sortedMonths.size(); i++) {
            yData[i] = monthlyBalances.get(sortedMonths.get(i));
        }

        XYSeries series = chart.addSeries("Balance",
                Arrays.stream(xData).mapToDouble(x -> x).toArray(),
                yData
        );

        series.setLineColor(yData[yData.length - 1] >= 0 ? Color.GREEN : Color.RED);
        series.setMarker(SeriesMarkers.CIRCLE);

        // Customize x-axis labels
        chart.getStyler().setxAxisTickLabelsFormattingFunction(idx -> {
            int index = (int) Math.round(idx);
            return index >= 0 && index < sortedMonths.size() ? sortedMonths.get(index) : "";
        });

        JFrame chartFrame = new JFrame("Income vs Expenses Line Chart");
        chartFrame.add(new XChartPanel<>(chart));
        chartFrame.pack();
        return chartFrame;
    }

    public static JFrame createCategoryBreakdownChart(List<ExpenseIncomeEntry> entries) {
        // Handle empty entries
        if (entries == null || entries.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "No entries available to create chart.",
                    "Chart Error",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        // Group entries by category and month
        Map<String, Map<String, Double>> categorySeries = calculateCategoryMonthlySeries(entries);

        XYChart chart = new XYChartBuilder()
                .width(1000)
                .height(600)
                .title("Category Breakdown")
                .xAxisTitle("Month")
                .yAxisTitle("Amount")
                .theme(Styler.ChartTheme.GGPlot2)
                .build();

        // Customize chart styler
        chart.getStyler().setLegendPosition(Styler.LegendPosition.OutsideE);
        chart.getStyler().setMarkerSize(8);

        // Predefined color palette
        Color[] colors = {
                Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE,
                Color.MAGENTA, Color.CYAN, Color.PINK
        };

        // Add series for each category
        int colorIndex = 0;
        for (Map.Entry<String, Map<String, Double>> categoryEntry : categorySeries.entrySet()) {
            String category = categoryEntry.getKey();
            Map<String, Double> monthlyData = categoryEntry.getValue();

            // Sort months chronologically
            List<String> sortedMonths = new ArrayList<>(monthlyData.keySet());
            Collections.sort(sortedMonths);

            // Use Integer stream for x-axis
            int[] xData = IntStream.range(0, sortedMonths.size()).toArray();
            double[] yData = new double[sortedMonths.size()];

            for (int i = 0; i < sortedMonths.size(); i++) {
                yData[i] = monthlyData.get(sortedMonths.get(i));
            }

            XYSeries series = chart.addSeries(category,
                    Arrays.stream(xData).mapToDouble(x -> x).toArray(),
                    yData
            );
            series.setLineColor(colors[colorIndex % colors.length]);
            series.setMarker(SeriesMarkers.CIRCLE);

            colorIndex++;
        }

        // Customize x-axis labels
        chart.getStyler().setxAxisTickLabelsFormattingFunction(idx -> {
            for (Map<String, Double> monthlyData : categorySeries.values()) {
                List<String> sortedMonths = new ArrayList<>(monthlyData.keySet());
                Collections.sort(sortedMonths);
                int index = (int) Math.round(idx);
                return index >= 0 && index < sortedMonths.size() ? sortedMonths.get(index) : "";
            }
            return "";
        });

        JFrame chartFrame = new JFrame("Category Breakdown Line Chart");
        chartFrame.add(new XChartPanel<>(chart));
        chartFrame.pack();
        return chartFrame;
    }

    private static Map<String, Double> calculateMonthlyBalances(List<ExpenseIncomeEntry> entries) {
        // Group entries by month and calculate cumulative balance
        Map<String, Double> monthlyBalances = new TreeMap<>();

        entries.stream()
                .sorted(Comparator.comparing(entry -> parseDate(entry.getDate())))
                .forEach(entry -> {
                    LocalDate entryDate = parseDate(entry.getDate());
                    String month = entryDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    monthlyBalances.merge(month, entry.getAmount(), Double::sum);
                });

        // Calculate cumulative balance
        double cumulative = 0;
        for (String month : new ArrayList<>(monthlyBalances.keySet())) {
            cumulative += monthlyBalances.get(month);
            monthlyBalances.put(month, cumulative);
        }

        return monthlyBalances;
    }

    private static Map<String, Map<String, Double>> calculateCategoryMonthlySeries(List<ExpenseIncomeEntry> entries) {
        // Extract unique categories from description
        Map<String, Map<String, Double>> categorySeries = new HashMap<>();

        entries.stream()
                .sorted(Comparator.comparing(entry -> parseDate(entry.getDate())))
                .forEach(entry -> {
                    LocalDate entryDate = parseDate(entry.getDate());
                    String category = extractCategory(entry.getDescription());
                    String month = entryDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

                    categorySeries
                            .computeIfAbsent(category, k -> new TreeMap<>())
                            .merge(month, entry.getAmount(), Double::sum);
                });

        return categorySeries;
    }

    private static String extractCategory(String description) {
        // Simple category extraction - you might want to enhance this
        description = description.toLowerCase().trim();

        // Predefined categories mapping
        if (description.contains("food") || description.contains("restaurant")) return "Food";
        if (description.contains("rent") || description.contains("housing")) return "Rent";
        if (description.contains("grocery") || description.contains("supermarket")) return "Groceries";
        if (description.contains("transport") || description.contains("fuel")) return "Transportation";
        if (description.contains("utility") || description.contains("bill")) return "Utilities";

        return "Miscellaneous";
    }
}