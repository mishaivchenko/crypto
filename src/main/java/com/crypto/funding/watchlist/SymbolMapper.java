//package com.crypto.funding.watchlist;
//
//public class SymbolMapper
//{
//    public static String toUnified(String input) {
//        if (input == null) return null;
//        String s = input.trim().toUpperCase();
//        s = s.replace("-", "/");
//        if (!s.contains("/")) s = s.replace("USDT", "/USDT");
//        if (!s.contains("/")) s = s.substring(0, Math.max(3, s.length() - 4)) + "/USDT";
//        return s;
//    }
//
//    public static String toExchange(String unified) {
//        return unified.replace("/", "");
//    }
//}
