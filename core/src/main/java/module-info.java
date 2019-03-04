module name.maxdeliso.teflon.core {
    requires slf4j.api;
    requires gson;
    requires dagger;
    requires javax.inject;

    exports name.maxdeliso.teflon.core.data;
    exports name.maxdeliso.teflon.core.net;
    exports name.maxdeliso.teflon.core.di;
}