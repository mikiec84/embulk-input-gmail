# Gmail input plugin for Embulk

 Gmail API の検索結果を取得します。

## Overview

* **Plugin type**: input
* **Resume supported**: no
* **Cleanup supported**: no
* **Guess supported**: no


## Pre setting

You need get tokens, before `embulk run`.

```sh
java -cp "/PATH/TO/GEM_DIR/classpath/*;/PATH/TO/embulk" org.embulk.input.gmail.GoogleCredentialCreator /PATH/TO/client_secret.json /PATH/TO/tokens
```

Set`client_secret.json` and `tokens` path, to `config.yaml`.(See [Configuration](#Configuration) and [Example](#Example))


## Configuration

- **client_secret**: client secret json file of Google APIs. (string, required)
- **tokens_directory**: tokens directory of Gmail API Client Library for Java. (string, required)
- **user**: search user. (string, default: `me`)
- **query**: search query. (string, default: ``(empty string))
- **after**: Gmail search query "after: xxx".
                  Concat this config string, after "query" config string.
                  You use if '-c' option. (string, default: null)

## Example

### basic

```yaml
in:
  type: gmail
  client_secret: ./client_secret_xxx.json
  tokens_directory: ./tokens
  query: "\"Google アラート\""
  columns:
    - {name: Subject, type: string}
    - {name: From, type: string}
    - {name: To, type: string}
    - {name: Date, type: timestamp, format: "%a, %d %b %Y %H:%M:%S %Z"}
    - {name: Body, type: string}
```

Sending query is `"Google アラート"`.

### Use `-c` option.

```yaml
in:
  type: gmail
  client_secret: ./client_secret_xxx.json
  tokens_directory: ./tokens
  query: "\"Google アラート\""
  after: 2018/10/31 # automatically update.
  # after: 1540929600 # you can use unixtime.
  columns:
    - {name: Subject, type: string}
    - {name: From, type: string}
    - {name: To, type: string}
    - {name: Date, type: timestamp, format: "%a, %d %b %Y %H:%M:%S %Z"}
    - {name: Body, type: string}
```

Sending query is `"Google アラート" before:CURRENT_TIME after:2018/10/31`.
Next time, `CURRENT_TIME` to set in  `after`.


# TODO

- [x] : `-c` option
- [ ] : Use option perser in GoogleCredentialCreator


## Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```
