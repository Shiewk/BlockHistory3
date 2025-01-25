package de.shiewk.blockhistory.v3.exception;

public class LowDiskSpaceException extends Exception {

    private final long freeBytes;

    public LowDiskSpaceException(long freeBytes){
        super("Free disk space is too low to safely execute this action: " + freeBytes + " bytes");
        this.freeBytes = freeBytes;
    }

    public long getFreeBytes() {
        return freeBytes;
    }
}
