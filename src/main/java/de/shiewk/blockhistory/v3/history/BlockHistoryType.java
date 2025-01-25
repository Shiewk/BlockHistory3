package de.shiewk.blockhistory.v3.history;

public enum BlockHistoryType {
    PLACE("PLACED"),
    BREAK("BROKEN"),
    EMPTY_BUCKET("PLACED USING BUCKET"),
    FILL_BUCKET("PICKED UP USING BUCKET"),
    EXPLODE_END_CRYSTAL("EXPLODED USING END CRYSTAL"),
    EXPLODE_TNT("EXPLODED USING TNT"),
    EXPLODE_CREEPER("EXPLODED USING CREEPER"),
    EXPLODE_BLOCK("EXPLODED USING BLOCK"),
    SIGN("CHANGED"),
    CHEST_OPEN("OPENED");

    public final String displayName;

    BlockHistoryType(String displayName) {
        this.displayName = displayName;
    }
}
