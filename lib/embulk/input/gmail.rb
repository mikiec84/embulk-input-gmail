Embulk::JavaPlugin.register_input(
  "gmail", "org.embulk.input.gmail.GmailInputPlugin",
  File.expand_path('../../../../classpath', __FILE__))
