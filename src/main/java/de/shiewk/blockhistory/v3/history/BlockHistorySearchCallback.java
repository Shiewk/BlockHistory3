package de.shiewk.blockhistory.v3.history;

import java.io.FileNotFoundException;

public interface BlockHistorySearchCallback {

    void onElementFound(BlockHistoryElement element);

    void onNoFilePresent(FileNotFoundException e);

}
