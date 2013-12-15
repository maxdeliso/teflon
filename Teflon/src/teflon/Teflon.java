/*
 * author: Max DeLiso <maxdeliso@gmail.com>
 * purpose: graphical UDP chat program 
 */

package teflon;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

class Teflon {
   /*
    * These are constants which are global to the entire application. In the
    * future they should probably be moved into a preference file so that they
    * can be configured by the user.
    */
   public static final int TEFLON_PORT = 1337;
   public static final byte[] TEFLON_RECV_ADDRESS = new byte[] { 0, 0, 0, 0 };
   public static final byte[] TEFLON_SEND_ADDRESS = new byte[] { (byte) 0xff, (byte) 0xff,
         (byte) 0xff, (byte) 0xff };
   public static final int IO_TIMEOUT_MS = 50;
   public static final int INPUT_BUFFER_LEN = 1024;
   public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

   /*
    * These are are application global variables. A reference to each thread and
    * a boolean maintaining whether or not the application is alive. These
    * references are maintained here so that each handler can easily and
    * elegantly obtain a reference to any other handlers, as well as being able
    * to signal application exit uniformly.
    */
   private TeflonRemoteHandler remoteHandler;
   private TeflonLocalHandler localHandler;
   private Thread remoteHandlerThread;
   private Thread localHandlerThread;
   private boolean alive;

   /*
    * This is an application global helper routine which is used to signal a
    * bug. I pass all unanticipated exceptions here which makes fixing bugs a
    * lot easier.
    */
   private static void reportException(Exception ex) {
      System.err.println("ERROR: " + Thread.currentThread() + " : " + ex);
      ex.printStackTrace();
      System.err.println("there was a fatal error. please report it. aborting.");
      System.exit(1);
   }

   /*
    * This is an application global helper routine which is used to report an
    * arbitrary string along with the thread which emanated it. It's very
    * convenient to have all debugging output statements pass through a
    * centralized gate so that they might be easily disabled in a release build.
    */
   private static void debugMessage(String message) {
      System.out.println("DEBUG: " + Thread.currentThread() + " : " + message);
   }

   /*
    * This is the application entry point. Note that the application's command
    * line arguments are currently ignored, and all that it does is instantiate
    * a Teflon object. The Teflon object, which is the top level class for the
    * application, contains all application logic. It is also useful to note
    * that every single line of code that executes in this program can be traced
    * back to this root invocation of the constructor of Teflon.
    */
   public static void main(String args[]) {
      new Teflon();
   }

   /*
    * This is a utility function to convert a string of bytes into a java
    * string. This is necessary because strings need to be converted to and from
    * a byte representation in order to pass over the network. The java virtual
    * machine takes care of abstractions at the transport layer, but you still
    * need to care about the character set.
    */
   public static String decodeUTF8(byte[] bytes) {
      return new String(bytes, UTF8_CHARSET);
   }

   /*
    * This function is a simple inverse of decodeUTF8.
    */
   public static byte[] encodeUTF8(String string) {
      return string.getBytes(UTF8_CHARSET);
   }

   /*
    * This function returns a reference to the remote handler, which is useful
    * when another child of Teflon wants to be able to communicate with the
    * remote handler, for instance the local handler.
    */
   public TeflonRemoteHandler remote() {
      return remoteHandler;
   }

   /*
    * This function returns a reference to the local handler, it is useful for
    * the same reasons the remote() function is useful.
    */
   public TeflonLocalHandler local() {
      return localHandler;
   }

   /*
    * This function returns a boolean which the caller interprets to mean that
    * it should keep executing. Note that this is a synchronized method which
    * means that all invocations of this method on the same instance of this
    * class will block each other. It is used by the handler threads to check
    * that they should continue executing.
    */
   public synchronized boolean alive() {
      return alive;
   }

   /*
    * This function sets the alive flag to false, effectively signaling to all
    * the parts of the application that they should initiate an orderly
    * shutdown. It is used by the handlers when they wish to signal termination.
    */
   public synchronized void kill() {
      alive = false;
      remoteHandlerThread.interrupt();
      localHandlerThread.interrupt();
   }

