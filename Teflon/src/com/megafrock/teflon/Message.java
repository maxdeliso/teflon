package com.megafrock.teflon;

/*
 * The message class describes a message. It needs to be extended
 * significantly.
 */
public class Message {
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