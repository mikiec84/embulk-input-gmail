package org.embulk.input.gmail;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigInject;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.spi.BufferAllocator;
import org.embulk.spi.Column;
import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.Exec;
import org.embulk.spi.InputPlugin;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.embulk.spi.SchemaConfig;
import org.embulk.spi.time.Timestamp;
import org.embulk.spi.time.TimestampParseException;
import org.embulk.spi.time.TimestampParser;
import org.embulk.spi.util.Timestamps;

import org.slf4j.Logger;

public class GmailInputPlugin
        implements InputPlugin
{
    public interface PluginTask
            extends Task, TimestampParser.Task
    {
        // Gmail client secret json path. (required string)
        @Config("client_secret")
        public String getClientSecretPath();

        // Gmail API tokens directory. (required string)
        @Config("tokens_directory")
        public String getTokensDirectory();

        // Gmail search user. (optional, default: "me")
        @Config("user")
        @ConfigDefault("\"me\"")
        public String getUser();

        // Gmail search query. (optional, default: "")
        @Config("query")
        @ConfigDefault("\"\"")
        public String getQuery();

        // Gmail search query "after_than: xxx". (optional, default: null)
        // Concat this config string, after "query" config string.
        // You use if '-o' option.
        @Config("after_than")
        @ConfigDefault("null")
        public Optional<String> getAfterThan();

        // schema
        @Config("columns")
        public SchemaConfig getColumns();

        // 謎。バッファアロケーターの実装を定義？
        @ConfigInject
        public BufferAllocator getBufferAllocator();
    }

    private Logger log = Exec.getLogger(GmailInputPlugin.class);

    @Override
    public ConfigDiff transaction(ConfigSource config,
            InputPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);

        Schema schema = task.getColumns().toSchema();
        int taskCount = 1;  // number of run() method calls

        return resume(task.dump(), schema, taskCount, control);
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
            Schema schema, int taskCount,
            InputPlugin.Control control)
    {
        control.run(taskSource, schema, taskCount);
        return Exec.newConfigDiff();
    }

    @Override
    public void cleanup(TaskSource taskSource,
            Schema schema, int taskCount,
            List<TaskReport> successTaskReports)
    {
    }

    @Override
    public TaskReport run(TaskSource taskSource,
            Schema schema, int taskIndex,
            PageOutput output)
    {
        PluginTask task = taskSource.loadTask(PluginTask.class);

        log.info("Try login use '{}' and '{}'.", task.getClientSecretPath(), task.getTokensDirectory());

        // query 文字列組み立て
        String query = task.getQuery();
        Optional<String> afterThan = task.getAfterThan();
        for (String p : afterThan.asSet()) {
            query += " after_than:" + p;
        }

        log.info("Send query : '{}'", query);

        // Visitor 作成
        BufferAllocator allocator = task.getBufferAllocator();
        PageBuilder pageBuilder = new PageBuilder(allocator, schema, output);
        Map<String, String> row = new HashMap<>();
        row.put("Subject", "test subject.");
        row.put("Body", "test body.");

        try {
        GmailWrapper gmail = new GmailWrapper(
                task.getClientSecretPath(),
                task.getTokensDirectory());
        } catch (IOException|GeneralSecurityException e) {
            log.error("{}", e.getClass(), e);

            TaskReport taskReport = Exec.newTaskReport();
            return taskReport;
        }

        // for
            // Visitor 作成
            ColumnVisitor visitor = new ColumnVisitorImpl(row, task, pageBuilder);
            // スキーマ解析
            schema.visitColumns(visitor);
            // 編集したレコードを追加
            pageBuilder.addRecord();
        // }
        pageBuilder.finish();

        TaskReport taskReport = Exec.newTaskReport();
        return taskReport;
    }

    @Override
    public ConfigDiff guess(ConfigSource config)
    {
        return Exec.newConfigDiff();
    }

    class ColumnVisitorImpl implements ColumnVisitor {
        private final Map<String, String> row;
        private final TimestampParser[] timestampParsers;
        private final PageBuilder pageBuilder;

        ColumnVisitorImpl(Map<String, String> row, PluginTask task, PageBuilder pageBuilder) {
            this.row = row;
            this.pageBuilder = pageBuilder;

            this.timestampParsers = Timestamps.newTimestampColumnParsers(
                    task, task.getColumns());
        }

        @Override
        public void booleanColumn(Column column) {
            String value = row.get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                pageBuilder.setBoolean(column, Boolean.parseBoolean(value));
            }
        }

        @Override
        public void longColumn(Column column) {
            String value = row.get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                try {
                    pageBuilder.setLong(column, Long.parseLong(value));
                } catch (NumberFormatException e) {
                    log.error("NumberFormatError: Row: {}", row);
                    log.error("{}", e);
                    pageBuilder.setNull(column);
                }
            }
        }

        @Override
        public void doubleColumn(Column column) {
            String value = row.get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                try {
                    pageBuilder.setDouble(column, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    log.error("NumberFormatError: Row: {}", row);
                    log.error("{}", e);
                    pageBuilder.setNull(column);
                }
            }
        }

        @Override
        public void stringColumn(Column column) {
            String value = row.get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                pageBuilder.setString(column, value);
            }
        }

        @Override
        public void jsonColumn(Column column) {
            throw new UnsupportedOperationException("This plugin doesn't support json type. Please try to upgrade version of the plugin using 'embulk gem update' command. If the latest version still doesn't support json type, please contact plugin developers, or change configuration of input plugin not to use json type.");
        }

        @Override
        public void timestampColumn(Column column) {
            String value = row.get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                try {
                    Timestamp timestamp = timestampParsers[column.getIndex()]
                            .parse(value);
                    pageBuilder.setTimestamp(column, timestamp);
                } catch (TimestampParseException e) {
                    log.error("TimestampParseError: Row: {}", row);
                    log.error("{}", e);
                    pageBuilder.setNull(column);
                }
            }
        }
    }
}
