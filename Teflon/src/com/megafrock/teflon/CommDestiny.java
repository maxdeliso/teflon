package com.megafrock.teflon;

/*
 * The CommDestiny interface signifies that a class can serve as a
 * destination for communications. Both the local and remote handlers
 * implement this class, and use the queueMessage(...) method to send each
 * other data.
 */

public interface CommDestiny {
   public void queueMessage(Message msg);
}