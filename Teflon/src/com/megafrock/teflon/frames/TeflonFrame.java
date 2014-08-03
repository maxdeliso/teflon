package com.megafrock.teflon.frames;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.megafrock.teflon.data.TeflonMessage;

public class TeflonFrame extends JFrame {
   private static final long serialVersionUID = 1L;
   private static final int TEFLON_WIDTH = 512;
   private static final int TEFLON_HEIGHT = 316;
   private static final String TEFLON_TITLE = "Teflon";

   private JPanel headerPanel;
   private JTextArea outputTextArea;
   private JTextField inputTextField;

   private WindowAdapter windowAdapter = new WindowAdapter() {
      @Override
      public void windowOpened(WindowEvent we) {
         inputTextField.requestFocus();
      }
   };

   private KeyAdapter inputFieldKeyAdapter = new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent ke) {
         if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
            TeflonMessage outoingMessage = new TeflonMessage(inputTextField.getText());

            try {
               outgoingMsgQueue.put(outoingMessage);
            } catch (InterruptedException ie) {
               ie.printStackTrace();
            }

            renderMessage(outoingMessage);
            inputTextField.setText("");
         }
      }
   };

   private LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue;

   private void renderMessage(final TeflonMessage msg) {
      String timeString = DateFormat.getInstance().format(new Date());
      outputTextArea.append(timeString + " : " + msg.toString() + "\n");
   }

   public TeflonFrame(LinkedBlockingQueue<TeflonMessage> outgoingMsgQueue) {
      this.outgoingMsgQueue = outgoingMsgQueue;

      headerPanel = new JPanel();
      headerPanel.setLayout(new BorderLayout());

      outputTextArea = new JTextArea();
      outputTextArea.setLineWrap(true);
      outputTextArea.setEditable(false);

      inputTextField = new JTextField();
      inputTextField.addKeyListener(inputFieldKeyAdapter);

      setSize(TEFLON_WIDTH, TEFLON_HEIGHT);
      setTitle(TEFLON_TITLE);
      setLayout(new BorderLayout());

      add(BorderLayout.PAGE_START, headerPanel);
      add(BorderLayout.CENTER, new JScrollPane(outputTextArea));
      add(BorderLayout.PAGE_END, new JScrollPane(inputTextField));

      addWindowListener(windowAdapter);
      setDefaultCloseOperation(EXIT_ON_CLOSE);
   }

   public void queueMessageDisplay(final TeflonMessage msg) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            renderMessage(msg);
         }
      });
   }
}
