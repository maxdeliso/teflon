package com.megafrock.teflon.data;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import com.megafrock.teflon.Teflon;

public final class TeflonMessage implements Serializable {
   private static final long serialVersionUID = 1L;
   private final int sender_id;
   private final String body;

   public TeflonMessage(int sender_id, String body) {
      this.sender_id = sender_id;
      this.body = body;
   }

   public TeflonMessage(String body) {
      this(simpleSenderId(), body);
   }

   @Override
   public String toString() {
      return Integer.toHexString(sender_id) + " >> " + body.toString();
   }

   /*
    * this is a cheap way to generate reasonably good numeric identifiers for
    * hosts
    */
   private static int simpleSenderId() {
      try {
         return InetAddress.getLocalHost().getHostName().hashCode();
      } catch (UnknownHostException uhe) {
         Teflon.debugMessage("there was a problem retrieving your hostname. falling back to random id.");
         return new Random().nextInt(Integer.MAX_VALUE);
      }
   }
}
