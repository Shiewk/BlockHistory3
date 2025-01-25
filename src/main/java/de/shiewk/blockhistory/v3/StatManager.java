package de.shiewk.blockhistory.v3;

public final class StatManager {

    private final long startTime = System.nanoTime();
    private long elementsWritten = 0;
    private long bytesWritten = 0;

    StatManager(){}

    void elementWritten(){
        elementsWritten++;
    }

    void bytesWritten(int bytes){
        bytesWritten += bytes;
    }

    public long getElementsWritten() {
        return elementsWritten;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getTimeMsSinceStart(){
        return (System.nanoTime() - getStartTime()) / 1000000;
    }

    public long getTimeSecondsSinceStart(){
        return getTimeMsSinceStart() / 1000;
    }

    public float getElementsWrittenPerSecond() {
        return Math.round((float) getElementsWritten() / getTimeSecondsSinceStart() * 10f) / 10f;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public float getBytesWrittenPerSecond() {
        return Math.round((float) getBytesWritten() / getTimeSecondsSinceStart() * 10f) / 10f;
    }
}
