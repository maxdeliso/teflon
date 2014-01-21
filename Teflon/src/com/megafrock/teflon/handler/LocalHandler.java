package com.megafrock.teflon.handler;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.megafrock.teflon.CommDestiny;
import com.megafrock.teflon.Message;
import com.megafrock.teflon.Teflon;

/*
 * The LocalHandler is responsible for interacting with the user on the
 * local machine. Its duties include displaying the user interface, handling
 * all user input events, and handling any messages it receives from other
 * threads, but usually messages from the remote handler.
 */
@SuppressWarnings("serial")
public class LocalHandler extends JFrame implements Runnable, CommDestiny {
   private static final int TEFLON_WIDTH = 512;
   private static final int TEFLON_HEIGHT = 316;
   private static final String TEFLON_TITLE = "Teflon";

   private LinkedBlockingQueue<Message> sendQueue = new LinkedBlockingQueue<Message>();
   private JTextArea outputTextArea;
   private JTextField inputTextField;
   private JPanel headerPanel;
   private JButton helpButton;
   final private Teflon parent;

   public LocalHandler(Teflon parent) {
      this.parent = parent;
   }

   private class LocalKeyAdapter extends KeyAdapter {
      @Override
      public void keyReleased(KeyEvent ke) {
         if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
            parent.remote().queueMessage(new Message("L", inputTextField.getText()));

            inputTextField.setText("");
         }
      }
   }

   private class LocalWindowAdapter extends WindowAdapter {
      @Override
      public void windowClosing(WindowEvent we) {
         parent.kill();
      }

      @Override
      public void windowOpened(WindowEvent we) {
         inputTextField.requestFocus();
      }
   }

   private Runnable createInitRunnable() {
      final LocalHandler teflonLocalHandler = this;

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
            inputTextField.addKeyListener(new LocalKeyAdapter());

            teflonLocalHandler.setSize(TEFLON_WIDTH, TEFLON_HEIGHT);
            teflonLocalHandler.setTitle(TEFLON_TITLE);
            teflonLocalHandler.setLayout(new BorderLayout());

            teflonLocalHandler.addWindowListener(new LocalWindowAdapter());
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
         Teflon.reportException(ie);
      } catch (InvocationTargetException ite) {
         Teflon.reportException(ite);
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
         Teflon.reportException(ie);
      } catch (InvocationTargetException ite) {
         Teflon.reportException(ite);
      }

      Teflon.debugMessage("swing context destroyed, local handler thread exiting");
   }

   private Runnable createDisposalRunnable() {
      final LocalHandler teflonLocalHandler = this;

      return new Runnable() {
         @Override
         public void run() {
            teflonLocalHandler.dispose();
         }
      };
   }

   @Override
   public void queueMessage(Message msg) {
      Teflon.debugMessage("in local handler thread with message: " + msg);
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