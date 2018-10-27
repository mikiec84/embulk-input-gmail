package org.embulk.input.gmail;

import java.util.List;

import com.google.common.base.Optional;

import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.Task;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.spi.Exec;
import org.embulk.spi.InputPlugin;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.embulk.spi.SchemaConfig;

import org.slf4j.Logger;

public class GmailInputPlugin
        implements InputPlugin
{
    public interface PluginTask
            extends Task
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

        throw new UnsupportedOperationException("GmailInputPlugin.run method is not implemented yet");
    }

    @Override
    public ConfigDiff guess(ConfigSource config)
    {
        return Exec.newConfigDiff();
    }
}
