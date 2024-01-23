// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2024 MariaDB Corporation Ab

package org.mariadb.r2dbc.codec.list;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import org.mariadb.r2dbc.ExceptionFactory;
import org.mariadb.r2dbc.codec.Codec;
import org.mariadb.r2dbc.codec.DataType;
import org.mariadb.r2dbc.message.Context;
import org.mariadb.r2dbc.message.server.ColumnDefinitionPacket;
import org.mariadb.r2dbc.util.BufferUtils;

public class StringCodec implements Codec<String> {

  public static final StringCodec INSTANCE = new StringCodec();

  private static final EnumSet<DataType> COMPATIBLE_TYPES =
      EnumSet.of(
          DataType.BIT,
          DataType.OLDDECIMAL,
          DataType.TINYINT,
          DataType.SMALLINT,
          DataType.INTEGER,
          DataType.FLOAT,
          DataType.DOUBLE,
          DataType.TIMESTAMP,
          DataType.BIGINT,
          DataType.MEDIUMINT,
          DataType.DATE,
          DataType.TIME,
          DataType.DATETIME,
          DataType.YEAR,
          DataType.NEWDATE,
          DataType.JSON,
          DataType.DECIMAL,
          DataType.ENUM,
          DataType.SET,
          DataType.TEXT,
          DataType.VARSTRING,
          DataType.STRING);

  public static String zeroFilling(String value, ColumnDefinitionPacket col) {
    StringBuilder zeroAppendStr = new StringBuilder();
    long zeroToAdd = col.getDisplaySize() - value.length();
    while (zeroToAdd-- > 0) {
      zeroAppendStr.append("0");
    }
    return zeroAppendStr.append(value).toString();
  }

  public boolean canDecode(ColumnDefinitionPacket column, Class<?> type) {
    return COMPATIBLE_TYPES.contains(column.getDataType()) && type.isAssignableFrom(String.class);
  }

  public boolean canEncode(Class<?> value) {
    return String.class.isAssignableFrom(value);
  }

  @Override
  public String decodeText(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends String> type,
      ExceptionFactory factory) {
    if (column.getDataType() == DataType.BIT) {

      byte[] bytes = new byte[length];
      buf.readBytes(bytes);
      StringBuilder sb = new StringBuilder(bytes.length * Byte.SIZE + 3);
      sb.append("b'");
      boolean firstByteNonZero = false;
      for (int i = 0; i < Byte.SIZE * bytes.length; i++) {
        boolean b = (bytes[i / Byte.SIZE] & 1 << (Byte.SIZE - 1 - (i % Byte.SIZE))) > 0;
        if (b) {
          sb.append('1');
          firstByteNonZero = true;
        } else if (firstByteNonZero) {
          sb.append('0');
        }
      }
      sb.append("'");
      return sb.toString();
    }

    return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
  }

