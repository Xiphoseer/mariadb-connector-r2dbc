// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2024 MariaDB Corporation Ab

package org.mariadb.r2dbc.message.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.mariadb.r2dbc.message.ClientMessage;
import org.mariadb.r2dbc.message.Context;
import reactor.core.publisher.Mono;

public final class QuitPacket implements ClientMessage {
  public static final QuitPacket INSTANCE = new QuitPacket();

  @Override
  public String toString() {
    return "QuitPacket{}";
  }

  @Override
  public Mono<ByteBuf> encode(Context context, ByteBufAllocator allocator) {
    ByteBuf buf = allocator.ioBuffer(1);
    buf.writeByte(0x01);
    return Mono.just(buf);
  }
}
