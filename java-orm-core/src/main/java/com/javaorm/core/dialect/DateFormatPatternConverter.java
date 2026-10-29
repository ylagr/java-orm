package com.javaorm.core.dialect;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨数据库日期时间格式化模板转换器。
 * 统一接受标准 Java 格式（如 "yyyy-MM-dd HH:mm:ss"）或 POSIX 格式（如 "%Y-%m-%d %H:%i:%s"），
 * 自动无缝转换为各目标数据库方言（MySQL, Oracle, PostgreSQL, SQLite, H2）原生适配的格式字符串。
 */
public class DateFormatPatternConverter {

    private enum Token {
        YEAR4,
        YEAR2,
        MONTH,
        DAY,
        HOUR24,
        HOUR12,
        MINUTE,
        SECOND,
        LITERAL
    }

    private static class TokenPart {
        final Token token;
        final String text;

        TokenPart(Token token, String text) {
            this.token = token;
            this.text = text;
        }
    }

    public static String toMySQL(String pattern) {
        List<TokenPart> parts = parse(pattern);
        StringBuilder sb = new StringBuilder();
        for (TokenPart part : parts) {
            switch (part.token) {
                case YEAR4: sb.append("%Y"); break;
                case YEAR2: sb.append("%y"); break;
                case MONTH: sb.append("%m"); break;
                case DAY: sb.append("%d"); break;
                case HOUR24: sb.append("%H"); break;
                case HOUR12: sb.append("%h"); break;
                case MINUTE: sb.append("%i"); break;
                case SECOND: sb.append("%s"); break;
                case LITERAL: sb.append(part.text); break;
            }
        }
        return sb.toString();
    }

    public static String toOracle(String pattern) {
        List<TokenPart> parts = parse(pattern);
        StringBuilder sb = new StringBuilder();
        for (TokenPart part : parts) {
            switch (part.token) {
                case YEAR4: sb.append("YYYY"); break;
                case YEAR2: sb.append("YY"); break;
                case MONTH: sb.append("MM"); break;
                case DAY: sb.append("DD"); break;
                case HOUR24: sb.append("HH24"); break;
                case HOUR12: sb.append("HH12"); break;
                case MINUTE: sb.append("MI"); break;
                case SECOND: sb.append("SS"); break;
                case LITERAL: sb.append(part.text); break;
            }
        }
        return sb.toString();
    }

    public static String toPostgreSQL(String pattern) {
        return toOracle(pattern);
    }

    public static String toSQLite(String pattern) {
        List<TokenPart> parts = parse(pattern);
        StringBuilder sb = new StringBuilder();
        for (TokenPart part : parts) {
            switch (part.token) {
                case YEAR4: sb.append("%Y"); break;
                case YEAR2: sb.append("%y"); break;
                case MONTH: sb.append("%m"); break;
                case DAY: sb.append("%d"); break;
                case HOUR24: sb.append("%H"); break;
                case HOUR12: sb.append("%I"); break;
                case MINUTE: sb.append("%M"); break;
                case SECOND: sb.append("%S"); break;
                case LITERAL: sb.append(part.text); break;
            }
        }
        return sb.toString();
    }

    public static String toH2(String pattern) {
        List<TokenPart> parts = parse(pattern);
        StringBuilder sb = new StringBuilder();
        for (TokenPart part : parts) {
            switch (part.token) {
                case YEAR4: sb.append("yyyy"); break;
                case YEAR2: sb.append("yy"); break;
                case MONTH: sb.append("MM"); break;
                case DAY: sb.append("dd"); break;
                case HOUR24: sb.append("HH"); break;
                case HOUR12: sb.append("hh"); break;
                case MINUTE: sb.append("mm"); break;
                case SECOND: sb.append("ss"); break;
                case LITERAL: sb.append(part.text); break;
            }
        }
        return sb.toString();
    }

    public static String toCanonical(String pattern) {
        return toH2(pattern);
    }

    private static List<TokenPart> parse(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            pattern = "yyyy-MM-dd HH:mm:ss";
        }

