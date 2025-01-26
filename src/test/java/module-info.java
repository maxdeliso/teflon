/**
 * Test module definition for the Teflon chat application.
 */
open module name.maxdeliso.teflon.test {
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.engine;
    requires org.junit.jupiter.params;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires java.desktop;
    requires org.apache.logging.log4j;
    requires com.google.gson;
    requires org.apache.commons.text;
    requires org.jsoup;
    requires java.net.http;

    // Require the main module
    requires name.maxdeliso.teflon;

    // Export test packages
    exports name.maxdeliso.teflon.commands.test;
    exports name.maxdeliso.teflon.data.test;
    exports name.maxdeliso.teflon.net.test;
}
