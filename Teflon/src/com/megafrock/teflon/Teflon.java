package com.megafrock.teflon;

import java.util.concurrent.LinkedBlockingQueue;

import com.megafrock.teflon.data.TeflonMessage;
import com.megafrock.teflon.frames.TeflonFrame;
import com.megafrock.teflon.runnables.SwingTransferRunnable;
import com.megafrock.teflon.runnables.UDPTransferRunnable;

public class Teflon {
   public static void reportException(Exception ex) {
      System.err.println("ERROR: " + Thread.currentThread() + " : " + ex);
      ex.printStackTrace();
      System.err.println("there was a fatal error. please report it. aborting.");
      System.exit(1);
   }

   public static void debugMessage(String message) {
      System.out.println("DEBUG: " + Thread.currentThread() + " : " + message);
   }

   public static void main(String args[]) {
      LinkedBlockingQueue<TeflonMessage> incomingMsgQueue = new LinkedBlockingQueue<TeflonMessage>();
      LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue = new LinkedBlockingQueue<TeflonMessage>();

      TeflonFrame teflonFrame = new TeflonFrame(outgoingMsgQueue);
      teflonFrame.setVisible(true);

      SwingTransferRunnable swingTransferRunnable = new SwingTransferRunnable(teflonFrame,
            incomingMsgQueue, outgoingMsgQueue);
      Thread swingTransferThread = new Thread(swingTransferRunnable, "UI Synchronizer Thread");

      UDPTransferRunnable udpTransferRunnable = new UDPTransferRunnable(incomingMsgQueue,
            outgoingMsgQueue);
      Thread udpTransferThread = new Thread(udpTransferRunnable, "UDP I/O Thread");

      try {
         swingTransferThread.start();
         udpTransferThread.start();
         udpTransferThread.join();
         swingTransferThread.join();
      } catch (InterruptedException exc) {
         exc.printStackTrace();
      }
   }
}
