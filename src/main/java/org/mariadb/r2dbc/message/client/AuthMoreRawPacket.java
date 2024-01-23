// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2024 MariaDB Corporation Ab

package org.mariadb.r2dbc.message.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.mariadb.r2dbc.message.ClientMessage;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.MessageSequence;
import reactor.core.publisher.Mono;

public final class AuthMoreRawPacket implements ClientMessage {

  private final byte[] raw;
  private final MessageSequence sequencer;

  public AuthMoreRawPacket(MessageSequence sequencer, byte[] raw) {
    this.sequencer = sequencer;
    this.raw = raw;
  }

  @Override
  public Mono<ByteBuf> encode(Context context, ByteBufAllocator allocator) {
    ByteBuf buf = allocator.ioBuffer(raw.length);
    buf.writeBytes(raw);
    return Mono.just(buf);
  }

  @Override
  public MessageSequence getSequencer() {
    return sequencer;
  }
}
