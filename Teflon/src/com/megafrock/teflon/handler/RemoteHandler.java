package com.megafrock.teflon.handler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.IllegalBlockingModeException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import com.megafrock.teflon.CommDestiny;
import com.megafrock.teflon.Message;
import com.megafrock.teflon.Teflon;

/*
 * The TeflonRemoteHandler is responsible for sending and receiving messages
 * from other computers. Note that it implements CommDestiny and therefore
 * represents an entity which is capable of receiving messages for delivery.
 * Also note that it implements Runnable and can therefore run in its own
 * thread. It is mostly responsible for handling its UDP socket.
 */
public class RemoteHandler implements Runnable, CommDestiny {
   private InetAddress listeningAddress;
   private DatagramSocket udpSocket;
   private Teflon parent;
   private LinkedBlockingQueue<Message> sendQueue = new LinkedBlockingQueue<Message>();

   public RemoteHandler(Teflon parent) {
      this.parent = parent;
   }

   private boolean init() {
      try {
         listeningAddress = InetAddress.getByAddress(Teflon.TEFLON_RECV_ADDRESS);
         udpSocket = new DatagramSocket(Teflon.TEFLON_PORT, listeningAddress);
         udpSocket.setBroadcast(true);
         udpSocket.setSoTimeout(Teflon.IO_TIMEOUT_MS);
      } catch (UnknownHostException uhe) {
         Teflon.reportException(uhe);
         return false;
      } catch (SocketException soe) {
         Teflon.reportException(soe);
         return false;
      } catch (SecurityException se) {
         Teflon.reportException(se);
         return false;
      }

      return true;
   }

   @Override
   public void run() {
      if (!init()) {
         parent.kill();
         return;
      }

      byte[] inputBuffer = new byte[Teflon.INPUT_BUFFER_LEN];
      DatagramPacket inputDatagram = new DatagramPacket(inputBuffer, Teflon.INPUT_BUFFER_LEN);

      while (parent.alive()) {
         try {
            udpSocket.receive(inputDatagram);

            Teflon.debugMessage("received block with offset/length of: "
                  + inputDatagram.getOffset() + " / " + inputDatagram.getLength());

            final byte[] messageBytes = Arrays.copyOfRange(inputDatagram.getData(),
                  inputDatagram.getOffset(), inputDatagram.getOffset() + inputDatagram.getLength());

            parent.local().queueMessage(new Message("", Teflon.decodeUTF8(messageBytes)));
         } catch (IllegalBlockingModeException ibme) {
            Teflon.reportException(ibme);
         } catch (PortUnreachableException pue) {
            Teflon.reportException(pue);
         } catch (SocketTimeoutException ste) {

         } catch (IOException ioe) {
            Teflon.reportException(ioe);
         }

         Message msg = sendQueue.poll();
         /*
          * this will not be busy due to timed blocking i/o in above receive
          * call
          */

         if (msg != null) {
            byte[] encodedMessage = Teflon.encodeUTF8(msg.toString());

            try {
               DatagramPacket outgoingPacket = new DatagramPacket(encodedMessage,
                     encodedMessage.length, InetAddress.getByAddress(Teflon.TEFLON_SEND_ADDRESS),
                     Teflon.TEFLON_PORT);

               udpSocket.send(outgoingPacket);
            } catch (UnknownHostException uhe) {
               Teflon.reportException(uhe);
            } catch (IOException ioe) {
               Teflon.reportException(ioe);
            }
         }
      }

      udpSocket.close();
      Teflon.debugMessage("remote handler thread exiting");
   }

   @Override
   public void queueMessage(Message msg) {
      synchronized (sendQueue) {
         sendQueue.add(msg);
      }
   }
}