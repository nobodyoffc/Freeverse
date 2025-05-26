package data.fcData;

import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;

import config.Settings;
import constants.Constants;
import handlers.AccountHandler;
import handlers.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ui.Inputer;
import utils.NumberUtils;

public class AutoTask {
    private AutoTaskType type;
    private Handler.HandlerType handlerType;
    private String methodName;
    private Integer interval;
    private String listenFile;
    private String listenDir;
    final static Logger log = LoggerFactory.getLogger(AccountHandler.class);

    private transient Settings settings;
    private static ScheduledExecutorService intervalExecutor;
    private static ExecutorService listenerExecutor;

    public enum AutoTaskType{
        INTERVAL_SEC,
        LISTEN_FILE,
        NULL, LISTEN_DIR
    }

    public AutoTask(Handler.HandlerType handlerType, String methodName, Integer interval, String listenFile, String listenDir) {
        this.handlerType = handlerType;
        this.methodName = methodName;
        this.interval = interval;
        this.listenFile = listenFile;
        this.listenDir = listenDir;
        if(interval != null){
            this.type = AutoTaskType.INTERVAL_SEC;
        }else if(listenFile != null){
            this.type = AutoTaskType.LISTEN_FILE;
        }else if(listenDir != null){
            this.type = AutoTaskType.LISTEN_DIR;
        }else this.type = AutoTaskType.NULL;
    }

    public AutoTask(Handler.HandlerType handlerType, String methodName, String listenDir) {
        this(handlerType, methodName, null, null, listenDir);
    }

    public AutoTask(Handler.HandlerType handlerType, String methodName, Integer intervalSec) {
        this(handlerType, methodName, intervalSec, null, null);
    }
    
    public AutoTaskType getType() {
        return type;
    }

    public void setType(AutoTaskType type) {
        this.type = type;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public String getListenFile() {
        return listenFile;
    }

    public void setListenFile(String listenFile) {
        this.listenFile = listenFile;
    }

    public String getListenDir() {
        return listenDir;
    }

    public void setListenDir(String listenDir) {
        this.listenDir = listenDir;
    }

    public Handler.HandlerType getHandlerType() {
        return handlerType;
    }

    public void setHandlerType(Handler.HandlerType handlerType) {
        this.handlerType = handlerType;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }
    
    /**
     * Runs all auto tasks in the provided list.
     * For interval-based tasks (INTERVAL_SEC, INTERVAL_DAY), a single thread is created.
     * For file/directory listening tasks (LISTEN_FILE, LISTEN_DIR), separate threads are created for each task.
     * 
     * @param autoTasks List of auto tasks to run
     * @param settings Settings object containing handlers
     */
    public static void runAutoTask(List<AutoTask> autoTasks, Settings settings) {
        if (autoTasks == null || autoTasks.isEmpty()) {
            log.debug("No auto tasks provided to run");
            return;
        }

        // Set settings for all tasks
        for (AutoTask task : autoTasks) {
            task.setSettings(settings);
        }

        // Create a scheduled executor for interval-based tasks
        intervalExecutor = Executors.newScheduledThreadPool(1);
        
        // Create a thread pool for file/directory listening tasks
        listenerExecutor = Executors.newCachedThreadPool();

        for (AutoTask task : autoTasks) {
            try {
                if (task.getType() == AutoTaskType.INTERVAL_SEC) {
                    // Handle interval-based tasks
                    scheduleIntervalTask(task, intervalExecutor);
                } else if (task.getType() == AutoTaskType.LISTEN_FILE) {
                    // Handle file listening tasks
                    listenerExecutor.submit(() -> listenToFile(task));
                } else if (task.getType() == AutoTaskType.LISTEN_DIR) {
                    // Handle directory listening tasks
                    listenerExecutor.submit(() -> listenToDirectory(task));
                }
            } catch (Exception e) {
                log.info("Error setting up auto task: " + task.getMethodName(), e);
            }
        }
    }

    /**
     * Schedules an interval-based task to run at the specified interval
     */
    private static void scheduleIntervalTask(AutoTask task, ScheduledExecutorService executor) {
        try {
            // Get the handler from settings
            Handler<?> handler = task.getSettings().getHandler(task.getHandlerType());
            if (handler == null) {
                log.info("Handler not found for type: " + task.getHandlerType());
                return;
            }
            
            // Get the method to invoke
            Method method = handler.getClass().getMethod(task.getMethodName());
            
            // Calculate the interval in milliseconds
            long intervalMillis = task.getInterval() * 1000L;
            
            // Schedule the task to run at the specified interval
            executor.scheduleAtFixedRate(() -> {
                try {
                    method.invoke(handler); // Invoke the method on the handler instance
                } catch (Exception e) {
                    log.info("Error executing interval task: " + task.getMethodName(), e);
                }
            }, 0, intervalMillis, TimeUnit.MILLISECONDS);
            
            log.info("Scheduled interval task: " + task.getMethodName() + " with interval: " + intervalMillis + "ms");
        } catch (Exception e) {
            log.info("Error scheduling interval task: " + task.getMethodName(), e);
        }
    }

    /**
     * Sets up a file watcher for a specific file
     */
    private static void listenToFile(AutoTask task) {
        try {
            Path filePath = Paths.get(task.getListenFile());
            if (!Files.exists(filePath)) {
                log.debug("File does not exist: " + task.getListenFile());
                return;
            }
            
            // Get the handler from settings
            Handler<?> handler = task.getSettings().getHandler(task.getHandlerType());
            if (handler == null) {
                log.info("Handler not found for type: " + task.getHandlerType());
                return;
            }
            
            // Get the method to invoke
            Method method = handler.getClass().getMethod(task.getMethodName());
            
            // Create a watch service
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path parentDir = filePath.getParent();
            parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            
            log.info("Started listening to file: " + task.getListenFile());
            
            // Watch for changes
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changedFile = (Path) event.context();
                        if (changedFile.equals(filePath.getFileName())) {
                            // File has changed, invoke the method
                            method.invoke(handler); // Invoke the method on the handler instance
//                            log.info("File changed, executed: " + task.getMethodName());
                        }
                    }
                    key.reset();
                } catch (InterruptedException e) {
                    // Thread was interrupted, exit the loop
                    log.info("File listener interrupted for: " + task.getListenFile());
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    break;
                }
            }
            
