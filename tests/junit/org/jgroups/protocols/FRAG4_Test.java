package org.jgroups.protocols;

import org.jgroups.*;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Tests fragmentation of different messages with {@link FRAG4}. The interactive version of this is
 * {@link org.jgroups.tests.MessageSendTest}.
 * @author Bela Ban
 * @since  5.0
 */
@Test(groups=Global.FUNCTIONAL,singleThreaded=true)
public class FRAG4_Test {
    protected JChannel         a, b;
    protected final MyReceiver<Message> r1=new MyReceiver<>().rawMsgs(true), r2=new MyReceiver<>().rawMsgs(true);
    protected static final int    FRAG_SIZE=10_000;
    protected static final byte[] array=generate(FRAG_SIZE*2);

    @BeforeMethod
    protected void setup() throws Exception {
        a=new JChannel(Util.getTestStack()).name("A").setReceiver(r1);
        b=new JChannel(Util.getTestStack()).name("B").setReceiver(r2);
        addFRAG4(a, b);
        a.connect(FRAG4_Test.class.getSimpleName());
        b.connect(FRAG4_Test.class.getSimpleName());
        Util.waitUntilAllChannelsHaveSameView(10000, 1000, a,b);
    }

    @AfterMethod protected void teardown() {
        Util.close(b,a);
        r1.reset(); r2.reset();
    }


    public void testEmptyMessage() throws Exception {
        Message m1=new EmptyMessage(null), m2=new EmptyMessage(b.getAddress());
        send(m1, m2);
        assertForAllMessages(m -> m.getLength() == 0);
    }

    public void testBytesMessage() throws Exception {
        Message m1=new BytesMessage(null, array), m2=new BytesMessage(b.getAddress(), array);
        send(m1, m2);
        assertForAllMessages(m -> m.getLength() == array.length);
        assertForAllMessages(m -> verify(m.getArray()));
    }

    public void testObjectMessage() throws Exception {
        MySizeData obj=new MySizeData(322649, array);
        Message m1=new ObjectMessage(null, obj), m2=new ObjectMessage(b.getAddress(), obj);
        send(m1, m2);
        assertForAllMessages(m -> {
            MySizeData data=m.getObject();
            return data.equals(obj) && verify(data.array());
        });
    }

    public void testObjectMessageSerializable() throws Exception {
        MySizeData obj=new MySizeData(322649, array);
        Message m1=new ObjectMessageSerializable(null, obj), m2=new ObjectMessageSerializable(b.getAddress(), obj);
        send(m1, m2);
        assertForAllMessages(m -> {
            MySizeData data=m.getObject();
            return data.equals(obj) && verify(data.array());
        });
    }

    public void testObjectMessageSerializable2() throws Exception {
        MyData obj=new MyData(322649, array);
        Message m1=new ObjectMessageSerializable(null, obj), m2=new ObjectMessageSerializable(b.getAddress(), obj);
        send(m1, m2);
        assertForAllMessages(m -> {
            MyData data=m.getObject();
            return data.equals(obj) && verify(data.array());
        });
    }

    public void testObjectMessageSerializable3() throws Exception {
        Person p=new Person("Bela Ban", 53, array);
        Message m1=new ObjectMessageSerializable(null, p), m2=new ObjectMessageSerializable(b.getAddress(), p);
        send(m1, m2);
        assertForAllMessages(m -> {
            Person p2=m.getObject();
            return p2.name.equals("Bela Ban") && p2.age == p.age && verify(p.buf);
        });
    }

    public void testNioHeapMessage() throws Exception {
        NioMessage m1=new NioMessage(null, ByteBuffer.wrap(array)),
          m2=new NioMessage(b.getAddress(), ByteBuffer.wrap(array));
        send(m1, m2);
        assertForAllMessages(m -> verify(m.getArray()));
    }


    protected void send(Message mcast, Message ucast) throws Exception {
        a.send(mcast); // from A --> all
        a.send(ucast); // from A --> B

        // wait until A and B have received mcast, and until B has received ucast
        for(int i=0; i < 10; i++) {
            if(r1.size() == 1 && r2.size() == 2)
                break;
            Util.sleep(1000);
        }
        System.out.printf("A: %s\nB: %s\nB: %s\n",
                          String.format("%s %s", r1.list().get(0).getClass().getSimpleName(), r1.list().get(0)),
                          String.format("%s %s", r2.list().get(0).getClass().getSimpleName(), r2.list().get(0)),
                          String.format("%s %s", r2.list().get(1).getClass().getSimpleName(), r2.list().get(1)));
        assert r1.size() == 1 && r2.size() == 2;
        assertForAllMessages(m -> m.getClass().equals(mcast.getClass()) && m.getClass().equals(ucast.getClass()));
        assertForAllMessages(m -> m.getSrc().equals(a.getAddress()));

        assert r1.list().get(0).getDest() == null;

        // one dest must be null and the other B:
        assert Stream.of(r2.list()).flatMap(Collection::stream).anyMatch(m -> m.getDest() == null);
        assert Stream.of(r2.list()).flatMap(Collection::stream).anyMatch(m -> Objects.equals(m.getDest(), b.getAddress()));
    }

    protected void assertForAllMessages(Predicate<Message> p) {
        assert Stream.of(r1.list(), r2.list()).flatMap(Collection::stream).allMatch(p);
    }



    protected static void addFRAG4(JChannel... channels) throws Exception {
        for(JChannel ch: channels) {
            ProtocolStack stack=ch.getProtocolStack();
            stack.removeProtocol(FRAG.class, FRAG2.class, FRAG3.class);
            FRAG4 frag=new FRAG4();
            frag.setFragSize(FRAG_SIZE);
            stack.addProtocol(frag); // adds to the top
            frag.init();
        }
    }


    protected static byte[] generate(int length) {
        byte[] array=new byte[length];
        for(int i=0,num=0; i < array.length/4; i+=4,num++)
            Bits.writeInt(num, array, i);
        return array;
    }

    protected static boolean verify(byte[] array) {
        for(int i=0,num=0; i < array.length/4; i+=4,num++) {
            int val=Bits.readInt(array, i);
            assert val == num : String.format("expected %d, but got %d", num, val);
            if(val != num)
                return false;
        }
        return true;
    }




    public static class MyData implements Streamable {
        protected int    num;
        protected byte[] arr;

        public MyData() {}

        public MyData(int num, byte[] buf) {
            this.num=num;
            this.arr=buf;
        }

        public byte[] array() {return arr;}

        public boolean equals(Object obj) {
            MyData d=(MyData)obj;
            return num == d.num && arr.length == d.arr.length;
        }

        public String toString() {
            return String.format("num=%d, data: %d bytes", num, arr != null? arr.length : 0);
        }

        public void writeTo(DataOutput out) throws IOException {
            out.writeInt(num);
            out.writeInt(arr != null? arr.length : 0);
            if(arr != null)
                out.write(arr, 0, arr.length);
        }

        public void readFrom(DataInput in) throws IOException {
            num=in.readInt();
            int len=in.readInt();
            if(len > 0) {
                arr=new byte[len];
                in.readFully(arr);
            }
        }
    }


    public static class MySizeData extends MyData implements SizeStreamable {
        public MySizeData() {}
        public MySizeData(int num, byte[] buf) {super(num, buf);}
        public int serializedSize() {
            return Global.INT_SIZE*2 + (array != null? array.length : 0);
        }
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