  @Override
  public String decodeBinary(
      ByteBuf buf,
      int length,
      ColumnDefinitionPacket column,
      Class<? extends String> type,
      ExceptionFactory factory) {
    String rawValue;
    switch (column.getDataType()) {
      case BIT:
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * Byte.SIZE + 3);
        sb.append("b'");
        boolean firstByteNonZero = false;
        for (int i = 0; i < Byte.SIZE * bytes.length; i++) {
          boolean b = (bytes[i / Byte.SIZE] & 1 << (Byte.SIZE - 1 - (i % Byte.SIZE))) > 0;
          if (b) {
            sb.append('1');
            firstByteNonZero = true;
          } else if (firstByteNonZero) {
            sb.append('0');
          }
        }
        sb.append("'");
        return sb.toString();

      case TINYINT:
        rawValue = String.valueOf(column.isSigned() ? buf.readByte() : buf.readUnsignedByte());
        if (column.isZeroFill()) {
          return zeroFilling(rawValue, column);
        }
        return rawValue;

      case YEAR:
        String s = String.valueOf(buf.readUnsignedShortLE());
        while (s.length() < column.getLength()) s = "0" + s;
        return s;

      case SMALLINT:
        rawValue =
            String.valueOf(column.isSigned() ? buf.readShortLE() : buf.readUnsignedShortLE());
        if (column.isZeroFill()) {
          return zeroFilling(rawValue, column);
        }
        return rawValue;

      case MEDIUMINT:
        rawValue =
            String.valueOf(column.isSigned() ? buf.readMediumLE() : buf.readUnsignedMediumLE());
        // medium int is encoded on 3 bytes + one empty byte
        buf.skipBytes(1);
        if (column.isZeroFill()) {
          return zeroFilling(rawValue, column);
        }
        return rawValue;

      case INTEGER:
        rawValue = String.valueOf(column.isSigned() ? buf.readIntLE() : buf.readUnsignedIntLE());
        if (column.isZeroFill()) {
          return zeroFilling(rawValue, column);
        }
        return rawValue;

      case BIGINT:
        BigInteger val;
        if (column.isSigned()) {
          val = BigInteger.valueOf(buf.readLongLE());
        } else {
          // need BIG ENDIAN, so reverse order
          byte[] bb = new byte[8];
          for (int ii = 7; ii >= 0; ii--) {
            bb[ii] = buf.readByte();
          }
          val = new BigInteger(1, bb);
        }
        rawValue = String.valueOf(val);
        if (column.isZeroFill()) {
          rawValue = zeroFilling(rawValue, column);
        }
        return rawValue;

      case FLOAT:
        return String.valueOf(buf.readFloatLE());

      case DOUBLE:
        return String.valueOf(buf.readDoubleLE());

      case TIME:
        long tDays = 0;
        int tHours = 0;
        int tMinutes = 0;
        int tSeconds = 0;
        long tMicroseconds = 0;
        boolean negate = false;

        if (length > 0) {
          negate = buf.readByte() == 0x01;
          tDays = buf.readUnsignedIntLE();
          tHours = buf.readByte();
          tMinutes = buf.readByte();
          tSeconds = buf.readByte();
          if (length > 8) {
            tMicroseconds = buf.readIntLE();
          }
        }
        int totalHour = (int) (tDays * 24 + tHours);
        String stTime =
            (negate ? "-" : "")
                + (totalHour < 10 ? "0" : "")
                + totalHour
                + ":"
                + (tMinutes < 10 ? "0" : "")
                + tMinutes
                + ":"
                + (tSeconds < 10 ? "0" : "")
                + tSeconds;
        if (column.getDecimals() == 0) return stTime;
        String stMicro = String.valueOf(tMicroseconds);
        while (stMicro.length() < column.getDecimals()) {
          stMicro = "0" + stMicro;
        }
        return stTime + "." + stMicro;

      case DATE:
        if (length == 0) return LocalDate.of(1970, 1, 1).toString();
        int dateYear = buf.readUnsignedShortLE();
        int dateMonth = buf.readByte();
        int dateDay = buf.readByte();
        return LocalDate.of(dateYear, dateMonth, dateDay).toString();

      case DATETIME:
      case TIMESTAMP:
        int year = 1970;
        int month = 1;
        int day = 1;
        int hour = 0;
        int minutes = 0;
        int seconds = 0;
        long microseconds = 0;
        if (length > 0) {
          year = buf.readUnsignedShortLE();
          month = buf.readByte();
          day = buf.readByte();
          if (length > 4) {
            hour = buf.readByte();
            minutes = buf.readByte();
            seconds = buf.readByte();

            if (length > 7) {
              microseconds = buf.readUnsignedIntLE();
            }
          }
          LocalDateTime dateTime =
              LocalDateTime.of(year, month, day, hour, minutes, seconds)
                  .plusNanos(microseconds * 1000);
          return dateTime.toLocalDate().toString() + ' ' + dateTime.toLocalTime().toString();
        }
        return null;

      default:
        return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }
  }

  @Override
  public void encodeDirectText(ByteBuf out, Object value, Context context) {
    out.writeBytes(BufferUtils.STRING_PREFIX);
    byte[] b = ((String) value).getBytes(StandardCharsets.UTF_8);
    BufferUtils.escapedBytes(out, b, b.length, context);
    out.writeByte('\'');
  }

  @Override
  public void encodeDirectBinary(
      ByteBufAllocator allocator, ByteBuf out, Object value, Context context) {
    byte[] b = ((String) value).getBytes(StandardCharsets.UTF_8);
    out.writeBytes(BufferUtils.encodeLength(b.length));
    out.writeBytes(b);
  }

  public DataType getBinaryEncodeType() {
    return DataType.TEXT;
  }
}
