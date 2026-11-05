package com.yingchang.warehouse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** 接口保留原有字段名及 Excel 预览扩展字段，数据库记录保留列名。 */
public class Data extends LinkedHashMap<String, Object> {
    private static final ObjectMapper JSON = new ObjectMapper();
    public Data() {}
    public Data(Map<String, ?> values) { super(values); }
    public static Data of(Object... pairs) {
        Data value = new Data();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }
    public Data with(Object... pairs) { putAll(of(pairs)); return this; }
    public String s(String key) { return s(key, ""); }
    public String s(String key, String fallback) { Object value = get(key); return value == null || value.equals("") ? fallback : value.toString(); }
    public double n(String key) { return get(key) instanceof Number value ? value.doubleValue() : 0; }
    public int i(String key) { return (int) n(key); }
    public boolean b(String key) { return Boolean.TRUE.equals(get(key)); }
    public boolean has(String key) { return containsKey(key) && get(key) != null; }
    @SuppressWarnings("unchecked") public Data obj(String key) {
        Object value = get(key);
        if (value instanceof Data d) return d;
        Data d = value == null ? new Data() : new Data((Map<String, Object>) value);
        if (value != null) put(key, d);
        return d;
    }
    @SuppressWarnings("unchecked") public List<Data> rows(String key) {
        List<?> values = (List<?>) getOrDefault(key, List.of());
        List<Data> result = new ArrayList<>();
        for (Object value : values) result.add(value instanceof Data d ? d : new Data((Map<String, Object>) value));
        put(key, result);
        return result;
    }
    @SuppressWarnings("unchecked") public List<String> strings(String key) { return (List<String>) getOrDefault(key, List.of()); }
    public static String json(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static Data parse(String value) {
        try { return JSON.readValue(value, Data.class); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static Object parseValue(String value) {
        try { return JSON.readValue(value, new TypeReference<Object>() {}); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static Data copy(Data value) { return parse(json(value)); }
    public static String id() { return UUID.randomUUID().toString(); }
    public static String now() { return Instant.now().toString(); }
    public static String dayAfter(String date) { return LocalDate.parse(date).plusDays(1).toString(); }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    public static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[\\s'′’\"“”]", "").replace('呎', '尺'); }
    public static double ownerTwo(String partNo, double quantity) {
        String value = partNo.replaceAll("\\s", "");
        if (value.isEmpty()) return 0;
        if (value.equals("2")) return quantity;
        if (value.matches("2\\*\\d+")) {
            double two = Double.parseDouble(value.substring(2));
            if (two <= quantity) return two;
        }
        throw new BusinessError("SOURCE_INVALID", "货号须为空白、2或2*N，且2号库数量不能超过总数量", 400);
    }
}
