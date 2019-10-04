package org.jgroups.tests;

import org.jgroups.*;
import org.jgroups.util.*;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests {@link org.jgroups.FragmentedMessage}
 * @author Bela Ban
 * @since  5.0
 */
@Test(groups=Global.FUNCTIONAL)
public class FragmentedMessageTest {
    protected static final int     FRAG_SIZE=500;
    protected final MessageFactory msg_factory=new DefaultMessageFactory();
    protected final byte[]         array=generate(1200);
    protected final Address        src=Util.createRandomAddress("X"), dest=Util.createRandomAddress("D");

    public void testFragmentationWithBytesMessage() throws Exception {
        Message original_msg=new BytesMessage(dest, array, 0, array.length).setSrc(src);
        Consumer<Message> verifier=m -> verify(m.getArray());
        _testFragmentation(original_msg, verifier);
    }

    public void testFragmentationWithObjectMessage() throws Exception {
        MessageSendTest.MySizeData data=new MessageSendTest.MySizeData(322649, array);
        Message original_msg=new ObjectMessage(dest, data).setSrc(src);
        Consumer<Message> verifier=m -> {
            MessageSendTest.MySizeData d=m.getObject();
            System.out.printf("obj: %s\n", d);
            assert d.num == 322649;
            assert d.data.length == data.data.length;
            verify(d.data);
        };
        _testFragmentation(original_msg, verifier);
    }

    public void testFragmentationWithObjectMessageSerializable() throws Exception {
        MessageSendTest.MyData data=new MessageSendTest.MyData(322649, array);
        Message original_msg=new ObjectMessageSerializable(dest, data).setSrc(src);
        Consumer<Message> verifier=m -> {
            MessageSendTest.MyData d=m.getObject();
            System.out.printf("obj: %s\n", d);
            assert d.num == 322649;
            assert d.data.length == data.data.length;
            verify(d.data);
        };
        _testFragmentation(original_msg, verifier);
    }


    public void testFragmentationWithObjectMessageSerializable2() throws Exception {
        Person data=new Person("Bela Ban", 322649, array);
        Message original_msg=new ObjectMessageSerializable(dest, data).setSrc(src);
        Consumer<Message> verifier=m -> {
            Person d=m.getObject();
            System.out.printf("obj: %s\n", d);
            assert d.age == 322649;
            assert d.buf.length == data.buf.length;
            verify(d.buf);
        };
        _testFragmentation(original_msg, verifier);
    }

    public void testFragmentationWithNioHeapMessage() throws Exception {
        Message original_msg=new NioMessage(dest, ByteBuffer.wrap(array)).setSrc(src);
        Consumer<Message> verifier=m -> verify(m.getArray());
        _testFragmentation(original_msg, verifier);
    }

    public void testFragmentationWithNioDirectMessage() throws Exception {
        ByteBuffer buf=ByteBuffer.allocateDirect(array.length);
        buf.put(array).flip();
        Message original_msg=new NioDirectMessage(dest, buf).setSrc(src);
        Consumer<Message> verifier=m -> verify(m.getArray());
        _testFragmentation(original_msg, verifier);
    }


    protected void _testFragmentation(Message original_msg, Consumer<Message> verifier) throws Exception {
        int serialized_size=original_msg.size();
        ByteArrayDataOutputStream out=new ByteArrayDataOutputStream(serialized_size);
        List<Range> fragments=Util.computeFragOffsets(0, serialized_size, FRAG_SIZE);
        for(Range r: fragments) {
            Message frag=new FragmentedMessage(original_msg, (int)r.low, (int)r.high)
              .setDest(original_msg.getDest()).setSrc(original_msg.getSrc());
            frag.writeTo(out);
        }

        ByteArrayDataInputStream in=new ByteArrayDataInputStream(out.buffer(), 0, out.position());
        Message[] msgs=new FragmentedMessage[fragments.size()];
        for(int i=0; i < msgs.length; i++) {
            FragmentedMessage m=new FragmentedMessage();
            m.readFrom(in);
            msgs[i]=m;
        }

        InputStream seq=new SequenceInputStream(Util.enumerate(msgs, 0, msgs.length,
                                                               m -> new ByteArrayDataInputStream(m.getArray(),
                                                                                                 m.getOffset(),
                                                                                                 m.getLength())));
        DataInput input=new DataInputStream(seq);
        Message new_msg=msg_factory.create(original_msg.getType());
        new_msg.readFrom(input);
        assert original_msg.getLength() == new_msg.getLength();
        verifier.accept(new_msg);
    }

    protected static byte[] getArray(ByteBuffer direct_bb) {
        byte[] retval=new byte[direct_bb.remaining()];
        direct_bb.get(retval).flip();
        return retval;
    }


    protected static void verify(byte[] array) {
        for(int i=0,num=0; i < array.length/4; i+=4,num++) {
            int val=Bits.readInt(array, i);
            assert val == num : String.format("expected %d, but got %d", num, val);
        }
    }

    protected static byte[] generate(int length) {
        byte[] array=new byte[length];
        for(int i=0,num=0; i < array.length/4; i+=4,num++)
            Bits.writeInt(num, array, i);
        return array;
    }

    protected static class Person implements Serializable {
        private static final long serialVersionUID=8635045223414419580L;
        protected String name;
        protected int    age;
        protected byte[] buf;

        public Person(String name, int age, byte[] buf) {
            this.name=name;
            this.age=age;
            this.buf=buf;
        }

        public String toString() {
            return String.format("name=%s age=%d bytes=%d", name, age, buf != null? buf.length : 0);
        }
    }

}
