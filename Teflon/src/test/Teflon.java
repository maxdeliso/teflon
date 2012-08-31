/*
 * author: Max DeLiso <maxdeliso@gmail.com>
 * purpose: simple multithreaded UDP chat program 
 */

package test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.net.PortUnreachableException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;

import java.nio.channels.IllegalBlockingModeException;
import java.nio.charset.Charset;

import java.util.Queue;
import java.util.LinkedList;

class Teflon {
   public static final int     TEFLON_PORT      = 1337;
   public static final byte[]  TEFLON_ADDRESS   = new byte[] { 0, 0, 0, 0 };
   public static final int     IO_TIMEOUT_MS    = 10;
   public static final int     INPUT_BUFFER_LEN = 1024;
   public static final Charset UTF8_CHARSET     = Charset.forName("UTF-8");

   private TeflonRemoteHandler remoteHandler;
   private TeflonLocalHandler  localHandler;
   private boolean             alive;

   private static void reportException(Exception ex) {
      System.err.println(ex);
      ex.printStackTrace();
   }

   public static void main(String args[]) {
      new Teflon();
   }

   public static String decodeUTF8(byte[] bytes) {
      return new String(bytes, UTF8_CHARSET);
   }

   public static byte[] encodeUTF8(String string) {
      return string.getBytes(UTF8_CHARSET);
   }

   public TeflonRemoteHandler remote() {
      return remoteHandler;
   }

   public TeflonLocalHandler local() {
      return localHandler;
   }

   public synchronized boolean alive() {
      return alive;
   }

   public synchronized void kill() {
      alive = false;
   }

   public Teflon() {
      alive = new Boolean(true);
      remoteHandler = new TeflonRemoteHandler(this);
      localHandler = new TeflonLocalHandler(this);
      Thread remoteHandlerThread = new Thread(remoteHandler);
      Thread localHandlerThread = new Thread(localHandler);

      remoteHandlerThread.start();
      localHandlerThread.start();

      try {
         remoteHandlerThread.join();
         localHandlerThread.join();
      } catch (InterruptedException ie) {
         reportException(ie);
      }
   }

   private class Message {
      public String id, body;

      public Message(String id, String body) {
         this.id = id;
         this.body = body;
      }

      @Override
      public String toString() {
         return id + ">>" + body;
      }
   }

   private interface CommDestiny {
      public void queueMessage(Message msg);
   }

   private class TeflonLocalHandler implements Runnable, CommDestiny {
      private BufferedReader kbInput;
      private BufferedWriter kbOutput;
      private Teflon         parent;
      private Queue<Message> sendQueue = new LinkedList<Message>();

      public TeflonLocalHandler(Teflon parent) {
         this.parent = parent;
      }

      private void init() {
         kbInput = new BufferedReader(new InputStreamReader(System.in));
         kbOutput = new BufferedWriter(new OutputStreamWriter(System.out));
      }

      @Override
      public void run() {
         init();

         while (parent.alive()) {
            try {
               if (kbInput.ready()) {
                  /* if there was data from local */
                  String inputLine = kbInput.readLine();

                  kbOutput.write("<" + inputLine + "\n", 0,
                        inputLine.length() + 2);
                  kbOutput.flush();

                  /* TODO: actually retrieve local name */
                  parent.remote().queueMessage(new Message("test", inputLine));
               } else
                  synchronized (sendQueue) {
                     Message msg = sendQueue.poll();

                     if (msg != null) {
                        /* TODO: print to local */
                     }
                  }

               Thread.sleep(IO_TIMEOUT_MS);
            } catch (IOException ioe) {
               reportException(ioe);
            } catch (InterruptedException ie) {
               reportException(ie);
            }
         }
      }

      @Override
      public void queueMessage(Message msg) {
         System.out.println("in local handler with message: " + msg);

         synchronized (sendQueue) {
            sendQueue.add(msg);
         }
      }
   }

   private class TeflonRemoteHandler implements Runnable, CommDestiny {
      private InetAddress    listeningAddress;
      private DatagramSocket udpSocket;
      private Teflon         parent;
      private Queue<Message> sendQueue = new LinkedList<Message>();

      public TeflonRemoteHandler(Teflon parent) {
         this.parent = parent;
      }

      private boolean init() {
         try {
            listeningAddress = InetAddress.getByAddress(TEFLON_ADDRESS);
            udpSocket = new DatagramSocket(TEFLON_PORT, listeningAddress);
            udpSocket.setBroadcast(true);
            udpSocket.setSoTimeout(IO_TIMEOUT_MS);
         } catch (UnknownHostException uhe) {
            reportException(uhe);
            return false;
         } catch (SocketException soe) {
            reportException(soe);
            return false;
         } catch (SecurityException se) {
            reportException(se);
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

         byte[] inputBuffer = new byte[INPUT_BUFFER_LEN];
         DatagramPacket inputDatagram = new DatagramPacket(inputBuffer,
               INPUT_BUFFER_LEN);

         while (parent.alive()) {
            try {
               udpSocket.receive(inputDatagram);
            } catch (IllegalBlockingModeException ibme) {
               reportException(ibme);
            } catch (PortUnreachableException pue) {
               reportException(pue);
            } catch (SocketTimeoutException ste) {
               /* this is going to be the usual case, so we ignore it */
            } catch (IOException ioe) {
               reportException(ioe);
            }

            if (inputDatagram.getOffset() > 0) {
               /*
                * if there was data received from remote, post it to local
                * handler's message queue
                */
               parent.local().queueMessage(
                     new Message(inputDatagram.getSocketAddress().toString(),
                           decodeUTF8(inputDatagram.getData())));

            } else
               synchronized (sendQueue) {
                  Message msg = sendQueue.poll();

                  if (msg != null) {
                     /* TODO: broadcast UDP message to remote */
                  }
               }

         }
      }

      @Override
      public void queueMessage(Message msg) {
         System.out.println("in remote handler with message: " + msg);

         synchronized (sendQueue) {
            sendQueue.add(msg);
         }
      }
   }
}