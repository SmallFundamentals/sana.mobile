package org.sana.android.util;

/**
 * Created by Errin on 12/03/2017.
 */


import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.lang.Math;
/**
 * Implementation of Mark Adler's checksum algorithm which enables rolling calculation
 * for faster computation.
 *
 * See: https://en.wikipedia.org/wiki/Adler-32#The_algorithm
 */
public class Adler32 {

    // Large prime number used for Adler-32 calculation
    private final int MOD_ADLER = 65521;
    private final HashMap<Long, Map.Entry<Long, Long>> ABmap;

    public Adler32() {
        this.ABmap = new HashMap<>();
    }

    /**
     * Calculates the Adler32 checksum for a given block of data.
     *
     * @param block         the block of data to calculate the checksum for
     * @param len           the length of the data block to calculate on (can be less than block.length)
     * @return              a long representation of the Adler32 checksum for the data block
     */
    public long calc(byte[] block, int len) {
        long result;
        long a = 0;
        long b = 0;
        for (int i = 0; i < len; i++) {
            a += (long) block[i];
            b += (len - i) * (long) block[i];
        }

        // https://en.wikipedia.org/wiki/Modulo_operation
        // Java's Math.floorMod == Python's %

        //Java 7 does not support floorMod. Replacing with customized floorMod


        a = floorMod(a, this.MOD_ADLER);
        b = floorMod(b, this.MOD_ADLER);

        result = this.getChecksumForIntermediateValues(a, b);
        this.cacheIntermediateValues(a, b, result);
        return result;
    }

    private long floorMod(long num, long modAdler) {
        //modAdler seems to be always positive from our current usage
        //so result should always be positive according to python's % algo
        long result = num % modAdler;
        if(result < 0) result += modAdler;
        return result;
    }
    /**
     * Calculates the Adler32 checksum in a rolling fashion given a previous result and the next byte.
     *
     * e.g. for byte[] b = {1, 2, 3, ..., 1024, 1025, ...}
     *
     * If we have a previous result calculated using b[1:1024], we can find the result of [2:1025] through
     * this method using the previous result.
     *
     * At least 1 call of calc(byte[], int) must be made prior to calling this function.
     *
     * @param adler32Value          a Adler32 checksum previously calculated
     * @param len                   the length of the data block used for this and the previous calculation
     * @param previousFirstByte     the first byte from the previous calculation
     * @param nextByte              the next byte of data
     * @return                      a long representation of the Adler32 checksum for the data block
     */
    public long calc(long adler32Value, int len, byte previousFirstByte, byte nextByte) {
        Map.Entry<Long, Long> pair = this.ABmap.get(adler32Value);
        long result;
        long a = pair.getKey();
        long b = pair.getValue();

        // https://rsync.samba.org/tech_report/node3.html
        a -= (long) previousFirstByte;
        a += (long) nextByte;
        b -= len * (long) previousFirstByte;
        b += a;

        a = floorMod(a, this.MOD_ADLER);
        b = floorMod(b, this.MOD_ADLER);
        result = this.getChecksumForIntermediateValues(a, b);
        this.cacheIntermediateValues(a, b, result);
        return result;
    }

    /**
     * Given intermediate values a & b for Adler-32 calculation, return the final checksum.
     *
     * @param a     one of two intermediate values from the checksum calculation process
     * @param b     one of two intermediate values from the checksum calculation process
     * @return      the calculated Adler-32 checksum
     */
    private long getChecksumForIntermediateValues(long a, long b) {
        return (long) (a + (b * Math.pow(2, 16)));
    }

    /**
     * Store the intermediate values in a HashMap, using the resulting checksum as the key.
     *
     * This is useful for calculating the rolling checksum, which allows us to use the intermediate
     * values to quickly calculate the checksum for an overlapping, subsequent block of data.
     *
     * @param a             one of two intermediate values from the checksum calculation process
     * @param b             one of two intermediate values from the checksum calculation process
     * @param checksum      the resulting checksum from a & b
     */
    private void cacheIntermediateValues(long a, long b, long checksum) {
        Map.Entry<Long, Long> value = new AbstractMap.SimpleEntry<>(a, b);
        this.ABmap.put(checksum, value);
    }
}