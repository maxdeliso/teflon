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
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import com.megafrock.teflon.Teflon;
import com.megafrock.teflon.data.TeflonMessage;

public class UDPTransferRunnable implements Runnable {
   private static final byte[] TEFLON_SEND_ADDRESS = new byte[] { (byte) 0xff, (byte) 0xff,
         (byte) 0xff, (byte) 0xff };
   private static final int TEFLON_PORT = 1337;
   private static final int IO_TIMEOUT_MS = 50;
   private static final int INPUT_BUFFER_LEN = 4096;

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
      DatagramSocket udpSocket = tryInitialize();

      if (udpSocket == null)
         System.exit(1);

      byte[] inputBuffer = new byte[INPUT_BUFFER_LEN];
      DatagramPacket inputDatagram = new DatagramPacket(inputBuffer, INPUT_BUFFER_LEN);

      for (;;) {
         tryUDPRecieve(udpSocket, inputDatagram);
         tryUDPSend(udpSocket);
      }
   }

   private DatagramSocket tryInitialize() {
      try {
         udpSocket = new DatagramSocket(TEFLON_PORT);
      } catch (SocketException sockex) {
         sockex.printStackTrace();
         System.err.println("Failed to bind to UDP Port: " + sockex.getMessage());
         return null;
      }

      try {
         udpSocket.setSoTimeout(IO_TIMEOUT_MS);
         udpSocket.setBroadcast(true);
      } catch (SocketException se) {
         se.printStackTrace();
         System.err.println("Failed set UDP socket options : " + se.getMessage());
         return null;
      }

      return udpSocket;
   }

   private void tryUDPRecieve(DatagramSocket udpSocket, DatagramPacket inputDatagram) {
      try {
         Teflon.debugMessage("calling receive()...");
         udpSocket.receive(inputDatagram);

         ObjectInput datagramInput = new ObjectInputStream(new ByteArrayInputStream(
               inputDatagram.getData()));

         TeflonMessage datagramMessage = (TeflonMessage) datagramInput.readObject();

         /*
          * put a teflon message into the transfer queue for displaying in the
          * UI
          */
         incomingMsgQueue.put(datagramMessage);
      } catch (SocketTimeoutException ste) {
         /*
          * this is expected to occur frequently due to reads timing out, so we
          * just ignore it
          */
      } catch (InterruptedException inte) {
         inte.printStackTrace();
         System.err.println("Interrupted while enqueing decoded message: " + inte.getMessage());
      } catch (IOException ioe) {
         ioe.printStackTrace();
         System.err.println("IO exception while recieving from UDP socket: " + ioe.getMessage());
      } catch (ClassNotFoundException cnfe) {
         cnfe.printStackTrace();
         System.err.println("Deserialization of received message failed: " + cnfe.getMessage());
      }
   }

   private void tryUDPSend(DatagramSocket udpSocket) {
      Teflon.debugMessage("polling outgoing message queue...");
      TeflonMessage outputMessage = outgoingMsgQueue.poll();

      if (outputMessage != null) {
         try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(outputMessage);
            byte[] messageBytes = byteArrayOutputStream.toByteArray();

            DatagramPacket outgoingPacket = new DatagramPacket(messageBytes, messageBytes.length,
                  InetAddress.getByAddress(TEFLON_SEND_ADDRESS), TEFLON_PORT);

            Teflon.debugMessage("calling send()...");
            udpSocket.send(outgoingPacket);
         } catch (IOException ioe) {
            ioe.printStackTrace();
            System.err.println("IO exception while sending to UDP socket: " + ioe.getMessage());
         }
      }
   }
}