        // 统一预归一化：若为 POSIX 或 Oracle 格式先转换为标准 Java Token 序列
        if (pattern.contains("%")) {
            return parsePosix(pattern);
        } else if (pattern.contains("HH24") || pattern.contains("YYYY") || pattern.contains("MI")) {
            return parseOracle(pattern);
        } else {
            return parseJava(pattern);
        }
    }

    private static List<TokenPart> parseJava(String pattern) {
        List<TokenPart> list = new ArrayList<>();
        int len = pattern.length();
        int i = 0;
        while (i < len) {
            if (pattern.startsWith("yyyy", i)) {
                list.add(new TokenPart(Token.YEAR4, "yyyy"));
                i += 4;
            } else if (pattern.startsWith("yy", i)) {
                list.add(new TokenPart(Token.YEAR2, "yy"));
                i += 2;
            } else if (pattern.startsWith("MM", i)) {
                list.add(new TokenPart(Token.MONTH, "MM"));
                i += 2;
            } else if (pattern.startsWith("dd", i)) {
                list.add(new TokenPart(Token.DAY, "dd"));
                i += 2;
            } else if (pattern.startsWith("HH", i)) {
                list.add(new TokenPart(Token.HOUR24, "HH"));
                i += 2;
            } else if (pattern.startsWith("hh", i)) {
                list.add(new TokenPart(Token.HOUR12, "hh"));
                i += 2;
            } else if (pattern.startsWith("mm", i)) {
                list.add(new TokenPart(Token.MINUTE, "mm"));
                i += 2;
            } else if (pattern.startsWith("ss", i)) {
                list.add(new TokenPart(Token.SECOND, "ss"));
                i += 2;
            } else {
                list.add(new TokenPart(Token.LITERAL, String.valueOf(pattern.charAt(i))));
                i++;
            }
        }
        return list;
    }

    private static List<TokenPart> parsePosix(String pattern) {
        List<TokenPart> list = new ArrayList<>();
        int len = pattern.length();
        int i = 0;
        while (i < len) {
            if (pattern.startsWith("%Y", i)) {
                list.add(new TokenPart(Token.YEAR4, "yyyy"));
                i += 2;
            } else if (pattern.startsWith("%y", i)) {
                list.add(new TokenPart(Token.YEAR2, "yy"));
                i += 2;
            } else if (pattern.startsWith("%m", i)) {
                list.add(new TokenPart(Token.MONTH, "MM"));
                i += 2;
            } else if (pattern.startsWith("%d", i)) {
                list.add(new TokenPart(Token.DAY, "dd"));
                i += 2;
            } else if (pattern.startsWith("%H", i)) {
                list.add(new TokenPart(Token.HOUR24, "HH"));
                i += 2;
            } else if (pattern.startsWith("%h", i) || pattern.startsWith("%I", i)) {
                list.add(new TokenPart(Token.HOUR12, "hh"));
                i += 2;
            } else if (pattern.startsWith("%i", i) || pattern.startsWith("%M", i)) {
                list.add(new TokenPart(Token.MINUTE, "mm"));
                i += 2;
            } else if (pattern.startsWith("%s", i) || pattern.startsWith("%S", i)) {
                list.add(new TokenPart(Token.SECOND, "ss"));
                i += 2;
            } else {
                list.add(new TokenPart(Token.LITERAL, String.valueOf(pattern.charAt(i))));
                i++;
            }
        }
        return list;
    }

    private static List<TokenPart> parseOracle(String pattern) {
        List<TokenPart> list = new ArrayList<>();
        int len = pattern.length();
        int i = 0;
        while (i < len) {
            if (pattern.startsWith("YYYY", i)) {
                list.add(new TokenPart(Token.YEAR4, "yyyy"));
                i += 4;
            } else if (pattern.startsWith("YY", i)) {
                list.add(new TokenPart(Token.YEAR2, "yy"));
                i += 2;
            } else if (pattern.startsWith("HH24", i)) {
                list.add(new TokenPart(Token.HOUR24, "HH"));
                i += 4;
            } else if (pattern.startsWith("HH12", i)) {
                list.add(new TokenPart(Token.HOUR12, "hh"));
                i += 4;
            } else if (pattern.startsWith("MI", i)) {
                list.add(new TokenPart(Token.MINUTE, "mm"));
                i += 2;
            } else if (pattern.startsWith("SS", i)) {
                list.add(new TokenPart(Token.SECOND, "ss"));
                i += 2;
            } else if (pattern.startsWith("MM", i)) {
                list.add(new TokenPart(Token.MONTH, "MM"));
                i += 2;
            } else if (pattern.startsWith("DD", i)) {
                list.add(new TokenPart(Token.DAY, "dd"));
                i += 2;
            } else {
                list.add(new TokenPart(Token.LITERAL, String.valueOf(pattern.charAt(i))));
                i++;
            }
        }
        return list;
    }
}
