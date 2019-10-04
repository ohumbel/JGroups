
package org.jgroups;


import org.jgroups.conf.ClassConfigurator;
import org.jgroups.util.ByteArray;
import org.jgroups.util.Headers;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

/**
 * A common superclass for all {@link Message} implementations. It contains functionality to manage headers, flags and
 * destination and source addresses.
 *
 * @since  5.0
 * @author Bela Ban
 */
public abstract class BaseMessage implements Message {
    protected Address           dest;
    protected Address           sender;
    protected volatile Header[] headers;
    protected volatile short    flags;
    protected volatile byte     transient_flags; // transient_flags is neither marshalled nor copied

    static final byte           DEST_SET         =  1;
    static final byte           SRC_SET          =  1 << 1;


    /**
    * Constructs a message given a destination address
    * @param dest The Address of the receiver. If it is null, then the message is sent to the group. Otherwise, it is
    *             sent to a single member.
    */
    public BaseMessage(Address dest) {
        setDest(dest);
        headers=createHeaders(Util.DEFAULT_HEADERS);
    }

   /**
    * Constructs a message given a destination and source address and the payload byte buffer
    * @param dest The Address of the receiver. If it is null, then the message is sent to the group. Otherwise, it is
    *             sent to a single member.
    * @param buf The payload. Note that this buffer must not be modified (e.g. buf[0]='x' is not
    *           allowed) since we don't copy the contents.
    */
    public BaseMessage(Address dest, byte[] buf) {
        this(dest, buf, 0, buf != null? buf.length : 0);
    }


   /**
    * Constructs a message. The index and length parameters provide a reference to a byte buffer, rather than a copy,
    * and refer to a subset of the buffer. This is important when we want to avoid copying. When the message is
    * serialized, only the subset is serialized.</p>
    * <em>
    * Note that the byte[] buffer passed as argument must not be modified. Reason: if we retransmit the
    * message, it would still have a ref to the original byte[] buffer passed in as argument, and so we would
    * retransmit a changed byte[] buffer !
    * </em>
    *
    * @param dest The Address of the receiver. If it is null, then the message is sent to the group. Otherwise, it is
    *             sent to a single member.
    * @param buf A reference to a byte buffer
    * @param offset The index into the byte buffer
    * @param length The number of bytes to be used from <tt>buf</tt>. Both index and length are checked
    *           for array index violations and an ArrayIndexOutOfBoundsException will be thrown if invalid
    */
    public BaseMessage(Address dest, byte[] buf, int offset, int length) {
        this(dest);
        setArray(buf, offset, length);
    }


    public BaseMessage(Address dest, ByteArray buf) {
        this(dest);
        setArray(buf);
    }


   /**
    * Constructs a message given a destination and source address and the payload object
    * @param dest The Address of the receiver. If it is null, then the message is sent to the group. Otherwise, it is
    *             sent to a single member.
    * @param obj The object that will be marshalled into the byte buffer. Has to be serializable (e.g. implementing
    *            Serializable, Externalizable or Streamable, or be a basic type (e.g. Integer, Short etc)).
    */
    public BaseMessage(Address dest, Object obj) {
        this(dest);
        setObject(obj);
    }


    public BaseMessage() {
        this(true);
    }


    public BaseMessage(boolean create_headers) {
        if(create_headers)
            headers=createHeaders(Util.DEFAULT_HEADERS);
    }


    public Address             getDest()                 {return dest;}
    public Message             setDest(Address new_dest) {dest=new_dest; return this;}
    public Address             getSrc()                  {return sender;}
    public Message             setSrc(Address new_src)   {sender=new_src; return this;}
    public int                 getNumHeaders()           {return Headers.size(this.headers);}
    public Map<Short,Header>   getHeaders()              {return Headers.getHeaders(this.headers);}
    public String              printHeaders()            {return Headers.printHeaders(this.headers);}


    /**
     * Sets a number of flags in a message
     * @param flags The flag or flags
     * @return A reference to the message
     */
    public Message setFlag(Flag... flags) {
        if(flags != null) {
            short tmp=this.flags;
            for(Flag flag : flags) {
                if(flag != null)
                    tmp|=flag.value();
            }
            this.flags=tmp;
        }
        return this;
    }

    /**
     * Same as {@link #setFlag(Flag...)} except that transient flags are not marshalled
     * @param flags The flag
     */
    public Message setFlag(TransientFlag... flags) {
        if(flags != null) {
            short tmp=this.transient_flags;
            for(TransientFlag flag : flags)
                if(flag != null)
                    tmp|=flag.value();
            this.transient_flags=(byte)tmp;
        }
        return this;
    }


    public Message setFlag(short flag, boolean transient_flags) {
        short tmp=transient_flags? this.transient_flags : this.flags;
        tmp|=flag;
        if(transient_flags)
            this.transient_flags=(byte)tmp;
        else
            this.flags=tmp;
        return this;
    }


    /**
     * Returns the internal representation of flags. Don't use this, as the internal format might change at any time !
     * This is only used by unit test code
     * @return
     */
    public short getFlags(boolean transient_flags) {return transient_flags? this.transient_flags : flags;}

    /**
     * Clears a number of flags in a message
     * @param flags The flags
     * @return A reference to the message
     */
    public Message clearFlag(Flag... flags) {
        if(flags != null) {
            short tmp=this.flags;
            for(Flag flag : flags)
                if(flag != null)
                    tmp&=~flag.value();
            this.flags=tmp;
        }
        return this;
    }

    public Message clearFlag(TransientFlag... flags) {
        if(flags != null) {
            short tmp=this.transient_flags;
            for(TransientFlag flag : flags)
                if(flag != null)
                    tmp&=~flag.value();
            this.transient_flags=(byte)tmp;
        }
        return this;
    }

