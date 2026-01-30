package com.crypto.funding.utills;

// SymbolMapper.java (идея)
public final class SymbolMapper {
    public static String toUnified(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase().replace('-', '/');
        if (!s.contains("/")) {
            if (s.endsWith("USDT")) {
                s = s.replace("USDT", "/USDT");
            } else {
                s = s + "/USDT";
            }
        }
        if (s.contains( "_" )) s = s.replace( "_", "" );
        return s;
    }

    public static String toExchange( String unified )
    {
        return unified.replace( "/", "" );
    }

    public static String toBinance(String unified) { return unified.replace("/", ""); }     // SDUSDT
    public static String toBybit(String unified)   { return unified.replace("/", ""); }     // SDUSDT
    public static String toGate(String unified)    { return unified.split("/")[0] + "_USDT";} // SD_USDT
}
