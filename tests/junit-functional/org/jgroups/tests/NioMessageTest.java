package org.jgroups.tests;

import org.jgroups.Global;
import org.jgroups.Message;
import org.jgroups.NioMessage;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Test {@link org.jgroups.NioMessage}
 * @author Bela Ban
 * @since  5.0.0
 */
@Test(groups=Global.FUNCTIONAL)
public class NioMessageTest extends MessageTestBase {
    protected static final ByteBuffer BUF=ByteBuffer.wrap("hello world".getBytes());


    public void testConstructor() {
        Message msg=new NioMessage();
        assert msg.getType() == Message.NIO_MSG;
        assert !msg.hasPayload();
        assert !msg.hasArray();
        assert msg.getLength() == 0;
    }

    public void testConstructor2() {
        Message msg=new NioMessage(null, BUF);
        assert msg.getType() == Message.NIO_MSG;
        assert msg.hasPayload();
        assert msg.hasArray();
        assert msg.getLength() > 0;
    }

    public void testConstructorWithDirectByteBuffer() {
        try {
            //noinspection ResultOfObjectAllocationIgnored
            new NioMessage(null, ByteBuffer.allocateDirect(4));
            assert false: "initialization of NioMessage with direct byte buffer should have thrown an exception";
        }
        catch(Exception ex) {
            assert ex instanceof IllegalArgumentException;
            System.out.printf("received exception as expected: %s", ex);
        }
    }

    public void testGetArray() {
        byte[] array="hello world".getBytes();
        Message msg=new NioMessage(null, array);
        assert msg.hasArray() && msg.getArray().length == array.length;
    }

    public void testSetArrayWithOffset() {
        Message msg=new NioMessage(null);
        byte[] array="hello world".getBytes();
        msg.setArray(array, 6, 5);
        assert msg.getLength() == 5 && msg.getOffset() == 6;
        String s=new String(msg.getArray(), msg.getOffset(), msg.getLength());
        assert s.equals("world");
    }

    public void testObject() throws Exception {
        Message msg=new NioMessage(null, new Person(53, "Bela"));
        _testSize(msg);
        byte[] buf=marshal(msg);
        Message msg2=unmarshal(NioMessage.class, buf);
        Person p=msg2.getObject();
        assert p != null && p.name.equals("Bela") && p.age == 53;
    }


    public void testObject3() throws Exception {
        Message msg=new NioMessage(null, new BasePerson(53, "Bela"));
        _testSize(msg);
        byte[] buf=marshal(msg);
        Message msg2=unmarshal(NioMessage.class, buf);
        BasePerson p=msg2.getObject();
        assert p != null && p.name.equals("Bela") && p.age == 53;
    }

    public void testObject4() throws Exception {
        Message msg=new NioMessage(null, "hello world");
        _testSize(msg);
        byte[] buf=marshal(msg);
        Message msg2=unmarshal(NioMessage.class, buf);
        String s=msg2.getObject();
        assert Objects.equals(s, "hello world");
    }


    public void testSetNullObject() throws Exception {
        Message msg=new NioMessage(null, (ByteBuffer)null);
        _testSize(msg);
        byte[] buf=marshal(msg);
        Message msg2=unmarshal(NioMessage.class, buf);
        Object p=msg2.getObject();
        assert p == null;
    }


    public void testSetObject() {
        Message msg=new NioMessage(null, new Person(53, "Bela"));
        assert msg.getObject() != null;
        msg.setObject(new Person(15, "Nicole"));
        Person p=msg.getObject();
        assert p.age == 15 && p.name.equals("Nicole");
        msg.setObject(null);
        assert msg.getObject() == null;
    }



}
