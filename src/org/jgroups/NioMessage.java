
package org.jgroups;


import org.jgroups.util.ByteArray;
import org.jgroups.util.Headers;
import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * A {@link Message} with a heap-based {@link java.nio.ByteBuffer} as payload.<br/>
 * <br/>
 * Note that the payload of an NioMessage must not be modified after sending it (ie. {@link JChannel#send(Message)};
 * serialization depends on position and limit to be correct.
 *
 * @since  5.0
 * @author Bela Ban
 */
public class NioMessage extends BaseMessage {

    /** The payload */
    protected ByteBuffer buf;


    /**
     * Constructs a message given a destination address
     * @param dest The Address of the receiver. If it is null, then the message is sent to all cluster members.
     *            Otherwise, it is sent to a single member.
     */
    public NioMessage(Address dest) {
        super(dest);
    }

   /**
    * Constructs a message given a destination and source address and the payload byte buffer
    * @param dest The Address of the receiver. If it is null, then the message is sent to all cluster members.
    *             Otherwise, it is sent to a single member.
    * @param buf The payload. Note that this buffer must not be modified (e.g. buf[0]='x' is not
    *            allowed) since we don't copy the contents.
    */
    public NioMessage(Address dest, ByteBuffer buf) {
        this(dest);
        this.buf=checkCorrectType(buf);
    }

    /**
     * Creates an NioMessage with a {@link ByteBuffer} as payload that is created by wrapping the passed-in byte array
     * @param dest The destination address
     * @param buf The byte array
     */
    public NioMessage(Address dest, byte[] buf) {
        this(dest, buf, 0, buf.length);
    }

    /**
     * Creates an NioMessage with a {@link ByteBuffer} as payload that is created by wrapping the passed-in byte array
     * and an offset and length
     * @param dest The destination address
     * @param buf The byte array
     * @param offset The offset into the array
     * @param length The number of bytes starting at offset
     */
    public NioMessage(Address dest, byte[] buf, int offset, int length) {
        super(dest);
        this.buf=createBuffer(buf, offset, length);
    }


   /**
    * Constructs a message given a destination and source address and the payload object
    * @param dest The Address of the receiver. If it is null, then the message is sent to all cluster members.
    *             Otherwise, it is sent to a single member.
    * @param obj The object that will be marshalled into the byte buffer. Has to be serializable (e.g. implementing
    *            Serializable, Externalizable or Streamable, or be a basic type (e.g. Integer, Short etc)).
    */
    public NioMessage(Address dest, Object obj) {
        super(dest, obj);
    }


    public NioMessage() {
        super();
    }


    public NioMessage(boolean create_headers) {
        super(create_headers);
    }

    /** Returns the byte buffer. Do not read from/write to it, or else retransmissions will fail!
        Use {@link ByteBuffer#duplicate()} before, to create a copy, if the buffer needs to be read from */
    public ByteBuffer        getBuf() {return buf;}
    public Supplier<Message> create() {return NioMessage::new;}
    public byte              getType()                 {return Message.NIO_MSG;}
    public boolean           hasPayload()              {return buf != null;}
    public boolean           hasArray()                {return buf != null && buf.hasArray();}
    public int               getOffset()               {return hasArray()? buf.arrayOffset()+buf.position() : 0;}
    public int               getLength()               {return buf != null? buf.remaining() : 0;}
    public byte[]            getArray()                {return hasArray()? buf.array() : null;}
    public NioMessage        create(Address dest)      {return new NioMessage(dest);}



    /**
     * Sets the internal buffer to point to a subset of a given buffer.<p/>
     * <em>
     * Note that the byte[] buffer passed as argument must not be modified. Reason: if we retransmit the
     * message, it would still have a ref to the original byte[] buffer passed in as argument, and so we would
     * retransmit a changed byte[] buffer !
     * </em>
     *
     * @param b The reference to a given buffer. If null, we'll reset the buffer to null
     * @param offset The initial position
     * @param length The number of bytes
     */
    public Message setArray(byte[] b, int offset, int length) {
        if(b != null)
            buf=createBuffer(b, offset, length);
        return this;
    }

    /**
     * Sets the buffer<p/>
     * Note that the byte[] buffer passed as argument must not be modified. Reason: if we retransmit the
     * message, it would still have a ref to the original byte[] buffer passed in as argument, and so we would
     * retransmit a changed byte[] buffer !
     */
    public  Message setArray(ByteArray b) {
        if(b != null)
            this.buf=createBuffer(b.getArray(), b.getOffset(), b.getLength());
        return this;
    }



    /**
     * Takes an object and uses Java serialization to generate the byte[] buffer which is set in the
     * message. Parameter 'obj' has to be serializable (e.g. implementing Serializable,
     * Externalizable or Streamable, or be a basic type (e.g. Integer, Short etc)).
     */
    public Message setObject(Object obj) {
        if(obj instanceof byte[])
            return setArray((byte[])obj, 0, ((byte[])obj).length);
        if(obj instanceof ByteArray)
            return setArray((ByteArray)obj);
        try {
            byte[] tmp=Util.objectToByteBuffer(obj);
            return setArray(tmp, 0, tmp.length);
        }
        catch(Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }


    public <T extends Object> T getObject() {
        return getObject(null);
    }

    /**
     * Uses custom serialization to create an object from the buffer of the message. Note that this is dangerous when
     * using your own classloader, e.g. inside of an application server ! Most likely, JGroups will use the system
     * classloader to deserialize the buffer into an object, whereas (for example) a web application will want to use
     * the webapp's classloader, resulting in a ClassCastException. The recommended way is for the application to use
     * their own serialization and only pass byte[] buffer to JGroups.<p/>
     * As of 3.5, a classloader can be passed in. It will be used first to find a class, before contacting
     * the other classloaders in the list. If null, the default list of classloaders will be used.
     * @return The object
     */
    public <T extends Object> T getObject(ClassLoader loader) {
        try {
            return hasArray()? Util.objectFromByteBuffer(getArray(), getOffset(), getLength(), loader) : null;
        }
        catch(Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }



   /**
    * Create a copy of the message.<br/>
    * Note that for headers, only the arrays holding references to the headers are copied, not the headers themselves !
    * The consequence is that the headers array of the copy hold the *same* references as the original, so do *not*
    * modify the headers ! If you want to change a header, copy it and call {@link NioMessage#putHeader(short,Header)} again.
    *
    * @param copy_payload Copy the buffer
    * @param copy_headers Copy the headers
    * @return Message with specified data
    */
    public Message copy(boolean copy_payload, boolean copy_headers) {
        NioMessage retval=create(dest);
        retval.sender=sender;
        short tmp_flags=this.flags;
        byte tmp_tflags=this.transient_flags;
        retval.flags=tmp_flags;
        retval.transient_flags=tmp_tflags;

        if(copy_payload && buf != null)
            retval.buf=buf.duplicate();

        retval.headers=copy_headers && headers != null? Headers.copy(this.headers) : createHeaders(Util.DEFAULT_HEADERS);
        return retval;
    }




    /* ----------------------------------- Interface Streamable  ------------------------------- */

    public int size() {return super.size() +sizeOfPayload();}


    @Override public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        writePayload(out);
    }


   /**
    * Writes the message to the output stream, but excludes the dest and src addresses unless the
    * src address given as argument is different from the message's src address
    * @param excluded_headers Don't marshal headers that are part of excluded_headers
    */
    @Override public void writeToNoAddrs(Address src, DataOutput out, short... excluded_headers) throws IOException {
        super.writeToNoAddrs(src, out, excluded_headers);
        writePayload(out);
    }


    @Override public void readFrom(DataInput in) throws IOException, ClassNotFoundException {
        super.readFrom(in);
        readPayload(in);
    }

    protected int sizeOfPayload() {
        return Global.INT_SIZE + getLength();
    }

    protected void writePayload(DataOutput out) throws IOException {
        out.writeInt(buf != null? getLength() : -1);
        if(buf != null) {
            byte[] buffer=buf.array();
            int offset=buf.arrayOffset()+buf.position(), length=buf.remaining();
            out.write(buffer, offset, length);
        }
    }

    protected void readPayload(DataInput in) throws IOException {
        int len=in.readInt();
        if(len < 0)
            return;
        byte[] tmp=new byte[len];
        in.readFully(tmp, 0, tmp.length);
        buf=createBuffer(tmp, 0, tmp.length);
    }


    protected ByteBuffer checkCorrectType(ByteBuffer b) {
        if(b != null && b.isDirect())
            throw new IllegalArgumentException("a direct byte buffer is not a valid argument to create an "
                                                 + NioMessage.class.getSimpleName());
        return b;
    }

    protected ByteBuffer createBuffer(byte[] array, int offset, int length) {
        return ByteBuffer.wrap(array, offset, length);
    }

}
