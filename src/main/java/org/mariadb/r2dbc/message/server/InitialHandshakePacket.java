// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2024 MariaDB Corporation Ab

package org.mariadb.r2dbc.message.server;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.mariadb.r2dbc.message.ServerMessage;
import org.mariadb.r2dbc.util.constants.Capabilities;

public final class InitialHandshakePacket implements ServerMessage {

  private static final String MARIADB_RPL_HACK_PREFIX = "5.5.5-";

  private final Sequencer sequencer;
  private final String serverVersion;
  private final long threadId;
  private final byte[] seed;
  private final long capabilities;
  private final short defaultCollation;
  private final short serverStatus;
  private final boolean mariaDBServer;
  private final String authenticationPluginType;
  private int majorVersion;
  private int minorVersion;
  private int patchVersion;

  private InitialHandshakePacket(
      Sequencer sequencer,
      String serverVersion,
      long threadId,
      byte[] seed,
      long capabilities,
      short defaultCollation,
      short serverStatus,
      boolean mariaDBServer,
      String authenticationPluginType) {

    this.sequencer = sequencer;
    this.serverVersion = serverVersion;
    this.threadId = threadId;
    this.seed = seed;
    this.capabilities = capabilities;
    this.defaultCollation = defaultCollation;
    this.serverStatus = serverStatus;
    this.mariaDBServer = mariaDBServer;
    this.authenticationPluginType = authenticationPluginType;
    parseVersion(serverVersion);
  }

  public static InitialHandshakePacket decode(Sequencer sequencer, ByteBuf buffer) {
    byte protocolVersion = buffer.readByte();
    if (protocolVersion != 0x0a) {
      throw new IllegalArgumentException(
          String.format("Unexpected initial handshake protocol value [%s]", protocolVersion));
    }

    int nullLength = buffer.bytesBefore((byte) 0x00);
    String serverVersion =
        buffer.toString(buffer.readerIndex(), nullLength, StandardCharsets.US_ASCII);
    buffer.skipBytes(nullLength + 1);
    long threadId = buffer.readIntLE();
    final byte[] seed1 = new byte[8];
    buffer.readBytes(seed1);
    buffer.skipBytes(1);
    int serverCapabilities2FirstBytes = buffer.readShortLE() & 0x0000ffff;
    short defaultCollation = buffer.readUnsignedByte();
    short serverStatus = buffer.readShortLE();
    int serverCapabilities4FirstBytes =
        serverCapabilities2FirstBytes + (buffer.readShortLE() << 16);
    int saltLength = 0;

    if ((serverCapabilities4FirstBytes & Capabilities.PLUGIN_AUTH) != 0) {
      saltLength = Math.max(12, buffer.readByte() - 9);
    } else {
      buffer.skipBytes(1);
    }
    buffer.skipBytes(6);

    // MariaDB additional capabilities.
    // Filled only if MariaDB server 10.2+
    long mariaDbAdditionalCapacities = buffer.readIntLE();
    byte[] seed;
    if ((serverCapabilities4FirstBytes & Capabilities.SECURE_CONNECTION) != 0) {
      final byte[] seed2;
      if (saltLength > 0) {
        seed2 = new byte[saltLength];
        buffer.readBytes(seed2);
      } else {
        seed2 = new byte[buffer.bytesBefore((byte) 0x00)];
        buffer.readBytes(seed2);
        buffer.skipBytes(1); // null ended
      }
      seed = new byte[seed1.length + seed2.length];
      System.arraycopy(seed1, 0, seed, 0, seed1.length);
      System.arraycopy(seed2, 0, seed, seed1.length, seed2.length);
    } else {
      seed = seed1;
    }
    buffer.skipBytes(1);

    /*
     * check for MariaDB 10.x replication hack , remove fake prefix if needed
     *  (see comments about MARIADB_RPL_HACK_PREFIX)
     */
    boolean serverMariaDb;
    if (serverVersion.startsWith(MARIADB_RPL_HACK_PREFIX)) {
      serverMariaDb = true;
      serverVersion = serverVersion.substring(MARIADB_RPL_HACK_PREFIX.length());
    } else {
      serverMariaDb = serverVersion.contains("MariaDB");
    }

    // since MariaDB 10.2
    long serverCapabilities;
    if ((serverCapabilities4FirstBytes & Capabilities.CLIENT_MYSQL) == 0) {
      serverCapabilities =
          (serverCapabilities4FirstBytes & 0xffffffffL) + (mariaDbAdditionalCapacities << 32);
      serverMariaDb = true;
    } else {
      serverCapabilities = serverCapabilities4FirstBytes & 0xffffffffL;
    }

    String authenticationPluginType = null;
    if ((serverCapabilities4FirstBytes & Capabilities.PLUGIN_AUTH) != 0) {
      int nullStLength = buffer.bytesBefore((byte) 0x00);
      authenticationPluginType =
          buffer.toString(buffer.readerIndex(), nullStLength, StandardCharsets.US_ASCII);
      buffer.skipBytes(nullStLength + 1);
    }

    return new InitialHandshakePacket(
        sequencer,
        serverVersion,
        threadId,
        seed,
        serverCapabilities,
        defaultCollation,
        serverStatus,
        serverMariaDb,
        authenticationPluginType);
  }

  public String getServerVersion() {
    return serverVersion;
  }

  public long getThreadId() {
    return threadId;
  }

  public byte[] getSeed() {
    return seed;
  }

  public long getCapabilities() {
    return capabilities;
  }

  public short getDefaultCollation() {
    return defaultCollation;
  }

  public short getServerStatus() {
    return serverStatus;
  }

  public boolean isMariaDBServer() {
    return mariaDBServer;
  }

  public String getAuthenticationPluginType() {
    return authenticationPluginType;
  }

  public Sequencer getSequencer() {
    return sequencer;
  }

  private void parseVersion(String serverVersion) {
    int length = serverVersion.length();
    char car;
    int offset = 0;
    int type = 0;
    int val = 0;
    for (; offset < length; offset++) {
      car = serverVersion.charAt(offset);
      if (car < '0' || car > '9') {
        switch (type) {
          case 0:
            majorVersion = val;
            break;
          case 1:
            minorVersion = val;
            break;
          case 2:
            patchVersion = val;
            return;
          default:
            break;
        }
        type++;
        val = 0;
      } else {
        val = val * 10 + car - 48;
      }
    }

    // serverVersion finished by number like "5.5.57", assign patchVersion
    if (type == 2) {
      patchVersion = val;
    }
  }

  public int getMajorServerVersion() {
    return majorVersion;
  }

  public int getMinorServerVersion() {
    return minorVersion;
  }

  @Override
  public boolean ending() {
    return true;
  }
}
