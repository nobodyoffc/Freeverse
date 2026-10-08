package server;

import config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import exception.LocalDbUnavailableException;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import java.io.Serial;

import static config.Configure.makeConfigFileName;

public abstract class FcWebServerInitiator extends HttpServlet {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FcWebServerInitiator.class);
    protected String serverName;
    public static Settings settings;

    protected abstract void setServiceName();

    @Override
    public void destroy(){
        log.debug("Destroy {} server...",serverName);
        
        // Ensure settings are properly closed
        if (settings != null) {
            settings.close();
        }

        log.debug("The {} server is destroyed.",serverName);
    }
    @Override
    public void init(ServletConfig config) throws ServletException {
        setServiceName();

        System.out.println("Initiate " + serverName + " web server...");

        String configFileName = makeConfigFileName(serverName);

        settings = Settings.loadSettingsForServer(configFileName);

        if (settings == null) {
            log.error("Failed to load settings.");
            return;
        }

        // Add a shutdown hook to handle abrupt termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown detected, cleaning up resources...");
            if (settings != null) {
                try {
                    settings.close();
                } catch (Exception e) {
                    log.error("Error during settings cleanup during shutdown: {}", e.getMessage());
                }
            }
            log.info("Cleanup completed during shutdown");
        }));

        try {
            settings.initModulesMute();
        } catch (LocalDbUnavailableException e) {
            // Fail this webapp, not the JVM: System.exit here deadlocks with Tomcat's shutdown
            // hook. Close what did open, so its database locks are released.
            log.error("The {} server cannot start: {}", serverName, e.getMessage());
            settings.close();
            settings = null;
            throw new ServletException(e.getMessage(), e);
        }

        settings.runAutoTasks();

        System.out.println("The "+serverName+" server initiated.\n");
    }
}
