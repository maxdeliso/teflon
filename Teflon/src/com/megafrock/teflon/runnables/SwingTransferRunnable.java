package com.megafrock.teflon.runnables;

import java.util.concurrent.LinkedBlockingQueue;

import com.megafrock.teflon.data.TeflonMessage;
import com.megafrock.teflon.frames.TeflonFrame;

public class SwingTransferRunnable implements Runnable {
   private TeflonFrame teflonFrame;
   private LinkedBlockingQueue<TeflonMessage> incomingMsgQueue;

   public SwingTransferRunnable(TeflonFrame teflonFrame,
         LinkedBlockingQueue<TeflonMessage> incomingMsgQueue,
         LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue) {
      this.teflonFrame = teflonFrame;
      this.incomingMsgQueue = incomingMsgQueue;
   }

   @Override
   public void run() {
      for (;;) {
         try {
            TeflonMessage newMessage = incomingMsgQueue.take();
            teflonFrame.queueMessageDisplay(newMessage);
         } catch (InterruptedException ie) {
            ie.printStackTrace();
         }
      }
   }
}
