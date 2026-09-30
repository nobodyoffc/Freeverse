package fapi.components.disk;

import config.Settings;
import core.crypto.KeyTools;
import data.fcData.DiskItem;
import data.feipData.ServiceType;
import fapi.client.FapiClient;
import fapi.components.DiskComponent;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DISK sync between two servers, over FUDP on localhost, with both sides keeping metadata in
 * LevelDB: disk.list pages (sort since asc, id asc, cursor) and disk.get downloads.
 */
class DiskSyncOverStoreTest {

    static final SecureRandom RNG = new SecureRandom();

    @TempDir
    Path dir;
    final List<FudpNode> nodes = new ArrayList<>();
    final List<AutoCloseable> closers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (AutoCloseable c : closers) {
            try { c.close(); } catch (Exception ignored) { }
        }
        for (FudpNode n : nodes) {
            try { n.stop(); } catch (Exception ignored) { }
        }
    }

    FudpNode node(byte[] priv, int port, String name) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setMaxPacketSize(1400);
        c.setDataDir(dir.resolve(name).toString());
        FudpNode n = new FudpNode(priv, c);
        nodes.add(n);
        return n;
    }

    static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    @Test
    void aDestinationCopiesEverySourceFileAcrossPages() throws Exception {
        int port = 29000 + RNG.nextInt(2000);

        // ---- source: a real FAPI server running DISK, no ES
        byte[] srcPriv = key();
        byte[] srcPub = KeyTools.prikeyToPubkey(srcPriv);
        String srcFid = KeyTools.pubkeyToFchAddr(srcPub);
        FudpNode srcNode = node(srcPriv, port, "src-node");
        Settings srcSettings = mock(Settings.class);
        when(srcSettings.getMainFid()).thenReturn(srcFid);
        when(srcSettings.getSid()).thenReturn("srcsid");
        when(srcSettings.getDbDir()).thenReturn(dir.resolve("src-db").toString());
        when(srcSettings.getClient(any(ServiceType.class))).thenReturn(null);
        FapiServer source = new FapiServer(srcSettings);
        source.setFudpNode(srcNode);
        source.initialize();
        DiskComponent srcDisk = new DiskComponent();
        source.registerComponent(srcDisk);
        closers.add(() -> srcDisk.close(1000));
        srcNode.setEventListener(source);
        srcNode.start();

        // 230 files, several per millisecond of "since": more than two pages of 100
        Set<String> want = new HashSet<>();
        for (int i = 0; i < 230; i++) {
            byte[] content = new byte[64 + RNG.nextInt(512)];
            RNG.nextBytes(content);
            want.add(srcDisk.getDiskHandler().storeFromBytes(content, true, 30).getId());
        }
        assertEquals(230, srcDisk.getDiskHandler().getMetaStore().count());

        // ---- destination: a handler on its own store, syncing through a real client
        FudpNode dstNode = node(key(), port + 1, "dst-node");
        dstNode.start();
        dstNode.addPeer(srcFid, srcPub, "127.0.0.1", port);
        FapiClient toSource = new FapiClient(dstNode, srcFid, "srcsid");
        FapiServer dstServer = mock(FapiServer.class);
        when(dstServer.getOrCreateClient(anyString())).thenReturn(toSource);

        DiskMetaStore dstStore = DiskMetaStore.open(dir.resolve("dst-meta"), 100_000);
        closers.add(dstStore);
        FapiDiskHandler dstHandler = new FapiDiskHandler(dir.resolve("dst-files"), dstStore);
        DiskSyncManager sync = new DiskSyncManager(dstServer, dstHandler,
                List.of(new DiskSyncSource("srcsid", "fudp://127.0.0.1:" + port, true)),
                10_000_000, 1_000_000_000L, 0, 30, dir.resolve("dst-db").toString(), 24);
        sync.start();                // loads saved state; its first scheduled run is a minute away
        closers.add(sync::stop);
        sync.syncAll();

        assertEquals(230, dstStore.count(), "state: " + sync.getSyncStates());
        for (String did : want) {
            DiskItem meta = dstHandler.getMetadata(did);
            assertNotNull(meta, did);
            assertNotNull(dstHandler.retrieve(did), "content hash verified for " + did);
        }
        assertEquals(srcDisk.getDiskHandler().getTotalStorageSize(), dstHandler.getTotalStorageSize());

        // a second cycle resumes from the saved cursor and finds nothing new
        sync.syncAll();
        assertEquals(230, dstStore.count());
    }
}
