package com.nirvana.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * Test rows made by copying a seeded row and changing some columns, so a test doesn't have to know
 * every NOT NULL column of a wide table. Unique columns not given are blanked, or made unique.
 */
final class GstRows {
    private GstRows() {}

    static long copy(JdbcTemplate db, String table, long fromId, Map<String, Object> changes) {
        var columns = db.queryForList("""
                SELECT COLUMN_NAME AS name, IS_NULLABLE AS nullable, EXTRA AS extra, DATA_TYPE AS type FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION""", table);
        Set<String> unique = new HashSet<>(db.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?
                AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY'""", String.class, table));
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (var c : columns) {
            String name = c.get("name").toString();
            if (c.get("extra").toString().contains("auto_increment")) continue;
            names.add("`" + name + "`");
            if (changes.containsKey(name)) {
                values.add("?");
                args.add(changes.get(name));
            } else if (unique.contains(name)) {
                if ("YES".equals(c.get("nullable"))) values.add("NULL");
                else {
                    values.add("?");
                    args.add(UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
                }
            } else {
                values.add("`" + name + "`");
            }
        }
        args.add(fromId);
        String sql = "INSERT INTO `" + table + "` (" + String.join(",", names) + ") SELECT " + String.join(",", values)
                + " FROM `" + table + "` WHERE id=?";
        var keys = new GeneratedKeyHolder();
        db.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    static String reference() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
