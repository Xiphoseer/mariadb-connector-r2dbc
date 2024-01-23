// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2024 MariaDB Corporation Ab

package org.mariadb.r2dbc.codec.list;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.mariadb.r2dbc.ExceptionFactory;
import org.mariadb.r2dbc.codec.Codec;
import org.mariadb.r2dbc.codec.DataType;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.server.ColumnDefinitionPacket;

public class ShortCodec implements Codec<Short> {

  public static final ShortCodec INSTANCE = new ShortCodec();

  private static final EnumSet<DataType> COMPATIBLE_TYPES =
      EnumSet.of(
          DataType.FLOAT,
          DataType.DOUBLE,
          DataType.OLDDECIMAL,
          DataType.TEXT,
          DataType.DECIMAL,
          DataType.ENUM,
          DataType.VARSTRING,
          DataType.STRING,
          DataType.TINYINT,
          DataType.SMALLINT,
          DataType.MEDIUMINT,
          DataType.INTEGER,
          DataType.BIGINT,
          DataType.BIT,
          DataType.YEAR);

  public boolean canDecode(ColumnDefinitionPacket column, Class<?> type) {
    return COMPATIBLE_TYPES.contains(column.getDataType())
        && ((type.isPrimitive() && type == Short.TYPE) || type.isAssignableFrom(Short.class));
  }

  public boolean canEncode(Class<?> value) {
    return Short.class.isAssignableFrom(value);
  }

  @Override
  public Short decodeText(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends Short> type,
      ExceptionFactory factory) {
    long result;
    switch (column.getDataType()) {
      case TINYINT:
      case SMALLINT:
      case MEDIUMINT:
      case INTEGER:
      case BIGINT:
      case YEAR:
        result = LongCodec.parse(buf, length);
        break;

      case BIT:
        result = 0;
        for (int i = 0; i < length; i++) {
          byte b = buf.readByte();
          result = (result << 8) + (b & 0xff);
        }
        break;

      default:
        // FLOAT, DOUBLE, OLDDECIMAL, VARCHAR, DECIMAL, ENUM, VARSTRING, STRING:
        String str = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        try {
          result = new BigDecimal(str).setScale(0, RoundingMode.DOWN).longValueExact();
          break;
        } catch (NumberFormatException | ArithmeticException nfe) {
          throw factory.createParsingException(
              String.format("value '%s' cannot be decoded as Short", str));
        }
    }

    if ((short) result != result || (result < 0 && !column.isSigned())) {
      throw factory.createParsingException("Short overflow");
    }

    return (short) result;
  }

  @Override
  public Short decodeBinary(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends Short> type,
      ExceptionFactory factory) {

    long result;
    switch (column.getDataType()) {
      case TINYINT:
        result = column.isSigned() ? buf.readByte() : buf.readUnsignedByte();
        break;

      case YEAR:
      case SMALLINT:
        result = column.isSigned() ? buf.readShortLE() : buf.readUnsignedShortLE();
        break;

      case MEDIUMINT:
        result = column.isSigned() ? buf.readMediumLE() : buf.readUnsignedMediumLE();
        buf.readByte(); // needed since binary protocol exchange for medium are on 4 bytes
        break;

      case INTEGER:
        result = column.isSigned() ? buf.readIntLE() : buf.readUnsignedIntLE();
        break;

      case BIGINT:
        result = buf.readLongLE();
        if (result < 0 & !column.isSigned()) {
          throw factory.createParsingException("Short overflow");
        }
        break;

      case BIT:
        result = 0;
        for (int i = 0; i < length; i++) {
          byte b = buf.readByte();
          result = (result << 8) + (b & 0xff);
        }
        break;

      case FLOAT:
        result = (long) buf.readFloatLE();
        break;

      case DOUBLE:
        result = (long) buf.readDoubleLE();
        break;

      default:
        // OLDDECIMAL, VARCHAR, DECIMAL, ENUM, VARSTRING, STRING:
        String str = buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        try {
          result = new BigDecimal(str).setScale(0, RoundingMode.DOWN).longValueExact();
          break;
        } catch (NumberFormatException | ArithmeticException nfe) {
          throw factory.createParsingException(
              String.format("value '%s' cannot be decoded as Short", str));
        }
    }

    if ((short) result != result || (result < 0 && !column.isSigned())) {
      throw factory.createParsingException("Short overflow");
    }

    return (short) result;
  }

  @Override
  public void encodeDirectText(ByteBuf out, Object value, Context context) {
    out.writeCharSequence(value.toString(), StandardCharsets.US_ASCII);
  }

  @Override
  public void encodeDirectBinary(
      ByteBufAllocator allocator, ByteBuf out, Object value, Context context) {
    out.writeShortLE((Short) value);
  }

  public DataType getBinaryEncodeType() {
    return DataType.SMALLINT;
  }
}
