package freeim;

import fudp.node.FudpNode;
import fudp.node.Peer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around FudpNode's peer management for CLI display.
 */
public class PeerManager {
    private final FudpNode node;
    private final BufferedReader br;

    public PeerManager(FudpNode node, BufferedReader br) {
        this.node = node;
        this.br = br;
    }

    public void addPeer() {
        try {
            System.out.print("  Peer public key (hex): ");
            String pubkeyHex = br.readLine().trim();
            if (pubkeyHex.isEmpty()) return;

            byte[] pubkey = utils.Hex.fromHex(pubkeyHex);
            String peerId = core.crypto.KeyTools.pubkeyToFchAddr(pubkey);

            System.out.print("  Host (IP or domain): ");
            String host = br.readLine().trim();

            System.out.print("  Port: ");
            int port = Integer.parseInt(br.readLine().trim());

            System.out.print("  Alias (optional): ");
            String alias = br.readLine().trim();

            if (alias.isEmpty()) {
                node.addPeer(peerId, pubkey, host, port);
            } else {
                node.addPeer(peerId, pubkey, host, port, alias);
            }
            System.out.println("  Added: " + peerId);
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    public void listPeers() {
        List<Peer> peers = node.listPeers();
        if (peers.isEmpty()) {
            System.out.println("  No peers.");
            return;
        }
        System.out.println();
        for (int i = 0; i < peers.size(); i++) {
            Peer p = peers.get(i);
            String alias = p.getAlias() != null ? " (" + p.getAlias() + ")" : "";
            List<Peer.Endpoint> endpoints = p.getEndpoints();
            if (endpoints.isEmpty()) {
                System.out.printf("  %d. %s%s @ no address%n", i + 1, p.getId(), alias);
            } else if (endpoints.size() == 1) {
                System.out.printf("  %d. %s%s @ %s%n", i + 1, p.getId(), alias, endpoints.get(0));
            } else {
                System.out.printf("  %d. %s%s @ %d endpoints:%n", i + 1, p.getId(), alias, endpoints.size());
                for (Peer.Endpoint ep : endpoints) {
                    System.out.printf("       - %s%n", ep);
                }
            }
        }
    }

    public void removePeer() {
        try {
            String peerId = promptSelectPeer("Remove peer");
            if (peerId == null) return;
            node.removePeer(peerId);
            System.out.println("  Removed.");
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    public void setAlias() {
        try {
            String peerId = promptSelectPeer("Set alias for");
            if (peerId == null) return;

            Peer peer = node.getPeer(peerId);
            if (peer != null && peer.getAlias() != null) {
                System.out.println("  Current alias: " + peer.getAlias());
            }

            System.out.print("  New alias (empty to clear): ");
            String alias = br.readLine().trim();
            node.setAlias(peerId, alias.isEmpty() ? null : alias);
            System.out.println("  Alias " + (alias.isEmpty() ? "cleared" : "set to '" + alias + "'") + ".");
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    public void pingPeer() {
        try {
            String peerId = promptSelectPeer("Ping");
            if (peerId == null) return;
            node.ping(peerId);
            System.out.println("  Ping sent.");
        } catch (Exception e) {
            System.out.println("  Error: " + e.getMessage());
        }
    }

    /**
     * Prompt user to select a peer with fuzzy search.
     * Accepts partial FID or alias, shows matching list if multiple hits.
     * Returns the selected peer's FID, or null if cancelled.
     */
    public String promptSelectPeer(String action) throws IOException {
        System.out.print("  " + action + " — peer FID or alias: ");
        String input = br.readLine().trim();
        if (input.isEmpty()) return null;

        // Exact match first
        Peer exact = node.getPeer(input);
        if (exact != null) return exact.getId();

        // Fuzzy search: match partial FID or alias (case-insensitive)
        String lower = input.toLowerCase();
        List<Peer> matches = new ArrayList<>();
        for (Peer p : node.listPeers()) {
            boolean fidMatch = p.getId().toLowerCase().contains(lower);
            boolean aliasMatch = p.getAlias() != null && p.getAlias().toLowerCase().contains(lower);
            if (fidMatch || aliasMatch) {
                matches.add(p);
            }
        }

        if (matches.isEmpty()) {
            System.out.println("  No peers matching '" + input + "'.");
            return null;
        }

        if (matches.size() == 1) {
            Peer p = matches.get(0);
            String display = p.getAlias() != null ? p.getAlias() + " (" + shortFid(p.getId()) + ")" : p.getId();
            System.out.println("  -> " + display);
            return p.getId();
        }

        // Multiple matches — show numbered list
        System.out.println("  Matches:");
        for (int i = 0; i < matches.size(); i++) {
            Peer p = matches.get(i);
            String alias = p.getAlias() != null ? " (" + p.getAlias() + ")" : "";
            System.out.printf("    %d. %s%s%n", i + 1, p.getId(), alias);
        }
        System.out.print("  Choose (1-" + matches.size() + "): ");
        String choice = br.readLine().trim();
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx >= 0 && idx < matches.size()) {
                return matches.get(idx).getId();
            }
        } catch (NumberFormatException ignored) {}

        System.out.println("  Cancelled.");
        return null;
    }

    private String shortFid(String fid) {
        if (fid == null) return "?";
        return fid.length() > 10 ? fid.substring(0, 6) + ".." + fid.substring(fid.length() - 4) : fid;
    }
}