   /*
    * This is the constructor of the application class. It creates instances of
    * the local and remote handlers and passes them an instance of itself, so
    * that they may easily acquire references to each other, and any other
    * handlers that may be introduced in the future. Note that the handlers all
    * implement Runnable and are started in their own threads, which means that
    * they execute concurrently, or at the same time.
    */
   public Teflon() {
      alive = true;
      remoteHandler = new TeflonRemoteHandler(this);
      localHandler = new TeflonLocalHandler(this);
      remoteHandlerThread = new Thread(remoteHandler);
      localHandlerThread = new Thread(localHandler);

      remoteHandlerThread.start();
      localHandlerThread.start();

      try {
         remoteHandlerThread.join();
         localHandlerThread.join();
      } catch (InterruptedException ie) {
         reportException(ie);
      }

      debugMessage("main thread exiting");
   }

   /*
    * The message class describes a message. It needs to be extended
    * significantly.
    */
   private class Message {
      public String id, body;

      public Message(String id, String body) {
         this.id = id;
         this.body = body;
      }

      @Override
      public String toString() {
         return id + ": " + body;
      }
   }

   /*
    * The CommDestiny interface signifies that a class can serve as a
    * destination for communications. Both the local and remote handlers
    * implement this class, and use the queueMessage(...) method to send each
    * other data.
    */
   private interface CommDestiny {
      public void queueMessage(Message msg);
   }

   /*
    * The TeflonLocalHandler is responsible for interacting with the user on the
    * local machine. Its duties include displaying the user interface, handling
    * all user input events, and handling any messages it receives from other
    * threads, but usually messages from the remote handler.
    */
   @SuppressWarnings("serial")
   private class TeflonLocalHandler extends JFrame implements Runnable, CommDestiny {
      private static final int TEFLON_WIDTH = 512;
      private static final int TEFLON_HEIGHT = 316;
      private static final String TEFLON_TITLE = "Teflon";

      private LinkedBlockingQueue<Message> sendQueue = new LinkedBlockingQueue<Message>();
      private JTextArea outputTextArea;
      private JTextField inputTextField;
      private JPanel headerPanel;
      private JButton helpButton;
      final private Teflon parent;

      public TeflonLocalHandler(Teflon parent) {
         this.parent = parent;
      }

      private KeyListener localKeyListener = new KeyListener() {
         @Override
         public void keyPressed(KeyEvent ke) {
         }

         @Override
         public void keyReleased(KeyEvent ke) {
            if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
               parent.remote().queueMessage(new Message("L", inputTextField.getText()));

               inputTextField.setText("");
            }
         }

         @Override
         public void keyTyped(KeyEvent ke) {

         }
      };

      private WindowListener localWindowListener = new WindowListener() {
         @Override
         public void windowActivated(WindowEvent we) {
            inputTextField.requestFocus();
         }

         @Override
         public void windowClosed(WindowEvent we) {
         }

         @Override
         public void windowClosing(WindowEvent we) {
            parent.kill();
         }

         @Override
         public void windowDeactivated(WindowEvent we) {
         }

         @Override
         public void windowDeiconified(WindowEvent we) {
         }

         @Override
         public void windowIconified(WindowEvent we) {
         }

         @Override
         public void windowOpened(WindowEvent we) {
            inputTextField.requestFocus();
         }
      };

      private Runnable createInitRunnable() {
         final TeflonLocalHandler teflonLocalHandler = this;

         return new Runnable() {

            @Override
            public void run() {
               headerPanel = new JPanel();
               headerPanel.setLayout(new BorderLayout());

               helpButton = new JButton("Help");
               headerPanel.add(BorderLayout.LINE_END, helpButton);

               outputTextArea = new JTextArea();
               outputTextArea.setLineWrap(true);
               outputTextArea.setEditable(false);

               inputTextField = new JTextField();
               inputTextField.addKeyListener(localKeyListener);

               teflonLocalHandler.setSize(TEFLON_WIDTH, TEFLON_HEIGHT);
               teflonLocalHandler.setTitle(TEFLON_TITLE);
               teflonLocalHandler.setLayout(new BorderLayout());

               teflonLocalHandler.addWindowListener(localWindowListener);
               teflonLocalHandler.add(BorderLayout.PAGE_START, headerPanel);
               teflonLocalHandler.add(BorderLayout.CENTER, new JScrollPane(outputTextArea));
               teflonLocalHandler.add(BorderLayout.PAGE_END, new JScrollPane(inputTextField));

               teflonLocalHandler.setVisible(true);
            }
         };
      }

