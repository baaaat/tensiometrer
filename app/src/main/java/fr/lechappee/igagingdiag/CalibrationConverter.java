package fr.lechappee.igagingdiag;

import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Converts readings using only the bundled Wheel Fanatyk conversion chart. */
public final class CalibrationConverter {
    private static final String TABLE_ASSET = "wheel_fanatyk_conversion.csv";
    private final Map<String, TreeMap<Double, Double>> tables = new LinkedHashMap<>();

    public CalibrationConverter(AssetManager assets) throws IOException {
        load(assets);
    }

    private void load(AssetManager assets) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            assets.open(TABLE_ASSET), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new IOException("Empty calibration table");
            String[] columns = header.split(";", -1);
            for (int i = 1; i < columns.length; i++) {
                tables.put(columns[i].trim(), new TreeMap<>());
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] values = line.split(";", -1);
                if (values.length != columns.length) {
                    throw new IOException("Invalid calibration row: " + line);
                }
                double reading = Double.parseDouble(values[0].trim());
                for (int i = 1; i < values.length; i++) {
                    String value = values[i].trim();
                    if (!value.isEmpty()) tables.get(columns[i].trim())
                        .put(reading, Double.parseDouble(value));
                }
            }
        }
    }

    public Set<String> categories() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    /**
     * Returns an exact chart value or a linear interpolation between two published rows.
     * Returns null for unknown categories, gaps, and values outside the published range.
     */
    public Double toKgf(String correctedReading, String spokeCategory) {
        if (correctedReading == null || spokeCategory == null) return null;
        final double reading;
        try {
            reading = Double.parseDouble(correctedReading.trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            return null;
        }
        TreeMap<Double, Double> table = tables.get(spokeCategory.trim());
        if (table == null) return null;
        Double exact = table.get(reading);
        if (exact != null) return exact;
        Map.Entry<Double, Double> lower = table.floorEntry(reading);
        Map.Entry<Double, Double> upper = table.ceilingEntry(reading);
        if (lower == null || upper == null || lower.getKey().equals(upper.getKey())) return null;
        double fraction = (reading - lower.getKey()) / (upper.getKey() - lower.getKey());
        return lower.getValue() + fraction * (upper.getValue() - lower.getValue());
    }
}
