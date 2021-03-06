/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.shardingproxy.backend.jdbc.connection;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import io.shardingsphere.transaction.api.TransactionType;
import io.shardingsphere.transaction.core.loader.ShardingTransactionHandlerRegistry;
import io.shardingsphere.transaction.spi.ShardingTransactionHandler;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

/**
 * Proxy transaction manager.
 *
 * @author zhaojun
 */
@RequiredArgsConstructor
public final class BackendTransactionManager implements TransactionManager {
    
    private final BackendConnection connection;
    
    @Override
    public void begin() {
        Optional<ShardingTransactionHandler> shardingTransactionHandler = getShardingTransactionHandler(connection);
        if (!connection.getStateHandler().isInTransaction()) {
            connection.getStateHandler().getAndSetStatus(ConnectionStatus.TRANSACTION);
            connection.releaseConnections(false);
        }
        if (!shardingTransactionHandler.isPresent()) {
            new LocalTransactionManager(connection).begin();
        } else if (TransactionType.XA == shardingTransactionHandler.get().getTransactionType()) {
            shardingTransactionHandler.get().begin();
        }
    }
    
    @Override
    public void commit() throws SQLException {
        Optional<ShardingTransactionHandler> shardingTransactionHandler = getShardingTransactionHandler(connection);
        if (!shardingTransactionHandler.isPresent()) {
            new LocalTransactionManager(connection).commit();
        } else if (TransactionType.XA == shardingTransactionHandler.get().getTransactionType()) {
            shardingTransactionHandler.get().commit();
            connection.getStateHandler().getAndSetStatus(ConnectionStatus.TERMINATED);
        }
    }
    
    @Override
    public void rollback() throws SQLException {
        Optional<ShardingTransactionHandler> shardingTransactionHandler = getShardingTransactionHandler(connection);
        if (!shardingTransactionHandler.isPresent()) {
            new LocalTransactionManager(connection).rollback();
        } else if (TransactionType.XA == shardingTransactionHandler.get().getTransactionType()) {
            shardingTransactionHandler.get().rollback();
            connection.getStateHandler().getAndSetStatus(ConnectionStatus.TERMINATED);
        }
    }
    
    private Optional<ShardingTransactionHandler> getShardingTransactionHandler(final BackendConnection connection) {
        TransactionType transactionType = connection.getTransactionType();
        ShardingTransactionHandler result = ShardingTransactionHandlerRegistry.getHandler(transactionType);
        if (null != transactionType && transactionType != TransactionType.LOCAL) {
            Preconditions.checkNotNull(result, String.format("Cannot find transaction manager of [%s]", transactionType));
        }
        return Optional.fromNullable(result);
    }
}
