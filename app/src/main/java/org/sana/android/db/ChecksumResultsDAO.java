package org.sana.android.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores rolling and md5 checksum results.
 */
public class ChecksumResultsDAO {

    private List<Long> rolling;
    private List<String> md5;
    private String fileName;

    public List<Long> getRolling() {
        return this.rolling;
    }

    public void setRolling(ArrayList<Long> rolling) {
        this.rolling = rolling;
    }

    public List<String> getMd5() {
        return this.md5;
    }

    public void setMd5(ArrayList<String> md5) {
        this.md5 = md5;
    }

    public String getFileName() { return this.fileName; }

    public void setFileName(String fileName) { this.fileName = fileName; }
}
