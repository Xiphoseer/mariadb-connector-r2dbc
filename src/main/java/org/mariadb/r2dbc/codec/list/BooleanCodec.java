// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2024 MariaDB Corporation Ab

package org.mariadb.r2dbc.codec.list;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.mariadb.r2dbc.ExceptionFactory;
import org.mariadb.r2dbc.codec.Codec;
import org.mariadb.r2dbc.codec.DataType;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.server.ColumnDefinitionPacket;

public class BooleanCodec implements Codec<Boolean> {

  public static final BooleanCodec INSTANCE = new BooleanCodec();

  private static final EnumSet<DataType> COMPATIBLE_TYPES =
      EnumSet.of(
          DataType.TEXT,
          DataType.VARSTRING,
          DataType.STRING,
          DataType.BIGINT,
          DataType.INTEGER,
          DataType.MEDIUMINT,
          DataType.SMALLINT,
          DataType.TINYINT,
          DataType.DECIMAL,
          DataType.OLDDECIMAL,
          DataType.FLOAT,
          DataType.DOUBLE,
          DataType.BIT);

  public boolean canDecode(ColumnDefinitionPacket column, Class<?> type) {
    return COMPATIBLE_TYPES.contains(column.getDataType())
        && ((type.isPrimitive() && type == Boolean.TYPE) || type.isAssignableFrom(Boolean.class));
  }

  public boolean canEncode(Class<?> value) {
    return Boolean.class.isAssignableFrom(value);
  }

  @Override
  public Boolean decodeText(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends Boolean> type,
      ExceptionFactory factory) {
    switch (column.getDataType()) {
      case BIT:
        return ByteCodec.parseBit(buf, length) != 0;

      case DECIMAL:
      case OLDDECIMAL:
      case FLOAT:
      case DOUBLE:
        return new BigDecimal(buf.readCharSequence(length, StandardCharsets.US_ASCII).toString())
                .intValue()
            != 0;

      case TINYINT:
      case SMALLINT:
      case MEDIUMINT:
      case INTEGER:
      case BIGINT:
      case YEAR:
        String val = buf.readCharSequence(length, StandardCharsets.US_ASCII).toString();
        return !"0".equals(val);

      default:
        // VARCHAR, VARSTRING, STRING:
        String s = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        return !"0".equals(s);
    }
  }

  @Override
  public Boolean decodeBinary(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends Boolean> type,
      ExceptionFactory factory) {

    switch (column.getDataType()) {
      case BIT:
        return ByteCodec.parseBit(buf, length) != 0;

      case DECIMAL:
      case OLDDECIMAL:
        return new BigDecimal(buf.readCharSequence(length, StandardCharsets.US_ASCII).toString())
                .intValue()
            != 0;
      case FLOAT:
        return ((int) buf.readFloatLE()) != 0;

      case DOUBLE:
        return ((int) buf.readDoubleLE()) != 0;

      case TINYINT:
        return buf.readByte() != 0;

      case YEAR:
      case SMALLINT:
        return buf.readShortLE() != 0;

      case MEDIUMINT:
        boolean b = buf.readMediumLE() != 0;
        buf.readByte(); // needed since binary protocol exchange for medium are on 4 bytes
        return b;

      case INTEGER:
        return buf.readIntLE() != 0;
      case BIGINT:
        return buf.readLongLE() != 0;

      default:
        // VARCHAR, VARSTRING, STRING:
        return !"0".equals(buf.readCharSequence(length, StandardCharsets.UTF_8).toString());
    }
  }

  @Override
  public void encodeDirectText(ByteBuf out, Object value, Context context) {
    out.writeCharSequence(((Boolean) value) ? "1" : "0", StandardCharsets.US_ASCII);
  }

  @Override
  public void encodeDirectBinary(
      ByteBufAllocator allocator, ByteBuf out, Object value, Context context) {
    out.writeByte(((Boolean) value) ? 1 : 0);
  }

  public DataType getBinaryEncodeType() {
    return DataType.TINYINT;
  }
}