            // Close the watch service when done
            watchService.close();
        } catch (Exception e) {
            log.error("Error in file listener: " + task.getListenFile(), e);
        }
    }

    /**
     * Sets up a directory watcher for a specific directory
     */
    private static void listenToDirectory(AutoTask task) {
        try {
            Path dirPath = Paths.get(task.getListenDir());
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                log.debug("Directory does not exist: " + task.getListenDir());
                return;
            }
            
            // Get the handler from settings
            Handler<?> handler = task.getSettings().getHandler(task.getHandlerType());
            if (handler == null) {
                log.info("Handler not found for type: " + task.getHandlerType());
                return;
            }
            
            // Get the method to invoke
            Method method = handler.getClass().getMethod(task.getMethodName());
            
            // Create a watch service
            WatchService watchService = FileSystems.getDefault().newWatchService();
            dirPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            
            log.info("Started listening to directory: " + task.getListenDir());
            
            // Watch for changes
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // Directory has changed, invoke the method
                        method.invoke(handler); // Invoke the method on the handler instance
//                        log.info("Directory changed, executed: " + task.getMethodName());
                    }
                    key.reset();
                } catch (InterruptedException e) {
                    // Thread was interrupted, exit the loop
                    log.info("Directory listener interrupted for: " + task.getListenDir());
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    break;
                }
            }
            
            // Close the watch service when done
            watchService.close();
        } catch (Exception e) {
            log.error("Error in directory listener: " + task.getListenDir(), e);
        }
    }

    /**
     * Stops all running auto tasks by shutting down the executor services
     */
    public static void stopAllTasks() {
        log.info("Stopping all auto tasks...");
        
        try {
            if (intervalExecutor != null) {
                try {
                    intervalExecutor.shutdown();
                    if (!intervalExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                        log.debug("Interval executor did not terminate in the specified time, forcing shutdown");
                        intervalExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.debug("Interrupted while waiting for interval executor to terminate, forcing shutdown");
                    intervalExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.info("Error shutting down interval executor: " + e.getMessage());
                }
                log.info("Interval executor shutdown complete");
            }
            
            if (listenerExecutor != null) {
                try {
                    listenerExecutor.shutdown();
                    if (!listenerExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                        log.debug("Listener executor did not terminate in the specified time, forcing shutdown");
                        listenerExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.debug("Interrupted while waiting for listener executor to terminate, forcing shutdown");
                    listenerExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("Error shutting down listener executor: " + e.getMessage());
                }
                log.info("Listener executor shutdown complete");
            }
        } catch (Exception e) {
            log.error("Unexpected error while stopping auto tasks: " + e.getMessage());
        }
    }

    public void checkTrigger(BufferedReader br) {
        switch (type) {
            case INTERVAL_SEC -> {
                if (Inputer.askIfYes(br, this.handlerType.name() + "Handler." + this.methodName + " will auto run every " + this.interval + " seconds(" + NumberUtils.roundDouble2((double) this.interval / Constants.SEC_PER_DAY) + " days). Change it?")) {
                    interval = Inputer.inputInteger(br, "Input the interval seconds", 0, 0);
                }
            }
            case LISTEN_FILE -> {
                if (Inputer.askIfYes(br, this.handlerType.name() + "Handler." + this.methodName + " will auto run when the file " + this.listenFile + " is modified. Change it?")) {
                    listenFile = Inputer.inputString(br, "Input the file path", listenFile);
                }
            }
            case LISTEN_DIR -> {
                if (Inputer.askIfYes(br, this.handlerType.name() + "Handler." + this.methodName + " will auto run when the directory " + this.listenDir + " is modified. Change it?")) {
                    listenDir = Inputer.inputString(br, "Input the directory path", listenDir);
                }
            }
            default -> {
                System.out.println("Unknown trigger type:"+type);
                return;
            }
        }
    }
}
