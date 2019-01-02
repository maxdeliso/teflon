package name.maxdeliso.teflon.di;

import dagger.Component;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;

import javax.inject.Singleton;

@Singleton
@Component(modules = TeflonModule.class)
public
interface TeflonComponent {
    MainFrame mainFrame();
    NetSelector netSelector();
}