    /**
     * Checks if a given flag is set
     * @param flag The flag
     * @return Whether or not the flag is currently set
     */
    public boolean isFlagSet(Flag flag) {
        return Util.isFlagSet(flags, flag);
    }

    public boolean isFlagSet(TransientFlag flag) {
        return Util.isTransientFlagSet(transient_flags, flag);
    }

    /**
    * Atomically checks if a given flag is set and - if not - sets it. When multiple threads
    * concurrently call this method with the same flag, only one of them will be able to set the
    * flag
    *
    * @param flag
    * @return True if the flag could be set, false if not (was already set)
    */
    public synchronized boolean setFlagIfAbsent(TransientFlag flag) {
        if(isFlagSet(flag))
            return false;
        setFlag(flag);
        return true;
    }


    /*---------------------- Used by protocol layers ----------------------*/

    /** Puts a header given an ID into the hashmap. Overwrites potential existing entry. */
    public Message putHeader(short id, Header hdr) {
        if(id < 0)
            throw new IllegalArgumentException("An ID of " + id + " is invalid");
        if(hdr != null)
            hdr.setProtId(id);
        synchronized(this) {
            Header[] resized_array=Headers.putHeader(this.headers, id, hdr, true);
            if(resized_array != null)
                this.headers=resized_array;
        }
        return this;
    }



    public <T extends Header> T getHeader(short id) {
        if(id <= 0)
            throw new IllegalArgumentException("An ID of " + id + " is invalid. Add the protocol which calls " +
                                                 "getHeader() to jg-protocol-ids.xml");
        return Headers.getHeader(this.headers, id);
    }

    /*---------------------------------------------------------------------*/


    public String toString() {
        return String.format("[%s to %s, %d bytes%s%s]",
                             sender,
                             dest == null? "<all>" : dest,
                             getLength(),
                             flags > 0? ", flags=" + Util.flagsToString(flags) : "",
                             transient_flags > 0? ", transient_flags=" + Util.transientFlagsToString(transient_flags) : "");
    }



    public int serializedSize() {
        return size();
    }

    public int size() {
        int retval=Global.BYTE_SIZE // leading byte
          + Global.SHORT_SIZE;      // flags
        if(dest != null)
            retval+=Util.size(dest);
        if(sender != null)
            retval+=Util.size(sender);

        retval+=Global.SHORT_SIZE;  // number of headers
        retval+=Headers.marshalledSize(this.headers);
        return retval;
    }


    public void writeTo(DataOutput out) throws IOException {
        byte leading=0;

        if(dest != null)
            leading=Util.setFlag(leading, DEST_SET);

        if(sender != null)
            leading=Util.setFlag(leading, SRC_SET);

        // 1. write the leading byte first
        out.write(leading);

        // 2. the flags (e.g. OOB, LOW_PRIO), skip the transient flags
        out.writeShort(flags);

        // 3. dest_addr
        if(dest != null)
            Util.writeAddress(dest, out);

        // 4. src_addr
        if(sender != null)
            Util.writeAddress(sender, out);

        // 5. headers
        Header[] hdrs=this.headers;
        int size=Headers.size(hdrs);
        out.writeShort(size);
        if(size > 0) {
            for(Header hdr : hdrs) {
                if(hdr == null)
                    break;
                out.writeShort(hdr.getProtId());
                writeHeader(hdr, out);
            }
        }
    }

    public void writeToNoAddrs(Address src, DataOutput out, short... excluded_headers) throws IOException {
        byte leading=0;

        boolean write_src_addr=src == null || sender != null && !sender.equals(src);

        if(write_src_addr)
            leading=Util.setFlag(leading, SRC_SET);

        // 1. write the leading byte first
        out.write(leading);

        // 2. the flags (e.g. OOB, LOW_PRIO)
        out.writeShort(flags);

        // 4. src_addr
        if(write_src_addr)
            Util.writeAddress(sender, out);

        // 5. headers
        Header[] hdrs=this.headers;
        int size=Headers.size(hdrs, excluded_headers);
        out.writeShort(size);
        if(size > 0) {
            for(Header hdr : hdrs) {
                if(hdr == null)
                    break;
                short id=hdr.getProtId();
                if(Util.containsId(id, excluded_headers))
                    continue;
                out.writeShort(id);
                writeHeader(hdr, out);
            }
        }
    }


    public void readFrom(DataInput in) throws IOException, ClassNotFoundException {
        // 1. read the leading byte first
        byte leading=in.readByte();

        // 2. the flags
        flags=in.readShort();

        // 3. dest_addr
        if(Util.isFlagSet(leading, DEST_SET))
            dest=Util.readAddress(in);

        // 4. src_addr
        if(Util.isFlagSet(leading, SRC_SET))
            sender=Util.readAddress(in);

        // 5. headers
        int len=in.readShort();
        this.headers=createHeaders(len);
        for(int i=0; i < len; i++) {
            short id=in.readShort();
            Header hdr=readHeader(in).setProtId(id);
            this.headers[i]=hdr;
        }
    }



    protected static void writeHeader(Header hdr, DataOutput out) throws IOException {
        short magic_number=hdr.getMagicId();
        out.writeShort(magic_number);
        hdr.writeTo(out);
    }



    protected static Header readHeader(DataInput in) throws IOException, ClassNotFoundException {
        short magic_number=in.readShort();
        Header hdr=ClassConfigurator.create(magic_number);
        hdr.readFrom(in);
        return hdr;
    }

    protected static Header[] createHeaders(int size) {
        return size > 0? new Header[size] : new Header[3];
    }


}
