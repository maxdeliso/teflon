package name.maxdeliso.teflon.swing.di;

import dagger.Component;
import name.maxdeliso.teflon.core.di.TeflonCoreModule;
import name.maxdeliso.teflon.core.net.NetSelector;
import name.maxdeliso.teflon.swing.ui.MainFrame;

import javax.inject.Singleton;

@Singleton
@Component(modules = {TeflonSwingModule.class, TeflonCoreModule.class})
public
interface TeflonSwingComponent {
    MainFrame mainFrame();

    NetSelector netSelector();
}
