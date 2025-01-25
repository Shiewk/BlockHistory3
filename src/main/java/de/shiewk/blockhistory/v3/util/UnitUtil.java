package de.shiewk.blockhistory.v3.util;

public final class UnitUtil {
    private UnitUtil(){}

    public static String formatTime(long totalMillis){
        long millis = totalMillis % 1000;
        long seconds = totalMillis / 1000 % 60;
        long minutes = totalMillis / 60000 % 60;
        long hours = totalMillis / 3600000 % 24;
        long days = totalMillis / 86400000;

        StringBuilder builder = new StringBuilder();

        if (days != 0){
            builder.append(days).append("d ");
        }
        if (hours != 0){
            builder.append(hours).append("h ");
        }
        if (minutes != 0){
            builder.append(minutes).append("m ");
        }
        if (seconds != 0){
            builder.append(seconds).append("s ");
        }
        if (builder.isEmpty() || millis != 0){
            builder.append(millis).append("ms");
        }
        return builder.toString().trim();
    }

    public static String formatDataSize(double bytes) {
        String[] suffixes = new String[]{"KiB", "MiB", "GiB"};
        String suffix = "bytes";
        int i = -1;
        while (bytes > 1024 && ++i < suffixes.length){
            bytes /= 1024;
            suffix = suffixes[i];
        }
        return Math.floor(bytes * 10d) / 10d + " " + suffix;
    }

    public static byte getBlockChunkLocation(int x){
        byte b = (byte) (x % 16);
        if (b < 0) b = (byte) (16 + b);
        return b;
    }
}
