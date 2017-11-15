
package org.jgroups;


import org.jgroups.util.Util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * A {@link Message} with a direct {@link ByteBuffer} as payload.
 * <br/>
 * Note that the byte buffer of an NioMessage must not be modified after sending it (ie. {@link JChannel#send(Message)};
 * serialization depends on position and limit to be correct.
 *
 * @since  5.0
 * @author Bela Ban
 */
public class NioDirectMessage extends NioMessage {
    /**
     * If true, when reading a message from the network, memory for {@link #buf} is allocated from the heap rather than
     * off-heap. This may be useful if we want to use off-heap (direct) memory only for sending, but not receiving of
     * messages.<br/>
     * An {@link NioDirectMessage} always starts out with this flag disabled, so the initial buffer uses direct memory.
     * If the flag is changed to true, then subsequent allocations (e.g. through {@link #setArray(byte[], int, int)},
     * {@link #setObject(Object)} or reading the contents from the network will use heap-memory.
     */
    protected boolean use_heap_memory;

    /**
     * Constructs a message given a destination address
     * @param dest The Address of the receiver. If it is null, then the message is sent to all cluster members.
     *            Otherwise, it is sent to a single member.
     */
    public NioDirectMessage(Address dest) {
        super(dest);
    }

   /**
    * Constructs a message given a destination and source address and the payload byte buffer
    * @param dest The Address of the receiver. If it is null, then the message is sent to all cluster members.
    *             Otherwise, it is sent to a single member.
    * @param buf The payload. Note that this buffer must not be modified (e.g. buf[0]='x' is not
    *            allowed) since we don't copy the contents.
    */
    public NioDirectMessage(Address dest, ByteBuffer buf) {
        super(dest, buf);
    }


   /**
    * Constructs a message given a destination and source address and the payload object
    * @param dest The Address of the receiver. If it is null, then the message is sent to all cluster members.
    *             Otherwise, it is sent to a single member.
    * @param obj The object that will be marshalled into the byte buffer. Has to be serializable (e.g. implementing
    *            Serializable, Externalizable or Streamable, or be a basic type (e.g. Integer, Short etc)).
    */
    public NioDirectMessage(Address dest, Object obj) {
        super(dest, obj);
    }

    public NioDirectMessage() {
        super();
    }

    public NioDirectMessage(boolean create_headers) {
        super(create_headers);
    }

    public Supplier<Message> create()                 {return NioDirectMessage::new;}
    public byte              getType()                {return Message.NIO_DIRECT_MSG;}
    public boolean           useHeapMemory()          {return use_heap_memory;}
    public Message           useHeapMemory(boolean b) {use_heap_memory=b; return this;}

    /** Returns a copy of the remaining data in {@link ByteBuffer} (if the buffer is not null). Note that this
     * operation is expensive as a new array will be allocated, so use sparingly! */
    public byte[]                      getArray()               {return hasArray()? buf.array() : getContents();}
    public NioMessage                  create(Address dest)     {return new NioDirectMessage(dest);}

    /**
     * Uses custom serialization to create an object from the buffer of the message. Note that this is dangerous when
     * using your own classloader, e.g. inside of an application server ! Most likely, JGroups will use the system
     * classloader to deserialize the buffer into an object, whereas (for example) a web application will want to use
     * the webapp's classloader, resulting in a ClassCastException. The recommended way is for the application to use
     * their own serialization and only pass byte[] buffer to JGroups.<p/>
     * As of 3.5, a classloader can be passed in. It will be used first to find a class, before contacting
     * the other classloaders in the list. If null, the default list of classloaders will be used.
     * @return the object
     */
    public <T extends Object> T getObject(ClassLoader loader) {
        if(buf == null)
            return null;

        try {
            return Util.objectFromByteBuffer(buf, loader);
        }
        catch(Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }




    /* ----------------------------------- Interface Streamable  ------------------------------- */


    protected int sizeOfPayload() {
        return super.sizeOfPayload() + (buf != null? Global.BYTE_SIZE : 0); // use_heap_memory_on_read;
    }

    protected void writePayload(DataOutput out) throws IOException {
        out.writeInt(buf != null? getLength() : -1);
        if(buf != null) {
            out.writeBoolean(use_heap_memory);
            // We need to duplicate the buffer, or else writing its contents to the output stream would modify
            // position; this would break potential retransmission
            // We still need a transfer buffer as there is no way to transfer contents of a ByteBuffer directly to
            // an output stream; once we have a transport that directly supports ByteBuffers, we can change this
            ByteBuffer copy=buf.duplicate();
            byte[] transfer_buf=new byte[Math.max(copy.remaining()/10, 128)];
            while(copy.remaining() > 0) {
                int bytes=Math.min(transfer_buf.length, copy.remaining());
                copy.get(transfer_buf, 0, bytes);
                out.write(transfer_buf, 0, bytes);
            }
        }
    }

    protected void readPayload(DataInput in) throws IOException {
        int len=in.readInt();
        if(len < 0)
            return;
        use_heap_memory=in.readBoolean();
        byte[] tmp=new byte[len];
        in.readFully(tmp, 0, tmp.length);
        // todo: replace with factory; so users can provide their own allocation mechanism (e.g. pooling)
        buf=createBuffer(tmp, 0, tmp.length);
    }

    /** Returns a copy of the byte array between position and limit; requires a non-null buffer */
    protected byte[] getContents() {
        ByteBuffer tmp=buf.duplicate();
        int length=tmp.remaining();
        byte[] retval=new byte[length];
        tmp.get(retval, 0, retval.length);
        return retval;
    }

    protected ByteBuffer createBuffer(byte[] array, int offset, int length) {
        return use_heap_memory? super.createBuffer(array, offset, length) :
          (ByteBuffer)ByteBuffer.allocateDirect(length).put(array, offset, length).flip();
    }

    protected ByteBuffer checkCorrectType(ByteBuffer b) {
        return b; // we can also handle a heap-based ByteBuffer
    }
}
