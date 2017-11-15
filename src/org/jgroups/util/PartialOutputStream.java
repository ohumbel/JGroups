package org.jgroups.util;

import java.io.DataOutput;
import java.io.IOException;

/**
 * Decorates an output stream ({@link java.io.DataOutput}) and writes only a subset (range at offset/length) of the
 * original data to the underlying output stream.
 * @author Bela Ban
 * @since  5.0
 */
public class PartialOutputStream extends BaseDataOutputStream {
    protected final DataOutput  out;
    protected final int         offset, length;

    public PartialOutputStream(DataOutput out, int offset, int length) {
        this.out=out;
        this.offset=offset;
        this.length=length;
    }

    public void write(int b) {
        int end_range=offset+length;
        if(pos >= end_range)         // greater than range
            return;
        if(pos < offset)             // smaller than range
            pos++;
        else {                       // in range
            if(pos+1 <= end_range)
                _write(b);
        }
    }

    public void write(byte[] b, int off, int len) {
        int end_range=offset+length;
        if(pos >= end_range)         // greater than range
            return;
        if(pos < offset) {           // smaller than range
            int bytes_to_write=offset-pos;
            if(bytes_to_write >= len) {
                pos+=len;
                return;
            }
            pos+=bytes_to_write;
            write(b, off+bytes_to_write, len-bytes_to_write);
        }
        else {                       // in range
            int bytes_to_be_written=Math.min(len, end_range-pos);
            _write(b, off, bytes_to_be_written);
        }
    }

    public String toString() {
        return String.format("%s, off=%d, len=%d", super.toString(), offset, length);
    }

    protected void ensureCapacity(int bytes) {
    }

    protected void _write(int b) {
        try {
            out.write(b);
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            pos++;
        }
    }

    protected void _write(byte[] b, int off, int len) {
        try {
            out.write(b, off, len);
        }
        catch(IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            pos+=len;
        }
    }

    /** Checks if pos is in range [offset .. offset+length] */
    protected boolean isInRange() {
        return pos >= offset && pos < offset+length;
    }
}
