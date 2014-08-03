package com.megafrock.teflon.runnables;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import com.megafrock.teflon.Teflon;
import com.megafrock.teflon.data.TeflonMessage;

public class UDPTransferRunnable implements Runnable {
   private static final byte[] TEFLON_RECV_ADDRESS = new byte[] { 0, 0, 0, 0 };
   private static final byte[] TEFLON_SEND_ADDRESS = new byte[] { (byte) 0xff, (byte) 0xff,
         (byte) 0xff, (byte) 0xff };
   private static final int TEFLON_PORT = 1337;
   private static final int IO_TIMEOUT_MS = 50;
   private static final int INPUT_BUFFER_LEN = 4096;

   private InetAddress listeningAddress;
   private DatagramSocket udpSocket;
   private LinkedBlockingQueue<TeflonMessage> incomingMsgQueue;
   private LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue;

   public UDPTransferRunnable(LinkedBlockingQueue<TeflonMessage> incomingMsgQueue,
         LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue) {
      this.incomingMsgQueue = incomingMsgQueue;
      this.outgoingMsgQueue = outgoingMsgQueue;
   }

   @Override
   public void run() {
      try {
         listeningAddress = InetAddress.getByAddress(TEFLON_RECV_ADDRESS);
         udpSocket = new DatagramSocket(TEFLON_PORT, listeningAddress);
         udpSocket.setSoTimeout(IO_TIMEOUT_MS);
         udpSocket.setBroadcast(true);
      } catch (Exception exc) {
         Teflon.reportException(exc);
         return;
      }

      byte[] inputBuffer = new byte[INPUT_BUFFER_LEN];
      DatagramPacket inputDatagram = new DatagramPacket(inputBuffer, INPUT_BUFFER_LEN);

      for (;;) {
         try {
            Teflon.debugMessage("calling receive()...");
            udpSocket.receive(inputDatagram);

            ObjectInput datagramInput = new ObjectInputStream(new ByteArrayInputStream(
                  inputDatagram.getData()));

            TeflonMessage datagramMessage = (TeflonMessage) datagramInput.readObject();

            /*
             * put a teflon message into the transfer queue for displaying in
             * the UI
             */
            incomingMsgQueue.put(datagramMessage);
         } catch (SocketTimeoutException ste) {

         } catch (Exception exc) {
            Teflon.reportException(exc);
            return;
         }

         Teflon.debugMessage("polling outgoing message queue...");
         TeflonMessage outputMessage = outgoingMsgQueue.poll();

         if (outputMessage != null) {
            try {
               ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
               ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
               objectOutputStream.writeObject(outputMessage);
               byte[] messageBytes = byteArrayOutputStream.toByteArray();

               DatagramPacket outgoingPacket = new DatagramPacket(messageBytes,
                     messageBytes.length, InetAddress.getByAddress(TEFLON_SEND_ADDRESS),
                     TEFLON_PORT);

               Teflon.debugMessage("calling send()...");
               udpSocket.send(outgoingPacket);
            } catch (IOException ioe) {
               ioe.printStackTrace();
            }
         }
      } /* for(;;) */
   }
}