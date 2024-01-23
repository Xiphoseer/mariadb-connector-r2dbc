// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2020-2022 MariaDB Corporation Ab

package org.mariadb.r2dbc;

import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import org.mariadb.r2dbc.api.MariadbStatement;
import org.mariadb.r2dbc.client.Client;
import org.mariadb.r2dbc.client.MariadbResult;
import org.mariadb.r2dbc.codec.Codecs;
import org.mariadb.r2dbc.message.Protocol;
import org.mariadb.r2dbc.message.ServerMessage;
import org.mariadb.r2dbc.util.Assert;
import org.mariadb.r2dbc.util.Binding;
import org.mariadb.r2dbc.util.ServerPrepareResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public abstract class MariadbCommonStatement implements MariadbStatement {
  public static final int UNKNOWN_SIZE = -1;
  protected final List<Binding> bindings = new ArrayList<>();
  protected final Client client;
  protected final String initialSql;
  protected final MariadbConnectionConfiguration configuration;
  private final Protocol defaultProtocol;
  protected int expectedSize;
  protected ExceptionFactory factory;
  protected String[] generatedColumns;
  private Binding currentBinding;

  public MariadbCommonStatement(
      Client client,
      String sql,
      MariadbConnectionConfiguration configuration,
      Protocol defaultProtocol) {
    this.defaultProtocol = defaultProtocol;
    this.client = client;
    this.configuration = configuration;
    this.initialSql = Assert.requireNonNull(sql, "sql must not be null");
    this.factory = ExceptionFactory.withSql(sql);
  }

  /**
   * Augments an SQL statement with a {@code RETURNING} statement and column names. If the
   * collection is empty, uses {@code *} for column names.
   *
   * @param sql the SQL to augment
   * @param generatedColumns the names of the columns to augment with
   * @return an augmented sql statement returning the specified columns or a wildcard
   * @throws IllegalArgumentException if {@code sql} or {@code generatedColumns} is {@code null}
   */
  public static String augment(String sql, String[] generatedColumns) {
    Assert.requireNonNull(sql, "sql must not be null");
    Assert.requireNonNull(generatedColumns, "generatedColumns must not be null");
    return String.format(
        "%s RETURNING %s",
        sql, generatedColumns.length == 0 ? "*" : String.join(", ", generatedColumns));
  }

  protected static void tryNextBinding(
      Iterator<Binding> iterator, Sinks.Many<Binding> bindingSink, AtomicBoolean canceled) {

    if (canceled.get()) {
      return;
    }

    try {
      if (iterator.hasNext()) {
        bindingSink.emitNext(iterator.next(), Sinks.EmitFailureHandler.FAIL_FAST);
      } else {
        bindingSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
      }
    } catch (Exception e) {
      bindingSink.emitError(e, Sinks.EmitFailureHandler.FAIL_FAST);
    }
  }

  protected void initializeBinding() {
    currentBinding = new Binding(getExpectedSize());
  }

  public MariadbStatement add() {
    currentBinding.validate(getExpectedSize());
    this.bindings.add(currentBinding);
    currentBinding = new Binding(getExpectedSize());
    return this;
  }

  @Override
  public MariadbStatement bind(String identifier, Object value) {
    return bind(getColumnIndex(identifier), value);
  }

  @Override
  public MariadbStatement bindNull(String identifier, Class<?> type) {
    return bindNull(getColumnIndex(identifier), type);
  }

  @Override
  public MariadbStatement bindNull(int index, Class<?> type) {
    if (index < 0) {
      throw new IndexOutOfBoundsException(
          String.format("wrong index value %d, index must be positive", index));
    }
    if (index >= expectedSize && expectedSize != UNKNOWN_SIZE) {

      throw new IndexOutOfBoundsException(
          (getExpectedSize() == 0)
              ? String.format(
                  "Binding parameters is not supported for the statement '%s'", initialSql)
              : String.format(
                  "Cannot bind parameter %d, statement has %d parameters", index, expectedSize));
    }
    getCurrentBinding().add(index, Codecs.encodeNull(type, index));
    return this;
  }

  @Override
  public MariadbStatement bind(int index, Object value) {
    Assert.requireNonNull(value, "value must not be null");
    if (index < 0) {
      throw new IndexOutOfBoundsException(
          String.format("wrong index value %d, index must be positive", index));
    }

    getCurrentBinding().add(index, Codecs.encode(value, index));
    return this;
  }

  protected abstract int getColumnIndex(String name);

  @Nonnull
  protected Binding getCurrentBinding() {
    return currentBinding;
  }

  public Flux<org.mariadb.r2dbc.api.MariadbResult> toResult(
      final Protocol protocol,
      Flux<ServerMessage> messages,
      ExceptionFactory factory,
      AtomicReference<ServerPrepareResult> prepareResult) {
    return messages
        .doOnDiscard(ReferenceCounted.class, ReferenceCountUtil::release)
        .windowUntil(it -> it.resultSetEnd())
        .map(
            dataRow ->
                new MariadbResult(
                    protocol,
                    prepareResult,
                    dataRow,
                    factory,
                    generatedColumns,
                    client.getVersion().supportReturning(),
                    configuration));
  }

  protected void clearBindings(Iterator<Binding> iterator, AtomicBoolean canceled) {
    canceled.set(true);
    while (iterator.hasNext()) {
      iterator.next().clear();
    }
    this.bindings.forEach(Binding::clear);
  }

  protected int getExpectedSize() {
    return expectedSize;
  }
}
