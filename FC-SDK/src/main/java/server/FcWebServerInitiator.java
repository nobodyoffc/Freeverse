package server;

import appTools.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import java.io.Serial;

import static configure.Configure.makeConfigFileName;


public abstract class FcWebServerInitiator extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FcWebServerInitiator.class);
    protected String serverName;
    public static Settings settings;

    protected Object[] modules;
    protected Object[] runningModules;

    protected abstract void setServiceName();
    protected abstract void setModules ();
    protected abstract void setRunningModules();
    @Override
    public void destroy(){
        log.debug("Destroy {} server...",serverName);
        settings.close();

        log.debug("The {} server is destroyed.",serverName);
    }
    @Override
    public void init(ServletConfig config) {
        setServiceName();

        System.out.println("Initiate " + serverName + " web server...");

        String configFileName = makeConfigFileName(serverName);

        settings = Settings.loadSettingsForServer(configFileName);

        if (settings == null) {
            log.error("Failed to load settings.");
            return;
        }

        setModules();

        setRunningModules();

        settings.initModulesMute(modules);

        settings.runAutoTasks(runningModules);

        System.out.println("The "+serverName+" server initiated.\n");
    }


}
