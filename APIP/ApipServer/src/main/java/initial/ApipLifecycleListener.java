package initial;

import config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.FcWebServerInitiator;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ApipLifecycleListener implements ServletContextListener {
    private static final Logger log = LoggerFactory.getLogger(ApipLifecycleListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("APIP webapp context initialized.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("APIP webapp context destroying — closing settings and stopping background tasks.");
        Settings settings = FcWebServerInitiator.settings;
        if (settings != null) {
            try {
                settings.close();
            } catch (Exception e) {
                log.error("Error during settings.close(): {}", e.getMessage(), e);
            }
        }
        log.info("APIP webapp context destroyed.");
    }
}
