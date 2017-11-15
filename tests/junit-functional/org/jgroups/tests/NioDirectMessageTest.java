package org.jgroups.tests;

import org.jgroups.Global;
import org.jgroups.Message;
import org.jgroups.NioDirectMessage;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Test {@link NioDirectMessage}
 * @author Bela Ban
 * @since  5.0.0
 */
@Test(groups=Global.FUNCTIONAL)
public class NioDirectMessageTest extends MessageTestBase {
    protected static byte[]           ARRAY="hello world".getBytes();
    protected static final ByteBuffer BUF=(ByteBuffer)ByteBuffer.allocateDirect(ARRAY.length).put(ARRAY).flip();


    public void testConstructor() {
        Message msg=new NioDirectMessage();
        assert msg.getType() == Message.NIO_DIRECT_MSG;
        assert !msg.hasPayload();
        assert !msg.hasArray();
        assert msg.getLength() == 0;
    }

    public void testConstructor2() {
        Message msg=new NioDirectMessage(null, BUF);
        assert msg.getType() == Message.NIO_DIRECT_MSG;
        assert msg.hasPayload();
        assert !msg.hasArray();
        assert msg.getLength() == ARRAY.length;
    }

    public void testConstructorWithDirectByteBuffer() {
        Message msg=new NioDirectMessage(null, ByteBuffer.allocateDirect(4));
        assert msg.hasPayload();
        assert !msg.hasArray();
        assert msg.getOffset() == 0 && msg.getLength() == 4;
    }

    public void testGetArray() {
        Message msg=new NioDirectMessage(null, ARRAY);
        assert msg.getArray().length == ARRAY.length;
    }

    public void testSetArrayWithOffset() {
        Message msg=new NioDirectMessage(null);
        msg.setArray(ARRAY, 6, 5);
        assert msg.getLength() == 5 && msg.getOffset() == 0;
        String s=new String(msg.getArray(), msg.getOffset(), msg.getLength());
        assert s.equals("world");
    }

    public void testUseHeapMemory() {
        Message msg=new NioDirectMessage(null).useHeapMemory(true);
        assert !msg.hasArray();
        msg.setArray(ARRAY, 6, 5);
        assert msg.hasArray();
        assert msg.getLength() == 5 && msg.getOffset() == 6;
        String s=new String(msg.getArray(), msg.getOffset(), msg.getLength());
        assert s.equals("world");
    }

    public void testObject() throws Exception {
        Message msg=new NioDirectMessage(null, new Person(53, "Bela"));
        _testSize(msg);
        byte[] buf=marshal(msg);
        ByteBuffer tmp=ByteBuffer.wrap(buf);
        Message msg2=unmarshal(NioDirectMessage.class, tmp);
        Person p=msg2.getObject();
        assert p != null && p.name.equals("Bela") && p.age == 53;
    }


    public void testObject3() throws Exception {
        Message msg=new NioDirectMessage(null, new BasePerson(53, "Bela"));
        _testSize(msg);
        byte[] buf=marshal(msg);
        ByteBuffer tmp=ByteBuffer.wrap(buf);
        Message msg2=unmarshal(NioDirectMessage.class, tmp);
        BasePerson p=msg2.getObject();
        assert p != null && p.name.equals("Bela") && p.age == 53;
    }

    public void testObject4() throws Exception {
        Message msg=new NioDirectMessage(null, "hello world");
        _testSize(msg);
        byte[] buf=marshal(msg);
        ByteBuffer tmp=ByteBuffer.wrap(buf);
        Message msg2=unmarshal(NioDirectMessage.class, tmp);
        String s=msg2.getObject();
        assert Objects.equals(s, "hello world");
    }


    public void testSetNullObject() throws Exception {
        Message msg=new NioDirectMessage(null, null);
        _testSize(msg);
        byte[] buf=marshal(msg);
        Message msg2=unmarshal(NioDirectMessage.class, buf);
        Object p=msg2.getObject();
        assert p == null;
    }


    public void testSetObject() {
        Message msg=new NioDirectMessage(null, new Person(53, "Bela"));
        assert msg.getObject() != null;
        msg.setObject(new Person(15, "Nicole"));
        Person p=msg.getObject();
        assert p.age == 15 && p.name.equals("Nicole");
        msg.setObject(null);
        assert msg.getObject() == null;
    }



}
