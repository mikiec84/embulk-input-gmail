package org.embulk.input.gmail;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

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

        // Gmail search query "after: xxx". (optional, default: null)
        // Concat this config string, after "query" config string.
        // You use if '-o' option.
        @Config("after")
        @ConfigDefault("null")
        public Optional<String> getAfter();

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

        // ConfigDiff を作成し、after に now をセット
        List<TaskReport> taskReportList =
                control.run(taskSource, schema, taskCount);
        ConfigDiff configDiff = Exec.newConfigDiff();
        for (TaskReport taskReport : taskReportList) {
            final String label = "after";
            final String after = taskReport.get(String.class, label, null);
            if (after != null) {
                configDiff.set(label, after);
            }
        }
        return configDiff;
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

        // user 取得
        String user = task.getUser();
        log.info("Query user: '{}'", user);

        // query 文字列組み立て
        String query = task.getQuery();

        String now = String.valueOf(System.currentTimeMillis() / 1000L);
        Optional<String> after = task.getAfter();
        for (String p : after.asSet()) {
            query += " before:" + now;
            query += " after:" + p;
        }

        log.info("Send query : '{}'", query);

        // Visitor 作成
        BufferAllocator allocator = task.getBufferAllocator();
        PageBuilder pageBuilder = new PageBuilder(allocator, schema, output);

        GmailWrapper.Result result;
        try {
            GmailWrapper gmail = new GmailWrapper(
                    task.getClientSecretPath(),
                    task.getTokensDirectory());
            result = gmail.search(user, query);
        } catch (IOException|GeneralSecurityException e) {
            log.error("{}", e.getClass(), e);

            TaskReport taskReport = Exec.newTaskReport();
            return taskReport;
        }


        // success messages.
        for (GmailWrapper.Message message : result.getSuccessMessages()) {
            // Visitor 作成
            ColumnVisitor visitor = new ColumnVisitorImpl(message, task, pageBuilder);
            // スキーマ解析
            schema.visitColumns(visitor);
            // 編集したレコードを追加
            pageBuilder.addRecord();
        }
        pageBuilder.finish();

        // TODO: output failed message info to log.

        TaskReport taskReport = Exec.newTaskReport();
        taskReport.set("after", now);

        return taskReport;
    }

    @Override
    public ConfigDiff guess(ConfigSource config)
    {
        return Exec.newConfigDiff();
    }

    class ColumnVisitorImpl implements ColumnVisitor {
        private final GmailWrapper.Message message;
        private final TimestampParser[] timestampParsers;
        private final PageBuilder pageBuilder;

        ColumnVisitorImpl(GmailWrapper.Message message, PluginTask task, PageBuilder pageBuilder) {
            this.message = message;
            this.pageBuilder = pageBuilder;

            this.timestampParsers = Timestamps.newTimestampColumnParsers(
                    task, task.getColumns());
        }

        @Override
        public void booleanColumn(Column column) {
            String value = message.getHeaders().get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                pageBuilder.setBoolean(column, Boolean.parseBoolean(value));
            }
        }

        @Override
        public void longColumn(Column column) {
            String value = message.getHeaders().get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                try {
                    pageBuilder.setLong(column, Long.parseLong(value));
                } catch (NumberFormatException e) {
                    log.error("NumberFormatError: Header: `{}:{}`", column.getName(), value);
                    log.error("{}", e);
                    pageBuilder.setNull(column);
                }
            }
        }

        @Override
        public void doubleColumn(Column column) {
            String value = message.getHeaders().get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                try {
                    pageBuilder.setDouble(column, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    log.error("NumberFormatError: Header: `{}:{}`", column.getName(), value);
                    log.error("{}", e);
                    pageBuilder.setNull(column);
                }
            }
        }

        @Override
        public void stringColumn(Column column) {
            if (column.getName().equals("Body")) {
                String value = message.getBody().orElse(null);
                if (value == null) {
                    pageBuilder.setNull(column);
                } else {
                    pageBuilder.setString(column, value);
                }
            } else {
                String value = message.getHeaders().get(column.getName());
                if (value == null) {
                    pageBuilder.setNull(column);
                } else {
                    pageBuilder.setString(column, value);
                }
            }
        }

        @Override
        public void jsonColumn(Column column) {
            throw new UnsupportedOperationException("This plugin doesn't support json type. Please try to upgrade version of the plugin using 'embulk gem update' command. If the latest version still doesn't support json type, please contact plugin developers, or change configuration of input plugin not to use json type.");
        }

        @Override
        public void timestampColumn(Column column) {
            String value = message.getHeaders().get(column.getName());
            if (value == null) {
                pageBuilder.setNull(column);
            } else {
                try {
                    Timestamp timestamp = timestampParsers[column.getIndex()]
                            .parse(value);
                    pageBuilder.setTimestamp(column, timestamp);
                } catch (TimestampParseException e) {
                    log.error("TimestampParseError: Header: `{}:{}`", column.getName(), value);
                    log.error("{}", e);
                    pageBuilder.setNull(column);
                }
            }
        }
    }
}
