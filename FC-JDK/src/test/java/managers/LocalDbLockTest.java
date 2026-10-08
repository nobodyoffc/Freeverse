package managers;

import data.fcData.FcEntity;
import db.LevelDB;
import db.LocalDB;
import exception.LocalDbUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A database another process holds must fail with an exception, never by exiting the JVM.
 *
 * Inside Tomcat the old System.exit(1) deadlocked with Tomcat's own shutdown hook: the process
 * stayed alive, never opened its ports, and could not be stopped with shutdown.sh. This holds the
 * lock from a second JVM, as the stray Tomcat did, and checks that initializeDB throws a
 * LocalDbUnavailableException naming the database and the cause.
 */
public class LocalDbLockTest {

    private static final String FID = "FEk41Kqjar45fLDriztUDTUkdki7mmcjWK";
    private static final String SID = "sid1";
    private static final String DB_NAME = "ACCOUNT";

    @TempDir
    Path dbDir;

    private Process holder;

    /** Run in a separate JVM: open the database and hold its lock until stdin closes. */
    public static void main(String[] args) throws Exception {
        LevelDB<FcEntity> db = new LevelDB<>(LocalDB.SortType.NO_SORT, FcEntity.class);
        db.initialize(args[0], args[1], args[2], args[3]);
        System.out.println("LOCKED");
        System.out.flush();
        while (System.in.read() != -1) {
            // hold until the test closes our stdin
        }
        db.close();
    }

    @AfterEach
    void stopHolder() throws Exception {
        if (holder != null && holder.isAlive()) {
            holder.getOutputStream().close();
            if (!holder.waitFor(10, TimeUnit.SECONDS)) holder.destroyForcibly();
        }
    }

    @SuppressWarnings("unchecked")
    private static Manager<FcEntity> managerWithoutConstructor() {
        Manager<FcEntity> manager = mock(Manager.class, CALLS_REAL_METHODS);
        doAnswer(inv -> new LevelDB<>(inv.getArgument(0), inv.getArgument(1)))
                .when(manager).createLocalDB(any(), any(), any());
        return manager;
    }

    @Test
    void databaseHeldByAnotherProcessThrowsInsteadOfExiting() throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        holder = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                LocalDbLockTest.class.getName(), FID, SID, dbDir.toString(), DB_NAME)
                .redirectErrorStream(true)
                .start();
        BufferedReader out = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        String line;
        boolean locked = false;
        while ((line = out.readLine()) != null) {
            if (line.contains("LOCKED")) {
                locked = true;
                break;
            }
        }
        assertTrue(locked, "the holder JVM did not open the database");

        Manager<FcEntity> manager = managerWithoutConstructor();
        LocalDbUnavailableException e = assertThrows(LocalDbUnavailableException.class,
                () -> manager.initializeDB(FID, SID, dbDir.toString(), DB_NAME, LocalDB.SortType.NO_SORT, FcEntity.class));

        assertTrue(e.getMessage().contains(DB_NAME), e.getMessage());
        assertTrue(e.getMessage().contains(dbDir.toString()), e.getMessage());
        assertTrue(e.getMessage().contains("in use by another process"), e.getMessage());
        assertTrue(holder.isAlive(), "the holder must be unaffected");
    }

    @Test
    void freeDatabaseOpens() {
        Manager<FcEntity> manager = managerWithoutConstructor();
        assertDoesNotThrow(() -> manager.initializeDB(FID, SID, dbDir.toString(), DB_NAME, LocalDB.SortType.NO_SORT, FcEntity.class));
        manager.localDB.close();
    }
}
