# FUDP8V1_TcpBinding

|Field|Content|
|---|---|
|Title|TCP Binding|
|Type|FUDP|
|SN|8|
|Ver|1|
|Status|Draft|
|Author|C_armX|
|Created|2026-09-29|
|PID||

## Contents

- [Abstract](#abstract)
- [1. Motivation](#1-motivation)
- [2. Framing](#2-framing)
- [3. Addressing](#3-addressing)
- [4. Server Behaviour](#4-server-behaviour)
- [5. Client Behaviour](#5-client-behaviour)
- [6. What Changes and What Does Not](#6-what-changes-and-what-does-not)
- [7. Security Considerations](#7-security-considerations)
- [8. Versioning](#8-versioning)
- [9. References](#9-references)

---

## Abstract

FUDP8V1 carries FUDP packets over a TCP connection, for networks where UDP to a server goes out but nothing comes back. Each FUDP packet, byte for byte as it would travel in a UDP datagram, is sent on the TCP stream prefixed with its length. Everything above the socket — the handshake, encryption, streams, ACKs, congestion control, DATAGRAM frames — is unchanged. A server listens for TCP beside its UDP port; a client that hears nothing over UDP connects over TCP, to port 443 first.

## 1. Motivation

Some networks let UDP out and drop every reply. On one mainland Chinese mobile network, a capture at a Hong Kong server showed every packet from a phone arriving and every reply — down to 100-byte acknowledgments, on ports 443, 3478 and 8443 alike — sent back to the phone's exact address and port and never received. The same phone worked over home broadband. No retry can fix this. TCP to the same server gets through such networks, and TCP 443 almost always does.

## 2. Framing

On the TCP stream, in both directions, each FUDP packet is sent as

```
length  (2)  big-endian unsigned: the number of bytes that follow
packet  (length)  one FUDP packet, exactly as it would be the payload of a UDP datagram
```

A `length` of 0 is allowed and carries nothing; receivers skip it. Packets MUST NOT exceed 65535 bytes, and SHOULD keep to the same size budget as over UDP (FUDP1 §Packet Size Budget), so a connection can move between the two.

A sender MAY put several packets in one TCP write. A receiver reads the stream as a sequence of frames and hands each packet to the protocol as if it had arrived as a UDP datagram from the peer's address (§3).

## 3. Addressing

FUDP keys a peer by its network address. Over TCP:

- **A server** takes each accepted TCP connection's remote address as the peer's address, and sends everything for that address down that connection.
- **A client** makes the TCP connection stand in for the UDP address it already knows the server by: packets it would send to that address go down the connection, and packets from the connection are handled as if from that address. So the peer book, discovery and every connection record stay as they are.

A reflexive address a server reports for a TCP peer (for example, a CALL relay's `map` candidate, FAPI16) is the TCP connection's remote address, which is of no use for UDP hole punching. Clients reached over TCP SHOULD expect direct UDP paths to fail, and fall back as they would anyway.

## 4. Server Behaviour

- A server MAY listen for FUDP over TCP beside its UDP port. It SHOULD listen on TCP 443 where it can, since that is the port restrictive networks leave open, and MAY listen on the UDP port's number as well.
- It MUST process packets from TCP connections in the same order and on the same path as UDP packets: replay protection, the DDoS defences of FUDP5 and connection resolution apply unchanged, with the TCP remote address as the source.
- It MUST NOT let a slow TCP connection block anything else. A server SHOULD queue each connection's outgoing packets, bounded, and drop when the queue is full, as a congested UDP path would.
- When a TCP connection closes, the peer's FUDP connection is not closed with it: it times out, or resumes if the client reconnects, as after a UDP path change.

## 5. Client Behaviour

- A client SHOULD use UDP first, and fall back to TCP only when nothing at all comes back over UDP within a few seconds — neither a PUBLIC_KEY to a HELLO nor a reply over an established connection.
- It then connects over TCP to the same host: port 443 first, then the UDP port's number. It SHOULD try them at the same time, since a port whose packets are silently dropped costs its whole connect timeout.
- It SHOULD remember, for a while (half an hour is suggested), that UDP to that server got no answer on this network, and try TCP first next time; and forget it if TCP then fails, since the device may have moved to a network where UDP works.
- Discovery (HELLO and PUBLIC_KEY, FUDP4) works over TCP as over UDP.

## 6. What Changes and What Does Not

Nothing above the socket changes: the packet format, handshake, encryption, streams, ACKs, retransmission and congestion control are those of UDP. The costs of running a loss-recovering protocol over a reliable stream follow:

- **Head-of-line blocking.** A lost TCP segment holds back every FUDP packet behind it until TCP resends it, so DATAGRAM audio arrives in bursts under loss instead of with gaps. Real-time use degrades, but works.
- **Double recovery.** FUDP's own retransmission still runs, and may resend what TCP is already resending. This costs some bandwidth under loss and nothing otherwise.
- **Congestion control** runs in both layers; FUDP's window adapts to the delay TCP's recovery adds.

These are why UDP stays the first choice.

## 7. Security Considerations

1. **Nothing is weaker.** Every data packet is encrypted and authenticated end to end exactly as over UDP (FUDP4); TCP adds a transport, not a trust boundary. An on-path attacker can reset or stall the TCP connection, which it could equally do by dropping UDP.
2. **Source addresses are real.** Unlike UDP, a TCP peer's address has completed a handshake, so it cannot be spoofed; address-based defences (FUDP5) are, if anything, stronger.
3. **Connection exhaustion.** A server accepting TCP SHOULD bound the number of connections per source address and in total, and close connections idle past the FUDP idle timeout.
4. **Visibility.** On TCP 443 the traffic is not TLS; a network that inspects port 443 can see that it is something else. FUDP8 does not try to disguise itself.

## 8. Versioning

| Version | Date | Changes |
|---|---|---|
| 1 | 2026-09-29 | Initial specification: length-prefixed FUDP packets over TCP, addressing, server and client behaviour. Implemented in FC-JDK and FC-AJDK (`TcpBridge`) and FreerForMac (`TcpDatagramTransport`), first for CALL relays (FAPI16). |

## 9. References

- **FUDP1**: Core transport, packet format and size budget.
- **FUDP4**: Security, including HELLO/PUBLIC_KEY discovery.
- **FUDP5**: DDoS defence.
- **FUDP7**: DATAGRAM frames.
- **FAPI16**: CALL, whose relays first used this binding.