      @Override
      public void run() {
         try {
            SwingUtilities.invokeAndWait(createInitRunnable());
         } catch (InterruptedException ie) {
            reportException(ie);
         } catch (InvocationTargetException ite) {
            reportException(ite);
         }

         displayMessageWithDate(new Message("system", "started up"));

         while (parent.alive()) {
            try {
               final Message msg = sendQueue.take();

               if (msg != null) {
                  displayMessageWithDate(msg);
               }
            } catch (InterruptedException ie) {
               /* queue take interrupted - this is probably a normal termination */
            }
         }

         try {
            SwingUtilities.invokeAndWait(createDisposalRunnable());
         } catch (InterruptedException ie) {
            reportException(ie);
         } catch (InvocationTargetException ite) {
            reportException(ite);
         }

         debugMessage("swing context destroyed, local handler thread exiting");
      }

      private Runnable createDisposalRunnable() {
         final TeflonLocalHandler teflonLocalHandler = this;

         return new Runnable() {

            @Override
            public void run() {
               teflonLocalHandler.dispose();
            }
         };
      }

      @Override
      public void queueMessage(Message msg) {
         debugMessage("in local handler thread with message: " + msg);
         sendQueue.add(msg);
      }

      private void displayMessageWithDate(final Message msg) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               outputTextArea.append(DateFormat.getInstance().format(new Date()) + " : "
                     + msg.toString() + "\n");
            }
         });
      }
   }

   /*
    * The TeflonRemoteHandler is responsible for sending and receiving messages
    * from other computers. Note that it implements CommDestiny and therefore
    * represents an entity which is capable of receiving messages for delivery.
    * Also note that it implements Runnable and can therefore run in its own
    * thread. It is mostly responsible for handling its UDP socket.
    */
   private class TeflonRemoteHandler implements Runnable, CommDestiny {
      private InetAddress listeningAddress;
      private DatagramSocket udpSocket;
      private Teflon parent;
      private LinkedBlockingQueue<Message> sendQueue = new LinkedBlockingQueue<Message>();

      public TeflonRemoteHandler(Teflon parent) {
         this.parent = parent;
      }

      private boolean init() {
         try {
            listeningAddress = InetAddress.getByAddress(TEFLON_RECV_ADDRESS);
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
         DatagramPacket inputDatagram = new DatagramPacket(inputBuffer, INPUT_BUFFER_LEN);

         while (parent.alive()) {
            try {
               udpSocket.receive(inputDatagram);

               debugMessage("received block with offset/length of: " + inputDatagram.getOffset()
                     + " / " + inputDatagram.getLength());

               final byte[] messageBytes = Arrays.copyOfRange(inputDatagram.getData(),
                     inputDatagram.getOffset(),
                     inputDatagram.getOffset() + inputDatagram.getLength());

               parent.local().queueMessage(new Message("", decodeUTF8(messageBytes)));
            } catch (IllegalBlockingModeException ibme) {
               reportException(ibme);
            } catch (PortUnreachableException pue) {
               reportException(pue);
            } catch (SocketTimeoutException ste) {

            } catch (IOException ioe) {
               reportException(ioe);
            }

            Message msg = sendQueue.poll();
            /*
             * this will not be busy due to timed blocking i/o in above receive
             * call
             */

            if (msg != null) {
               byte[] encodedMessage = encodeUTF8(msg.toString());

               try {
                  DatagramPacket outgoingPacket = new DatagramPacket(encodedMessage,
                        encodedMessage.length, InetAddress.getByAddress(TEFLON_SEND_ADDRESS),
                        TEFLON_PORT);

                  udpSocket.send(outgoingPacket);
               } catch (UnknownHostException uhe) {
                  reportException(uhe);
               } catch (IOException ioe) {
                  reportException(ioe);
               }
            }
         }

         udpSocket.close();
         debugMessage("remote handler thread exiting");
      }

      @Override
      public void queueMessage(Message msg) {
         synchronized (sendQueue) {
            sendQueue.add(msg);
         }
      }
   }
}
