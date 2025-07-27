/**
 * Module definition for the Teflon chat application.
 * Specifies required dependencies and exposed packages.
 */
module name.maxdeliso.teflon {
    requires com.google.gson;
    requires org.apache.logging.log4j;
    requires java.desktop;
    requires org.apache.commons.text;
    requires org.jsoup;

    // Export our packages so they're visible to other modules
    exports name.maxdeliso.teflon;
    exports name.maxdeliso.teflon.data;
    exports name.maxdeliso.teflon.net;
    exports name.maxdeliso.teflon.ui;
    exports name.maxdeliso.teflon.commands;

    // Open packages that need reflection access
    opens name.maxdeliso.teflon.data to com.google.gson;
    opens name.maxdeliso.teflon.ui;  // Open for testing with Mockito
    opens name.maxdeliso.teflon.commands;
}