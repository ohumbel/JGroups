package org.jgroups.tests;

import org.jgroups.*;
import org.jgroups.util.Util;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;

/**
 * Tests {@link org.jgroups.CompositeMessage}
 * @author Bela Ban
 * @since  5.0
 */
@Test(groups=Global.FUNCTIONAL)
public class CompositeMessageTest extends MessageTestBase {
    protected static final Address SRC=Util.createRandomAddress("X"), DEST=Util.createRandomAddress("A");

    public void testCreation() {
        Message m1=create(DEST, 10, false, false);
        Message m2=((NioDirectMessage)create(DEST, 1000, true, true)).useHeapMemory(true);

        Message msg=new CompositeMessage(DEST, m1, m2);
        assert msg.getLength() == m1.getLength() + m2.getLength();
    }


    protected static Message create(Address dest, int length, boolean nio, boolean direct) {
        if(!nio)
            return new BytesMessage(dest, new byte[length]).setSrc(SRC);
        return direct? new NioDirectMessage(dest, ByteBuffer.allocateDirect(length)).setSrc(SRC) :
          new NioMessage(dest, ByteBuffer.allocate(length)).setSrc(SRC);
    }

}
