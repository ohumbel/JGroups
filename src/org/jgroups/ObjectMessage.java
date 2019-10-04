
package org.jgroups;


import org.jgroups.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * A {@link Message} with an object (implementing {@link SizeStreamable}) as payload. The object won't get
 * serialized until it is sent by the transport.
 * <br/>
 * Note that the object passed to the constructor (or set with {@link #setObject(Object)}) must not be changed after
 * the creation of an {@link ObjectMessage}.
 * @since  5.0
 * @author Bela Ban
 */
public class ObjectMessage extends BaseMessage {
    protected Object obj; // must implement SizeStreamable (though the type is Object because of the subclass)

    /**
    * Constructs a message given a destination address
    * @param dest The Address of the receiver. If it is null, then the message is sent to the group. Otherwise, it is
    *             sent to a single member.
    */
    public ObjectMessage(Address dest) {
        setDest(dest);
        headers=createHeaders(Util.DEFAULT_HEADERS);
    }



   /**
    * Constructs a message given a destination address and the payload object
    * @param dest The Address of the receiver. If it is null, then the message is sent to the group. Otherwise, it is
    *             sent to a single member.
    * @param obj To be used as payload. If obj doesn't implement {@link SizeStreamable}, an exception will be thrown
    */
    public ObjectMessage(Address dest, Object obj) {
        this(dest);
        setObject(obj);
    }


    public ObjectMessage() {
        this(true);
    }


    public ObjectMessage(boolean create_headers) {
        if(create_headers)
            headers=createHeaders(Util.DEFAULT_HEADERS);
    }

    public Supplier<Message> create()                             {return ObjectMessage::new;}
    public byte              getType()                            {return Message.OBJ_MSG;}
    public boolean           hasPayload()                         {return obj != null;}
    public boolean           hasArray()                           {return false;}
    public int               getOffset()                          {return 0;}
    public int               getLength()                          {return obj != null? objSize() : 0;}
    public byte[]            getArray()                           {throw new UnsupportedOperationException();}
    public Message           setArray(byte[] b, int off, int len) {throw new UnsupportedOperationException();}
    public Message           setArray(ByteArray buf)              {throw new UnsupportedOperationException();}

    /** Sets the object. Note that the object should not be changed after sending the message.
     * @param obj The object to be set; has to implement {@link SizeStreamable}
     */
    public Message setObject(Object obj) {
        this.obj=check(obj);
        return this;
    }


    public <T extends Object> T getObject() {
        return (T)obj;
    }


    public Message copy(boolean copy_payload, boolean copy_headers) {
        ObjectMessage retval=new ObjectMessage(false);
        retval.dest=dest;
        retval.sender=sender;
        short tmp_flags=this.flags;
        byte tmp_tflags=this.transient_flags;
        retval.flags=tmp_flags;
        retval.transient_flags=tmp_tflags;

        if(copy_payload && obj != null)
            retval.setObject(obj);
        retval.headers=copy_headers && headers != null? Headers.copy(this.headers) : createHeaders(Util.DEFAULT_HEADERS);
        return retval;
    }



    protected Object check(Object obj) {
        if(obj != null && !(obj instanceof SizeStreamable))
            throw new IllegalArgumentException(String.format("obj (%s) does not implement %s",
                                                             obj.getClass().getSimpleName(), SizeStreamable.class.getSimpleName()));
        return obj;
    }

    protected int objSize() {
        return Util.size((SizeStreamable)obj);
    }

    /* ----------------------------------- Interface Streamable  ------------------------------- */

    public int size() {
        return super.size() + objSize();
    }



    /** Streams all members (dest and src addresses, buffer and headers) to the output stream */
    public void writeTo(DataOutput out) throws IOException {
        super.writeTo(out);
        write(out);
    }

   /**
    * Writes the message to the output stream, but excludes the dest and src addresses unless the
    * src address given as argument is different from the message's src address
    * @param excluded_headers Don't marshal headers that are part of excluded_headers
    */
    public void writeToNoAddrs(Address src, DataOutput out, short... excluded_headers) throws IOException {
        super.writeToNoAddrs(src, out, excluded_headers);
        write(out);
    }


    public void readFrom(DataInput in) throws IOException, ClassNotFoundException {
        super.readFrom(in);
        read(in);
    }


    protected void write(DataOutput out) throws IOException {
        Util.writeGenericStreamable((Streamable)obj, out);
    }

    protected void read(DataInput in) throws IOException, ClassNotFoundException {
        obj=Util.readGenericStreamable(in);
    }


    /* --------------------------------- End of Interface Streamable ----------------------------- */


    public String toString() {
        return super.toString() + String.format(", obj: %s", obj);
    }
}
