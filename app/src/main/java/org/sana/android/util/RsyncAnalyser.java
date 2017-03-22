package org.sana.android.util;

import org.apache.commons.codec.binary.Hex;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Generates Rsync data and perform analysis.
 */

public class RsyncAnalyser {

    private BufferedInputStream dataStream;
    private Adler32 adler;
    private MessageDigest md;

    public RsyncAnalyser() throws NoSuchAlgorithmException {
        this.adler = new Adler32();
        this.md = MessageDigest.getInstance("MD5");
    }

    public void update(BufferedInputStream dataStream) {
        this.dataStream = dataStream;
    }

    /**
     * Given two set of hashes for a supposedly possibly incomplete remote version of a file
     * (specified by update(BufferedInputStream)), generate instructions for the remote machine to recreate the file.
     *
     * @param remoteRollingChksms       a List of Adler-32 checksums calculated for the remote file
     * @param remoteMD5Chksms           a List of MD5 checksums calculated for the remote file
     * @param defaultBlockSize          the size of data blocks used to calculate checksums, in bytes
     * @param lastBlockSize             remote file size % default block size
     */
    public List<Object> generate(List<Long> remoteRollingChksms, List<String> remoteMD5Chksms, int defaultBlockSize,
                                 int lastBlockSize) throws IOException {
        // TODO: Have a list of tuples for the two checksums instead of two lists.
        // TODO: Differentiate between different types of errors
        if (this.dataStream == null) {
            return null;
        }

        // We mandate that the blocks must be of a uniform size
        if (lastBlockSize > defaultBlockSize) {
            // TODO: Error code here for invalid block sizes
            return null;
        }

        // Preprocess remote rolling checksums for faster lookup
        HashMap<Long, Integer> remoteRollingMap = this.preprocessRemoteRollingChecksums(remoteRollingChksms);

        byte[] fullBlock = new byte[defaultBlockSize];
        byte[] b = new byte[1];
        int bytesRead;

        // TODO: Get rid of these counters
        int i = 0;
        int lastBlockEnd = -1;
        byte previousFirstByte = 0;
        long rollingChecksum = 0;

        // Entire instructions set, unfragmented
        List<Object> instructions = new ArrayList<>();
        // A buffer for holding bytes until it is ready to be written/sent
        // TODO: This is not really useful right now, but could be useful if we use a generator approach in the future
        List<Byte> instrBuffer = new ArrayList<>();
        // Keep track of intermediate bytes between found blocks, since it could be more than one $(blockSize)
        List<Byte> blockBuffer = null;

        // For each local checksum (at every offset), check to see if it is present in the map.
        while (dataStream.available() > 0) {
            assert i > lastBlockEnd;
            // A previous calculation is available to do rolling checksum calculation on
            if ((i - lastBlockEnd > 1) && (rollingChecksum != 0)) {
                // Read 1 additional byte and do rolling checksum calculation
                bytesRead = this.dataStream.read(b);
                assert bytesRead == 1;
                previousFirstByte = blockBuffer.remove(0);
                instrBuffer.add(previousFirstByte);
                rollingChecksum = adler.calc(rollingChecksum, defaultBlockSize, previousFirstByte, b[0]);
                blockBuffer.add(b[0]);

                // Modify fullBlock so that we can calculate MD5 of the correct block.
                for (int idx = 1; idx < fullBlock.length; idx++) {
                    fullBlock[idx - 1] = fullBlock[idx];
                }
                fullBlock[fullBlock.length - 1] = b[0];
                bytesRead = defaultBlockSize;
            }
            // Start a new block for calculation.
            else {
                // Do full block read
                bytesRead = this.dataStream.read(fullBlock);

                // Changed so that incomplete blocks (in standard cases, usually the last block) still gets checked.
                // TODO: This still needs to be tested thoroughly.
                // Uncomment to make it such that the last block is sent as raw without checking.
                /*
                if (bytesRead < defaultBlockSize) {
                    // If we read less than a full block but there's still data left, something unexpected is wrong.
                    assert dataStream.available() == 0;

                    // Write all remaining bytes to instructions and update counters
                    blockBuffer = new ArrayList<>(Arrays.asList(this.toByteObjArray(fullBlock)));
                    instructions.add(blockBuffer);
                    lastBlockEnd = i + bytesRead;
                    break;

                }
                */
                rollingChecksum = adler.calc(fullBlock, bytesRead);
                blockBuffer = new ArrayList<>(Arrays.asList(this.toByteObjArray(fullBlock)));
            }

            Integer remoteIDX = remoteRollingMap.get(rollingChecksum);

            // Local block found in remote hashes
            if (remoteIDX != null) {
                // Validate MD5 checksum.
                if (remoteMD5Chksms.get(remoteIDX).equals(this.getMD5HashString(fullBlock, bytesRead))) {
                    if (instrBuffer.size() > 0) {
                        List<Byte> copy = new ArrayList<Byte>(instrBuffer);
                        instructions.add(copy);
                        instrBuffer.clear();
                    }
                    instructions.add(remoteIDX);
                    // TODO: Convert remoteRollingMap to <Long, List<Integer>> instead
                    // Prevent duplicate matches to same block
                    // If this scenario is actually valid, then the map would only create 1 entry for the colliding hashes

                    // Increment counters and go onto next iteration
                    lastBlockEnd = -1;
                    i = 0;
                    blockBuffer = null;
                    continue;
                }
                // MD5 doesn't match; we assume it's a coincidence
            }
            i++;
        }

        // Clear buffers one last time
        if (blockBuffer != null) {
            instrBuffer.addAll(blockBuffer);
        }
        if (instrBuffer.size() > 0) {
            System.out.println(String.format("Last buffer size: %d", instrBuffer.size()));
            instructions.add(instrBuffer);
        }

        System.out.println("-- Instructions --");
        System.out.println(instructions);
        System.out.println();

        return instructions;
    }

    /**
     * Given a list of checksums, convert it into a HashMap of its checksum values to its indices.
     *
     * @param remoteRollingChksms       a List of Adler-32 rolling checksums
     * @return                          a mapping of checksums to its index
     */
    private HashMap<Long, Integer> preprocessRemoteRollingChecksums(List<Long> remoteRollingChksms) {
        HashMap<Long, Integer> ret = new HashMap<>();
        for (int i = 0; i < remoteRollingChksms.size(); i++) {
            ret.put(remoteRollingChksms.get(i), i);
        }
        return ret;
    }

    /**
     * Given a block of data, return a String representation of its MD5 checksum.
     *
     * @param block         the block of data to calculate the checksum for
     * @param len           the length of the data block to calculate on (can be less than block.length)
     * @return              a String representation of the MD5 checksum for the data block
     */
    private String getMD5HashString(byte[] block, int len) {
        this.md.reset();
        this.md.update(block, 0, len);
        byte[] md5 = md.digest();
        return encodeHexString(md5);
    }

    private static final char[] DIGITS_LOWER =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private String encodeHexString(byte[] md5) {
        final int l = md5.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = DIGITS_LOWER[(0xF0 & md5[i]) >>> 4];
            out[j++] = DIGITS_LOWER[0x0F & md5[i]];
        }
        return new String(out);
    }

    /**
     * Convert a primitive byte[] to a Byte[]
     *
     * @param prim          a primitive byte[] to convert
     * @return              the converted Byte[]
     */
    private Byte[] toByteObjArray(byte[] prim) {
        // http://stackoverflow.com/questions/6430841/java-byte-to-byte
        Byte[] ret = new Byte[prim.length];
        int i = 0;
        for (byte b : prim) ret[i++] = b;
        return ret;
    }
}
