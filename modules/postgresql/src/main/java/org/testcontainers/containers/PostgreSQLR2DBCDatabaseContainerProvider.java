package org.testcontainers.containers;

import com.google.auto.service.AutoService;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import lombok.NonNull;
import org.testcontainers.r2dbc.AbstractR2DBCDatabaseContainerProvider;
import org.testcontainers.r2dbc.R2DBCDatabaseContainer;
import org.testcontainers.r2dbc.R2DBCDatabaseContainerProvider;

import javax.annotation.Nullable;

@AutoService(R2DBCDatabaseContainerProvider.class)
public final class PostgreSQLR2DBCDatabaseContainerProvider extends AbstractR2DBCDatabaseContainerProvider {

    static final String DRIVER = PostgresqlConnectionFactoryProvider.POSTGRESQL_DRIVER;

    public PostgreSQLR2DBCDatabaseContainerProvider() {
        super(DRIVER);
    }

    @Override
    public R2DBCDatabaseContainer doCreateContainer(ConnectionFactoryOptions options) {
        String image = String.format(
            "%s:%s",
            options.hasOption(IMAGE_OPTION)
                ? options.getValue(IMAGE_OPTION)
                : PostgreSQLContainer.IMAGE,
            options.getRequiredValue(IMAGE_TAG_OPTION)
        );
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(image)
            .withDatabaseName(options.getRequiredValue(ConnectionFactoryOptions.DATABASE));

        if (Boolean.TRUE.equals(options.getValue(REUSABLE_OPTION))) {
            container.withReuse(true);
        }
        return new PostgreSQLR2DBCDatabaseContainer(container);
    }

    @Nullable
    @Override
    public ConnectionFactoryMetadata getMetadata(ConnectionFactoryOptions options) {
        ConnectionFactoryOptions.Builder builder = options.mutate();
        if (!options.hasOption(ConnectionFactoryOptions.USER)) {
            builder.option(ConnectionFactoryOptions.USER, PostgreSQLContainer.DEFAULT_USER);
        }
        if (!options.hasOption(ConnectionFactoryOptions.PASSWORD)) {
            builder.option(ConnectionFactoryOptions.PASSWORD, PostgreSQLContainer.DEFAULT_PASSWORD);
        }
        return super.getMetadata(builder.build());
    }
}
